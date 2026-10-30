package pt.sonora.app;

import android.app.*;
import android.view.View;
import android.widget.*;

/** Selection-specific commands; unsupported motor functions are explained, never simulated. */
final class ContextTools {
  final EditorController c;
  final LinearLayout host;

  ContextTools(EditorController controller, LinearLayout container) {
    c = controller;
    host = container;
  }

  View action(String icon, String name, Runnable task) {
    return UiTheme.action(
        c,
        icon,
        name,
        () -> {
          if (c.busy) {
            c.message("Aguarda a operação atual.");
            return;
          }
          task.run();
        });
  }

  void render() {
    host.removeAllViews();
    if (c.lyricLines != null && c.lyricIndex < c.lyricLines.length)
      UiTheme.item(
          host, UiTheme.primary(c, "Marcar: " + c.lyricLines[c.lyricIndex], c::markLyric), -1, 44);
    Project.Clip clip = c.clip();
    Project.Caption caption = c.selectedCaption;
    if (clip == null && caption == null) {
      TextView hint =
          UiTheme.text(c, "+ adiciona média · toca num clip para editar", 12, UiTheme.MUTED);
      hint.setGravity(android.view.Gravity.CENTER);
      UiTheme.item(host, hint, -1, 28);
      return;
    }
    if (caption != null) {
      UiTheme.actions(
          host,
          action("text", "Editar", () -> c.editCaption(caption)),
          action("trim", "Tempo", () -> captionTime(caption)),
          action("text", "Fonte", () -> SubtitlePanel.style(c, caption)),
          action("effect", "Cor", () -> SubtitlePanel.color(c, caption)),
          action("format", "Tamanho", () -> SubtitlePanel.size(c, caption)),
          action("media", "Posição", () -> SubtitlePanel.position(c, caption)),
          action("effect", "Animação", () -> SubtitlePanel.animation(c, caption)),
          action(
              "delete",
              "Eliminar",
              () -> {
                c.project.captions.remove(caption);
                c.selectedCaption = null;
                c.changed();
              }));
      return;
    }
    if (c.originalSelected) {
      UiTheme.actions(
          host,
          action("volume", "Volume", () -> AudioPanel.volume(c, clip)),
          action(
              "audio",
              clip.originalAudio ? "Silenciar" : "Ativar",
              () -> {
                clip.originalAudio = !clip.originalAudio;
                c.changed();
              }),
          action("audio", "Separar", () -> c.separate(clip)),
          action("export", "Extrair", () -> c.extract(clip)),
          action(
              "delete",
              "Eliminar",
              () -> {
                clip.originalAudio = false;
                c.originalSelected = false;
                c.changed();
              }));
      return;
    }
    if (clip.type.equals("video"))
      UiTheme.actions(
          host,
          action("trim", "Cortar", () -> trim(clip)),
          action("split", "Dividir", () -> split(clip)),
          action("volume", "Volume", () -> AudioPanel.volume(c, clip)),
          action(
              "audio",
              clip.muted ? "Ativar" : "Silenciar",
              () -> {
                clip.muted = !clip.muted;
                c.changed();
              }),
          action("export", "Extrair", () -> c.extract(clip)),
          action("effect", "Efeitos", () -> EffectsPanel.show(c)),
          action("copy", "Duplicar", () -> duplicate(clip)),
          action("delete", "Eliminar", () -> c.confirmRemove(clip)),
          action(
              "more",
              "Limites",
              () ->
                  UiTheme.notice(
                      c,
                      "Velocidade de vídeo",
                      "O motor atual não suporta alterar a velocidade de um clip. A velocidade em"
                          + " Efeitos afeta apenas a animação musical.")));
    else if (clip.type.equals("image"))
      UiTheme.actions(
          host,
          action("trim", "Duração", () -> trim(clip)),
          action(
              "format",
              "Enquadrar",
              () -> {
                c.project.mediaFit = !c.project.mediaFit;
                c.visualChanged();
              }),
          action("plus", "Zoom", () -> imageTransform()),
          action(
              "effect",
              "Transição",
              () -> {
                c.project.transition = c.project.transition.equals("fade") ? "none" : "fade";
                c.visualChanged();
              }),
          action("effect", "Efeitos", () -> EffectsPanel.show(c)),
          action("copy", "Duplicar", () -> duplicate(clip)),
          action("delete", "Eliminar", () -> c.confirmRemove(clip)),
          action(
              "more",
              "Limites",
              () ->
                  UiTheme.notice(
                      c,
                      "Imagem",
                      "Enquadrar e Zoom ajustam a composição do projeto. Recorte manual e movimento"
                          + " individual da fotografia ainda não são suportados pelo motor. Os"
                          + " ficheiros originais não são alterados.")));
    else
      UiTheme.actions(
          host,
          action("volume", "Volume", () -> AudioPanel.volume(c, clip)),
          action("trim", "Cortar", () -> trim(clip)),
          action("audio", "Fade In", () -> AudioPanel.fades(c, clip)),
          action("audio", "Fade Out", () -> AudioPanel.fades(c, clip)),
          action("effect", "Visualizador", () -> EffectsPanel.show(c)),
          action("copy", "Duplicar", () -> duplicate(clip)),
          action("delete", "Eliminar", () -> c.confirmRemove(clip)));
  }

