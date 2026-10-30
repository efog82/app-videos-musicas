package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.*;

/** One deterministic scene implementation for interactive preview and encoded frames. */
final class SceneRenderer implements AutoCloseable {
  final Context context;
  final Map<String, Bitmap> images = new HashMap<>();
  final Map<String, MediaMetadataRetriever> videos = new HashMap<>();
  final Paint paint = new Paint(3);
  Project project;

  SceneRenderer(Context c, Project p) {
    context = c;
    project = p;
  }

  void draw(Canvas canvas, int w, int h, long ms, Spectrum.Features features) throws Exception {
    canvas.drawColor(0xff0d1224);
    canvas.save();
    canvas.translate(w / 2f, h / 2f);
    canvas.rotate(project.rotation);
    canvas.scale(project.zoom, project.zoom);
    canvas.translate(-w / 2f, -h / 2f);
    ArrayList<Project.Clip> clips = new ArrayList<>(project.clips);
    Collections.sort(clips, (a, b) -> Integer.compare(a.track, b.track));
    for (Project.Clip clip : clips)
      if (!clip.type.equals("audio") && clip.active(ms)) {
        Bitmap bitmap = source(clip, ms, w, h);
        if (bitmap != null) {
          float scale = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
          float bw = bitmap.getWidth() * scale, bh = bitmap.getHeight() * scale;
          int alpha = 255;
          if (project.transition.equals("fade")) {
            float fade =
                Math.min(1, Math.min((ms - clip.startMs) / 500f, (clip.endMs() - ms) / 500f));
            alpha = (int) (Math.max(0, fade) * 255);
          }
          paint.setAlpha(alpha);
          canvas.drawBitmap(
              bitmap,
              null,
              new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2),
              paint);
          paint.setAlpha(255);
          if (clip.type.equals("video")) bitmap.recycle();
        }
      }
    canvas.restore();
    VisualEffects.draw(canvas, w, h, ms / 1000f, project, features, paint);
    if (!project.title.trim().isEmpty())
      text(canvas, project.title, w, h * .27f, w * .058f, "sans", 255);
    for (Project.Caption caption : project.captions)
      if (ms >= caption.startMs && ms < caption.endMs) {
        String value = caption.text;
        int alpha = 255;
        if (project.subtitleAnimation.equals("fade"))
          alpha =
              (int)
                  (255
                      * Math.min(
                          1, Math.min((ms - caption.startMs) / 200f, (caption.endMs - ms) / 200f)));
        if (project.subtitleAnimation.equals("word")) {
          String[] words = value.split("\\s+");
          int count =
              Math.max(
                  1,
                  (int)
                      Math.ceil(
                          words.length
                              * (ms - caption.startMs + 1)
                              / (double) (caption.endMs - caption.startMs)));
          StringBuilder b = new StringBuilder();
          for (int i = 0; i < Math.min(words.length, count); i++) b.append(words[i]).append(' ');
          value = b.toString();
        }
        text(canvas, value, w, h * .82f, w * .049f, project.subtitleStyle, Math.max(0, alpha));
      }
  }

  Bitmap source(Project.Clip clip, long ms, int w, int h) throws Exception {
    if (clip.type.equals("image")) {
      Bitmap b = images.get(clip.id);
      if (b == null) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(clip.uri))) {
          BitmapFactory.decodeStream(in, null, opts);
        }
        if (opts.outWidth < 1) throw new IOException("Imagem indisponível: " + clip.name);
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = 1;
        while (opts.outWidth / opts.inSampleSize > Math.max(w, 1920)
            || opts.outHeight / opts.inSampleSize > Math.max(h, 1920)) opts.inSampleSize *= 2;
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(clip.uri))) {
          b = BitmapFactory.decodeStream(in, null, opts);
        }
        if (b == null) throw new IOException("Não foi possível ler " + clip.name);
        if (images.size() >= 2) {
          String old = images.keySet().iterator().next();
          images.remove(old).recycle();
        }
        images.put(clip.id, b);
      }
      return b;
    }
    MediaMetadataRetriever r = videos.get(clip.id);
    if (r == null) {
      r = new MediaMetadataRetriever();
      r.setDataSource(context, Uri.parse(clip.uri));
      if (videos.size() >= 2) {
        String old = videos.keySet().iterator().next();
        videos.remove(old).release();
      }
      videos.put(clip.id, r);
    }
    if (Build.VERSION.SDK_INT >= 27)
      return r.getScaledFrameAtTime(
          clip.sourceTime(ms) * 1000, MediaMetadataRetriever.OPTION_CLOSEST, w, h);
    return r.getFrameAtTime(clip.sourceTime(ms) * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
  }

  void text(Canvas c, String value, int w, float y, float size, String style, int alpha) {
    paint.setColor(Color.WHITE);
    paint.setAlpha(alpha);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(
        Typeface.create(
            style.equals("serif")
                ? "serif"
                : style.equals("mono") ? "monospace" : "sans-serif-medium",
            Typeface.NORMAL));
    paint.setTextSize(size);
    paint.setShadowLayer(size * .12f, 0, 2, Color.BLACK);
    // Wrap captions instead of shrinking an entire paragraph into an unreadable line.
    ArrayList<String> lines = new ArrayList<>();
    String current = "";
    for (String word : value.replace('\n', ' ').split("\\s+")) {
      String next = current.isEmpty() ? word : current + " " + word;
      if (paint.measureText(next) > w * .9f && !current.isEmpty()) {
        lines.add(current);
        current = word;
      } else current = next;
    }
    if (!current.isEmpty()) lines.add(current);
    for (int i = 0; i < lines.size(); i++)
      c.drawText(lines.get(i), w / 2f, y + i * size * 1.2f, paint);
    paint.clearShadowLayer();
    paint.setAlpha(255);
  }

  @Override
  public void close() {
    for (Bitmap b : images.values()) b.recycle();
    images.clear();
    for (MediaMetadataRetriever r : videos.values())
      try {
        r.release();
      } catch (Exception ignored) {
      }
    videos.clear();
  }
}
