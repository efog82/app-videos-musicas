package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.View;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Frame retrieval runs on a single worker, never the UI thread. */
final class PreviewView extends View {
  interface Listener {
    void position(long ms);

    void error(String message);
  }

  final Handler handler = new Handler(Looper.getMainLooper());
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Map<String, MediaPlayer> players = new HashMap<>();
  final Set<String> ready = new HashSet<>();
  MediaPlayer mixPlayer;
  boolean mixReady;
  File wave;
  volatile boolean rendering, closed;
  boolean playing;
  long position, base, epoch;
  Project project = new Project();
  Bitmap frame;
  SceneRenderer scene;
  Listener listener;
  Spectrum.Features features = new Spectrum.Features();
  File analysis;
  float previousBass;

  void setAnalysis(File file) {
    analysis = file;
  }

  final Paint paint = new Paint(3);

  PreviewView(Context c) {
    super(c);
    setContentDescription("Pré-visualização do projeto");
  }

  void updateAnnotations(Project p) {
    try {
      Project snapshot = p.copy();
      project = snapshot;
      worker.execute(
          () -> {
            if (scene != null) scene.project = snapshot;
          });
      render();
    } catch (Exception e) {
      if (listener != null) listener.error(e.getMessage());
    }
  }

  void setProject(Project p) {
    pause();
    try {
      project = p.copy();
    } catch (Exception e) {
      return;
    }
    position = Math.min(position, project.durationMs());
    releasePlayers();
    Project snapshot = project;
    worker.execute(
        () -> {
          if (scene != null) scene.close();
          scene = new SceneRenderer(getContext(), snapshot);
        });
    render();
  }

  void setAudio(File file) {
    if (mixPlayer != null) {
      mixPlayer.release();
      mixPlayer = null;
    }
    mixReady = false;
    wave = file;
    try {
      mixPlayer = new MediaPlayer();
      mixPlayer.setDataSource(file.toString());
      mixPlayer.setOnPreparedListener(
          p -> {
            if (p != mixPlayer || closed) return;
            mixReady = true;
            p.seekTo(position, MediaPlayer.SEEK_CLOSEST);
            if (playing) {
              base = position;
              epoch = SystemClock.elapsedRealtime();
              p.start();
            }
          });
      mixPlayer.setOnErrorListener(
          (p, a, b) -> {
            if (listener != null) listener.error("Não foi possível reproduzir a mistura.");
            return true;
          });
      mixPlayer.prepareAsync();
    } catch (Exception e) {
      if (listener != null) listener.error(e.getMessage());
    }
  }

  void play() {
    if (project.clips.isEmpty()) return;
    if (position >= project.durationMs()) position = 0;
    playing = true;
    base = position;
    epoch = SystemClock.elapsedRealtime();
    if (mixPlayer != null && mixReady) {
      mixPlayer.seekTo(position, MediaPlayer.SEEK_CLOSEST);
      mixPlayer.start();
    }
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  void pause() {
    if (playing)
      position = Math.min(project.durationMs(), base + SystemClock.elapsedRealtime() - epoch);
    playing = false;
    handler.removeCallbacks(tick);
    if (mixPlayer != null && mixReady)
      try {
        mixPlayer.pause();
      } catch (Exception ignored) {
      }
    for (MediaPlayer player : players.values())
      try {
        player.pause();
      } catch (Exception ignored) {
      }
  }

  void seek(long ms) {
    boolean was = playing;
    pause();
    position = Math.max(0, Math.min(project.durationMs(), ms));
    if (mixPlayer != null && mixReady) mixPlayer.seekTo(position, MediaPlayer.SEEK_CLOSEST);
    for (Project.Clip c : project.clips) {
      MediaPlayer p = players.get(c.id);
      if (p != null && ready.contains(c.id) && c.active(position))
        p.seekTo(c.sourceTime(position), MediaPlayer.SEEK_CLOSEST);
    }
    render();
    if (listener != null) listener.position(position);
    if (was) play();
  }

  final Runnable tick =
      new Runnable() {
        public void run() {
          if (!playing || closed) return;
          position =
              Math.min(
                  project.durationMs(),
                  mixPlayer != null && mixReady && mixPlayer.isPlaying()
                      ? mixPlayer.getCurrentPosition()
                      : base + SystemClock.elapsedRealtime() - epoch);
          render();
          if (listener != null) listener.position(position);
          if (position >= project.durationMs()) {
            pause();
            return;
          }
          handler.postDelayed(this, 50);
        }
      };

  void render() {
    if (rendering || closed || getWidth() < 1 || getHeight() < 1) return;
    rendering = true;
    long ms = position;
    float aspect = project.square ? 1 : project.vertical ? 9f / 16 : 16f / 9;
    int width = Math.max(2, Math.min(640, Math.min(getWidth(), (int) (getHeight() * aspect)))),
        height = Math.max(2, (int) (width / aspect));
    worker.execute(
        () -> {
          Bitmap next = null;
          try {
            if (scene == null) scene = new SceneRenderer(getContext(), project);
            next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (analysis != null && analysis.isFile())
              try (RandomAccessFile pcm = new RandomAccessFile(analysis, "r")) {
                features = EditorExporter.features(pcm, ms, previousBass);
                previousBass = features.bass;
              }
            scene.draw(new Canvas(next), width, height, ms, features);
            Bitmap done = next;
            handler.post(
                () -> {
                  if (closed) {
                    done.recycle();
                    rendering = false;
                    return;
                  }
                  Bitmap old = frame;
                  frame = done;
                  if (old != null) old.recycle();
                  rendering = false;
                  invalidate();
                });
          } catch (Exception e) {
            if (next != null) next.recycle();
            handler.post(
                () -> {
                  rendering = false;
                  if (listener != null) listener.error(e.getMessage());
                });
          }
        });
  }

  void releasePlayers() {
    if (mixPlayer != null) {
      mixPlayer.release();
      mixPlayer = null;
    }
    mixReady = false;
    for (MediaPlayer p : players.values())
      try {
        p.release();
      } catch (Exception ignored) {
      }
    players.clear();
    ready.clear();
  }

  void close() {
    closed = true;
    pause();
    releasePlayers();
    worker.execute(
        () -> {
          if (scene != null) scene.close();
        });
    worker.shutdown();
    if (frame != null) {
      frame.recycle();
      frame = null;
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawColor(0xff0d1224);
    if (frame != null) {
      float scale =
          Math.min(getWidth() / (float) frame.getWidth(), getHeight() / (float) frame.getHeight());
      float w = frame.getWidth() * scale, h = frame.getHeight() * scale;
      canvas.drawBitmap(
          frame,
          null,
          new RectF(
              (getWidth() - w) / 2,
              (getHeight() - h) / 2,
              (getWidth() + w) / 2,
              (getHeight() + h) / 2),
          paint);
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    render();
  }
}
