package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import java.util.*;

/** Each imported element has a visible, independently selectable row, even when clips overlap. */
final class TimelineView extends View {
  interface Listener {
    void select(Project.Clip clip);

    void seek(long ms);

    void changed();
  }

  Project project;
  Listener listener;
  final TextPaint paint = new TextPaint(3);
  final ArrayList<Project.Clip> elements = new ArrayList<>();
  float scale = .05f, downX;
  long oldStart, oldIn, oldLength;
  String selected;
  Project.Clip dragging;
  int edge;
  final float dp;
  long playhead;

  TimelineView(Context context) {
    super(context);
    dp = getResources().getDisplayMetrics().density;
    setContentDescription(
        "Linha temporal: cada vídeo, imagem e som tem uma linha. Toque num elemento para"
            + " selecionar; arraste para mover ou cortar.");
  }

  int labelWidth() {
    return (int) (68 * dp);
  }

  float row() {
    return 48 * dp;
  }

  int rowOf(String id) {
    for (int i = 0; i < elements.size(); i++) if (elements.get(i).id.equals(id)) return i;
    return -1;
  }

  int rowTop(String id) {
    int index = rowOf(id);
    return index < 0 ? 0 : (int) (28 * dp + index * row());
  }

  void update(Project value, String id, long ms) {
    project = value;
    selected = id;
    playhead = ms;
    elements.clear();
    elements.addAll(value.clips);
    Collections.sort(
        elements,
        (a, b) -> {
          int track = Integer.compare(a.track, b.track);
          return track == 0 ? Long.compare(a.startMs, b.startMs) : track;
        });
    requestLayout();
    invalidate();
  }

  RectF bounds(Project.Clip clip, int index) {
    float left = labelWidth() + clip.startMs * scale * dp, top = 28 * dp + index * row() + 3 * dp;
    return new RectF(
        left,
        top,
        Math.max(left + 76 * dp, labelWidth() + clip.endMs() * scale * dp),
        top + 42 * dp);
  }

