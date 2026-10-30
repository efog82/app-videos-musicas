package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;

/** Runs against the installed app and actual Android codecs, without AndroidX or mocks. */
public class EditorInstrumentation extends Instrumentation {
  int assertions;
  Context target;
  File dir;

  void check(boolean pass, String why) {
    assertions++;
    if (!pass) throw new AssertionError(why);
  }

  void progress(String value) {
    Bundle b = new Bundle();
    b.putString("stream", value + "\n");
    sendStatus(1, b);
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  File fixture(String name) throws Exception {
    File f = new File(dir, name);
    try (InputStream in = getContext().getAssets().open(name);
        OutputStream out = new FileOutputStream(f)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
    return f;
  }

  Project.Clip clip(File file, String type, int track) {
    Project.Clip c = new Project.Clip();
    c.uri = Uri.fromFile(file).toString();
    c.name = file.getName();
    c.type = type;
    c.track = track;
    c.lengthMs = 1000;
    c.sourceDurationMs = 1000;
    c.hasAudio = !type.equals("image");
    return c;
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      target = getTargetContext();
      dir = new File(target.getCacheDir(), "instrumentation-" + System.currentTimeMillis());
      dir.mkdirs();
      File video = fixture("source.mp4"),
          music = fixture("music.wav"),
          image = fixture("image.png");
      progress("1/6 Project persistence and non-destructive reopen");
      Project p = new Project();
      p.name = "Teste Android";
      p.title = "Canção 100% d'amor";
      Project.Clip original = clip(video, "video", 0);
      p.clips.add(original);
      Project.Clip added = clip(music, "audio", 2);
      added.volume = .5f;
      p.clips.add(added);
      p.captions.add(new Project.Caption(0, 1000, "Legenda de teste"));
      ProjectStore store = new ProjectStore(target);
      store.save(p, false);
      Project restored = store.load(new File(store.directory, p.id + ".json"));
      check(restored.clips.size() == 2, "saved clips");
      check(restored.clips.get(0).originalAudio, "original video audio stays enabled by default");
      check(video.exists(), "source unchanged");
      progress("2/6 Real audio decode, original/mute/mix/fade");
      EditorExporter engine = new EditorExporter(target);
      File mixed = new File(dir, "mixed.pcm");
      engine.mix(p, mixed, dir, m -> {});
      check(mixed.length() == 192000, "one second at 48k stereo PCM");
      double[] both = energy(mixed);
      check(
          both[0] > .005 && both[1] > .002,
          "original and music tones present " + Arrays.toString(both));
      original.originalAudio = false;
      File muted = new File(dir, "muted.pcm");
      engine.mix(p, muted, dir, m -> {});
      double[] onlyMusic = energy(muted);
      check(onlyMusic[0] < both[0] * .15, "mute original removes 440Hz");
      check(onlyMusic[1] > .002, "music remains after muting video");
      original.originalAudio = true;
      added.fadeInMs = 1000;
      File faded = new File(dir, "fade.pcm");
      engine.mix(p, faded, dir, m -> {});
      check(
          toneWindow(faded, 100, 880) < toneWindow(faded, 800, 880) * .35,
          "fade attenuates the music track at its start");
      added.fadeInMs = 0;
      progress("3/6 Scene rendering: all visual effects and subtitles");
      Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
      Spectrum.Features f = new Spectrum.Features();
      f.energy = .6f;
      f.bass = .8f;
      f.mid = .5f;
      f.treble = .4f;
      Arrays.fill(f.bins, .5f);
      p.effect = "none";
      try (SceneRenderer scene = new SceneRenderer(target, p)) {
        scene.draw(new Canvas(bitmap), 360, 640, 500, f);
      }
      int[] pixels = new int[360 * 640];
      bitmap.getPixels(pixels, 0, 360, 0, 0, 360, 640);
      int baselineHash = Arrays.hashCode(pixels);
      for (String effect : VisualEffects.IDS) {
        p.effect = effect;
        try (SceneRenderer scene = new SceneRenderer(target, p)) {
          scene.draw(new Canvas(bitmap), 360, 640, 500, f);
          bitmap.getPixels(pixels, 0, 360, 0, 0, 360, 640);
          check(
              effect.equals("none")
                  ? Arrays.hashCode(pixels) == baselineHash
                  : Arrays.hashCode(pixels) != baselineHash,
              "effect changes the rendered scene: " + effect);
        }
      }
      bitmap.recycle();
      p.effect = "bars";
      progress("4/6 Encode MP4 horizontal with original video, mixed audio and captions");
      p.vertical = false;
      File horizontal = new File(target.getExternalFilesDir(null), "test-horizontal.mp4");
      engine.export(p, horizontal, m -> progress(m));
      verify(horizontal, 1280, 720);
      progress("5/6 Encode vertical image/music using the legacy converter and preserve originals");
      File legacy = new File(target.getExternalFilesDir(null), "test-legacy.mp4");
      new Converter(target)
          .convert(Uri.fromFile(music), Uri.fromFile(image), "Legado", true, legacy, m -> {});
      verify(legacy, 720, 1280);
      check(video.exists() && music.exists(), "source media still present");
      progress("6/6 Hardware validation, cancellation and UI tabs");
      Project impossible = p.copy();
      impossible.bitrate = 80000000;
      impossible.resolution = 2160;
      impossible.fps = 60;
      try {
        String codec = ExportOptions.supportedCodec(impossible);
        check(codec != null, "hardware advertises support; no throughput promise");
      } catch (Exception expected) {
        check(
            (expected.getMessage().contains("não anuncia suporte")
                || expected.getMessage().contains("Memória insuficiente")),
            "unsupported profile gives actionable error");
      }
      EditorExporter cancelled = new EditorExporter(target);
      cancelled.cancelled = true;
      try {
        cancelled.export(p, new File(dir, "cancelled.mp4"), m -> {});
        throw new AssertionError("cancel ignored");
      } catch (IOException expected) {
        check(expected.getMessage().contains("cancelada"), "cancel reported");
      }
      Intent intent = new Intent(target, MainActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      Activity activity = startActivitySync(intent);
      waitForIdleSync();
      check(activity != null, "editor launches");
      runOnMainSync(
          () -> {
            MainActivity a = (MainActivity) activity;
            for (String tab : new String[] {"Projeto", "Efeitos", "Áudio", "Legendas", "Exportar"})
              a.showTab(tab);
          });
      waitForIdleSync();
      check(true, "all five tabs render without crash");
      runOnMainSync(activity::finish);
      result.putString(
          "stream",
          "PASS: "
              + assertions
              + " Android assertions; real MediaCodec/MediaMuxer exports validated.\n");
      result.putInt("assertions", assertions);
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      android.util.Log.e("SonoraTests", "Failed", e);
      StringWriter text = new StringWriter();
      e.printStackTrace(new PrintWriter(text));
      result.putString("stream", "FAIL after " + assertions + " assertions:\n" + text);
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (dir != null) EditorExporter.remove(dir);
    }
  }

  double[] energy(File pcm) throws Exception {
    return new double[] {toneWindow(pcm, 0, 440), toneWindow(pcm, 0, 880)};
  }

  double toneWindow(File pcm, int ms, int hz) throws Exception {
    double sin = 0, cos = 0;
    int count = 4800;
    try (RandomAccessFile in = new RandomAccessFile(pcm, "r")) {
      in.seek(ms * 48 * 4);
      for (int i = 0; i < count; i++) {
        int lo = in.readUnsignedByte(), hi = in.readByte();
        double value = (short) (lo | (hi << 8)) / 32768.0;
        double angle = (i + ms * 48) * 2 * Math.PI * hz / 48000;
        sin += value * Math.sin(angle);
        cos += value * Math.cos(angle);
        in.skipBytes(2);
      }
    }
    return Math.hypot(sin, cos) / count;
  }

  void verify(File file, int width, int height) throws Exception {
    check(file.length() > 1000, "encoded output exists");
    MediaExtractor ex = new MediaExtractor();
    try {
      ex.setDataSource(file.toString());
      boolean video = false, audio = false;
      for (int i = 0; i < ex.getTrackCount(); i++) {
        MediaFormat f = ex.getTrackFormat(i);
        String mime = f.getString(MediaFormat.KEY_MIME);
        if (mime.equals("video/avc")) {
          video = true;
          check(
              f.getInteger(MediaFormat.KEY_WIDTH) == width
                  && f.getInteger(MediaFormat.KEY_HEIGHT) == height,
              "video dimensions");
          check(f.getLong(MediaFormat.KEY_DURATION) > 800000, "duration > 0.8 s");
        }
        if (mime.equals("audio/mp4a-latm")) audio = true;
      }
      check(video && audio, "H264 and AAC tracks present");
    } finally {
      ex.release();
    }
  }
}
