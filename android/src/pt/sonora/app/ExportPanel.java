package pt.sonora.app;

import android.app.*;
import android.widget.*;
import java.util.*;

/** Export settings are a deliberate destination, not a permanent collection of editor buttons. */
final class ExportPanel {
  final EditorController c;
  Dialog dialog;
  LinearLayout body;
  TextView summary, phase, warning;
  ProgressBar progress;
  Button start, cancel, save, open, share;
  String checkedKey = "", capability = "";

  ExportPanel(EditorController controller) {
    c = controller;
  }

  static String shape(Project p) {
    return p.square ? "1:1" : p.vertical ? "9:16" : "16:9";
  }

  static void setShape(EditorController c, int index) {
    c.project.square = index == 2;
    c.project.vertical = index == 0;
    c.visualChanged();
  }

  static void format(EditorController c) {
    LinearLayout body = UiTheme.column(c);
    UiTheme.actions(
        body,
        UiTheme.action(c, "format", "9:16", () -> setShape(c, 0)),
        UiTheme.action(c, "media", "16:9", () -> setShape(c, 1)),
        UiTheme.action(c, "format", "1:1", () -> setShape(c, 2)));
    PanelControls.note(
        c,
        body,
        "Reels / TikTok / Shorts: 9:16. YouTube: 16:9. O enquadramento é partilhado pela composição"
            + " do projeto.");
    PanelControls.button(
        c,
        body,
        c.project.mediaFit ? "Ajustar → Preencher" : "Preencher → Ajustar",
        () -> {
          c.project.mediaFit = !c.project.mediaFit;
          c.visualChanged();
        });
    UiTheme.sheet(c, "Formato", body);
  }

  void show() {
    if (dialog != null && dialog.isShowing()) {
      update();
      return;
    }
    body = UiTheme.column(c);
    summary = UiTheme.text(c, "", 15, UiTheme.TEXT);
    UiTheme.item(body, summary, -1, 48);
    UiTheme.actions(
        body,
        UiTheme.action(
            c,
            "format",
            "9:16",
            () -> {
              setShape(c, 0);
              update();
            }),
        UiTheme.action(
            c,
            "media",
            "16:9",
            () -> {
              setShape(c, 1);
              update();
            }),
        UiTheme.action(
            c,
            "format",
            "1:1",
            () -> {
              setShape(c, 2);
              update();
            }));
    PanelControls.button(
        c,
        body,
        "Resolução",
        () ->
            c.choice(
                "Resolução",
                new String[] {"720p", "1080p", "1440p · se suportado", "4K · se suportado"},
                Arrays.asList(720, 1080, 1440, 2160).indexOf(c.project.resolution),
                i -> {
                  c.project.resolution = new int[] {720, 1080, 1440, 2160}[i];
                  c.visualChanged();
                  update();
                }));
    PanelControls.button(
        c,
        body,
        "Fotogramas por segundo",
        () ->
            c.choice(
                "FPS",
                new String[] {"24", "25", "30", "60 · se suportado"},
                Arrays.asList(24, 25, 30, 60).indexOf(c.project.fps),
                i -> {
                  c.project.fps = new int[] {24, 25, 30, 60}[i];
                  c.visualChanged();
                  update();
                }));
    PanelControls.range(
        c,
        body,
        "Qualidade / bitrate (Mbps)",
        c.project.bitrate / 1000000f,
        1,
        80,
        n -> {
          c.project.bitrate = (int) (n * 1000000);
          c.visualChanged();
          update();
        });
    warning = UiTheme.text(c, "", 13, UiTheme.MUTED);
    UiTheme.item(body, warning, -1, -2);
    phase = UiTheme.text(c, "", 13, UiTheme.TEXT);
    UiTheme.item(body, phase, -1, -2);
    progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    UiTheme.item(body, progress, -1, 8);
    start =
        UiTheme.primary(
            c,
            "Exportar MP4",
            () -> {
              c.exportVideo();
              update();
            });
    UiTheme.item(body, start, -1, 52);
    cancel =
        UiTheme.primary(
            c,
            "Cancelar exportação",
            () -> {
              if (EditorExportService.engine != null) EditorExportService.engine.cancelled = true;
              else if (c.exportStarting)
                c.startService(
                    new android.content.Intent(c, EditorExportService.class).setAction("cancel"));
              phase.setText("A cancelar… o projeto fica preservado.");
            });
    UiTheme.item(body, cancel, -1, 48);
    save = UiTheme.primary(c, "Guardar vídeo", () -> c.createBinary(20, "video/mp4", "sonora.mp4"));
    open = UiTheme.primary(c, "Reproduzir", () -> c.shareResult(false));
    share = UiTheme.primary(c, "Partilhar", () -> c.shareResult(true));
    UiTheme.item(body, save, -1, 48);
    UiTheme.item(body, open, -1, 48);
    UiTheme.item(body, share, -1, 48);
    dialog = UiTheme.sheet(c, "Exportar vídeo", body);
    update();
  }

  void update() {
    if (dialog == null || !dialog.isShowing() || summary == null) return;
    summary.setText(
        shape(c.project)
            + "  ·  "
            + c.project.resolution
            + "p  ·  "
            + c.project.fps
            + " FPS\n≈ "
            + String.format(
                Locale.ROOT, "%.1f MB", ProjectActions.estimatedBytes(c.project) / 1048576.0));
    boolean running = EditorExportService.running || c.exportStarting;
    String key =
        shape(c.project)
            + ":"
            + c.project.resolution
            + ":"
            + c.project.fps
            + ":"
            + c.project.bitrate;
    if (!running && !key.equals(checkedKey)) {
      checkedKey = key;
      try {
        ExportOptions.supportedCodec(c.project);
        capability =
            "Compatibilidade anunciada pelo codec. O tamanho é estimado; velocidade e resultado"
                + " podem variar.";
      } catch (Exception e) {
        capability = e.getMessage();
      }
    }
    warning.setText(capability);
    phase.setText(
        running
            ? EditorExportService.phase
            : EditorExportService.error.isEmpty()
                ? c.result != null
                    ? "Vídeo disponível para guardar, reproduzir ou partilhar."
                    : "A exportação é local, sem marca de água."
                : EditorExportService.error);
    progress.setVisibility(running ? android.view.View.VISIBLE : android.view.View.GONE);
    progress.setProgress(EditorExportService.percent);
    cancel.setVisibility(running ? android.view.View.VISIBLE : android.view.View.GONE);
    start.setEnabled(!running && !c.busy && !c.project.clips.isEmpty());
    boolean ready = !running && c.result != null && c.result.isFile();
    save.setVisibility(ready ? 0 : 8);
    open.setVisibility(ready ? 0 : 8);
    share.setVisibility(ready ? 0 : 8);
  }
}
