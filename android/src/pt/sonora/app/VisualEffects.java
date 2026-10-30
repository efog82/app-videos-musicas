package pt.sonora.app;

import android.graphics.*;

final class VisualEffects {
  static final String[] IDS = {
    "bars", "spectrum", "circle", "particles", "tunnel", "kaleidoscope", "fractal", "fluid", "none"
  };
  static final String[] NAMES = {
    "Barras clássicas",
    "Espectro de frequência",
    "Ondas circulares",
    "Partículas",
    "Túnel geométrico",
    "Caleidoscópio",
    "Fractal",
    "Fluxo fluido",
    "Sem efeito"
  };

  static void draw(
      Canvas c, int w, int h, float seconds, Project p, Spectrum.Features f, Paint brush) {
    if (p.effect.equals("none")) return;
    float drive =
        p.syncBand.equals("bass")
            ? f.bass
            : p.syncBand.equals("mid")
                ? f.mid
                : p.syncBand.equals("treble")
                    ? f.treble
                    : p.syncBand.equals("beat") ? f.beat : f.energy;
    float t = seconds * p.speed,
        amount = p.intensity * (.2f + drive * 1.8f),
        scale = w / 720f,
        cx = w / 2f,
        cy = h * .61f;
    brush.setColor(p.color);
    brush.setStrokeWidth(2 * scale);
    brush.setStyle(Paint.Style.STROKE);
    int count = p.density;
    if (p.effect.equals("bars")) {
      brush.setStyle(Paint.Style.FILL);
      float gap = w / (float) (count + 5);
      for (int i = 0; i < count; i++) {
        float x = cx + (i - (count - 1) / 2f) * gap;
        float movement =
            (float) (.25 + .75 * Math.abs(Math.sin(i * .67 + t * 4) * Math.cos(i * .19 - t * 2)));
        float size = (6 + 80 * f.energy * amount * movement) * scale;
        c.drawRoundRect(
            x - 3 * scale, cy - size, x + 3 * scale, cy + size, 3 * scale, 3 * scale, brush);
      }
    } else if (p.effect.equals("spectrum")) {
      brush.setStyle(Paint.Style.FILL);
      float gap = w / (float) (count + 3);
      for (int i = 0; i < count; i++) {
        float v = f.bins[Math.min(31, i * 32 / count)];
        float x = gap * (i + 1.5f);
        c.drawRoundRect(
            x,
            cy - (5 + 180 * v * amount) * scale,
            x + gap * .65f,
            cy,
            3 * scale,
            3 * scale,
            brush);
      }
    } else if (p.effect.equals("circle")) {
      Path path = new Path();
      for (int i = 0; i <= count * 3; i++) {
        double a = i * 2 * Math.PI / (count * 3);
        int bin = (i * 32 / (count * 3)) % 32;
        float r = (100 + 90 * f.bins[bin] * amount + 25 * f.bass) * scale;
        float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
        if (i == 0) path.moveTo(x, y);
        else path.lineTo(x, y);
      }
      path.close();
      c.drawPath(path, brush);
      for (int i = 1; i <= 3; i++)
        c.drawCircle(cx, cy, (110 + i * 25 + f.beat * 35) * scale, brush);
    } else if (p.effect.equals("particles")) {
      brush.setStyle(Paint.Style.FILL);
      for (int i = 0; i < count; i++) {
        double a = i * 2.399963 + t * (.1 + f.treble);
        float travel = (float) ((seconds * p.speed * .13 + i * .618) % 1);
        float r = (20 + travel * w * .58f) * (1 + f.bass * amount * .3f);
        brush.setAlpha((int) (255 * (1 - travel)));
        c.drawCircle(
            cx + (float) Math.cos(a) * r,
            cy + (float) Math.sin(a) * r,
            (2 + 8 * f.mid * amount) * scale,
            brush);
      }
      brush.setAlpha(255);
    } else if (p.effect.equals("tunnel")) {
      int sides = 6;
      for (int ring = 0; ring < 14; ring++) {
        float phase = (ring / 14f + t * .16f) % 1;
        float r = phase * w * .8f * (1 + f.bass * amount * .4f);
        Path path = new Path();
        for (int i = 0; i <= sides; i++) {
          double a = i * 2 * Math.PI / sides + t * .35 + ring * .09;
          float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
          if (i == 0) path.moveTo(x, y);
          else path.lineTo(x, y);
        }
        brush.setAlpha((int) (220 * (1 - phase)));
        c.drawPath(path, brush);
      }
      brush.setAlpha(255);
    } else if (p.effect.equals("kaleidoscope")) {
      int sections = Math.max(4, count / 4);
      float angle = 360f / sections;
      for (int section = 0; section < sections; section++) {
        c.save();
        c.translate(cx, cy);
        c.rotate(section * angle + t * 8);
        if (section % 2 == 1) c.scale(-1, 1);
        Path wedge = new Path();
        wedge.moveTo(0, 0);
        wedge.lineTo(w, 0);
        wedge.lineTo(
            (float) Math.cos(Math.toRadians(angle)) * w,
            (float) Math.sin(Math.toRadians(angle)) * w);
        wedge.close();
        c.clipPath(wedge);
        for (int k = 0; k < 12; k++) {
          float r = (35 + k * 24 + f.bass * amount * 50) * scale;
          float x = r * (float) Math.cos(k * .4 + t * .3),
              y = r * (float) Math.sin(k * .4 + t * .3);
          c.drawCircle(x, y, (14 + f.mid * amount * 25) * scale, brush);
          c.drawLine(0, 0, x, y, brush);
        }
        c.restore();
      }
    } else if (p.effect.equals("fractal")) {
      branch(
          c,
          cx,
          cy + 120 * scale,
          100 * scale,
          -Math.PI / 2,
          Math.min(7, Math.max(4, count / 16)),
          .35 + .5 * Math.sin(t * .3) + f.mid * amount * .5,
          brush);
    } else if (p.effect.equals("fluid")) {
      for (int line = 0; line < count / 2; line++) {
        Path path = new Path();
        for (int i = 0; i <= 80; i++) {
          float x = i * w / 80f;
          float y =
              cy
                  + (line - count / 4f) * 12 * scale
                  + (float) (Math.sin(x / w * 9 + t + line * .22) * Math.cos(x / w * 4 - t * .7))
                      * 80
                      * scale
                      * (.2f + f.mid * amount);
          if (i == 0) path.moveTo(x, y);
          else path.lineTo(x, y);
        }
        c.drawPath(path, brush);
      }
    }
    brush.setStyle(Paint.Style.FILL);
    brush.setAlpha(255);
  }

  static void branch(
      Canvas c, float x, float y, float length, double angle, int depth, double spread, Paint p) {
    if (depth == 0) return;
    float xx = x + (float) Math.cos(angle) * length, yy = y + (float) Math.sin(angle) * length;
    c.drawLine(x, y, xx, yy, p);
    branch(c, xx, yy, length * .7f, angle - spread, depth - 1, spread, p);
    branch(c, xx, yy, length * .7f, angle + spread, depth - 1, spread, p);
  }
}
