package pt.sonora.app;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded async covers and real PCM envelopes. No encoded bytes or invented audio waveforms. */
final class MediaArtwork implements AutoCloseable {
  final Context context;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler main = new Handler(Looper.getMainLooper());
  final LruCache<String, Bitmap> covers = new LruCache<>(32);
  final LruCache<String, float[]> waves = new LruCache<>(32);
  final Set<String> pending = new HashSet<>(), failed = new HashSet<>();
  volatile boolean closed;

  MediaArtwork(Context c) {
    context = c.getApplicationContext();
  }

  String key(Project.Clip c) {
    return c.uri + ":" + c.inMs;
  }

  Bitmap cover(Project.Clip c, View owner) {
    String key = key(c);
    Bitmap result = covers.get(key);
    if (result == null) request(c, owner, false);
    return result;
  }

  float[] wave(Project.Clip c, View owner) {
    float[] result = waves.get(c.uri);
    if (result == null) request(c, owner, true);
    return result;
  }

  void bind(ImageView view, Project p) {
    Project.Clip clip = null;
    for (Project.Clip c : p.clips)
      if (!c.type.equals("audio")) {
        clip = c;
        break;
      }
    if (clip == null) return;
    Project.Clip chosen = clip;
    Bitmap ready = covers.get(key(chosen));
    if (ready != null) {
      view.setImageBitmap(ready);
      return;
    }
    worker.execute(
        () -> {
          Bitmap b = null;
          try {
            b = decode(chosen);
            covers.put(key(chosen), b);
          } catch (Exception ignored) {
          }
          Bitmap done = b;
          main.post(
              () -> {
                if (!closed && done != null) view.setImageBitmap(done);
              });
        });
  }

  void request(Project.Clip clip, View owner, boolean audio) {
    String key = (audio ? "wave:" : "cover:") + (audio ? clip.uri : key(clip));
    synchronized (pending) {
      if (closed || pending.contains(key) || failed.contains(key)) return;
      pending.add(key);
    }
    worker.execute(
        () -> {
          Bitmap bitmap = null;
          float[] envelope = null;
          try {
            if (audio) {
              File temp = File.createTempFile("sonora-wave-", ".pcm", context.getCacheDir());
              try {
                pt.sonora.offline.LegacyBridge.Decoded data =
                    pt.sonora.offline.LegacyBridge.decode(
                        context,
                        Uri.parse(clip.uri),
                        temp,
                        text -> {
                          if (closed || Thread.currentThread().isInterrupted())
                            throw new InterruptedIOException();
                        });
                envelope = envelope(temp, data.channels);
              } finally {
                temp.delete();
              }
            } else bitmap = decode(clip);
          } catch (Exception failure) {
            synchronized (pending) {
              failed.add(key);
            }
          }
          Bitmap cover = bitmap;
          float[] wave = envelope;
          main.post(
              () -> {
                synchronized (pending) {
                  pending.remove(key);
                }
                if (closed) return;
                if (cover != null) covers.put(key(clip), cover);
                if (wave != null) waves.put(clip.uri, wave);
                owner.invalidate();
              });
        });
  }

  Bitmap decode(Project.Clip clip) throws Exception {
    Bitmap source;
    if (clip.type.equals("image")) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(clip.uri))) {
        BitmapFactory.decodeStream(in, null, options);
      }
      options.inJustDecodeBounds = false;
      options.inSampleSize = 1;
      while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 320)
        options.inSampleSize *= 2;
      try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(clip.uri))) {
        source = BitmapFactory.decodeStream(in, null, options);
      }
    } else {
      MediaMetadataRetriever r = new MediaMetadataRetriever();
      try {
        r.setDataSource(context, Uri.parse(clip.uri));
        source =
            Build.VERSION.SDK_INT >= 27
                ? r.getScaledFrameAtTime(
                    clip.inMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 192, 108)
                : r.getFrameAtTime(clip.inMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
      } finally {
        r.release();
      }
    }
    if (source == null) throw new IOException("Sem miniatura");
    float factor = Math.min(1, 192f / Math.max(source.getWidth(), source.getHeight()));
    Bitmap small =
        Bitmap.createScaledBitmap(
            source,
            Math.max(1, (int) (source.getWidth() * factor)),
            Math.max(1, (int) (source.getHeight() * factor)),
            true);
    if (small != source) source.recycle();
    return small;
  }

  static float[] envelope(File file, int channels) throws IOException {
    float[] result = new float[128];
    long frames = file.length() / (channels * 2);
    try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
      byte[] b = new byte[1024 * channels * 2];
      for (int i = 0; i < 128; i++) {
        in.seek(Math.min(Math.max(0, frames - 1024), i * frames / 128) * channels * 2);
        int n = in.read(b);
        double sum = 0;
        int count = 0;
        for (int j = 0; j + 1 < n; j += 2) {
          int v = (short) ((b[j] & 255) | (b[j + 1] << 8));
          sum += (double) v * v;
          count++;
        }
        result[i] = (float) Math.min(1, Math.sqrt(sum / Math.max(1, count)) / 12000.0);
      }
    }
    return result;
  }

  public void close() {
    closed = true;
    worker.shutdownNow();
    covers.evictAll();
    waves.evictAll();
  }
}
