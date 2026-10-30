package pt.sonora.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Small-screen editor: preview, transport and timeline stay visible across all tabs. */
public class MainActivity extends Activity {
  Project project = new Project();
  ProjectStore store;
  PreviewView preview;
  TimelineView timeline;
  LinearLayout root, panel;
  ScrollView tracksScroll;
  HorizontalScrollView timelineScroll;
  TextView selectedLabel;
  Button removeElement, elementsButton;
  Dialog fullscreenDialog;
  LinearLayout fullscreenHost;
  TextView fullscreenTime;
  Button fullscreenPlay;
  SeekBar fullscreenSeek;
  ViewGroup previewHome;
  ViewGroup.LayoutParams previewHomeParams;
  int previewHomeIndex, previewHomeVisibility;
  int viewMode;
  ProgressBar progressBar;
  TextView status, time, heading;
  Button play;
  SeekBar seek;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler handler = new Handler(Looper.getMainLooper());
  String relinkId;
  int importing;
  String selected, tab = "Projeto", analysisKey = "";
  long position;
  boolean busy, updating;
  File result, audioResult, analysisFile, previewAudioFile;
  EditorExporter exporter;
  String pendingText, pendingMime, pendingName;
  LocalTranscriber transcriber;
  SpeechRecognizer downloadRecognizer;
  String transcriptionLanguage = "pt-PT";
  String[] lyricLines;
  int lyricIndex;
  Project.Caption lastLyric;
  final int accent = 0xffb7a4ff;
  float density;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    density = getResources().getDisplayMetrics().density;
    transcriptionLanguage = getPreferences(0).getString("transcriptionLanguage", "pt-PT");
    store = new ProjectStore(this);
    String previousOutput = getSharedPreferences("editor-export", 0).getString("output", "");
    if (!previousOutput.isEmpty() && new File(previousOutput).isFile())
      result = new File(previousOutput);
    try {
      File f = new File(store.directory, "autosave.json");
      if (f.exists()) project = store.load(f);
    } catch (Exception ignored) {
    }
    getWindow().setStatusBarColor(0xff090d19);
    getWindow().setNavigationBarColor(0xff090d19);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xff090d19);
    root.setPadding(dp(10), dp(4), dp(10), 0);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              dp(10),
              insets.getSystemWindowInsetTop() + dp(4),
              dp(10),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(root);
    LinearLayout header = row(root);
    heading = label(header, "SONORA · " + project.name, 16);
    header.removeView(heading);
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
    heading.setSingleLine(true);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    icon(
        header,
        "▥",
        "Alternar pré-visualização e linha temporal",
        v -> {
          viewMode = (viewMode + 1) % 4;
          preview.setVisibility(viewMode == 2 || viewMode == 3 ? View.GONE : View.VISIBLE);
          tracksScroll.setVisibility(viewMode == 1 || viewMode == 3 ? View.GONE : View.VISIBLE);
        });
    icon(header, "💾", "Guardar projeto", v -> saveProject());
    icon(header, "↗", "Exportar projeto JSON", v -> exportProject());
    preview = new PreviewView(this);
    int screen = getResources().getConfiguration().screenHeightDp;
    root.addView(
        preview,
        new LinearLayout.LayoutParams(-1, dp(Math.max(48, Math.min(72, (screen - 480) / 3)))));
    preview.listener =
        new PreviewView.Listener() {
          public void position(long ms) {
            position = ms;
            updateClock();
          }

          public void error(String m) {
            message(m);
          }
        };
    LinearLayout transport = row(root);
    play = icon(transport, "▶", "Reproduzir ou pausar", v -> togglePlayback());
    time = label(transport, "00:00", 12);
    icon(transport, "⛶", "Abrir pré-visualização em ecrã inteiro", v -> openFullscreen());
    seek = new SeekBar(this);
    transport.addView(seek, new LinearLayout.LayoutParams(0, dp(48), 1));
    seek.setContentDescription("Posição de reprodução");
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int value, boolean user) {
            if (user) {
              position = value;
              preview.seek(value);
              updateClock();
            }
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    HorizontalScrollView scroller = new HorizontalScrollView(this);
    timelineScroll = scroller;
    timeline = new TimelineView(this);
    scroller.addView(timeline);
    tracksScroll = new ScrollView(this);
    tracksScroll.addView(scroller);
    root.addView(tracksScroll, new LinearLayout.LayoutParams(-1, dp(screen < 600 ? 64 : 76)));
    if (screen < 600) {
      tracksScroll.setVisibility(View.GONE);
      viewMode = 1;
    }
    timeline.listener =
        new TimelineView.Listener() {
          public void select(Project.Clip c) {
            selectElement(c);
          }

          public void seek(long ms) {
            position = ms;
            preview.seek(ms);
            updateClock();
          }

          public void changed() {
            project.autoImages = false;
            MainActivity.this.changed();
          }
        };
    LinearLayout elementBar = row(root);
    elementsButton =
        icon(elementBar, "☷", "Ver todos os elementos do projeto", v -> showElements());
    selectedLabel = label(elementBar, "Sem elementos", 12);
    elementBar.removeView(selectedLabel);
    selectedLabel.setSingleLine(true);
    selectedLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
    elementBar.addView(selectedLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
    removeElement =
        icon(
            elementBar,
            "✕",
            "Remover elemento selecionado",
            v -> {
              Project.Clip c = clip();
              if (c != null) confirmRemove(c);
              else showElements();
            });
    status = label(root, "Importa música, vídeo ou imagens para começar.", 12);
    status.setMaxLines(1);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    status.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("Estado do projeto")
                .setMessage(status.getText())
                .setPositiveButton("Fechar", null)
                .show());
    progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progressBar.setMax(100);
    progressBar.setVisibility(View.GONE);
    root.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(5)));
    ScrollView tools = new ScrollView(this);
    panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(2), 0, dp(2), dp(8));
    tools.addView(panel);
    root.addView(tools, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout nav = row(root);
    String[] tabs = {"Projeto", "Efeitos", "Áudio", "Legendas", "Exportar"},
        symbols = {"▤", "✦", "♫", "CC", "⇧"};
    for (int i = 0; i < tabs.length; i++) {
      String name = tabs[i];
      Button b = new Button(this);
      b.setAllCaps(false);
      b.setText(symbols[i] + "\n" + name);
      b.setTextSize(10);
      b.setContentDescription(name);
      b.setMinHeight(dp(56));
      b.setPadding(0, 0, 0, 0);
      b.setOnClickListener(v -> showTab(name));
      nav.addView(b, new LinearLayout.LayoutParams(0, dp(58), 1));
    }
    preview.setProject(project);
    showTab(tab);
    refresh();
    if (EditorExportService.running) handler.post(exportTick);
  }

  int dp(float n) {
    return Math.round(n * density);
  }

  LinearLayout row(LinearLayout parent) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    return row;
  }

  TextView label(LinearLayout box, String text, int size) {
    TextView view = new TextView(this);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(0xffd7dcef);
    view.setGravity(Gravity.CENTER_VERTICAL);
    view.setPadding(dp(4), dp(4), dp(4), dp(4));
    box.addView(view);
    return view;
  }

  Button icon(LinearLayout box, String text, String description, View.OnClickListener click) {
    Button b = new Button(this);
    b.setText(text);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setContentDescription(description);
    b.setPadding(0, 0, 0, 0);
    b.setMinWidth(0);
    b.setOnClickListener(click);
    box.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48)));
    return b;
  }

  Button button(String text, Runnable action) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    b.setMinHeight(dp(48));
    b.setOnClickListener(
        v -> {
          if (busy) {
            message("Aguarda a operação atual ou cancela a exportação.");
            return;
          }
          action.run();
        });
    panel.addView(b, new LinearLayout.LayoutParams(-1, -2));
    return b;
  }

  void message(String value) {
    if (status != null) status.setText(value == null ? "Operação indisponível." : value);
    if (progressBar != null && value != null) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)%").matcher(value);
      if (m.find()) {
        progressBar.setIndeterminate(false);
        progressBar.setProgress(Integer.parseInt(m.group(1)));
      }
    }
  }

  void refresh() {
    if (heading != null) heading.setText("SONORA · " + project.name);
    timeline.update(project, selected, position);
    if (selectedLabel != null) {
      Project.Clip c = clip();
      selectedLabel.setText(c == null ? project.clips.size() + " elementos · toca em ☷" : c.name);
      removeElement.setEnabled(c != null);
      elementsButton.setContentDescription(
          "Ver os " + project.clips.size() + " elementos do projeto");
    }
    seek.setMax((int) project.durationMs());
    updateClock();
  }

  void updateClock() {
    time.setText(
        String.format(
            Locale.ROOT,
            "%02d:%02d / %02d:%02d",
            position / 60000,
            (position / 1000) % 60,
            project.durationMs() / 60000,
            (project.durationMs() / 1000) % 60));
    seek.setProgress((int) position);
    timeline.playhead = position;
    timeline.invalidate();
    if (!preview.playing) play.setText("▶");
    if (fullscreenTime != null) {
      fullscreenTime.setText(time.getText());
      fullscreenSeek.setMax((int) project.durationMs());
      fullscreenSeek.setProgress((int) position);
      fullscreenPlay.setText(preview.playing ? "Ⅱ" : "▶");
    }
  }

  void changed() {
    try {
      store.save(project, true);
    } catch (Exception e) {
      message("Não foi possível guardar automaticamente: " + e.getMessage());
    }
    preview.setProject(project);
    refresh();
  }

  Project.Clip clip() {
    return project.find(selected);
  }

  void pick(int code, String type, boolean multiple) {
    preview.pause();
    Intent intent =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType(type)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
    startActivityForResult(intent, code);
  }

  void showTab(String value) {
    tab = value;
    panel.removeAllViews();
    if ((tab.equals("Áudio") && BuildStage.PHASE < 2)
        || (tab.equals("Efeitos") && BuildStage.PHASE < 3)
        || (tab.equals("Legendas") && BuildStage.PHASE < 4)) {
      label(
          panel,
          "Esta versão experimental inclui até à fase "
              + BuildStage.PHASE
              + ". Este painel será ativado na fase seguinte correspondente.",
          14);
      return;
    }
    if (tab.equals("Projeto")) projectTab();
    else if (tab.equals("Efeitos")) effectsTab();
    else if (tab.equals("Áudio")) audioTab();
    else if (tab.equals("Legendas")) captionsTab();
    else exportTab();
  }

  void selectElement(Project.Clip clip) {
    selected = clip.id;
    refresh();
    showTab(tab);
  }

  void focusElement(Project.Clip clip) {
    if (clip == null) return;
    tracksScroll.setVisibility(View.VISIBLE);
    viewMode = preview.getVisibility() == View.VISIBLE ? 0 : 2;
    timeline.post(
        () -> {
          tracksScroll.scrollTo(0, Math.max(0, timeline.rowTop(clip.id) - dp(8)));
          timelineScroll.scrollTo(
              Math.max(0, (int) (clip.startMs * timeline.scale * density) - dp(10)), 0);
        });
  }

  void showElements() {
    if (project.clips.isEmpty()) {
      message("Ainda não há elementos. Importa vídeo, imagem ou som.");
      return;
    }
    ArrayList<Project.Clip> clips = new ArrayList<>(project.clips);
    String[] names = new String[clips.size()];
    for (int i = 0; i < clips.size(); i++) {
      Project.Clip c = clips.get(i);
      names[i] =
          (c.type.equals("video") ? "Vídeo" : c.type.equals("image") ? "Imagem" : "Som")
              + " · "
              + c.name;
    }
    new AlertDialog.Builder(this)
        .setTitle("Elementos do projeto")
        .setItems(
            names,
            (d, index) -> {
              Project.Clip c = clips.get(index);
              selectElement(c);
              focusElement(c);
              new AlertDialog.Builder(this)
                  .setTitle(c.name)
                  .setItems(
                      new String[] {"Selecionar na linha temporal", "Remover do projeto"},
                      (dialog, action) -> {
                        if (action == 1) confirmRemove(c);
                      })
                  .setNegativeButton("Fechar", null)
                  .show();
            })
        .setNegativeButton("Fechar", null)
        .show();
  }

  AlertDialog confirmRemove(Project.Clip clip) {
    if (clip == null || busy) {
      if (busy) message("Aguarda a operação atual antes de remover.");
      return null;
    }
    return new AlertDialog.Builder(this)
        .setTitle("Remover elemento?")
        .setMessage(clip.name + "\nO ficheiro original fica guardado no telemóvel.")
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Remover", (d, w) -> removeClip(clip.id))
        .show();
  }

  void removeClip(String id) {
    if (busy) return;
    if (!project.removeClip(id)) return;
    selected =
        project.clips.isEmpty()
            ? null
            : project.clips.get(Math.max(0, project.clips.size() - 1)).id;
    position = project.clips.isEmpty() ? 0 : Math.min(position, project.durationMs());
    analysisKey = "";
    preview.setAnalysis(null);
    changed();
    showTab(tab);
    focusElement(clip());
    message("Elemento removido do projeto. O ficheiro original foi preservado.");
  }

  void togglePlayback() {
    if (busy) {
      message("Aguarda a preparação do áudio.");
      return;
    }
    if (project.clips.isEmpty()) {
      message("Importa primeiro um vídeo, imagem ou som.");
      return;
    }
    if (preview.playing) {
      preview.pause();
      play.setText("▶");
      updateClock();
    } else preparePlay();
  }

  void openFullscreen() {
    if (fullscreenDialog != null) return;
    previewHome = (ViewGroup) preview.getParent();
    previewHomeIndex = previewHome.indexOfChild(preview);
    previewHomeParams = preview.getLayoutParams();
    previewHomeVisibility = preview.getVisibility();
    previewHome.removeView(preview);
    preview.setVisibility(View.VISIBLE);
    fullscreenDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    fullscreenHost = new LinearLayout(this);
    fullscreenHost.setOrientation(LinearLayout.VERTICAL);
    fullscreenHost.setBackgroundColor(Color.BLACK);
    fullscreenHost.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout controls = row(fullscreenHost);
    fullscreenPlay =
        icon(
            controls,
            preview.playing ? "Ⅱ" : "▶",
            "Reproduzir ou pausar em ecrã inteiro",
            v -> togglePlayback());
    fullscreenTime = label(controls, time.getText().toString(), 12);
    Button close = new Button(this);
    close.setText("Voltar ao editor");
    close.setAllCaps(false);
    close.setContentDescription("Sair do ecrã inteiro");
    close.setOnClickListener(
        v -> {
          if (fullscreenDialog != null) fullscreenDialog.dismiss();
        });
    controls.addView(close, new LinearLayout.LayoutParams(0, dp(48), 1));
    fullscreenSeek = new SeekBar(this);
    fullscreenSeek.setMax((int) project.durationMs());
    fullscreenSeek.setProgress((int) position);
    fullscreenSeek.setContentDescription("Posição da pré-visualização em ecrã inteiro");
    fullscreenHost.addView(fullscreenSeek, new LinearLayout.LayoutParams(-1, dp(48)));
    fullscreenSeek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}

          public void onProgressChanged(SeekBar b, int ms, boolean user) {
            if (user) {
              position = ms;
              preview.seek(ms);
              updateClock();
            }
          }
        });
    fullscreenDialog.setContentView(fullscreenHost);
    fullscreenDialog.setOnDismissListener(
        d -> {
          fullscreenHost.removeView(preview);
          previewHome.addView(preview, previewHomeIndex, previewHomeParams);
          preview.setVisibility(previewHomeVisibility);
          fullscreenDialog = null;
          fullscreenHost = null;
          fullscreenTime = null;
          fullscreenPlay = null;
          fullscreenSeek = null;
          setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
          preview.render();
        });
    fullscreenDialog.show();
    fullscreenDialog.getWindow().setLayout(-1, -1);
    fullscreenDialog
        .getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    preview.render();
  }

  void projectTab() {
    label(panel, "PROJETO · edição não destrutiva", 15);
    button("♫ Importar música", () -> pick(10, "audio/*", true));
    button("▣ Importar vídeo", () -> pick(11, "video/*", true));
    button("▧ Importar imagens", () -> pick(12, "image/*", true));
    button("☷ Elementos do projeto (" + project.clips.size() + ")", this::showElements);
    if (clip() != null) button("✕ Remover: " + clip().name, () -> confirmRemove(clip()));

    button(
        "Modo compatível · Todas as funções Sonora 0.3",
        () -> {
          if (EditorExportService.running) {
            message("Aguarda a exportação atual.");
            return;
          }
          startActivity(new Intent(this, pt.sonora.offline.MainActivity.class));
        });
    button(
        "Enquadramento: " + (project.mediaFit ? "Ajustar" : "Preencher"),
        () -> {
          project.mediaFit = !project.mediaFit;
          changed();
          showTab(tab);
        });
    button(
        project.showTitle ? "Ocultar título" : "Mostrar título",
        () -> {
          project.showTitle = !project.showTitle;
          changed();
          showTab(tab);
        });
    button(
        "Distribuir fotografias pela música",
        () -> {
          project.autoImages = true;
          arrangeImages();
          changed();
        });

    button("Abrir projeto guardado", this::openProject);
    button("Importar projeto JSON", () -> pick(13, "application/json", false));
    button(
        "Nome e título",
        () -> {
          LinearLayout fields = fields();
          EditText name = input(fields, "Nome do projeto", project.name),
              title = input(fields, "Título no vídeo", project.title);
          new AlertDialog.Builder(this)
              .setTitle("Projeto")
              .setView(fields)
              .setNegativeButton("Cancelar", null)
              .setPositiveButton(
                  "Guardar",
                  (d, w) -> {
                    project.name = name.getText().toString();
                    project.title = title.getText().toString();
                    changed();
                  })
              .show();
        });
    Project.Clip c = clip();
    if (c != null) {
      label(panel, "Selecionado: " + c.name, 14);
      button("✂ Tempos, faixa e posição", () -> editClip(c));
      button(
          "Relocalizar ficheiro de origem",
          () -> {
            relinkId = c.id;
            pick(
                15,
                c.type.equals("image") ? "image/*" : c.type.equals("video") ? "video/*" : "audio/*",
                false);
          });
      button("Remover clip do projeto", () -> confirmRemove(c));
    }
    button(
        "＋ Zoom da linha temporal",
        () -> {
          timeline.scale = Math.min(.4f, timeline.scale * 1.5f);
          refresh();
        });
    button(
        "− Zoom da linha temporal",
        () -> {
          timeline.scale = Math.max(.005f, timeline.scale / 1.5f);
          refresh();
        });
    button(
        "Novo projeto (guardar primeiro o atual)",
        () ->
            new AlertDialog.Builder(this)
                .setMessage("O projeto atual fica guardado antes de criar um novo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                    "Criar",
                    (d, w) -> {
                      try {
                        store.save(project, false);
                        project = new Project();
                        selected = null;
                        position = 0;
                        analysisKey = "";
                        lyricLines = null;
                        lastLyric = null;
                        changed();
                        showTab(tab);
                      } catch (Exception e) {
                        message(e.getMessage());
                      }
                    })
                .show());
  }

  LinearLayout fields() {
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(dp(20), dp(4), dp(20), dp(4));
    return fields;
  }

  EditText input(LinearLayout box, String hint, String value) {
    EditText e = new EditText(this);
    e.setText(value);
    e.setHint(hint);
    e.setContentDescription(hint);
    e.setMinHeight(dp(48));
    box.addView(e);
    return e;
  }

  void editClip(Project.Clip c) {
    LinearLayout fields = fields();
    label(fields, c.name + " · valores em segundos", 14);
    EditText start = input(fields, "Posição", String.valueOf(c.startMs / 1000.0)),
        in = input(fields, "Entrada no ficheiro", String.valueOf(c.inMs / 1000.0)),
        length = input(fields, "Duração", String.valueOf(c.lengthMs / 1000.0)),
        track = input(fields, "Faixa (0 vídeo, 1 imagem, 2+ áudio)", String.valueOf(c.track));
    new AlertDialog.Builder(this)
        .setTitle("Editar clip")
        .setView(fields)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Aplicar",
            (d, w) -> {
              try {
                Project.Clip copy = Project.Clip.read(c.json());
                copy.startMs = seconds(start);
                copy.inMs = seconds(in);
                copy.lengthMs = seconds(length);
                copy.track = Integer.parseInt(track.getText().toString());
                copy.fadeInMs = Math.min(copy.fadeInMs, copy.lengthMs);
                copy.fadeOutMs = Math.min(copy.fadeOutMs, copy.lengthMs);
                copy.validate();
                project.clips.set(project.clips.indexOf(c), copy);
                if (copy.type.equals("image")) project.autoImages = false;
                changed();
                showTab(tab);
              } catch (Exception e) {
                message("Tempos inválidos. O clip anterior foi preservado.");
              }
            })
        .show();
  }

  long seconds(EditText input) {
    double value = Double.parseDouble(input.getText().toString().replace(',', '.'));
    if (!Double.isFinite(value)) throw new IllegalArgumentException();
    return Math.round(value * 1000);
  }

  interface Choose {
    void chosen(int index);
  }

  void choice(String title, String[] names, int current, Choose action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(
            names,
            current,
            (d, i) -> {
              d.dismiss();
              action.chosen(i);
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  void slider(
      String title, float value, float min, float max, java.util.function.Consumer<Float> apply) {
    label(panel, title + ": " + String.format(Locale.ROOT, "%.2f", value), 13);
    SeekBar b = new SeekBar(this);
    b.setContentDescription(title);
    b.setMax(100);
    b.setProgress((int) ((value - min) / (max - min) * 100));
    b.setMinHeight(dp(48));
    panel.addView(b);
    b.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {}

          public void onProgressChanged(SeekBar s, int n, boolean user) {}

          public void onStopTrackingTouch(SeekBar s) {
            if (busy) return;
            apply.accept(min + (max - min) * s.getProgress() / 100);
            changed();
            showTab(tab);
          }
        });
  }

  void effectsTab() {
    button(
        "Efeitos anteriores · Sonora 0.3",
        () ->
            choice(
                "Estilos preservados",
                pt.sonora.offline.Options.NAMES,
                0,
                i -> {
                  project.effect = "legacy-" + i;
                  changed();
                  showTab(tab);
                }));
    button(
        project.enhancedEffects ? "Acabamento detalhado" : "Modo leve original",
        () -> {
          project.enhancedEffects = !project.enhancedEffects;
          changed();
          showTab(tab);
        });
    button(
        project.fullEffect ? "Composição em todo o ecrã" : "Composição centralizada",
        () -> {
          project.fullEffect = !project.fullEffect;
          changed();
          showTab(tab);
        });
    label(panel, "EFEITOS · parâmetros guardados no projeto", 15);
    button(
        "✦ "
            + VisualEffects.NAMES[
                Math.max(0, Arrays.asList(VisualEffects.IDS).indexOf(project.effect))],
        () ->
            choice(
                "Visualização",
                VisualEffects.NAMES,
                Arrays.asList(VisualEffects.IDS).indexOf(project.effect),
                i -> {
                  project.effect = VisualEffects.IDS[i];
                  changed();
                  showTab(tab);
                }));
    button(
        "Reatividade: " + project.syncBand,
        () ->
            choice(
                "Sincronizar com",
                new String[] {
                  "Energia global", "Graves", "Médios", "Agudos", "Transientes / batidas estimadas"
                },
                Arrays.asList("energy", "bass", "mid", "treble", "beat").indexOf(project.syncBand),
                i -> {
                  project.syncBand = new String[] {"energy", "bass", "mid", "treble", "beat"}[i];
                  changed();
                  showTab(tab);
                }));
    slider("Intensidade", project.intensity, 0, 3, n -> project.intensity = n);
    slider("Velocidade", project.speed, .1f, 4, n -> project.speed = n);
    slider("Densidade", project.density, 8, 120, n -> project.density = n.intValue());
    slider("Distorção do fundo", project.distortion, 0, 1, n -> project.distortion = n);
    slider("Zoom", project.zoom, 1, 3, n -> project.zoom = n);
    slider("Rotação", project.rotation, -180, 180, n -> project.rotation = n);
    button(
        "Cor",
        () ->
            choice(
                "Cor",
                new String[] {"Lavanda", "Ciano", "Rosa", "Dourado", "Branco"},
                0,
                i -> {
                  project.color =
                      new int[] {0xffb7a4ff, 0xff56e7ef, 0xffff6fae, 0xffffcf70, 0xffffffff}[i];
                  changed();
                }));
    button(
        "Transição: " + project.transition,
        () -> {
          project.transition = project.transition.equals("fade") ? "none" : "fade";
          changed();
          showTab(tab);
        });
    label(
        panel,
        "Graves, médios e agudos são analisados localmente. A pré-visualização prepara a mistura"
            + " antes de reproduzir. Batidas são transientes estimados, não deteção musical"
            + " perfeita.",
        12);
  }

  void audioTab() {
    label(panel, "ÁUDIO · seleciona uma faixa na linha temporal", 15);
    Project.Clip c = clip();
    if (c == null || c.type.equals("image")) {
      label(panel, "Seleciona música ou vídeo para ajustar o som.", 14);
      return;
    }
    label(panel, c.name, 14);
    if (c.type.equals("video")) {
      button(
          c.originalAudio ? "✓ Manter áudio original" : "Áudio original silenciado",
          () -> {
            c.originalAudio = !c.originalAudio;
            changed();
            showTab(tab);
          });
      button("Separar áudio para faixa independente", () -> separate(c));
      button("Extrair áudio para ficheiro M4A", () -> extract(c));
    }
    button(
        c.muted ? "Ativar som da faixa" : "Silenciar faixa",
        () -> {
          c.muted = !c.muted;
          changed();
          showTab(tab);
        });
    slider("Volume", c.volume, 0, 2, n -> c.volume = n);
    slider(
        "Fade de entrada (s)",
        c.fadeInMs / 1000f,
        0,
        Math.min(10, c.lengthMs / 1000f),
        n -> c.fadeInMs = (long) (n * 1000));
    slider(
        "Fade de saída (s)",
        c.fadeOutMs / 1000f,
        0,
        Math.min(10, c.lengthMs / 1000f),
        n -> c.fadeOutMs = (long) (n * 1000));
    label(
        panel,
        "Mistura automática das faixas ativas. Volumes altos podem saturar; reduz cada faixa se"
            + " necessário. Separar áudio não separa voz e instrumentos.",
        12);
  }

  void separate(Project.Clip c) {
    try {
      if (!c.hasAudio) {
        message("Este vídeo não tem áudio.");
        return;
      }
      Project.Clip a = Project.Clip.read(c.json());
      a.id = UUID.randomUUID().toString();
      a.type = "audio";
      a.name = "Áudio · " + c.name;
      a.track = nextAudioTrack();
      a.originalAudio = true;
      project.clips.add(a);
      c.originalAudio = false;
      selected = a.id;
      changed();
      showTab("Áudio");
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void arrangeImages() {
    ArrayList<Project.Clip> images = new ArrayList<>();
    long duration = 0;
    for (Project.Clip c : project.clips) {
      if (c.type.equals("image")) images.add(c);
      else duration = Math.max(duration, c.endMs());
    }
    if (duration == 0) duration = 10000;
    for (int i = 0; i < images.size(); i++) {
      Project.Clip c = images.get(i);
      c.startMs = i * duration / images.size();
      c.lengthMs = Math.max(100, (i + 1) * duration / images.size() - c.startMs);
      c.inMs = 0;
    }
  }

  int nextAudioTrack() {
    int n = 2;
    for (Project.Clip c : project.clips) if (c.type.equals("audio")) n = Math.max(n, c.track + 1);
    return Math.min(n, 15);
  }

  void captionsTab() {
    button("Diagnóstico da transcrição · Android", this::speechDiagnostics);
    button(
        "Idioma de transcrição: " + transcriptionLanguage,
        () ->
            choice(
                "Variante de português",
                new String[] {"Português (Portugal) · pt-PT", "Português (Brasil) · pt-BR"},
                transcriptionLanguage.equals("pt-PT") ? 0 : 1,
                i -> {
                  transcriptionLanguage = i == 0 ? "pt-PT" : "pt-BR";
                  getPreferences(0)
                      .edit()
                      .putString("transcriptionLanguage", transcriptionLanguage)
                      .apply();
                  showTab(tab);
                }));
    button(
        project.showSubtitles ? "Ocultar legendas" : "Mostrar legendas",
        () -> {
          project.showSubtitles = !project.showSubtitles;
          changed();
          showTab(tab);
        });
    button(
        "Posição das legendas",
        () ->
            choice(
                "Posição",
                new String[] {"Em baixo", "Ao centro", "Em cima"},
                project.subtitlePosition,
                i -> {
                  project.subtitlePosition = i;
                  changed();
                }));
    button(
        "Tamanho das legendas",
        () ->
            choice(
                "Tamanho",
                new String[] {"Pequeno", "Médio", "Grande"},
                project.subtitleSize,
                i -> {
                  project.subtitleSize = i;
                  changed();
                }));
    button(
        "Atraso / avanço das legendas",
        () -> {
          LinearLayout f = fields();
          EditText offset =
              input(f, "Segundos (-600 a 600)", String.valueOf(project.subtitleOffsetMs / 1000.0));
          new AlertDialog.Builder(this)
              .setView(f)
              .setPositiveButton(
                  "Aplicar",
                  (d, w) -> {
                    try {
                      long ms = seconds(offset);
                      if (Math.abs(ms) > 600000) throw new Exception();
                      project.subtitleOffsetMs = ms;
                      changed();
                    } catch (Exception e) {
                      message("Usa um valor entre -600 e 600 segundos.");
                    }
                  })
              .setNegativeButton("Cancelar", null)
              .show();
        });
    label(panel, "LEGENDAS · texto e tempos editáveis", 15);
    button(
        "Remover todas as legendas",
        () ->
            new AlertDialog.Builder(this)
                .setMessage("Remover as legendas apenas deste projeto?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                    "Remover",
                    (d, w) -> {
                      project.captions.clear();
                      changed();
                      showTab(tab);
                    })
                .show());
    button("＋ Adicionar legenda manual", () -> editCaption(null));
    button("Importar SRT", () -> pick(14, "*/*", false));
    button(
        "Exportar SRT",
        () -> createText(Subtitles.write(project.captions), "application/x-subrip", "sonora.srt"));
    button("Introduzir letra e marcar tempos", this::lyrics);
    if (lyricLines != null && lyricIndex < lyricLines.length)
      button("Marcar agora: " + lyricLines[lyricIndex], this::markLyric);
    button("Transcrever voz localmente · pt-PT", this::transcribe);
    button(
        "Estilo: " + project.subtitleStyle,
        () ->
            choice(
                "Tipografia",
                new String[] {"Sans", "Serif", "Monoespaçada"},
                0,
                i -> {
                  project.subtitleStyle = new String[] {"sans", "serif", "mono"}[i];
                  changed();
                  showTab(tab);
                }));
    button(
        "Animação: " + project.subtitleAnimation,
        () ->
            choice(
                "Animação",
                new String[] {"Frase", "Fade por frase", "Palavras (ritmo estimado)"},
                0,
                i -> {
                  project.subtitleAnimation = new String[] {"phrase", "fade", "word"}[i];
                  changed();
                  showTab(tab);
                }));
    for (Project.Caption c : project.captions)
      button(Subtitles.format(c.startMs) + " · " + c.text, () -> editCaption(c));
  }

  void editCaption(Project.Caption c) {
    LinearLayout fields = fields();
    EditText text = input(fields, "Texto", c == null ? "" : c.text),
        start =
            input(
                fields, "Início (s)", String.valueOf((c == null ? position : c.startMs) / 1000.0)),
        end =
            input(
                fields,
                "Fim (s)",
                String.valueOf(
                    (c == null ? Math.min(project.durationMs(), position + 2000) : c.endMs)
                        / 1000.0));
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle("Legenda")
            .setView(fields)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(
                "Guardar",
                (d, w) -> {
                  try {
                    long a = seconds(start), b = seconds(end);
                    if (a < 0
                        || b <= a
                        || b > Project.MAX_MS
                        || text.getText().toString().trim().isEmpty()) throw new Exception();
                    Project.Caption replacement =
                        new Project.Caption(a, b, text.getText().toString());
                    if (c != null) project.captions.set(project.captions.indexOf(c), replacement);
                    else project.captions.add(replacement);
                    changed();
                    showTab(tab);
                  } catch (Exception e) {
                    message("Texto ou tempos inválidos; legenda anterior preservada.");
                  }
                });
    if (c != null)
      dialog.setNeutralButton(
          "Remover",
          (d, w) -> {
            project.captions.remove(c);
            changed();
            showTab(tab);
          });
    dialog.show();
  }

  void lyrics() {
    LinearLayout fields = fields();
    EditText text = input(fields, "Uma frase por linha", "");
    text.setMinLines(4);
    new AlertDialog.Builder(this)
        .setTitle("Letra conhecida")
        .setMessage(
            "Reproduz o áudio e toca em Marcar agora no início de cada frase. O fim da frase"
                + " anterior será atualizado. Não há alinhamento automático de voz.")
        .setView(fields)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Preparar marcação",
            (d, w) -> {
              lyricLines =
                  Arrays.stream(text.getText().toString().split("\\n"))
                      .map(String::trim)
                      .filter(s -> !s.isEmpty())
                      .toArray(String[]::new);
              lyricIndex = 0;
              lastLyric = null;
              showTab(tab);
            })
        .show();
  }

  void markLyric() {
    if (position >= project.durationMs()) return;
    if (lastLyric != null && position > lastLyric.startMs) lastLyric.endMs = position;
    lastLyric = new Project.Caption(position, project.durationMs(), lyricLines[lyricIndex++]);
    project.captions.add(lastLyric);
    preview.updateAnnotations(project);
    try {
      store.save(project, true);
    } catch (Exception e) {
      message(e.getMessage());
    }
    timeline.update(project, selected, position);
    showTab(tab);
  }

  void exportTab() {
    label(panel, "EXPORTAR · MP4 H.264 + AAC", 15);
    if (BuildStage.PHASE < 5) {
      button(
          "Orientação: " + (project.vertical ? "Vertical" : "Horizontal"),
          () -> {
            project.vertical = !project.vertical;
            changed();
            showTab(tab);
          });
      button("Exportar 720p / 24 FPS", this::exportVideo);
      if (result != null) button("Guardar MP4", () -> createBinary(20, "video/mp4", "sonora.mp4"));
      return;
    }
    button(
        "Destino: " + (project.vertical ? "Reels / TikTok / Shorts" : "YouTube horizontal"),
        () ->
            choice(
                "Formato",
                new String[] {"Reels / TikTok / Shorts · 9:16", "YouTube · 16:9", "Quadrado · 1:1"},
                project.vertical ? 0 : 1,
                i -> {
                  project.vertical = i == 0;
                  project.square = i == 2;
                  changed();
                  showTab(tab);
                }));
    button(
        "Resolução: " + project.resolution + "p",
        () ->
            choice(
                "Verificar capacidade antes de exportar",
                new String[] {"720p", "1080p", "1440p (se suportado)", "2160p / 4K (se suportado)"},
                Arrays.asList(720, 1080, 1440, 2160).indexOf(project.resolution),
                i -> {
                  project.resolution = new int[] {720, 1080, 1440, 2160}[i];
                  changed();
                  showTab(tab);
                }));
    button(
        "FPS: " + project.fps,
        () ->
            choice(
                "Fotogramas por segundo",
                new String[] {"24", "25", "30", "60 (se suportado)"},
                Arrays.asList(24, 25, 30, 60).indexOf(project.fps),
                i -> {
                  project.fps = new int[] {24, 25, 30, 60}[i];
                  changed();
                  showTab(tab);
                }));
    slider(
        "Bitrate (Mbps)",
        project.bitrate / 1000000f,
        1,
        80,
        n -> project.bitrate = (int) (n * 1000000));
    button(
        "Verificar compatibilidade",
        () -> {
          try {
            message(
                "Codec disponível: "
                    + ExportOptions.supportedCodec(project)
                    + ". Isto não garante velocidade de exportação.");
          } catch (Exception e) {
            message(e.getMessage());
          }
        });
    button("⇧ Exportar vídeo", this::exportVideo);
    Button cancel = button("Cancelar exportação", () -> {});
    cancel.setOnClickListener(
        v -> {
          if (EditorExportService.engine != null) EditorExportService.engine.cancelled = true;
        });
    if (result != null && result.isFile()) {
      button("Reproduzir MP4", () -> shareResult(false));
      button("Partilhar MP4", () -> shareResult(true));
    }
    if (result != null && result.isFile())
      button("Guardar MP4 no telemóvel", () -> createBinary(20, "video/mp4", "sonora.mp4"));
    label(
        panel,
        "O dispositivo é verificado antes da exportação. 4K/60 FPS podem não ser suportados ou ser"
            + " muito lentos. Mantém a app aberta durante o processamento.",
        12);
  }

  void saveProject() {
    try {
      store.save(project, false);
      message("Projeto guardado no telemóvel.");
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void openProject() {
    File[] files = store.projects();
    String[] names = new String[files.length];
    for (int i = 0; i < files.length; i++)
      try {
        names[i] = store.load(files[i]).name;
      } catch (Exception e) {
        names[i] = "Projeto ilegível";
      }
    if (files.length == 0) {
      message("Ainda não há projetos guardados.");
      return;
    }
    choice(
        "Abrir projeto",
        names,
        -1,
        i -> {
          try {
            Project next = store.load(files[i]);
            store.save(project, false);
            project = next;
            selected = null;
            position = 0;
            changed();
            showTab(tab);
          } catch (Exception e) {
            message(e.getMessage());
          }
        });
  }

  void exportProject() {
    try {
      createText(project.json().toString(2), "application/json", "sonora-projeto.json");
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void createText(String text, String mime, String name) {
    pendingText = text;
    pendingMime = mime;
    pendingName = name;
    createBinary(21, mime, name);
  }

  void createBinary(int code, String mime, String name) {
    preview.pause();
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(mime)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, name);
    startActivityForResult(i, code);
  }

  void setBusy(boolean value) {
    busy = value;
    if (progressBar != null) {
      progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
      progressBar.setIndeterminate(value);
    }
    if (value) {
      preview.pause();
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  void exportVideo() {
    try {
      if (pt.sonora.offline.LegacyBridge.exporting()) {
        message("Aguarda a exportação do modo compatível.");
        return;
      }
      Project snapshot = project.copy();
      ExportOptions.supportedCodec(snapshot);
      if (Build.VERSION.SDK_INT >= 33
          && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
              != PackageManager.PERMISSION_GRANTED)
        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 41);
      File document = new File(getFilesDir(), "export-project-" + UUID.randomUUID() + ".json");
      try (OutputStream out = new FileOutputStream(document)) {
        out.write(snapshot.json().toString().getBytes(StandardCharsets.UTF_8));
      }
      Intent intent =
          new Intent(this, EditorExportService.class)
              .putExtra("projectPath", document.getAbsolutePath());
      setBusy(true);
      message("A iniciar exportação local em segundo plano…");
      startForegroundService(intent);
      handler.postDelayed(exportTick, 300);
    } catch (Exception e) {
      setBusy(false);
      message(e.getMessage());
    }
  }

  final Runnable exportTick =
      new Runnable() {
        public void run() {
          if (EditorExportService.running) {
            setBusy(true);
            message(EditorExportService.phase);
            handler.postDelayed(this, 300);
          } else {
            setBusy(false);
            if (!EditorExportService.error.isEmpty()) message(EditorExportService.error);
            else if (!EditorExportService.output.isEmpty()) {
              result = new File(EditorExportService.output);
              message("MP4 concluído. Podes guardar, reproduzir ou partilhar.");
            }
            showTab("Exportar");
          }
        }
      };

  Uri resultUri() {
    return Uri.parse("content://" + getPackageName() + ".legacy.videos/" + result.getName());
  }

  void shareResult(boolean share) {
    if (result == null || !result.exists()) return;
    Intent intent =
        share
            ? new Intent(Intent.ACTION_SEND)
                .setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, resultUri())
            : new Intent(Intent.ACTION_VIEW).setDataAndType(resultUri(), "video/mp4");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri("Vídeo Sonora", resultUri()));
    try {
      startActivity(share ? Intent.createChooser(intent, "Partilhar MP4") : intent);
    } catch (ActivityNotFoundException e) {
      message("Não existe aplicação para abrir ou partilhar este vídeo. Usa Guardar MP4.");
    }
  }

  String mediaKey() {
    try {
      org.json.JSONArray a = new org.json.JSONArray();
      for (Project.Clip c : project.clips) a.put(c.json());
      return a.toString();
    } catch (Exception e) {
      return UUID.randomUUID().toString();
    }
  }

  void preparePlay() {
    String key = mediaKey();
    if (analysisFile != null && analysisFile.exists() && key.equals(analysisKey)) {
      preview.setAnalysis(analysisFile);
      preview.setAudio(previewAudioFile);
      preview.play();
      play.setText("Ⅱ");
      return;
    }
    if (busy) return;
    try {
      Project snapshot = project.copy();
      setBusy(true);
      message("A preparar pré-visualização e análise local…");
      worker.execute(
          () -> {
            File dir = null;
            try {
              EditorExporter e = new EditorExporter(this);
              dir = e.workspace();
              File pcm = new File(getCacheDir(), "preview.pcm");
              e.mix(snapshot, pcm, dir, m -> runOnUiThread(() -> message(m)));
              File wave = new File(getCacheDir(), "preview.wav");
              AudioFiles.wave(pcm, wave, 48000, 2);
              runOnUiThread(
                  () -> {
                    previewAudioFile = wave;
                    analysisFile = pcm;
                    analysisKey = key;
                    preview.setAnalysis(pcm);
                    preview.setAudio(wave);
                    setBusy(false);
                    preview.play();
                    play.setText("Ⅱ");
                    message(
                        "Pré-visualização · o áudio e os efeitos usam a mesma montagem da"
                            + " exportação.");
                  });
            } catch (Exception ex) {
              runOnUiThread(
                  () -> {
                    setBusy(false);
                    message(ex.getMessage());
                  });
            } finally {
              if (dir != null) EditorExporter.remove(dir);
            }
          });
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void extract(Project.Clip c) {
    if (!c.hasAudio) {
      message("O vídeo não tem áudio.");
      return;
    }
    setBusy(true);
    worker.execute(
        () -> {
          File dir = null;
          try {
            dir = new EditorExporter(this).workspace();
            File pcm = new File(dir, "audio.pcm"), out = new File(getCacheDir(), "extracted.m4a");
            Converter converter = new Converter(this);
            converter.decode(Uri.parse(c.uri), pcm);
            converter.encodeAudio(pcm, out);
            runOnUiThread(
                () -> {
                  audioResult = out;
                  setBusy(false);
                  message("Áudio extraído sem alterar o vídeo original.");
                  createBinary(22, "audio/mp4", "sonora-audio.m4a");
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  setBusy(false);
                  message(e.getMessage());
                });
          } finally {
            if (dir != null) EditorExporter.remove(dir);
          }
        });
  }

  @Override
  protected void onActivityResult(int request, int response, Intent data) {
    super.onActivityResult(request, response, data);
    if (response != RESULT_OK || data == null) return;
    if (request == 10 || request == 11 || request == 12) {
      ArrayList<Uri> uris = new ArrayList<>();
      if (data.getClipData() != null)
        for (int i = 0; i < data.getClipData().getItemCount(); i++)
          uris.add(data.getClipData().getItemAt(i).getUri());
      else if (data.getData() != null) uris.add(data.getData());
      for (Uri uri : uris) importMedia(uri, request);
      return;
    }
    Uri uri = data.getData();
    if (uri == null) return;
    if (request == 15) {
      Project.Clip c = project.find(relinkId);
      if (c == null) return;
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!c.type.equals("image")) {
          MediaMetadataRetriever m = new MediaMetadataRetriever();
          try {
            m.setDataSource(this, uri);
            long duration =
                Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (duration < c.inMs + c.lengthMs)
              throw new IOException("O novo ficheiro é mais curto que o clip.");
            c.sourceDurationMs = duration;
          } finally {
            m.release();
          }
        }
        c.uri = uri.toString();
        c.name = displayName(uri);
        changed();
        message("Origem relocalizada. O ficheiro anterior não foi alterado.");
      } catch (Exception e) {
        message("Não foi possível relocalizar: " + e.getMessage());
      }
      return;
    }
    if (request == 13 || request == 14) {
      try (InputStream in = getContentResolver().openInputStream(uri)) {
        if (request == 13) {
          Project next = Project.read(ProjectStore.read(in));
          store.save(project, false);
          project = next;
          selected = null;
          position = 0;
        } else {
          ArrayList<Project.Caption> captions = Subtitles.read(in);
          project.captions.addAll(captions);
        }
        changed();
        showTab(tab);
      } catch (Exception e) {
        message("Importação rejeitada: " + e.getMessage());
      }
      return;
    }
    if (request >= 20 && request <= 22) {
      File file = request == 20 ? result : audioResult;
      String text = pendingText;
      worker.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
              if (out == null) throw new IOException("Destino indisponível");
              if (request == 21) out.write(text.getBytes(StandardCharsets.UTF_8));
              else
                try (InputStream in = new FileInputStream(file)) {
                  byte[] b = new byte[65536];
                  int n;
                  while ((n = in.read(b)) != -1) out.write(b, 0, n);
                }
              runOnUiThread(() -> message("Ficheiro guardado na pasta escolhida."));
            } catch (Exception e) {
              runOnUiThread(() -> message("Não foi possível guardar: " + e.getMessage()));
            }
          });
    }
  }

  void importMedia(Uri uri, int request) {
    boolean persistent = true;
    try {
      getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Exception e) {
      persistent = false;
    }
    final boolean retained = persistent;
    importing++;
    final long imageDuration = project.durationMs();
    setBusy(true);
    worker.execute(
        () -> {
          try {
            Project.Clip c = new Project.Clip();
            c.uri = uri.toString();
            c.name = displayName(uri);
            c.type = request == 10 ? "audio" : request == 11 ? "video" : "image";
            if (c.type.equals("image")) {
              android.graphics.BitmapFactory.Options options =
                  new android.graphics.BitmapFactory.Options();
              options.inJustDecodeBounds = true;
              try (InputStream in = getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(in, null, options);
              }
              if (options.outWidth <= 0) throw new IOException("Imagem inválida");
              c.lengthMs = imageDuration;
              c.sourceDurationMs = Project.MAX_MS;
            } else {
              MediaMetadataRetriever metadata = new MediaMetadataRetriever();
              try {
                metadata.setDataSource(this, uri);
                String value =
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                c.sourceDurationMs = Long.parseLong(value);
                if (c.sourceDurationMs > Project.MAX_MS)
                  throw new IOException("Nesta versão, cada ficheiro deve ter até 10 minutos.");
                c.lengthMs = Math.min(c.sourceDurationMs, Project.MAX_MS);
                c.hasAudio =
                    "yes"
                        .equals(
                            metadata.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
                if (c.type.equals("video")
                    && !"yes"
                        .equals(
                            metadata.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))
                  throw new IOException("Não contém vídeo");
              } finally {
                metadata.release();
              }
            }
            c.validate();
            runOnUiThread(
                () -> {
                  if (project.clips.size() >= 10000) {
                    message("Projeto demasiado grande para esta versão.");
                    importing--;
                    setBusy(importing > 0);
                    return;
                  }
                  c.track =
                      c.type.equals("video") ? 0 : c.type.equals("image") ? 1 : nextAudioTrack();
                  project.clips.add(c);
                  if (project.autoImages) arrangeImages();
                  selected = c.id;
                  position = 0;
                  importing--;
                  setBusy(importing > 0);
                  changed();
                  showTab("Projeto");
                  focusElement(c);
                  message(
                      retained
                          ? "Ficheiro importado. Arrasta o clip ou edita os tempos."
                          : "Importado; esta origem não garante acesso após reiniciar. Mantém o"
                              + " ficheiro disponível.");
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  importing--;
                  setBusy(importing > 0);
                  message("Não foi possível importar: " + e.getMessage());
                });
          }
        });
  }

  String displayName(Uri uri) {
    try (Cursor cursor =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
    } catch (Exception ignored) {
    }
    String segment = uri.getLastPathSegment();
    if (segment != null && !segment.trim().isEmpty())
      return segment.substring(segment.lastIndexOf('/') + 1);
    return "Ficheiro importado";
  }

  void speechDiagnostics() {
    new AlertDialog.Builder(this)
        .setTitle("Diagnóstico da transcrição")
        .setMessage(
            LocalTranscriber.diagnostics(this) + "\nIdioma pedido: " + transcriptionLanguage)
        .setPositiveButton(
            "Definições de voz",
            (d, w) -> {
              try {
                startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS));
              } catch (ActivityNotFoundException e) {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
              }
            })
        .setNeutralButton("Obter modelo", (d, w) -> requestSpeechModel())
        .setNegativeButton("Fechar", null)
        .show();
  }

  void requestSpeechModel() {
    if (!LocalTranscriber.available(this)) {
      new AlertDialog.Builder(this)
          .setTitle("Serviço local indisponível")
          .setMessage(
              "O Android não anuncia um serviço de reconhecimento local para esta aplicação."
                  + " Configurar uma voz em saída de texto não adiciona esse serviço. Atualiza ou"
                  + " configura o serviço de reconhecimento de voz nas definições do Android;"
                  + " depois volta a verificar o diagnóstico.")
          .setPositiveButton("Ver diagnóstico", (d, w) -> speechDiagnostics())
          .setNegativeButton("Fechar", null)
          .show();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Pedir modelo de " + transcriptionLanguage)
        .setMessage(
            "O Android pode descarregar o modelo através da Internet. É o modelo de reconhecimento,"
                + " não uma voz de leitura TTS. Nenhum áudio do projeto será enviado pelo Sonora. O"
                + " serviço decide se este idioma está disponível.")
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Pedir ao Android",
            (d, w) -> {
              try {
                if (downloadRecognizer != null) downloadRecognizer.destroy();
                downloadRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                downloadRecognizer.triggerModelDownload(
                    new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, transcriptionLanguage)
                        .putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM));
                message(
                    "Pedido de modelo enviado ao Android. Aguarda a instalação no serviço de"
                        + " reconhecimento e tenta transcrever novamente.");
              } catch (Exception e) {
                message("O serviço não aceitou o pedido de modelo: " + e.getMessage());
              }
            })
        .show();
  }

  void transcribe() {
    if (project.clips.isEmpty()) {
      message("Importa primeiro um áudio ou vídeo com voz.");
      return;
    }
    if (Build.VERSION.SDK_INT < 33 || !LocalTranscriber.available(this)) {
      speechDiagnostics();
      return;
    }
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 40);
      return;
    }
    prepareTranscription();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] names, int[] grants) {
    super.onRequestPermissionsResult(request, names, grants);
    if (request == 40) {
      if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) transcribe();
      else
        new AlertDialog.Builder(this)
            .setTitle("Permissão de reconhecimento")
            .setMessage(
                "A transcrição precisa da permissão de reconhecimento/microfone exigida pelo"
                    + " Android. Os ficheiros do projeto continuam preservados.")
            .setPositiveButton("Fechar", null)
            .show();
    }
  }

  void prepareTranscription() {
    try {
      Project snapshot = project.copy();
      setBusy(true);
      message("A preparar áudio para transcrição local…");
      worker.execute(
          () -> {
            File dir = null;
            try {
              EditorExporter e = new EditorExporter(this);
              dir = e.workspace();
              File mixed = new File(dir, "mixed.pcm");
              e.mix(snapshot, mixed, dir, m -> runOnUiThread(() -> message(m)));
              File speech = new File(getCacheDir(), "speech16.pcm");
              try (RandomAccessFile in = new RandomAccessFile(mixed, "r");
                  OutputStream out = new BufferedOutputStream(new FileOutputStream(speech))) {
                byte[] b = new byte[12];
                while (in.read(b) == 12) {
                  int total = 0;
                  for (int i = 0; i < 12; i += 2) total += (short) ((b[i] & 255) | (b[i + 1] << 8));
                  short sample = (short) (total / 6);
                  out.write(sample & 255);
                  out.write((sample >> 8) & 255);
                }
              }
              runOnUiThread(
                  () -> {
                    message("A transcrever voz localmente em " + transcriptionLanguage + "…");
                    transcriber = new LocalTranscriber(MainActivity.this);
                    transcriber.start(
                        speech,
                        snapshot.durationMs(),
                        transcriptionLanguage,
                        new LocalTranscriber.Result() {
                          public void success(String text, ArrayList<Project.Caption> timed) {
                            if (timed.isEmpty())
                              project.captions.add(
                                  new Project.Caption(0, snapshot.durationMs(), text));
                            else project.captions.addAll(timed);
                            setBusy(false);
                            changed();
                            showTab("Legendas");
                            message(
                                timed.isEmpty()
                                    ? "Rascunho de transcrição criado. O motor não forneceu tempos;"
                                        + " corrige e divide as frases manualmente."
                                    : "Transcrição com tempos fornecidos pelo motor. Revê palavras"
                                        + " e sincronização antes de exportar.");
                          }

                          public void failure(String error) {
                            setBusy(false);
                            message(error);
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Transcrição não concluída")
                                .setMessage(error)
                                .setPositiveButton("Ver diagnóstico", (d, w) -> speechDiagnostics())
                                .setNegativeButton("Fechar", null)
                                .show();
                          }
                        });
                  });
            } catch (Exception ex) {
              runOnUiThread(
                  () -> {
                    setBusy(false);
                    message(ex.getMessage());
                  });
            } finally {
              if (dir != null) EditorExporter.remove(dir);
            }
          });
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    preview.pause();
    try {
      store.save(project, true);
    } catch (Exception ignored) {
    }
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacks(exportTick);
    if (fullscreenDialog != null) fullscreenDialog.dismiss();
    if (downloadRecognizer != null) downloadRecognizer.destroy();
    if (transcriber != null) transcriber.close();
    preview.close();
    worker.shutdown();
    super.onDestroy();
  }
}
