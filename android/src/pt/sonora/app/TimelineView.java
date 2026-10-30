package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import java.util.*;

/** A composition view: independent media rows plus linked original audio and timed captions. */
final class TimelineView extends View {
  interface Listener {
    void select(Project.Clip c);

    void seek(long ms);

    void changed();

    default void selectCaption(Project.Caption caption) {}

    default void selectOriginal(Project.Clip clip) {
      select(clip);
    }
  }

  static final class Row {
    final Project.Clip clip;
    final boolean original;

    Row(Project.Clip c, boolean audio) {
      clip = c;
      original = audio;
    }
  }

  Project project;
  Listener listener;
  MediaArtwork artwork;
  final TextPaint paint = new TextPaint(3);
  final ArrayList<Project.Clip> elements = new ArrayList<>();
  final ArrayList<Row> rows = new ArrayList<>();
  float scale = .05f, downX;
  long oldStart, oldIn, oldLength, downTime;
  String selected;
  Project.Caption selectedCaption, dragCaption;
  Project.Clip dragging;
  int edge;
  boolean moved, fit = true;
  final float dp;
  long playhead;

  TimelineView(Context c) {
    super(c);
    dp = getResources().getDisplayMetrics().density;
    setContentDescription(
        "Timeline multifaixa. Desliza na régua para navegar; mantém premido um clip para mover. As"
            + " extremidades permitem cortar.");
  }

  int labelWidth() {
    return (int) (68 * dp);
  }

  float row() {
    return 50 * dp;
  }

  int rowOf(String id) {
    for (int i = 0; i < rows.size(); i++)
      if (rows.get(i).clip.id.equals(id) && !rows.get(i).original) return i;
    return -1;
  }

  int rowTop(String id) {
    return (int) (28 * dp + Math.max(0, rowOf(id)) * row());
  }

  void update(Project p, String id, long ms) {
    project = p;
    selected = id;
    playhead = ms;
    elements.clear();
    elements.addAll(p.clips);
    Collections.sort(
        elements,
        (a, b) -> {
          int track = Integer.compare(a.track, b.track);
          return track == 0 ? Long.compare(a.startMs, b.startMs) : track;
        });
    rows.clear();
    for (Project.Clip c : elements) {
      rows.add(new Row(c, false));
      if (c.type.equals("video") && c.hasAudio) rows.add(new Row(c, true));
    }
    requestLayout();
    invalidate();
  }

  RectF bounds(Project.Clip c, int index) {
    float left = labelWidth() + c.startMs * scale * dp, top = 28 * dp + index * row() + 3 * dp;
    return new RectF(
        left, top, Math.max(left + 72 * dp, labelWidth() + c.endMs() * scale * dp), top + 44 * dp);
  }

