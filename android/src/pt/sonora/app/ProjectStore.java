package pt.sonora.app;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class ProjectStore {
  final File directory;

  ProjectStore(Context context) {
    directory = new File(context.getFilesDir(), "projects-v1");
    directory.mkdirs();
  }

  void save(Project p, boolean autosave) throws Exception {
    // A copy validates before touching an existing document.
    String json = p.copy().json().toString(2);
    AtomicFile file =
        new AtomicFile(new File(directory, autosave ? "autosave.json" : p.id + ".json"));
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      out.write(json.getBytes(StandardCharsets.UTF_8));
      file.finishWrite(out);
    } catch (Exception e) {
      if (out != null) file.failWrite(out);
      throw e;
    }
  }

  Project load(File file) throws Exception {
    try (InputStream in = new AtomicFile(file).openRead()) {
      return Project.read(read(in));
    }
  }

  File[] projects() {
    File[] f = directory.listFiles((d, n) -> n.endsWith(".json") && !n.equals("autosave.json"));
    return f == null ? new File[0] : f;
  }

  static String read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) != -1) {
      if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Documento demasiado grande");
      out.write(b, 0, n);
    }
    return out.toString("UTF-8");
  }
}
