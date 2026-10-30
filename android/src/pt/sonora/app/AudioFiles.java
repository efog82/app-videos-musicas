package pt.sonora.app;

import java.io.*;

final class AudioFiles {
  static void integer(OutputStream out, long value, int bytes) throws IOException {
    for (int i = 0; i < bytes; i++) out.write((int) (value >> (i * 8)) & 255);
  }

  static void wave(File pcm, File wav, int rate, int channels) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(pcm));
        OutputStream out = new BufferedOutputStream(new FileOutputStream(wav))) {
      out.write(new byte[] {'R', 'I', 'F', 'F'});
      integer(out, pcm.length() + 36, 4);
      out.write(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
      integer(out, 16, 4);
      integer(out, 1, 2);
      integer(out, channels, 2);
      integer(out, rate, 4);
      integer(out, rate * channels * 2, 4);
      integer(out, channels * 2, 2);
      integer(out, 16, 2);
      out.write(new byte[] {'d', 'a', 't', 'a'});
      integer(out, pcm.length(), 4);
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
  }
}
