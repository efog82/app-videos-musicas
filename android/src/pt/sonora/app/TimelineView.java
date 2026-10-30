package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Four or more editable lanes. Drag clip body to move; edges trim without touching source. */
final class TimelineView extends View {
  interface Listener {
    void select(Project.Clip clip);

    void seek(long ms);

    void changed();
  }

  Project project;
  Listener listener;
  final Paint paint = new Paint(3);
  float scale = .05f, downX;
  long oldStart, oldIn, oldLength;
  String selected;
  Project.Clip dragging;
  int edge;
  final float dp;

  TimelineView(Context c) {
    super(c);
    dp = getResources().getDisplayMetrics().density;
    setContentDescription(
        "Linha temporal multifaixa. Toque para selecionar; arraste clip ou extremidades para"
            + " editar.");
  }

  int labelWidth() {
    return (int) (78 * dp);
  }

  float row() {
    return 45 * dp;
  }

  int lanes() {
    int max = 3;
    if (project != null) for (Project.Clip c : project.clips) max = Math.max(max, c.track);
    return max + 1;
  }

  long playhead;

  void update(Project p, String id, long ms) {
    project = p;
    selected = id;
    playhead = ms;
    requestLayout();
    invalidate();
  }

  @Override
  protected void onMeasure(int w, int h) {
    long duration = project == null ? 10000 : project.durationMs();
    setMeasuredDimension(
        Math.max(
            MeasureSpec.getSize(w), labelWidth() + (int) (duration * scale * dp) + (int) (50 * dp)),
        (int) (30 * dp + row() * (lanes() + 1)));
  }

  @Override
  protected void onDraw(Canvas c) {
    c.drawColor(0xff101629);
    if (project == null) return;
    int label = labelWidth();
    float px = scale * dp;
    paint.setTextSize(11 * dp);
    paint.setTypeface(Typeface.DEFAULT);
    for (int lane = 0; lane < lanes(); lane++) {
      float top = 30 * dp + row() * lane;
      paint.setColor(0xff242d45);
      c.drawLine(0, top, getWidth(), top, paint);
      paint.setColor(0xffb5bdd7);
      c.drawText(
          lane == 0 ? "Vídeo 1" : lane == 1 ? "Imagem" : "Áudio " + (lane - 1),
          6 * dp,
          top + 26 * dp,
          paint);
    }
    long step = scale < .03f ? 10000 : 5000;
    for (long t = 0; t <= project.durationMs(); t += step) {
      paint.setColor(0xffa8b3d2);
      c.drawText(
          String.format(java.util.Locale.ROOT, "%d:%02d", t / 60000, (t / 1000) % 60),
          label + t * px,
          19 * dp,
          paint);
    }
    for (Project.Clip clip : project.clips) {
      float left = label + clip.startMs * px,
          right = label + clip.endMs() * px,
          top = 30 * dp + row() * clip.track + 5 * dp;
      paint.setColor(
          clip.type.equals("audio")
              ? 0xff375a73
              : clip.type.equals("video") ? 0xff64528f : 0xff3d7466);
      RectF rect = new RectF(left, top, Math.max(left + 12 * dp, right), top + 35 * dp);
      c.drawRoundRect(rect, 6 * dp, 6 * dp, paint);
      if (clip.id.equals(selected)) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(0xffd3c6ff);
        c.drawRoundRect(rect, 6 * dp, 6 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
      }
      c.save();
      c.clipRect(rect);
      paint.setColor(Color.WHITE);
      c.drawText(clip.name, left + 8 * dp, top + 23 * dp, paint);
      c.restore();
    }
    float captionY = 30 * dp + row() * lanes();
    paint.setColor(0xffb5bdd7);
    c.drawText("Legendas", 6 * dp, captionY + 25 * dp, paint);
    for (Project.Caption caption : project.captions) {
      paint.setColor(0xff8b6c35);
      c.drawRoundRect(
          label + caption.startMs * px,
          captionY + 5 * dp,
          label + caption.endMs * px,
          captionY + 35 * dp,
          4 * dp,
          4 * dp,
          paint);
    }
    paint.setColor(0xffd9d0ff);
    paint.setStrokeWidth(2 * dp);
    float x = label + playhead * px;
    c.drawLine(x, 25 * dp, x, getHeight(), paint);
  }

  @Override
  public boolean onTouchEvent(android.view.MotionEvent e) {
    if (project == null) return false;
    float px = scale * dp;
    long time =
        Math.max(0, Math.min(project.durationMs(), (long) ((e.getX() - labelWidth()) / px)));
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      downX = e.getX();
      dragging = null;
      int lane = (int) ((e.getY() - 30 * dp) / row());
      for (int i = project.clips.size() - 1; i >= 0; i--) {
        Project.Clip c = project.clips.get(i);
        if (c.track == lane && time >= c.startMs && time <= c.endMs()) {
          dragging = c;
          oldStart = c.startMs;
          oldIn = c.inMs;
          oldLength = c.lengthMs;
          float left = labelWidth() + c.startMs * px, right = labelWidth() + c.endMs() * px;
          edge =
              Math.abs(e.getX() - left) < 14 * dp
                  ? -1
                  : Math.abs(e.getX() - right) < 14 * dp ? 1 : 0;
          selected = c.id;
          listener.select(c);
          break;
        }
      }
      if (dragging == null) listener.seek(time);
      getParent().requestDisallowInterceptTouchEvent(dragging != null);
      invalidate();
      return true;
    }
    if (e.getAction() == MotionEvent.ACTION_MOVE && dragging != null) {
      long delta = (long) ((e.getX() - downX) / px);
      Project.Clip c = dragging;
      if (edge == 0)
        c.startMs = Math.max(0, Math.min(Project.MAX_MS - c.lengthMs, oldStart + delta));
      else if (edge < 0) {
        delta = Math.max(-Math.min(oldStart, oldIn), Math.min(oldLength - 100, delta));
        c.startMs = oldStart + delta;
        c.inMs = oldIn + delta;
        c.lengthMs = oldLength - delta;
      } else
        c.lengthMs =
            Math.max(
                100,
                Math.min(
                    Project.MAX_MS - c.startMs,
                    Math.min(
                        c.type.equals("image") ? Project.MAX_MS : c.sourceDurationMs - c.inMs,
                        oldLength + delta)));
      c.fadeInMs = Math.min(c.fadeInMs, c.lengthMs);
      c.fadeOutMs = Math.min(c.fadeOutMs, c.lengthMs);
      invalidate();
      return true;
    }
    if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
      if (dragging != null) {
        dragging = null;
        listener.changed();
      } else performClick();
      getParent().requestDisallowInterceptTouchEvent(false);
      return true;
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