  @Override
  protected void onMeasure(int width, int height) {
    int viewport = MeasureSpec.getSize(width);
    if (getParent() instanceof View && ((View) getParent()).getWidth() > 0)
      viewport = ((View) getParent()).getWidth();
    long duration = project == null ? 10000 : project.durationMs();
    if (fit)
      scale = Math.max(.0001f, (viewport - labelWidth() - 20 * dp) / (Math.max(1, duration) * dp));
    setMeasuredDimension(
        Math.max(viewport, labelWidth() + (int) (duration * scale * dp) + (int) (80 * dp)),
        (int)
            (28 * dp
                + row()
                    * Math.max(
                        1,
                        rows.size() + (project != null && !project.captions.isEmpty() ? 1 : 0))));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawColor(UiTheme.SURFACE);
    if (project == null) return;
    Rect visible = canvas.getClipBounds();
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(Typeface.DEFAULT);
    paint.setTextSize(10 * dp);
    long step = Math.max(1000, (long) (65 / Math.max(.0001, scale)));
    step = (long) (Math.ceil(step / 1000.0) * 1000);
    for (long t = 0; t <= project.durationMs(); t += step) {
      float x = labelWidth() + t * scale * dp;
      if (x < visible.left || x > visible.right) continue;
      paint.setColor(UiTheme.MUTED);
      canvas.drawText(
          String.format(Locale.ROOT, "%d:%02d", t / 60000, (t / 1000) % 60), x, 18 * dp, paint);
    }
    if (rows.isEmpty()) {
      paint.setColor(UiTheme.MUTED);
      canvas.drawText("+ adiciona música, vídeos ou imagens", 10 * dp, 56 * dp, paint);
    }
    for (int index = 0; index < rows.size(); index++) {
      Row entry = rows.get(index);
      Project.Clip clip = entry.clip;
      RectF box = bounds(clip, index);
      if (box.bottom < visible.top || box.top > visible.bottom) continue;
      boolean audio = entry.original || clip.type.equals("audio");
      paint.setColor(audio ? 0xff294d6e : clip.type.equals("video") ? 0xff594781 : 0xff315c58);
      paint.setAlpha(clip.muted || entry.original && !clip.originalAudio ? 100 : 255);
      canvas.drawRoundRect(box, 6 * dp, 6 * dp, paint);
      paint.setAlpha(255);
      canvas.save();
      canvas.clipRect(box);
      if (artwork != null) {
        if (audio) {
          float[] waveform = artwork.wave(clip, this);
          if (waveform != null) {
            paint.setColor(0xff8acaff);
            paint.setStrokeWidth(1.2f * dp);
            float y = box.centerY() + 5 * dp;
            for (int i = 0; i < 100; i++) {
              float x = box.left + 4 * dp + (box.width() - 8 * dp) * i / 99;
              double source =
                  (clip.inMs + (clip.lengthMs * i / 100.0)) / Math.max(1.0, clip.sourceDurationMs);
              int bin =
                  Math.max(0, Math.min(waveform.length - 1, (int) (source * waveform.length)));
              float extent = Math.max(1, waveform[bin] * 13 * dp);
              canvas.drawLine(x, y - extent, x, y + extent, paint);
            }
          }
        } else {
          Bitmap thumbnail = artwork.cover(clip, this);
          if (thumbnail != null) {
            int first = Math.max(0, (int) ((visible.left - box.left) / (56 * dp)));
            for (float x = box.left + first * 56 * dp;
                x < Math.min(box.right, visible.right);
                x += 56 * dp) {
              paint.setAlpha(160);
              canvas.drawBitmap(
                  thumbnail, null, new RectF(x, box.top, x + 56 * dp, box.bottom), paint);
            }
            paint.setAlpha(255);
          }
        }
      }
      paint.setColor(0xbd0b1020);
      canvas.drawRect(box.left, box.top, box.right, box.top + 19 * dp, paint);
      paint.setColor(UiTheme.TEXT);
      paint.setTextSize(11 * dp);
      canvas.drawText(
          TextUtils.ellipsize(
                  clip.name, paint, Math.max(1, box.width() - 12 * dp), TextUtils.TruncateAt.END)
              .toString(),
          box.left + 6 * dp,
          box.top + 14 * dp,
          paint);
      canvas.restore();
      if (clip.id.equals(selected)) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(UiTheme.VIOLET);
        canvas.drawRoundRect(box, 6 * dp, 6 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
      }
      paint.setColor(UiTheme.SURFACE);
      float labelLeft = visible.left;
      canvas.drawRect(
          labelLeft,
          box.top - 2 * dp,
          labelLeft + labelWidth() - 2 * dp,
          box.bottom + 2 * dp,
          paint);
      paint.setColor(UiTheme.MUTED);
      paint.setTextSize(9 * dp);
      canvas.drawText(
          entry.original
              ? "Original"
              : audio ? "Música" : clip.type.equals("video") ? "Vídeo" : "Imagem",
          labelLeft + 3 * dp,
          box.top + 25 * dp,
          paint);
    }
    float captionY = 28 * dp + rows.size() * row();
    if (!project.captions.isEmpty()) {
      paint.setColor(UiTheme.MUTED);
      paint.setTextSize(9 * dp);
      canvas.drawText("Legendas", visible.left + 3 * dp, captionY + 25 * dp, paint);
      for (Project.Caption caption : project.captions) {
        RectF box =
            new RectF(
                labelWidth() + caption.startMs * scale * dp,
                captionY + 4 * dp,
                Math.max(
                    labelWidth() + caption.startMs * scale * dp + 28 * dp,
                    labelWidth() + caption.endMs * scale * dp),
                captionY + 45 * dp);
        paint.setColor(caption == selectedCaption ? 0xffb78b43 : 0xff6e5430);
        canvas.drawRoundRect(box, 4 * dp, 4 * dp, paint);
        canvas.save();
        canvas.clipRect(box);
        paint.setColor(UiTheme.TEXT);
        paint.setTextSize(10 * dp);
        canvas.drawText(caption.text, box.left + 4 * dp, box.top + 22 * dp, paint);
        canvas.restore();
      }
    }
    paint.setColor(0xfff0eaff);
    paint.setStrokeWidth(1.5f * dp);
    float x = labelWidth() + playhead * scale * dp;
    canvas.drawLine(x, 23 * dp, x, getHeight(), paint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (project == null || listener == null) return false;
    float px = scale * dp;
    long time =
        Math.max(0, Math.min(project.durationMs(), (long) ((event.getX() - labelWidth()) / px)));
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      downX = event.getX();
      downTime = event.getEventTime();
      dragging = null;
      dragCaption = null;
      moved = false;
      int index = (int) ((event.getY() - 28 * dp) / row());
      if (event.getY() >= 28 * dp && index >= 0 && index < rows.size()) {
        Row entry = rows.get(index);
        Project.Clip clip = entry.clip;
        RectF box = bounds(clip, index);
        if (box.contains(event.getX(), event.getY()) || event.getX() < labelWidth()) {
          dragging = clip;
          oldStart = clip.startMs;
          oldIn = clip.inMs;
          oldLength = clip.lengthMs;
          edge =
              Math.abs(event.getX() - box.left) < 12 * dp
                  ? -1
                  : Math.abs(event.getX() - box.right) < 12 * dp ? 1 : 0;
          selected = clip.id;
          if (entry.original) listener.selectOriginal(clip);
          else listener.select(clip);
        }
      } else if (index == rows.size())
        for (Project.Caption caption : project.captions)
          if (time >= caption.startMs && time < caption.endMs) {
            dragCaption = caption;
            oldStart = caption.startMs;
            oldLength = caption.endMs - caption.startMs;
            float left = labelWidth() + caption.startMs * px,
                right = labelWidth() + caption.endMs * px;
            edge =
                Math.abs(event.getX() - left) < 12 * dp
                    ? -1
                    : Math.abs(event.getX() - right) < 12 * dp ? 1 : 0;
            listener.selectCaption(caption);
            break;
          }
      if (dragging == null && dragCaption == null) listener.seek(time);
      getParent()
          .requestDisallowInterceptTouchEvent(
              edge != 0 && (dragging != null || dragCaption != null));
      invalidate();
      return true;
    }
    if (event.getAction() == MotionEvent.ACTION_MOVE && (dragging != null || dragCaption != null)) {
      if (edge == 0 && event.getEventTime() - downTime < 280) return true;
      getParent().requestDisallowInterceptTouchEvent(true);
      long delta = (long) ((event.getX() - downX) / px);
      moved = true;
      if (dragCaption != null) {
        if (edge == 0) {
          dragCaption.startMs = Math.max(0, Math.min(Project.MAX_MS - oldLength, oldStart + delta));
          dragCaption.endMs = dragCaption.startMs + oldLength;
        } else if (edge < 0)
          dragCaption.startMs = Math.max(0, Math.min(oldStart + oldLength - 100, oldStart + delta));
        else
          dragCaption.endMs =
              Math.min(
                  Project.MAX_MS,
                  Math.max(dragCaption.startMs + 100, oldStart + oldLength + delta));
      } else {
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
      }
      invalidate();
      return true;
    }
    if (event.getAction() == MotionEvent.ACTION_UP
        || event.getAction() == MotionEvent.ACTION_CANCEL) {
      dragging = null;
      dragCaption = null;
      if (moved) listener.changed();
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
