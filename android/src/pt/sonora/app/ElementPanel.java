package pt.sonora.app;

import android.widget.*;

final class ElementPanel {
  static void show(EditorController c) {
    LinearLayout body = UiTheme.column(c);
    if (c.project.clips.isEmpty())
      PanelControls.note(c, body, "Ainda não há elementos neste projeto.");
    for (Project.Clip clip : c.project.clips) {
      LinearLayout row = UiTheme.row(c);
      TextView name = UiTheme.text(c, clip.name, 14, UiTheme.TEXT);
      row.addView(name, new LinearLayout.LayoutParams(0, c.dp(52), 1));
      row.addView(
          UiTheme.icon(
              c,
              "more",
              "Opções de " + clip.name,
              () -> {
                c.selectElement(clip);
                c.focusElement(clip);
                c.choice(
                    clip.name,
                    new String[] {"Selecionar", "Duplicar", "Eliminar", "Relocalizar ficheiro"},
                    -1,
                    i -> {
                      if (i == 1) c.screen.context.duplicate(clip);
                      else if (i == 2) c.confirmRemove(clip);
                      else if (i == 3) {
                        c.relinkId = clip.id;
                        c.pick(
                            15,
                            clip.type.equals("image")
                                ? "image/*"
                                : clip.type.equals("video") ? "video/*" : "audio/*",
                            false);
                      }
                    });
              }),
          new LinearLayout.LayoutParams(c.dp(48), c.dp(48)));
      body.addView(row);
    }
    PanelControls.button(c, body, "Exportar projeto JSON", c::exportProject);
    PanelControls.button(
        c, body, "Importar projeto JSON", () -> c.pick(13, "application/json", false));
    PanelControls.button(c, body, "Opções de projeto anteriores", () -> c.advanced.show("Projeto"));
    PanelControls.button(
        c,
        body,
        "Modo compatível 0.3",
        () -> c.startActivity(new android.content.Intent(c, pt.sonora.offline.MainActivity.class)));
    UiTheme.sheet(c, "Elementos e projeto", body);
  }
}
