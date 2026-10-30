package pt.sonora.app;

import android.view.*;
import android.widget.*;

/** Three-area editor; only the contextual tools and bottom sheets change on selection. */
final class EditorScreen extends LinearLayout {
  final EditorController c;
  final ContextTools context;
  final ExportPanel exportPanel;
  final PlayerControls player;
  final TextView title;
  final LinearLayout contextHost;

  EditorScreen(EditorController controller) {
    super(controller);
    c = controller;
    setOrientation(VERTICAL);
    setBackgroundColor(UiTheme.BG);
    UiTheme.inset(this, 8);
    c.root = this;
    LinearLayout header = UiTheme.row(c);
    header.addView(
        UiTheme.icon(c, "back", "Projetos", c::showHome), new LayoutParams(c.dp(48), c.dp(48)));
    title = UiTheme.text(c, c.project.name, 16, UiTheme.TEXT);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(title, new LayoutParams(0, c.dp(48), 1));
    header.addView(
        UiTheme.icon(c, "save", "Guardar projeto", c::saveProject),
        new LayoutParams(c.dp(48), c.dp(48)));
    Button export = UiTheme.primary(c, "Exportar", () -> openTool("Exportar"));
    header.addView(export, new LayoutParams(c.dp(94), c.dp(44)));
    addView(header);
    boolean landscape =
        getResources().getDisplayMetrics().widthPixels
            > getResources().getDisplayMetrics().heightPixels;
    LinearLayout work = new LinearLayout(c);
    work.setOrientation(landscape ? HORIZONTAL : VERTICAL);
    addView(work, new LayoutParams(-1, 0, 1));
    LinearLayout playerColumn = UiTheme.column(c);
    if (c.preview == null) {
      c.preview = new PreviewView(c);
      c.preview.listener =
          new PreviewView.Listener() {
            public void position(long ms) {
              c.position = ms;
              c.updateClock();
            }

            public void error(String text) {
              c.message(text);
            }
          };
    } else detach(c.preview);
    playerColumn.addView(c.preview, new LayoutParams(-1, 0, 1));
    player = new PlayerControls(c);
    playerColumn.addView(player, new LayoutParams(-1, c.dp(44)));
    if (landscape) work.addView(playerColumn, new LayoutParams(0, -1, .48f));
    else
      work.addView(
          playerColumn,
          new LayoutParams(
              -1,
              c.dp(
                  Math.min(
                      210,
                      Math.max(120, getResources().getConfiguration().screenHeightDp * .25f)))));
    LinearLayout timelineColumn = UiTheme.column(c);
    timelineColumn.setBackground(UiTheme.shape(c, UiTheme.SURFACE, 12));
    LinearLayout ruler = UiTheme.row(c);
    ruler.addView(
        UiTheme.text(c, "LINHA TEMPORAL", 10, UiTheme.MUTED), new LayoutParams(0, c.dp(36), 1));
    ruler.addView(
        UiTheme.icon(c, "minus", "Diminuir zoom da timeline", () -> zoom(.7f)),
        new LayoutParams(c.dp(42), c.dp(36)));
    ruler.addView(
        UiTheme.icon(c, "plus", "Aumentar zoom da timeline", () -> zoom(1.4f)),
        new LayoutParams(c.dp(42), c.dp(36)));
    TextView fit = UiTheme.text(c, "Ajustar", 12, UiTheme.BLUE);
    fit.setOnClickListener(
        v -> {
          c.timeline.fit = true;
          c.timeline.requestLayout();
        });
    ruler.addView(fit, new LayoutParams(c.dp(56), c.dp(36)));
    timelineColumn.addView(ruler);
    HorizontalScrollView horizontal = new HorizontalScrollView(c);
    horizontal.setHorizontalScrollBarEnabled(true);
    if (c.timeline == null) c.timeline = new TimelineView(c);
    else detach(c.timeline);
    c.timeline.artwork = c.artwork;
    c.timeline.listener =
        new TimelineView.Listener() {
          public void select(Project.Clip clip) {
            c.originalSelected = false;
            c.selectElement(clip);
          }

          public void selectOriginal(Project.Clip clip) {
            c.selectElement(clip);
            c.originalSelected = true;
            refreshSelection();
          }

          public void selectCaption(Project.Caption caption) {
            c.selected = null;
            c.selectedCaption = caption;
            refreshSelection();
          }

          public void seek(long ms) {
            c.position = ms;
            c.preview.seek(ms);
            c.updateClock();
          }

          public void changed() {
            c.project.autoImages = false;
            c.changed();
          }
        };
    horizontal.addView(c.timeline);
    ScrollView vertical = new ScrollView(c);
    vertical.addView(horizontal);
    timelineColumn.addView(vertical, new LayoutParams(-1, 0, 1));
    c.timelineScroll = horizontal;
    c.tracksScroll = vertical;
    if (landscape) work.addView(timelineColumn, new LayoutParams(0, -1, .52f));
    else work.addView(timelineColumn, new LayoutParams(-1, 0, 1));
    contextHost = UiTheme.column(c);
    context = new ContextTools(c, contextHost);
    addView(contextHost, new LayoutParams(-1, -2));
    LinearLayout tools = UiTheme.column(c);
    String[] names = {"Média", "Áudio", "Texto", "Legendas", "Efeitos", "Elementos", "Formato"},
        icons = {"plus", "audio", "text", "subtitle", "effect", "media", "format"};
    View[] actions = new View[names.length];
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      actions[i] = UiTheme.action(c, icons[i], name, () -> openTool(name));
    }
    UiTheme.actions(tools, actions);
    addView(tools, new LayoutParams(-1, -2));
    c.status = UiTheme.text(c, "Tudo é processado no dispositivo", 11, UiTheme.MUTED);
    c.status.setMaxLines(1);
    c.status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    c.status.setOnClickListener(
        v -> UiTheme.notice(c, "Estado do projeto", c.status.getText().toString()));
    addView(c.status, new LayoutParams(-1, c.dp(22)));
    c.progressBar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
    c.progressBar.setVisibility(GONE);
    addView(c.progressBar, new LayoutParams(-1, c.dp(3)));
    exportPanel = new ExportPanel(c);
    refresh();
  }

  static void detach(View view) {
    if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);
  }

  void zoom(float factor) {
    c.timeline.fit = false;
    c.timeline.scale = Math.max(.0005f, Math.min(.6f, c.timeline.scale * factor));
    c.timeline.requestLayout();
    c.timeline.invalidate();
  }

  void refresh() {
    title.setText(c.project.name);
    c.timeline.update(c.project, c.selected, c.position);
    c.timeline.selectedCaption = c.selectedCaption;
    refreshSelection();
    c.updateClock();
  }

  void refreshSelection() {
    if (c.timeline != null) {
      c.timeline.selected = c.selected;
      c.timeline.selectedCaption = c.selectedCaption;
      c.timeline.invalidate();
    }
    context.render();
  }

  void openTool(String key) {
    if (c.busy && !key.equals("Exportar")) {
      c.message("Aguarda a operação atual ou cancela a exportação.");
      return;
    }
    switch (key) {
      case "Média":
        MediaPickerSheet.show(c);
        break;
      case "Áudio":
        AudioPanel.show(c);
        break;
      case "Texto":
        SubtitlePanel.title(c);
        break;
      case "Legendas":
        SubtitlePanel.show(c);
        break;
      case "Efeitos":
        EffectsPanel.show(c);
        break;
      case "Formato":
        ExportPanel.format(c);
        break;
      case "Exportar":
        exportPanel.show();
        break;
      default:
        ElementPanel.show(c);
    }
  }
}
