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
  final pt.sonora.offline.LegacyBridge legacy = new pt.sonora.offline.LegacyBridge();

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
          float scale =
              (project.mediaFit
                  ? Math.min(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight())
                  : Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight()));
          float bw = bitmap.getWidth() * scale, bh = bitmap.getHeight() * scale;
          int alpha = 255;
          if (project.transition.equals("fade")) {
            float fade =
                Math.min(1, Math.min((ms - clip.startMs) / 500f, (clip.endMs() - ms) / 500f));
            alpha = (int) (Math.max(0, fade) * 255);
          }
          paint.setAlpha(alpha);
          if (project.distortion > 0) {
            int grid = 12;
            float[] mesh = new float[(grid + 1) * (grid + 1) * 2];
            int at = 0;
            for (int y = 0; y <= grid; y++)
              for (int x = 0; x <= grid; x++) {
                mesh[at++] =
                    (w - bw) / 2
                        + bw * x / grid
                        + (float) Math.sin(y * .7 + ms * .002)
                            * project.distortion
                            * features.energy
                            * w
                            * .05f;
                mesh[at++] =
                    (h - bh) / 2
                        + bh * y / grid
                        + (float) Math.sin(x * .7 + ms * .0016)
                            * project.distortion
                            * features.mid
                            * h
                            * .04f;
              }
            canvas.drawBitmapMesh(bitmap, grid, grid, mesh, 0, null, 0, paint);
          } else
            canvas.drawBitmap(
                bitmap,
                null,
                new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2),
                paint);
          paint.setAlpha(255);
          if (clip.type.equals("video")) bitmap.recycle();
        }
      }
    if (project.effect.startsWith("legacy-"))
      legacy.draw(
          canvas,
          w,
          h,
          Integer.parseInt(project.effect.substring(7)),
          project.enhancedEffects,
          project.fullEffect,
          project.color,
          project.intensity,
          project.speed,
          features.energy,
          features.bass,
          features.mid,
          features.treble,
          features.bins,
          features.wave,
          ms / 1000.0);
    else VisualEffects.draw(canvas, w, h, ms / 1000f, project, features, paint);
    canvas.restore();
    if (project.showTitle && !project.title.trim().isEmpty())
      text(canvas, project.title, w, h * .27f, w * .058f, "sans", 255);
    long subtitleTime = ms - project.subtitleOffsetMs;
    java.util.LinkedHashMap<String, CaptionGroup> groups = new java.util.LinkedHashMap<>();
    if (project.showSubtitles)
      for (Project.Caption caption : project.captions)
        if (subtitleTime >= caption.startMs && subtitleTime < caption.endMs) {
          String font = caption.font.isEmpty() ? project.subtitleStyle : caption.font;
          String animation =
              caption.animation.isEmpty() ? project.subtitleAnimation : caption.animation;
          int size = caption.size < 0 ? project.subtitleSize : caption.size,
              position = caption.position < 0 ? project.subtitlePosition : caption.position;
          String key = font + ":" + size + ":" + position + ":" + caption.color;
          CaptionGroup group = groups.get(key);
          if (group == null) {
            group = new CaptionGroup(font, size, position, caption.color);
            groups.put(key, group);
          }
          String value = caption.text;
          if (animation.equals("fade"))
            group.alpha =
                Math.min(
                    group.alpha,
                    (int)
                        (255
                            * Math.min(
                                1,
                                Math.min(
                                    (subtitleTime - caption.startMs) / 200f,
                                    (caption.endMs - subtitleTime) / 200f))));
          if (animation.equals("word")) {
            String[] words = value.split("\\s+");
            int count =
                Math.max(
                    1,
                    (int)
                        Math.ceil(
                            words.length
                                * (subtitleTime - caption.startMs + 1)
                                / (double) (caption.endMs - caption.startMs)));
            StringBuilder part = new StringBuilder();
            for (int i = 0; i < Math.min(words.length, count); i++)
              part.append(words[i]).append(' ');
            value = part.toString();
          }
          if (group.text.length() > 0) group.text.append('\n');
          group.text.append(value);
        }
    for (CaptionGroup group : groups.values())
      text(
          canvas,
          group.text.toString(),
          w,
          group.position == 1 ? h * .5f : group.position == 2 ? h * .12f : h * .78f,
          w * new float[] {.041f, .049f, .063f}[group.size],
          group.font,
          Math.max(0, group.alpha),
          group.color);
  }

  static final class CaptionGroup {
    final String font;
    final int size, position, color;
    int alpha = 255;
    final StringBuilder text = new StringBuilder();

    CaptionGroup(String f, int s, int p, int c) {
      font = f;
      size = s;
      position = p;
      color = c;
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
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(clip.uri))) {
          android.media.ExifInterface exif = new android.media.ExifInterface(in);
          int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
          Matrix matrix = new Matrix();
          switch (orientation) {
            case 2:
              matrix.setScale(-1, 1);
              break;
            case 3:
              matrix.postRotate(180);
              break;
            case 4:
              matrix.setScale(1, -1);
              break;
            case 5:
              matrix.setScale(-1, 1);
              matrix.postRotate(270);
              break;
            case 6:
              matrix.postRotate(90);
              break;
            case 7:
              matrix.setScale(-1, 1);
              matrix.postRotate(90);
              break;
            case 8:
              matrix.postRotate(270);
              break;
          }
          Bitmap rotated = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
          if (rotated != b) b.recycle();
          b = rotated;
        } catch (IOException ignored) {
        }

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
    text(c, value, w, y, size, style, alpha, Color.WHITE);
  }

  void text(
      Canvas c, String value, int w, float y, float size, String style, int alpha, int color) {
    paint.setColor(color);
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
