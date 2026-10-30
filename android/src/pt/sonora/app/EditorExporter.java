package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.view.Surface;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Bounded-memory local composition. Original files are opened read-only. */
final class EditorExporter {
  final Context context;
  volatile boolean cancelled;

  EditorExporter(Context c) {
    context = c;
  }

  void check() throws IOException {
    if (cancelled || Thread.currentThread().isInterrupted())
      throw new IOException("Exportação cancelada; o projeto foi preservado.");
  }

  File workspace() throws IOException {
    File f = new File(context.getCacheDir(), "export-" + UUID.randomUUID());
    if (!f.mkdirs()) throw new IOException("Não foi possível criar a pasta temporária.");
    return f;
  }

  void export(Project project, File output, Converter.Progress progress) throws Exception {
    if (project.clips.isEmpty())
      throw new IOException("Importa primeiro uma música, vídeo ou imagem.");
    String codecName = ExportOptions.supportedCodec(project);
    check();
    File dir = workspace(),
        mixed = new File(dir, "mix.pcm"),
        audio = new File(dir, "audio.m4a"),
        video = new File(dir, "video.mp4"),
        finalFile = new File(dir, "final.mp4");
    try {
      progress.update("A preparar áudio e mistura…");
      mix(project, mixed, dir, progress);
      check();
      Converter c = new Converter(context);
      c.sampleRate = 48000;
      c.channels = 2;
      c.pcmBytes = mixed.length();
      c.encodeAudio(mixed, audio);
      progress.update("A criar vídeo…");
      video(project, mixed, video, codecName, progress);
      check();
      c.mux(video, audio, finalFile);
      check();
      // Keep the previous result until the new MP4 has completed.
      android.util.AtomicFile destination = new android.util.AtomicFile(output);
      FileOutputStream stream = null;
      try (InputStream in = new FileInputStream(finalFile)) {
        stream = destination.startWrite();
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) != -1) {
          check();
          stream.write(b, 0, n);
        }
        destination.finishWrite(stream);
        stream = null;
      } catch (Exception failure) {
        if (stream != null) destination.failWrite(stream);
        throw failure;
      }
    } finally {
      remove(dir);
    }
  }

  void mix(Project p, File output, File dir, Converter.Progress progress) throws Exception {
    ArrayList<PcmTrack> tracks = new ArrayList<>();
    try {
      int index = 0;
      for (Project.Clip clip : p.clips) {
        check();
        if (clip.type.equals("image")
            || clip.muted
            || (clip.type.equals("video") && (!clip.originalAudio || !clip.hasAudio))) continue;
        progress.update("A ler áudio: " + clip.name);
        File pcm = new File(dir, "source-" + (index++) + ".pcm");
        pt.sonora.offline.LegacyBridge.Decoded decoder =
            pt.sonora.offline.LegacyBridge.decode(
                context,
                Uri.parse(clip.uri),
                pcm,
                text -> {
                  check();
                  progress.update(text);
                });
        check();
        if (decoder.channels > 2)
          throw new IOException("Áudio com mais de dois canais não é suportado nesta versão.");
        tracks.add(new PcmTrack(clip, pcm, decoder.rate, decoder.channels));
      }
      long frames = p.durationMs() * 48L;
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
        byte[] bytes = new byte[4096];
        for (long base = 0; base < frames; base += 1024) {
          check();
          int count = (int) Math.min(1024, frames - base);
          for (int i = 0; i < count; i++) {
            long frame = base + i;
            long ms = frame / 48;
            double left = 0, right = 0;
            for (PcmTrack track : tracks) {
              float gain = AudioMath.gain(track.clip, ms);
              if (gain == 0) continue;
              double source =
                  (track.clip.inMs + (frame / 48.0 - track.clip.startMs)) * track.rate / 1000.0;
              left += track.sample(source, 0) * gain;
              right += track.sample(source, track.channels == 1 ? 0 : 1) * gain;
            }
            short l = AudioMath.clamp(left), r = AudioMath.clamp(right);
            int offset = i * 4;
            bytes[offset] = (byte) l;
            bytes[offset + 1] = (byte) (l >> 8);
            bytes[offset + 2] = (byte) r;
            bytes[offset + 3] = (byte) (r >> 8);
          }
          out.write(bytes, 0, count * 4);
        }
      }
    } finally {
      for (PcmTrack track : tracks) track.close();
    }
  }

  static final class PcmTrack implements AutoCloseable {
    final Project.Clip clip;
    final RandomAccessFile file;
    final int rate, channels;
    final long frames;
    final byte[] cache = new byte[65536];
    long base = -1;
    int count;

    PcmTrack(Project.Clip c, File f, int r, int ch) throws IOException {
      clip = c;
      file = new RandomAccessFile(f, "r");
      rate = r;
      channels = ch;
      frames = file.length() / (2 * ch);
    }

    int at(long frame, int channel) throws IOException {
      if (frame < 0 || frame >= frames) return 0;
      long offset = (frame * channels + channel) * 2;
      if (base < 0 || offset < base || offset + 1 >= base + count) {
        base = offset / 32768 * 32768;
        file.seek(base);
        count = file.read(cache);
      }
      int i = (int) (offset - base);
      return i + 1 < count ? (short) ((cache[i] & 255) | (cache[i + 1] << 8)) : 0;
    }

    double sample(double frame, int channel) throws IOException {
      long a = (long) Math.floor(frame);
      double part = frame - a;
      return at(a, channel) * (1 - part) + at(a + 1, channel) * part;
    }

    public void close() throws IOException {
      file.close();
    }
  }

  void video(Project p, File mixed, File output, String codecName, Converter.Progress progress)
      throws Exception {
    int w = ExportOptions.width(p), h = ExportOptions.height(p), fps = p.fps;
    long duration = p.durationMs() * 1000, frames = (duration * fps + 999999) / 1000000;
    MediaFormat format = MediaFormat.createVideoFormat("video/avc", w, h);
    format.setInteger(
        MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    format.setInteger(MediaFormat.KEY_BIT_RATE, p.bitrate);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
    MediaCodec codec = MediaCodec.createByCodecName(codecName);
    MediaMuxer mux = new MediaMuxer(output.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    Surface surface = null;
    Painter painter = null;
    Bitmap bitmap = null;
    boolean started = false, codecStarted = false;
    float lastBass = 0;
    try (SceneRenderer scene = new SceneRenderer(context, p);
        RandomAccessFile pcm = new RandomAccessFile(mixed, "r")) {
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      surface = codec.createInputSurface();
      codec.start();
      codecStarted = true;
      painter = new Painter(surface, w, h, null, "");
      bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      int track = -1;
      for (long frame = 0; frame <= frames; frame++) {
        check();
        if (frame < frames) {
          long ms = frame * 1000 / fps;
          Spectrum.Features features = features(pcm, ms, lastBass);
          lastBass = features.bass;
          scene.draw(canvas, w, h, ms, features);
          painter.drawFrame(bitmap, frame * 1000000000L / fps);
          if (frame % fps == 0)
            progress.update("A criar vídeo… " + (frame * 100 / frames) + "% (" + w + "×" + h + ")");
        } else codec.signalEndOfInputStream();
        boolean done = false;
        long last = android.os.SystemClock.elapsedRealtime();
        while (true) {
          check();
          int i = codec.dequeueOutputBuffer(info, frame == frames ? 10000 : 0);
          if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (frame < frames) break;
            if (android.os.SystemClock.elapsedRealtime() - last > 30000)
              throw new IOException("O codificador de vídeo não respondeu.");
            continue;
          }
          if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            track = mux.addTrack(codec.getOutputFormat());
            mux.start();
            started = true;
          }
          if (i >= 0) {
            last = android.os.SystemClock.elapsedRealtime();
            ByteBuffer b = codec.getOutputBuffer(i);
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
              if (!started) throw new IOException("Codificador sem formato de saída.");
              b.position(info.offset);
              b.limit(info.offset + info.size);
              mux.writeSampleData(track, b, info);
            }
            done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            codec.releaseOutputBuffer(i, false);
            if (done) break;
          }
        }
        if (done) break;
      }
    } finally {
      if (bitmap != null) bitmap.recycle();
      if (painter != null) painter.close();
      if (codecStarted) codec.stop();
      codec.release();
      if (surface != null) surface.release();
      if (started) mux.stop();
      mux.release();
    }
  }

  static Spectrum.Features features(RandomAccessFile pcm, long ms, float ignoredPrevious)
      throws IOException {
    float previous = ms >= 40 ? Spectrum.analyse(samples(pcm, ms - 40), 48000, 0).bass : 0;
    return Spectrum.analyse(samples(pcm, ms), 48000, previous);
  }

  static short[] samples(RandomAccessFile pcm, long ms) throws IOException {
    long offset = Math.max(0, ms * 48 - 1024) * 4;
    pcm.seek(offset);
    byte[] data = new byte[2048 * 4];
    int n = 0, r;
    while (n < data.length && (r = pcm.read(data, n, data.length - n)) > 0) n += r;
    short[] mono = new short[2048];
    for (int i = 0; i < 2048; i++) {
      int at = i * 4;
      if (at + 3 >= n) break;
      int left = (short) ((data[at] & 255) | (data[at + 1] << 8)),
          right = (short) ((data[at + 2] & 255) | (data[at + 3] << 8));
      mono[i] = (short) ((left + right) / 2);
    }
    return mono;
  }

  static void remove(File f) {
    File[] children = f.listFiles();
    if (children != null) for (File child : children) remove(child);
    f.delete();
  }
}
