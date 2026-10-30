package pt.sonora.app;

import android.app.*;
import android.widget.*;

final class SubtitlePanel {
  static void show(EditorController c) {
    LinearLayout body = UiTheme.column(c);
    PanelControls.button(c, body, "Importar SRT", () -> c.pick(14, "*/*", true));
    PanelControls.button(c, body, "Criar manualmente", () -> c.editCaption(null));
    PanelControls.button(c, body, "Gerar automaticamente", c::transcribe);
    PanelControls.note(
        c,
        body,
        "A geração automática usa o áudio importado, não exige ditar a letra. Depende do motor"
            + " local Android e do modelo português; o diagnóstico explica quando não estão"
            + " disponíveis.");
    PanelControls.button(c, body, "Sincronizar letra conhecida", c::lyrics);
    PanelControls.button(
        c,
        body,
        "Guardar / exportar SRT",
        () ->
            c.createText(
                Subtitles.write(c.project.captions), "application/x-subrip", "sonora.srt"));
    for (Project.Caption caption : c.project.captions)
      PanelControls.button(
          c,
          body,
          Subtitles.format(caption.startMs) + " · " + caption.text,
          () -> {
            c.selected = null;
            c.selectedCaption = caption;
            c.screen.refreshSelection();
            c.editCaption(caption);
          });
    PanelControls.button(
        c, body, "Diagnóstico e opções anteriores", () -> c.advanced.show("Legendas"));
    UiTheme.sheet(c, "Legendas", body);
  }

  static void title(EditorController c) {
    LinearLayout box = c.fields();
    EditText text = c.input(box, "Título no vídeo", c.project.title);
    new AlertDialog.Builder(c)
        .setTitle("Texto do projeto")
        .setView(box)
        .setNegativeButton("Cancelar", null)
        .setNeutralButton(
            c.project.showTitle ? "Ocultar" : "Mostrar",
            (d, w) -> {
              c.project.showTitle = !c.project.showTitle;
              c.visualChanged();
            })
        .setPositiveButton(
            "Aplicar",
            (d, w) -> {
              String value = text.getText().toString();
              if (value.length() > 200) {
                c.message("Usa até 200 caracteres.");
                return;
              }
              c.project.title = value;
              c.project.showTitle = true;
              c.visualChanged();
            })
        .show();
  }

  static void style(EditorController c, Project.Caption p) {
    c.choice(
        "Fonte",
        new String[] {"Sans", "Serif", "Monoespaçada"},
        -1,
        i -> {
          p.font = new String[] {"sans", "serif", "mono"}[i];
          c.visualChanged();
        });
  }

  static void color(EditorController c, Project.Caption p) {
    c.choice(
        "Cor da legenda",
        new String[] {"Branco", "Azul", "Violeta", "Amarelo", "Rosa"},
        -1,
        i -> {
          p.color = new int[] {0xffffffff, UiTheme.BLUE, UiTheme.VIOLET, 0xffffd36f, 0xffff8cba}[i];
          c.visualChanged();
        });
  }

  static void size(EditorController c, Project.Caption p) {
    c.choice(
        "Tamanho",
        new String[] {"Pequeno", "Médio", "Grande"},
        p.size,
        i -> {
          p.size = i;
          c.visualChanged();
        });
  }

  static void position(EditorController c, Project.Caption p) {
    c.choice(
        "Posição",
        new String[] {"Em baixo", "Ao centro", "Em cima"},
        p.position,
        i -> {
          p.position = i;
          c.visualChanged();
        });
  }

  static void animation(EditorController c, Project.Caption p) {
    c.choice(
        "Animação",
        new String[] {"Frase", "Entrada e saída suave", "Palavras · ritmo estimado"},
        -1,
        i -> {
          p.animation = new String[] {"phrase", "fade", "word"}[i];
          c.visualChanged();
        });
  }
}