  void duplicate(Project.Clip clip) {
    try {
      Project.Clip copy = ProjectActions.duplicateClip(c.project, clip);
      c.selected = copy.id;
      c.changed();
      c.focusElement(copy);
    } catch (Exception e) {
      c.message(e.getMessage());
    }
  }

  void split(Project.Clip clip) {
    try {
      Project.Clip right = ProjectActions.split(c.project, clip, c.position);
      c.selected = right.id;
      c.changed();
      c.focusElement(right);
    } catch (Exception e) {
      UiTheme.notice(c, "Dividir clip", e.getMessage());
    }
  }

  void trim(Project.Clip clip) {
    LinearLayout fields = c.fields();
    c.label(fields, clip.name, 14);
    EditText in = null;
    if (!clip.type.equals("image")) {
      in = c.input(fields, "Início no ficheiro (segundos)", String.valueOf(clip.inMs / 1000.0));
      in.setInputType(8194);
    }
    EditText length = c.input(fields, "Duração (segundos)", String.valueOf(clip.lengthMs / 1000.0));
    length.setInputType(8194);
    EditText input = in;
    new AlertDialog.Builder(c)
        .setTitle(clip.type.equals("image") ? "Duração da imagem" : "Cortar clip")
        .setView(fields)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Aplicar",
            (d, w) -> {
              try {
                ProjectActions.trim(clip, input == null ? 0 : c.seconds(input), c.seconds(length));
                c.project.autoImages = false;
                c.changed();
              } catch (Exception e) {
                UiTheme.notice(
                    c,
                    "Não aplicado",
                    "Usa tempos válidos, dentro do ficheiro original. O clip anterior foi"
                        + " preservado.");
              }
            })
        .show();
  }

  void imageTransform() {
    LinearLayout body = UiTheme.column(c);
    PanelControls.note(c, body, "Ajustes da composição do projeto; não regravam a fotografia.");
    PanelControls.range(
        c,
        body,
        "Zoom",
        c.project.zoom,
        1,
        3,
        n -> {
          c.project.zoom = n;
          c.visualChanged();
        });
    PanelControls.range(
        c,
        body,
        "Rotação",
        c.project.rotation,
        -180,
        180,
        n -> {
          c.project.rotation = n;
          c.visualChanged();
        });
    UiTheme.sheet(c, "Enquadramento", body);
  }

  void captionTime(Project.Caption caption) {
    LinearLayout box = c.fields();
    EditText start = c.input(box, "Início (segundos)", String.valueOf(caption.startMs / 1000.0)),
        end = c.input(box, "Fim (segundos)", String.valueOf(caption.endMs / 1000.0));
    start.setInputType(8194);
    end.setInputType(8194);
    new AlertDialog.Builder(c)
        .setTitle("Tempo da legenda")
        .setView(box)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Aplicar",
            (d, w) -> {
              try {
                long a = c.seconds(start), b = c.seconds(end);
                if (a < 0 || b <= a) throw new Exception();
                caption.startMs = a;
                caption.endMs = b;
                c.changed();
              } catch (Exception e) {
                c.message("O fim deve ser posterior ao início.");
              }
            })
        .show();
  }
}