  @Override
  protected void onMeasure(int width, int height) {
    long duration = project == null ? 10000 : project.durationMs();
    setMeasuredDimension(
        Math.max(
            MeasureSpec.getSize(width),
            labelWidth() + (int) (duration * scale * dp) + (int) (90 * dp)),
        (int)
            (28 * dp
                + row()
                    * Math.max(
                        1,
                        elements.size()
                            + (project != null && !project.captions.isEmpty() ? 1 : 0))));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawColor(0xff101629);
    if (project == null) return;
    paint.setTextSize(11 * dp);
    paint.setTypeface(Typeface.DEFAULT);
    paint.setStyle(Paint.Style.FILL);
    long step = scale < .03f ? 10000 : 5000;
    for (long t = 0; t <= project.durationMs(); t += step) {
      paint.setColor(0xffa8b3d2);
      canvas.drawText(
          String.format(Locale.ROOT, "%d:%02d", t / 60000, (t / 1000) % 60),
          labelWidth() + t * scale * dp,
          18 * dp,
          paint);
    }
    if (elements.isEmpty()) {
      paint.setColor(0xffbac5e3);
      canvas.drawText("Sem elementos. Importa vídeo, imagem ou som.", 8 * dp, 54 * dp, paint);
    }
    Rect visible = canvas.getClipBounds();
    for (int index = 0; index < elements.size(); index++) {
      Project.Clip clip = elements.get(index);
      RectF box = bounds(clip, index);
      if (box.bottom < visible.top || box.top > visible.bottom) continue;
      paint.setColor(0xff29314b);
      canvas.drawLine(0, box.top - 3 * dp, getWidth(), box.top - 3 * dp, paint);
      paint.setColor(0xffb5bdd7);
      String type =
          clip.type.equals("video") ? "Vídeo" : clip.type.equals("image") ? "Imagem" : "Som";
      canvas.drawText(type, 5 * dp, box.top + 26 * dp, paint);
      paint.setColor(
          clip.type.equals("audio")
              ? 0xff315e78
              : clip.type.equals("video") ? 0xff65508c : 0xff397263);
      canvas.drawRoundRect(box, 6 * dp, 6 * dp, paint);
      if (clip.id.equals(selected)) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(0xffd9caff);
        canvas.drawRoundRect(box, 6 * dp, 6 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
      }
      canvas.save();
      canvas.clipRect(box);
      paint.setColor(Color.WHITE);
      paint.setTextSize(12 * dp);
      String name =
          TextUtils.ellipsize(
                  clip.name, paint, Math.max(1, box.width() - 16 * dp), TextUtils.TruncateAt.END)
              .toString();
      canvas.drawText(name, box.left + 8 * dp, box.top + 18 * dp, paint);
      paint.setTextSize(10 * dp);
      canvas.drawText(
          String.format(
              Locale.ROOT, "%.1f s%s", clip.lengthMs / 1000.0, clip.muted ? " · sem som" : ""),
          box.left + 8 * dp,
          box.top + 34 * dp,
          paint);
      canvas.restore();
      paint.setTextSize(11 * dp);
    }
    if (!project.captions.isEmpty()) {
      float y = 28 * dp + elements.size() * row();
      paint.setColor(0xffb5bdd7);
      canvas.drawText("Legendas", 4 * dp, y + 27 * dp, paint);
      for (Project.Caption caption : project.captions) {
        paint.setColor(0xff8b6c35);
        canvas.drawRoundRect(
            labelWidth() + caption.startMs * scale * dp,
            y + 4 * dp,
            labelWidth() + caption.endMs * scale * dp,
            y + 43 * dp,
            4 * dp,
            4 * dp,
            paint);
      }
    }
    paint.setColor(0xffe5dcff);
    paint.setStrokeWidth(2 * dp);
    float x = labelWidth() + playhead * scale * dp;
    canvas.drawLine(x, 24 * dp, x, getHeight(), paint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (project == null || listener == null) return false;
    float px = scale * dp;
    long time =
        Math.max(0, Math.min(project.durationMs(), (long) ((event.getX() - labelWidth()) / px)));
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      downX = event.getX();
      dragging = null;
      int index = (int) ((event.getY() - 28 * dp) / row());
      if (event.getY() >= 28 * dp && index >= 0 && index < elements.size()) {
        Project.Clip clip = elements.get(index);
        RectF box = bounds(clip, index);
        if (box.contains(event.getX(), event.getY()) || event.getX() < labelWidth()) {
          dragging = clip;
          oldStart = clip.startMs;
          oldIn = clip.inMs;
          oldLength = clip.lengthMs;
          edge =
              event.getX() >= labelWidth() && Math.abs(event.getX() - box.left) < 12 * dp
                  ? -1
                  : event.getX() >= labelWidth()
                          && Math.abs(event.getX() - (labelWidth() + clip.endMs() * px)) < 12 * dp
                      ? 1
                      : 0;
          selected = clip.id;
          listener.select(clip);
        }
      }
      if (dragging == null) listener.seek(time);
      getParent().requestDisallowInterceptTouchEvent(dragging != null);
      invalidate();
      return true;
    }
    if (event.getAction() == MotionEvent.ACTION_MOVE && dragging != null) {
      long delta = (long) ((event.getX() - downX) / px);
      Project.Clip clip = dragging;
      if (edge == 0)
        clip.startMs = Math.max(0, Math.min(Project.MAX_MS - clip.lengthMs, oldStart + delta));
      else if (edge < 0) {
        delta = Math.max(-Math.min(oldStart, oldIn), Math.min(oldLength - 100, delta));
        clip.startMs = oldStart + delta;
        clip.inMs = oldIn + delta;
        clip.lengthMs = oldLength - delta;
      } else
        clip.lengthMs =
            Math.max(
                100,
                Math.min(
                    Project.MAX_MS - clip.startMs,
                    Math.min(
                        clip.type.equals("image")
                            ? Project.MAX_MS
                            : clip.sourceDurationMs - clip.inMs,
                        oldLength + delta)));
      clip.fadeInMs = Math.min(clip.fadeInMs, clip.lengthMs);
      clip.fadeOutMs = Math.min(clip.fadeOutMs, clip.lengthMs);
      invalidate();
      return true;
    }
    if (event.getAction() == MotionEvent.ACTION_UP
        || event.getAction() == MotionEvent.ACTION_CANCEL) {
      if (dragging != null) {
        boolean edited =
            dragging.startMs != oldStart
                || dragging.inMs != oldIn
                || dragging.lengthMs != oldLength;
        dragging = null;
        if (edited) listener.changed();
      }
      performClick();
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
