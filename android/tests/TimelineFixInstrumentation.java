package pt.sonora.app;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import java.io.*;

/** Real Android renderer/codec checks for the reported sequence and audio-only regressions. */
public final class TimelineFixInstrumentation extends Instrumentation {
  int assertions;

  void check(boolean success, String detail) {
    assertions++;
    if (!success) throw new AssertionError(detail);
  }

  void waitImports(EditorController controller) throws Exception {
    long end = SystemClock.elapsedRealtime() + 45000;
    final boolean[] pending = {true};
    do {
      runOnMainSync(() -> pending[0] = controller.importing > 0);
      if (!pending[0]) return;
      SystemClock.sleep(100);
    } while (SystemClock.elapsedRealtime() < end);
    throw new AssertionError("editor import did not finish within 45 seconds");
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    File dir =
        new File(getTargetContext().getCacheDir(), "timeline-fix-" + System.currentTimeMillis());
    dir.mkdirs();
    try {
      Context context = getTargetContext();
      Project project = new Project();
      project.effect = "none";
      project.transition = "none";
      for (int color : new int[] {Color.RED, Color.GREEN}) {
        File file = new File(dir, "image-" + color + ".png");
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (OutputStream out = new FileOutputStream(file)) {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        Project.Clip clip = new Project.Clip();
        clip.type = "image";
        clip.uri = Uri.fromFile(file).toString();
        clip.name = file.getName();
        clip.lengthMs = 5000;
        clip.sourceDurationMs = Project.MAX_MS;
        ProjectActions.appendVisual(project, clip);
      }
      try (SceneRenderer renderer = new SceneRenderer(context, project)) {
        Bitmap bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(bitmap), 160, 90, 1000, new Spectrum.Features());
        check(Color.red(bitmap.getPixel(80, 45)) > 220, "first image appears in first interval");
        renderer.draw(new Canvas(bitmap), 160, 90, 6000, new Spectrum.Features());
        check(Color.green(bitmap.getPixel(80, 45)) > 220, "second image appears after first");
        bitmap.recycle();
      }
      runOnMainSync(
          () -> {
            TimelineView timeline = new TimelineView(context);
            timeline.update(project, project.clips.get(1).id, 6000);
            check(
                timeline.rows.size() == 2 && timeline.laneCount == 1,
                "two image clips share one visible timeline lane");
            timeline.measure(
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
            timeline.layout(0, 0, timeline.getMeasuredWidth(), timeline.getMeasuredHeight());
            check(
                timeline.bounds(project.clips.get(0), 0).top
                    == timeline.bounds(project.clips.get(1), 1).top,
                "actual clip rectangles share the same row");
            check(
                timeline.bounds(project.clips.get(0), 0).right
                    <= timeline.bounds(project.clips.get(1), 1).left,
                "consecutive clip rectangles do not visually overlap");
            android.widget.FrameLayout host = new android.widget.FrameLayout(context);
            host.addView(timeline);
            final String[] selected = {null};
            timeline.listener =
                new TimelineView.Listener() {
                  public void select(Project.Clip clip) {
                    selected[0] = clip.id;
                  }

                  public void seek(long ms) {}

                  public void changed() {}
                };
            RectF secondBox = timeline.bounds(project.clips.get(1), 1);
            MotionEvent down =
                MotionEvent.obtain(
                    0, 0, MotionEvent.ACTION_DOWN, secondBox.centerX(), secondBox.centerY(), 0);
            timeline.onTouchEvent(down);
            down.recycle();
            MotionEvent up =
                MotionEvent.obtain(
                    0, 10, MotionEvent.ACTION_UP, secondBox.centerX(), secondBox.centerY(), 0);
            timeline.onTouchEvent(up);
            up.recycle();
            check(
                project.clips.get(1).id.equals(selected[0]),
                "touch selects the second clip on a shared lane");
          });
      File video = new File(dir, "source.mp4");
      try (InputStream in = getContext().getAssets().open("source.mp4");
          OutputStream out = new FileOutputStream(video)) {
        byte[] bytes = new byte[8192];
        int n;
        while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
      }
      Project.Clip clip = new Project.Clip();
      clip.uri = Uri.fromFile(video).toString();
      clip.name = "video";
      clip.type = "video";
      clip.hasAudio = true;
      clip.lengthMs = clip.sourceDurationMs = 1000;
      Project audioProject = new Project();
      audioProject.clips.add(clip);
      ProjectActions.audioOnly(audioProject, clip);
      runOnMainSync(
          () -> {
            TimelineView timeline = new TimelineView(context);
            timeline.update(audioProject, clip.id, 0);
            check(
                timeline.rows.size() == 1
                    && timeline.laneCount == 1
                    && !timeline.rows.get(0).original,
                "audio extraction has one real audio row without virtual duplicate");
          });
      check(video.isFile(), "audio-only keeps original video file");
      long start = SystemClock.elapsedRealtime();
      try (PreviewVideoDecoder decoder =
          new PreviewVideoDecoder(context, Uri.fromFile(video).toString())) {
        int frames = 0;
        for (long ms = 0; ms <= 750; ms += 50) {
          Bitmap frame = null;
          for (int retry = 0; retry < 8 && decoder.lastUs < ms * 1000; retry++) {
            if (frame != null) frame.recycle();
            frame = decoder.at(ms * 1000, 160, 120);
          }
          if (frame != null) {
            frames++;
            frame.recycle();
          }
        }
        check(
            frames >= 12 && decoder.lastUs >= 750000,
            "sequential codec advances through preview frames");
        Bitmap back = decoder.at(100000, 160, 120);
        for (int retry = 0; retry < 8 && decoder.lastUs < 100000; retry++) {
          if (back != null) back.recycle();
          back = decoder.at(100000, 160, 120);
        }
        check(
            back != null && decoder.lastUs >= 100000 && decoder.lastUs < 250000,
            "backward seek resets decoder");
        if (back != null) back.recycle();
      }
      android.content.Intent launch = new android.content.Intent(context, MainActivity.class);
      launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      MainActivity activity = (MainActivity) startActivitySync(launch);
      EditorController controller = activity.editor;
      runOnMainSync(
          () -> {
            controller.enterEditor(new Project(), false);
            controller.importMedia(Uri.fromFile(new File(dir, "image-" + Color.RED + ".png")), 12);
            controller.importMedia(
                Uri.fromFile(new File(dir, "image-" + Color.GREEN + ".png")), 12);
          });
      waitImports(controller);
      runOnMainSync(
          () -> {
            check(
                controller.project.clips.size() == 2
                    && controller.project.clips.get(1).startMs == 5000
                    && controller.timeline.laneCount == 1,
                "editor import puts two images sequentially in one lane");
            controller.enterEditor(new Project(), false);
            controller.importMedia(Uri.fromFile(video), 11);
          });
      waitImports(controller);
      runOnMainSync(
          () -> {
            Project.Clip imported = controller.project.clips.get(0);
            check(
                imported.type.equals("video") && imported.hasAudio && imported.originalAudio,
                "video import preserves original sound");
            android.app.AlertDialog choice = controller.extractChoice(imported);
            choice.getListView().performItemClick(choice.getListView().getChildAt(0), 0, 0);
            check(
                controller.project.clips.size() == 1
                    && controller.project.clips.get(0).type.equals("audio")
                    && controller.timeline.rows.size() == 1,
                "actual extraction menu leaves a single audio row");
            controller.enterEditor(new Project(), false);
            controller.importMedia(Uri.fromFile(video), 16);
          });
      waitImports(controller);
      runOnMainSync(
          () -> {
            check(
                controller.project.clips.size() == 1
                    && controller.project.clips.get(0).type.equals("audio")
                    && controller.timeline.rows.size() == 1,
                "direct video-audio import creates only one audio row");
            activity.finish();
          });
      result.putString(
          "stream",
          "PASS: "
              + assertions
              + " Android renderer/timeline/codec checks; decoding including open/seek took "
              + (SystemClock.elapsedRealtime() - start)
              + " ms (emulator, not phone performance).\n");
      finish(-1, result);
    } catch (Throwable error) {
      StringWriter trace = new StringWriter();
      error.printStackTrace(new PrintWriter(trace));
      result.putString("stream", "FAIL: " + trace);
      finish(0, result);
    } finally {
      File[] files = dir.listFiles();
      if (files != null) for (File file : files) file.delete();
      dir.delete();
    }
  }
}
