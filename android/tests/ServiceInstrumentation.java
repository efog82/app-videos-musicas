package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;

/** Integration of playback preparation, foreground ownership and read-only sharing. */
public class ServiceInstrumentation extends Instrumentation {
  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      android.content.Context context = getTargetContext();
      File source = new File(context.getFilesDir(), "service-test.wav");
      try (InputStream in = getContext().getAssets().open("music.wav");
          OutputStream out = new FileOutputStream(source)) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
      }
      Project p = new Project();
      p.vertical = false;
      Project.Clip c = new Project.Clip();
      c.uri = Uri.fromFile(source).toString();
      c.name = "Teste de reprodução";
      c.type = "audio";
      c.track = 2;
      c.lengthMs = 1000;
      c.sourceDurationMs = 1000;
      p.clips.add(c);
      MainActivity activity =
          (MainActivity)
              startActivitySync(
                  new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      runOnMainSync(
          () -> {
            activity.project = p;
            activity.changed();
            activity.preparePlay();
          });
      long deadline = SystemClock.elapsedRealtime() + 120000;
      while (activity.busy && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
      if (activity.busy || activity.analysisFile == null || !activity.analysisFile.isFile())
        throw new AssertionError(
            "Preview mixture preparation failed: " + activity.status.getText());
      runOnMainSync(
          () -> {
            activity.preview.pause();
            c.lengthMs = 100;
            activity.changed();
            activity.exportVideo();
            activity.finish();
          });
      deadline = SystemClock.elapsedRealtime() + 180000;
      while ((EditorExportService.running || EditorExportService.output.isEmpty())
          && EditorExportService.error.isEmpty()
          && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
      if (!EditorExportService.error.isEmpty()) throw new AssertionError(EditorExportService.error);
      if (EditorExportService.running || EditorExportService.output.isEmpty())
        throw new AssertionError("Background service did not finish");
      File output = new File(EditorExportService.output);
      if (!output.isFile() || output.length() < 1000) throw new AssertionError("No service MP4");
      Uri uri =
          Uri.parse("content://" + context.getPackageName() + ".legacy.videos/" + output.getName());
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        if (in == null || in.read() < 0) throw new AssertionError("Provider cannot read MP4");
      }
      if (!source.exists()) throw new AssertionError("Original source was removed");
      Activity legacy =
          startActivitySync(
              new Intent(context, pt.sonora.offline.MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      if (legacy == null) throw new AssertionError("Compatibility Activity did not launch");
      runOnMainSync(legacy::finish);
      result.putString(
          "stream",
          "PASS: preview mix prepared; export survived Activity closure; valid MP4 accessible"
              + " through read-only provider; original preserved; Sonora 0.3 compatibility UI"
              + " opens.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      android.util.Log.e("SonoraServiceTest", "Failure", error);
      result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
