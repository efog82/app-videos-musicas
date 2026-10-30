package pt.sonora.app;

import android.widget.*;

final class AudioPanel {
  static void show(EditorController c) {
    LinearLayout body = UiTheme.column(c);
    PanelControls.button(c, body, "+ Importar música", () -> c.pick(10, "audio/*", true));
    for (Project.Clip clip : c.project.clips)
      if (clip.type.equals("audio") || clip.type.equals("video") && clip.hasAudio)
        PanelControls.button(
            c,
            body,
            (clip.type.equals("video") ? "Áudio original · " : "Música · ") + clip.name,
            () -> {
              c.selectElement(clip);
              c.originalSelected = clip.type.equals("video");
              c.screen.refreshSelection();
              volume(c, clip);
            });
    PanelControls.note(
        c,
        body,
        "Seleciona uma faixa para ajustar volume e fades. O áudio original dos vídeos é mantido por"
            + " defeito. Separar não significa separar voz e instrumentos.");
    UiTheme.sheet(c, "Áudio", body);
  }

  static void volume(EditorController c, Project.Clip clip) {
    LinearLayout body = UiTheme.column(c);
    PanelControls.range(
        c,
        body,
        "Volume",
        clip.volume,
        0,
        2,
        n -> {
          clip.volume = n;
          c.changed();
        });
    PanelControls.button(
        c,
        body,
        clip.muted ? "Ativar som" : "Silenciar",
        () -> {
          clip.muted = !clip.muted;
          c.changed();
        });
    if (clip.type.equals("video")) {
      PanelControls.button(
          c,
          body,
          clip.originalAudio ? "Silenciar original" : "Ativar original",
          () -> {
            clip.originalAudio = !clip.originalAudio;
            c.changed();
          });
      PanelControls.button(c, body, "Separar para faixa independente", () -> c.separate(clip));
      PanelControls.button(c, body, "Extrair M4A", () -> c.extract(clip));
    }
    PanelControls.button(c, body, "Fade de entrada / saída", () -> fades(c, clip));
    PanelControls.note(c, body, "Ganho acima de 1 pode saturar ao misturar várias faixas.");
    UiTheme.sheet(c, clip.name, body);
  }

  static void fades(EditorController c, Project.Clip clip) {
    LinearLayout body = UiTheme.column(c);
    float max = Math.min(10, clip.lengthMs / 1000f);
    PanelControls.range(
        c,
        body,
        "Entrada (s)",
        clip.fadeInMs / 1000f,
        0,
        max,
        n -> {
          clip.fadeInMs = (long) (n * 1000);
          c.changed();
        });
    PanelControls.range(
        c,
        body,
        "Saída (s)",
        clip.fadeOutMs / 1000f,
        0,
        max,
        n -> {
          clip.fadeOutMs = (long) (n * 1000);
          c.changed();
        });
    UiTheme.sheet(c, "Fades · " + clip.name, body);
  }
}
