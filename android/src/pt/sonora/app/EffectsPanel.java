package pt.sonora.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Uses the original scene renderer for every preview card, never replaces effect implementations.
 */
final class EffectsPanel {
  static final java.util.concurrent.ExecutorService cards =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  static final String[] CATEGORIES = {
    "Espectro e equalizadores",
    "Partículas",
    "Caleidoscópio",
    "Geometria e fractais",
    "Luz e brilho",
    "Movimento e distorção",
    "Efeitos clássicos"
  };
  static final String[][] IDS = {
    {"bars", "spectrum", "circle", "legacy-0", "legacy-1", "legacy-6"},
    {"particles", "legacy-2", "legacy-7"},
    {"kaleidoscope", "legacy-5"},
    {"tunnel", "fractal", "legacy-4"},
    {"legacy-2", "legacy-5", "legacy-7"},
    {"fluid", "legacy-3", "tunnel"},
    {"legacy-0", "legacy-1", "legacy-2", "legacy-3", "legacy-4", "legacy-5", "legacy-6", "legacy-7"}
  };

  static String name(String id) {
    if (id.startsWith("legacy-"))
      return pt.sonora.offline.Options.NAMES[Integer.parseInt(id.substring(7))];
    int n = Arrays.asList(VisualEffects.IDS).indexOf(id);
    return n >= 0 ? VisualEffects.NAMES[n] : id;
  }

  static void apply(EditorController c, String id) {
    c.project.effect = id;
    c.visualChanged();
  }

  static void show(EditorController c) {
    Project deck;
    try {
      deck = c.project.copy();
    } catch (Exception e) {
      c.message(e.getMessage());
      return;
    }
    deck.clips.clear();
    deck.captions.clear();
    android.content.Context context = c.getApplicationContext();
    LinearLayout body = UiTheme.column(c);
    PanelControls.button(c, body, "Remover efeito", () -> apply(c, "none"));
    PanelControls.range(
        c,
        body,
        "Intensidade",
        c.project.intensity,
        0,
        3,
        n -> {
          c.project.intensity = n;
          c.visualChanged();
        });
    PanelControls.note(
        c,
        body,
        "Miniaturas ilustrativas do motor existente. A pré-visualização do editor usa o áudio do"
            + " projeto.");
    for (int i = 0; i < CATEGORIES.length; i++) {
      TextView label = UiTheme.text(c, CATEGORIES[i], 16, UiTheme.TEXT);
      UiTheme.item(body, label, -1, 40);
      HorizontalScrollView scroll = new HorizontalScrollView(c);
      LinearLayout cards = UiTheme.row(c);
      for (String id : IDS[i]) {
        LinearLayout card = UiTheme.column(c);
        card.setBackground(
            UiTheme.shape(c, id.equals(c.project.effect) ? 0xff343153 : UiTheme.RAISED, 10));
        ImageView image = new ImageView(c);
        EffectsPanel.cards.execute(
            () -> {
              Bitmap bitmap = thumbnail(context, deck, id);
              c.handler.post(
                  () -> {
                    if (image.isAttachedToWindow()) image.setImageBitmap(bitmap);
                  });
            });
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(image, new LinearLayout.LayoutParams(-1, c.dp(84)));
        TextView title = UiTheme.text(c, name(id), 12, UiTheme.TEXT);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(-1, c.dp(38)));
        card.setContentDescription("Aplicar efeito " + name(id));
        card.setOnClickListener(
            v -> {
              apply(c, id);
              card.setBackground(UiTheme.shape(c, 0xff343153, 10));
            });
        UiTheme.item(cards, card, 136, 126);
      }
      scroll.addView(cards);
      body.addView(scroll);
    }
    PanelControls.button(
        c, body, "Cor, sincronização e parâmetros", () -> c.advanced.show("Efeitos"));
    UiTheme.sheet(c, "Efeitos e visualizadores", body);
  }

  static Bitmap thumbnail(android.content.Context c, Project source, String id) {
    Bitmap b = Bitmap.createBitmap(240, 136, Bitmap.Config.ARGB_8888);
    try {
      Project p = source.copy();
      p.clips.clear();
      p.captions.clear();
      p.title = "";
      p.effect = id;
      p.zoom = 1;
      p.rotation = 0;
      Spectrum.Features f = new Spectrum.Features();
      f.energy = .6f;
      f.bass = .7f;
      f.mid = .5f;
      f.treble = .4f;
      for (int i = 0; i < f.bins.length; i++)
        f.bins[i] = .1f + .6f * (float) Math.abs(Math.sin(i * .4));
      for (int i = 0; i < f.wave.length; i++) f.wave[i] = (float) Math.sin(i * .12) * .5f;
      try (SceneRenderer scene = new SceneRenderer(c, p)) {
        scene.draw(new Canvas(b), 240, 136, 2400, f);
      }
    } catch (Exception ignored) {
    }
    return b;
  }
}
