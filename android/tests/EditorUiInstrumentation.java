package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Regression tests for real imports, visible selection/removal and a reparented fullscreen preview.
 */
public final class EditorUiInstrumentation extends Instrumentation {
  int checks;
  MainActivity activity;

  void check(boolean pass, String message) {
    checks++;
    if (!pass) throw new AssertionError(message);
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  File fixture(String name) throws Exception {
    File file = new File(getTargetContext().getFilesDir(), "ui-" + name);
    try (InputStream in = getContext().getAssets().open(name);
        OutputStream out = new FileOutputStream(file)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
    return file;
  }

  void idle() throws Exception {
    waitForIdleSync();
  }

  void waitImports() throws Exception {
    long end = SystemClock.elapsedRealtime() + 120000;
    while (SystemClock.elapsedRealtime() < end) {
      AtomicBoolean loading = new AtomicBoolean();
      runOnMainSync(() -> loading.set(activity.importing > 0));
      if (!loading.get()) {
        idle();
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Media import timeout");
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      File video = fixture("source.mp4"),
          image = fixture("image.png"),
          audio = fixture("music.wav");
      activity =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      runOnMainSync(
          () -> {
            activity.project = new Project();
            activity.selected = null;
            activity.changed();
            activity.importMedia(android.net.Uri.fromFile(video), 11);
            activity.importMedia(android.net.Uri.fromFile(image), 12);
            activity.importMedia(android.net.Uri.fromFile(audio), 10);
          });
      waitImports();
      check(activity.project.clips.size() == 3, "all three media types imported");
      HashSet<Integer> rows = new HashSet<>();
      for (Project.Clip clip : activity.project.clips) {
        int row = activity.timeline.rowOf(clip.id);
        check(row >= 0, "import is present on the timeline: " + clip.type);
        rows.add(row);
      }
      check(rows.size() == 3, "overlapping video/image/audio get separate visible rows");
      idle();
      check(activity.tracksScroll.getVisibility() == View.VISIBLE, "timeline expands after import");
      check(
          activity.tracksScroll.getScrollY() > 0,
          "new audio element automatically scrolled into view");
      check(
          activity.selectedLabel.getText().toString().contains(audio.getName()),
          "selected element name is visible outside hidden tools");
      check(activity.removeElement.isEnabled(), "persistent remove action is available");
      runOnMainSync(
          () -> {
            activity.preview.seek(200);
            activity.openFullscreen();
          });
      idle();
      check(
          activity.fullscreenDialog != null && activity.fullscreenDialog.isShowing(),
          "fullscreen opens");
      check(
          activity.preview.getParent() == activity.fullscreenHost,
          "same preview moves into fullscreen, avoiding another player");
      check(activity.fullscreenHost.getHeight() > activity.dp(200), "fullscreen uses the display");
      check(activity.fullscreenSeek.getProgress() == 200, "fullscreen preserves playback position");
      runOnMainSync(
          () -> {
            activity.fullscreenSeek.setProgress(400);
            activity.preview.seek(400);
            activity.fullscreenDialog.dismiss();
          });
      idle();
      check(activity.fullscreenDialog == null, "fullscreen closes");
      check(activity.preview.getParent() == activity.root, "preview returns to the editor");
      check(activity.position == 400, "returning from fullscreen preserves position");
      AtomicReference<AlertDialog> confirmation = new AtomicReference<>();
      Project.Clip first = activity.project.clips.get(0);
      runOnMainSync(() -> confirmation.set(activity.confirmRemove(first)));
      idle();
      check(confirmation.get().isShowing(), "remove asks confirmation with original-file notice");
      runOnMainSync(() -> confirmation.get().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
      check(activity.project.clips.size() == 3, "cancel preserves imported element");
      for (Project.Clip clip : new ArrayList<>(activity.project.clips)) {
        int count = activity.project.clips.size();
        runOnMainSync(
            () -> {
              activity.selectElement(clip);
              confirmation.set(activity.confirmRemove(clip));
            });
        idle();
        runOnMainSync(
            () -> confirmation.get().getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        idle();
        check(
            activity.project.clips.size() == count - 1,
            "remove actually deletes the selected project entry: " + clip.type);
        check(activity.timeline.rowOf(clip.id) < 0, "removed entry disappears from timeline");
      }
      check(!activity.removeElement.isEnabled(), "remove is disabled after deleting the last item");
      check(activity.timeline.elements.isEmpty(), "empty timeline after last removal");
      Project saved = activity.store.load(new File(activity.store.directory, "autosave.json"));
      check(saved.clips.isEmpty(), "removed items do not reappear after project reopen");
      check(
          video.exists() && image.exists() && audio.exists(), "all original files remain on disk");
      runOnMainSync(() -> activity.openFullscreen());
      idle();
      check(activity.fullscreenDialog.isShowing(), "empty preview can still open safely");
      runOnMainSync(() -> activity.fullscreenDialog.dismiss());
      runOnMainSync(activity::finish);
      result.putString(
          "stream",
          "PASS: "
              + checks
              + " UI assertions: real imports, separate timeline rows, selection focus,"
              + " cancel/remove every type and last item, persisted removal, originals, fullscreen"
              + " entry/return/position.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      android.util.Log.e("SonoraUiTest", "Failure", error);
      result.putString(
          "stream", "FAIL after " + checks + ": " + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
