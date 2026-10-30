package pt.sonora.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class HomeScreen extends LinearLayout {
  final EditorController c;
  final LinearLayout list;
  final TextView empty;
  TextView operation;

  HomeScreen(EditorController controller) {
    super(controller);
    c = controller;
    setOrientation(VERTICAL);
    setBackgroundColor(UiTheme.BG);
    UiTheme.inset(this, 18);
    LinearLayout header = UiTheme.row(c);
    TextView brand = UiTheme.text(c, "SONORA", 26, UiTheme.TEXT);
    brand.setTypeface(Typeface.create("sans-serif-medium", 0));
    header.addView(brand, new LayoutParams(0, c.dp(72), 1));
    header.addView(
        UiTheme.icon(c, "settings", "Definições", () -> c.projects.settings()),
        new LayoutParams(c.dp(48), c.dp(48)));
    addView(header);
    operation = UiTheme.text(c, "", 12, UiTheme.BLUE);
    operation.setVisibility(GONE);
    addView(operation, new LayoutParams(-1, c.dp(32)));
    TextView tagline = UiTheme.text(c, "O teu som. A tua visão.", 16, UiTheme.MUTED);
    addView(tagline, new LayoutParams(-1, c.dp(40)));
    Button create = UiTheme.primary(c, "+  Novo projeto", () -> c.projects.create());
    UiTheme.item(this, create, -1, 64);
    LinearLayout section = UiTheme.row(c);
    section.addView(
        UiTheme.text(c, "Projetos recentes", 18, UiTheme.TEXT), new LayoutParams(0, c.dp(64), 1));
    addView(section);
    ScrollView scroll = new ScrollView(c);
    list = UiTheme.column(c);
    scroll.addView(list);
    addView(scroll, new LayoutParams(-1, 0, 1));
    empty = UiTheme.text(c, "A carregar projetos…", 14, UiTheme.MUTED);
    list.addView(empty);
    c.worker.execute(
        () -> {
          File[] files = c.store.projects();
          Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
          ArrayList<Project> projects = new ArrayList<>();
          HashSet<String> ids = new HashSet<>();
          ArrayList<String> errors = new ArrayList<>();
          for (File file : files)
            try {
              Project p = c.store.load(file);
              projects.add(p);
              ids.add(p.id);
            } catch (Exception e) {
              errors.add(file.getName());
            }
          File auto = new File(c.store.directory, "autosave.json");
          if (auto.exists())
            try {
              Project p = c.store.load(auto);
              if (!ids.contains(p.id)) projects.add(0, p);
            } catch (Exception e) {
              errors.add("Recuperação automática indisponível");
            }
          c.runOnUiThread(() -> populate(projects, errors));
        });
  }

  void operation(String text) {
    operation.setText(text == null ? "" : text);
    operation.setVisibility(text == null || text.isEmpty() ? GONE : VISIBLE);
  }

  void populate(ArrayList<Project> projects, ArrayList<String> errors) {
    if (c.home != this || c.editorVisible) return;
    list.removeAllViews();
    if (projects.isEmpty())
      UiTheme.item(
          list,
          UiTheme.text(
              c,
              "Ainda não tens projetos.\nCria um e importa música, vídeos ou imagens para começar.",
              16,
              UiTheme.MUTED),
          -1,
          112);
    int shown = 0;
    for (Project p : projects) {
      if (shown++ >= 30) break;
      LinearLayout card = UiTheme.row(c);
      card.setPadding(c.dp(10), c.dp(10), c.dp(8), c.dp(10));
      card.setBackground(UiTheme.shape(c, UiTheme.SURFACE, 14));
      FrameLayout cover = new FrameLayout(c);
      cover.setBackground(UiTheme.shape(c, UiTheme.RAISED, 8));
      cover.addView(
          new UiTheme.Icon(
              c,
              p.clips.isEmpty()
                  ? "format"
                  : p.clips.get(0).type.equals("audio") ? "audio" : "media"),
          new FrameLayout.LayoutParams(-1, -1));
      ImageView image = new ImageView(c);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
      File poster = new File(c.store.directory, p.id + ".jpg");
      if (poster.exists()) image.setImageBitmap(BitmapFactory.decodeFile(poster.getAbsolutePath()));
      else c.artwork.bind(image, p);
      card.addView(cover, new LayoutParams(c.dp(88), c.dp(66)));
      LinearLayout copy = UiTheme.column(c);
      copy.setPadding(c.dp(12), 0, 0, 0);
      TextView name = UiTheme.text(c, p.name, 16, UiTheme.TEXT);
      name.setMaxLines(2);
      copy.addView(name);
      copy.addView(
          UiTheme.text(
              c,
              String.format(
                  Locale.ROOT,
                  "%d elementos · %d:%02d",
                  p.clips.size(),
                  p.durationMs() / 60000,
                  (p.durationMs() / 1000) % 60),
              12,
              UiTheme.MUTED));
      card.addView(copy, new LayoutParams(0, -2, 1));
      card.addView(
          UiTheme.icon(c, "more", "Opções do projeto " + p.name, () -> c.projects.menu(p)),
          new LayoutParams(c.dp(48), c.dp(48)));
      card.setContentDescription("Continuar projeto " + p.name);
      card.setOnClickListener(v -> c.projects.open(p));
      UiTheme.item(list, card, -1, -2);
    }
    if (projects.size() > 30)
      UiTheme.item(
          list,
          UiTheme.primary(
              c,
              "Ver todos os projetos",
              () -> {
                String[] names = new String[projects.size()];
                for (int i = 0; i < names.length; i++) names[i] = projects.get(i).name;
                c.choice("Projetos", names, -1, i -> c.projects.menu(projects.get(i)));
              }),
          -1,
          48);
    if (!errors.isEmpty())
      UiTheme.item(
          list,
          UiTheme.text(
              c,
              errors.size()
                  + " documentos não puderam ser abertos. Os ficheiros foram preservados.",
              12,
              UiTheme.MUTED),
          -1,
          -2);
  }
}
