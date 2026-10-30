package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Advanced 0.4.1 controls retained behind an explicit options sheet. */
final class LegacyPanels {
  final EditorController session;
  android.app.Dialog dialog;
  android.widget.LinearLayout body;

  LegacyPanels(EditorController controller) {
    session = controller;
  }

  boolean visible() {
    return dialog != null && dialog.isShowing();
  }

  void show(String key) {
    session.tab = key;
    body = UiTheme.column(session);
    dialog = UiTheme.sheet(session, "Opções avançadas · " + key, body);
    render();
  }

  void render() {
    if (body == null) return;
    body.removeAllViews();
    session.panel = body;
    switch (session.tab) {
      case "Projeto":
        projectTab();
        break;
      case "Efeitos":
        effectsTab();
        break;
      case "Áudio":
        audioTab();
        break;
      case "Legendas":
        captionsTab();
        break;
      default:
        exportTab();
    }
  }

  void projectTab() {
    session.label(session.panel, "PROJETO · edição não destrutiva", 15);
    session.button("♫ Importar música", () -> session.pick(10, "audio/*", true));
    session.button("▣ Importar vídeo", () -> session.pick(11, "video/*", true));
    session.button("▧ Importar imagens", () -> session.pick(12, "image/*", true));
    session.button(
        "☷ Elementos do projeto (" + session.project.clips.size() + ")", session::showElements);
    if (session.clip() != null)
      session.button(
          "✕ Remover: " + session.clip().name, () -> session.confirmRemove(session.clip()));

    session.button(
        "Modo compatível · Todas as funções Sonora 0.3",
        () -> {
          if (EditorExportService.running) {
            session.message("Aguarda a exportação atual.");
            return;
          }
          session.startActivity(new Intent(session, pt.sonora.offline.MainActivity.class));
        });
    session.button(
        "Enquadramento: " + (session.project.mediaFit ? "Ajustar" : "Preencher"),
        () -> {
          session.project.mediaFit = !session.project.mediaFit;
          session.changed();
          session.showTab(session.tab);
        });
    session.button(
        session.project.showTitle ? "Ocultar título" : "Mostrar título",
        () -> {
          session.project.showTitle = !session.project.showTitle;
          session.changed();
          session.showTab(session.tab);
        });
    session.button(
        "Distribuir fotografias pela música",
        () -> {
          session.project.autoImages = true;
          session.arrangeImages();
          session.changed();
        });

    session.button("Abrir projeto guardado", session::openProject);
    session.button("Importar projeto JSON", () -> session.pick(13, "application/json", false));
    session.button(
        "Nome e título",
        () -> {
          LinearLayout fields = session.fields();
          EditText name = session.input(fields, "Nome do projeto", session.project.name),
              title = session.input(fields, "Título no vídeo", session.project.title);
          new AlertDialog.Builder(session)
              .setTitle("Projeto")
              .setView(fields)
              .setNegativeButton("Cancelar", null)
              .setPositiveButton(
                  "Guardar",
                  (d, w) -> {
                    session.project.name = name.getText().toString();
                    session.project.title = title.getText().toString();
                    session.changed();
                  })
              .show();
        });
    Project.Clip c = session.clip();
    if (c != null) {
      session.label(session.panel, "Selecionado: " + c.name, 14);
      session.button("✂ Tempos, faixa e posição", () -> session.editClip(c));
      session.button(
          "Relocalizar ficheiro de origem",
          () -> {
            session.relinkId = c.id;
            session.pick(
                15,
                c.type.equals("image") ? "image/*" : c.type.equals("video") ? "video/*" : "audio/*",
                false);
          });
      session.button("Remover clip do projeto", () -> session.confirmRemove(c));
    }
    session.button(
        "＋ Zoom da linha temporal",
        () -> {
          session.timeline.scale = Math.min(.4f, session.timeline.scale * 1.5f);
          session.refresh();
        });
    session.button(
        "− Zoom da linha temporal",
        () -> {
          session.timeline.scale = Math.max(.005f, session.timeline.scale / 1.5f);
          session.refresh();
        });
    session.button(
        "Novo projeto (guardar primeiro o atual)",
        () ->
            new AlertDialog.Builder(session)
                .setMessage("O projeto atual fica guardado antes de criar um novo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                    "Criar",
                    (d, w) -> {
                      try {
                        session.store.save(session.project, false);
                        session.project = new Project();
                        session.selected = null;
                        session.position = 0;
                        session.analysisKey = "";
                        session.lyricLines = null;
                        session.lastLyric = null;
                        session.changed();
                        session.showTab(session.tab);
                      } catch (Exception e) {
                        session.message(e.getMessage());
                      }
                    })
                .show());
  }

  void effectsTab() {
    session.button(
        "Efeitos anteriores · Sonora 0.3",
        () ->
            session.choice(
                "Estilos preservados",
                pt.sonora.offline.Options.NAMES,
                0,
                i -> {
                  session.project.effect = "legacy-" + i;
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.button(
        session.project.enhancedEffects ? "Acabamento detalhado" : "Modo leve original",
        () -> {
          session.project.enhancedEffects = !session.project.enhancedEffects;
          session.changed();
          session.showTab(session.tab);
        });
    session.button(
        session.project.fullEffect ? "Composição em todo o ecrã" : "Composição centralizada",
        () -> {
          session.project.fullEffect = !session.project.fullEffect;
          session.changed();
          session.showTab(session.tab);
        });
    session.label(session.panel, "EFEITOS · parâmetros guardados no projeto", 15);
    session.button(
        "✦ "
            + VisualEffects.NAMES[
                Math.max(0, Arrays.asList(VisualEffects.IDS).indexOf(session.project.effect))],
        () ->
            session.choice(
                "Visualização",
                VisualEffects.NAMES,
                Arrays.asList(VisualEffects.IDS).indexOf(session.project.effect),
                i -> {
                  session.project.effect = VisualEffects.IDS[i];
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.button(
        "Reatividade: " + session.project.syncBand,
        () ->
            session.choice(
                "Sincronizar com",
                new String[] {
                  "Energia global", "Graves", "Médios", "Agudos", "Transientes / batidas estimadas"
                },
                Arrays.asList("energy", "bass", "mid", "treble", "beat")
                    .indexOf(session.project.syncBand),
                i -> {
                  session.project.syncBand =
                      new String[] {"energy", "bass", "mid", "treble", "beat"}[i];
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.slider(
        "Intensidade", session.project.intensity, 0, 3, n -> session.project.intensity = n);
    session.slider("Velocidade", session.project.speed, .1f, 4, n -> session.project.speed = n);
    session.slider(
        "Densidade", session.project.density, 8, 120, n -> session.project.density = n.intValue());
    session.slider(
        "Distorção do fundo",
        session.project.distortion,
        0,
        1,
        n -> session.project.distortion = n);
    session.slider("Zoom", session.project.zoom, 1, 3, n -> session.project.zoom = n);
    session.slider(
        "Rotação", session.project.rotation, -180, 180, n -> session.project.rotation = n);
    session.button(
        "Cor",
        () ->
            session.choice(
                "Cor",
                new String[] {"Lavanda", "Ciano", "Rosa", "Dourado", "Branco"},
                0,
                i -> {
                  session.project.color =
                      new int[] {0xffb7a4ff, 0xff56e7ef, 0xffff6fae, 0xffffcf70, 0xffffffff}[i];
                  session.changed();
                }));
    session.button(
        "Transição: " + session.project.transition,
        () -> {
          session.project.transition = session.project.transition.equals("fade") ? "none" : "fade";
          session.changed();
          session.showTab(session.tab);
        });
    session.label(
        session.panel,
        "Graves, médios e agudos são analisados localmente. A pré-visualização prepara a mistura"
            + " antes de reproduzir. Batidas são transientes estimados, não deteção musical"
            + " perfeita.",
        12);
  }

  void audioTab() {
    session.label(session.panel, "ÁUDIO · seleciona uma faixa na linha temporal", 15);
    Project.Clip c = session.clip();
    if (c == null || c.type.equals("image")) {
      session.label(session.panel, "Seleciona música ou vídeo para ajustar o som.", 14);
      return;
    }
    session.label(session.panel, c.name, 14);
    if (c.type.equals("video")) {
      session.button(
          c.originalAudio ? "✓ Manter áudio original" : "Áudio original silenciado",
          () -> {
            c.originalAudio = !c.originalAudio;
            session.changed();
            session.showTab(session.tab);
          });
      session.button("Separar áudio para faixa independente", () -> session.separate(c));
      session.button("Extrair áudio para ficheiro M4A", () -> session.extract(c));
    }
    session.button(
        c.muted ? "Ativar som da faixa" : "Silenciar faixa",
        () -> {
          c.muted = !c.muted;
          session.changed();
          session.showTab(session.tab);
        });
    session.slider("Volume", c.volume, 0, 2, n -> c.volume = n);
    session.slider(
        "Fade de entrada (s)",
        c.fadeInMs / 1000f,
        0,
        Math.min(10, c.lengthMs / 1000f),
        n -> c.fadeInMs = (long) (n * 1000));
    session.slider(
        "Fade de saída (s)",
        c.fadeOutMs / 1000f,
        0,
        Math.min(10, c.lengthMs / 1000f),
        n -> c.fadeOutMs = (long) (n * 1000));
    session.label(
        session.panel,
        "Mistura automática das faixas ativas. Volumes altos podem saturar; reduz cada faixa se"
            + " necessário. Separar áudio não separa voz e instrumentos.",
        12);
  }

  void captionsTab() {
    session.button("Diagnóstico da transcrição · Android", session::speechDiagnostics);
    session.button(
        "Idioma de transcrição: " + session.transcriptionLanguage,
        () ->
            session.choice(
                "Variante de português",
                new String[] {"Português (Portugal) · pt-PT", "Português (Brasil) · pt-BR"},
                session.transcriptionLanguage.equals("pt-PT") ? 0 : 1,
                i -> {
                  session.transcriptionLanguage = i == 0 ? "pt-PT" : "pt-BR";
                  session
                      .getPreferences(0)
                      .edit()
                      .putString("transcriptionLanguage", session.transcriptionLanguage)
                      .apply();
                  session.showTab(session.tab);
                }));
    session.button(
        session.project.showSubtitles ? "Ocultar legendas" : "Mostrar legendas",
        () -> {
          session.project.showSubtitles = !session.project.showSubtitles;
          session.changed();
          session.showTab(session.tab);
        });
    session.button(
        "Posição das legendas",
        () ->
            session.choice(
                "Posição",
                new String[] {"Em baixo", "Ao centro", "Em cima"},
                session.project.subtitlePosition,
                i -> {
                  session.project.subtitlePosition = i;
                  session.changed();
                }));
    session.button(
        "Tamanho das legendas",
        () ->
            session.choice(
                "Tamanho",
                new String[] {"Pequeno", "Médio", "Grande"},
                session.project.subtitleSize,
                i -> {
                  session.project.subtitleSize = i;
                  session.changed();
                }));
    session.button(
        "Atraso / avanço das legendas",
        () -> {
          LinearLayout f = session.fields();
          EditText offset =
              session.input(
                  f,
                  "Segundos (-600 a 600)",
                  String.valueOf(session.project.subtitleOffsetMs / 1000.0));
          new AlertDialog.Builder(session)
              .setView(f)
              .setPositiveButton(
                  "Aplicar",
                  (d, w) -> {
                    try {
                      long ms = session.seconds(offset);
                      if (Math.abs(ms) > 600000) throw new Exception();
                      session.project.subtitleOffsetMs = ms;
                      session.changed();
                    } catch (Exception e) {
                      session.message("Usa um valor entre -600 e 600 segundos.");
                    }
                  })
              .setNegativeButton("Cancelar", null)
              .show();
        });
    session.label(session.panel, "LEGENDAS · texto e tempos editáveis", 15);
    session.button(
        "Remover todas as legendas",
        () ->
            new AlertDialog.Builder(session)
                .setMessage("Remover as legendas apenas deste projeto?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                    "Remover",
                    (d, w) -> {
                      session.project.captions.clear();
                      session.changed();
                      session.showTab(session.tab);
                    })
                .show());
    session.button("＋ Adicionar legenda manual", () -> session.editCaption(null));
    session.button("Importar SRT", () -> session.pick(14, "*/*", false));
    session.button(
        "Exportar SRT",
        () ->
            session.createText(
                Subtitles.write(session.project.captions), "application/x-subrip", "sonora.srt"));
    session.button("Introduzir letra e marcar tempos", session::lyrics);
    if (session.lyricLines != null && session.lyricIndex < session.lyricLines.length)
      session.button("Marcar agora: " + session.lyricLines[session.lyricIndex], session::markLyric);
    session.button("Transcrever voz localmente · pt-PT", session::transcribe);
    session.button(
        "Estilo: " + session.project.subtitleStyle,
        () ->
            session.choice(
                "Tipografia",
                new String[] {"Sans", "Serif", "Monoespaçada"},
                0,
                i -> {
                  session.project.subtitleStyle = new String[] {"sans", "serif", "mono"}[i];
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.button(
        "Animação: " + session.project.subtitleAnimation,
        () ->
            session.choice(
                "Animação",
                new String[] {"Frase", "Fade por frase", "Palavras (ritmo estimado)"},
                0,
                i -> {
                  session.project.subtitleAnimation = new String[] {"phrase", "fade", "word"}[i];
                  session.changed();
                  session.showTab(session.tab);
                }));
    for (Project.Caption c : session.project.captions)
      session.button(Subtitles.format(c.startMs) + " · " + c.text, () -> session.editCaption(c));
  }

  void exportTab() {
    session.label(session.panel, "EXPORTAR · MP4 H.264 + AAC", 15);
    if (BuildStage.PHASE < 5) {
      session.button(
          "Orientação: " + (session.project.vertical ? "Vertical" : "Horizontal"),
          () -> {
            session.project.vertical = !session.project.vertical;
            session.changed();
            session.showTab(session.tab);
          });
      session.button("Exportar 720p / 24 FPS", session::exportVideo);
      if (session.result != null)
        session.button("Guardar MP4", () -> session.createBinary(20, "video/mp4", "sonora.mp4"));
      return;
    }
    session.button(
        "Destino: " + (session.project.vertical ? "Reels / TikTok / Shorts" : "YouTube horizontal"),
        () ->
            session.choice(
                "Formato",
                new String[] {"Reels / TikTok / Shorts · 9:16", "YouTube · 16:9", "Quadrado · 1:1"},
                session.project.vertical ? 0 : 1,
                i -> {
                  session.project.vertical = i == 0;
                  session.project.square = i == 2;
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.button(
        "Resolução: " + session.project.resolution + "p",
        () ->
            session.choice(
                "Verificar capacidade antes de exportar",
                new String[] {"720p", "1080p", "1440p (se suportado)", "2160p / 4K (se suportado)"},
                Arrays.asList(720, 1080, 1440, 2160).indexOf(session.project.resolution),
                i -> {
                  session.project.resolution = new int[] {720, 1080, 1440, 2160}[i];
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.button(
        "FPS: " + session.project.fps,
        () ->
            session.choice(
                "Fotogramas por segundo",
                new String[] {"24", "25", "30", "60 (se suportado)"},
                Arrays.asList(24, 25, 30, 60).indexOf(session.project.fps),
                i -> {
                  session.project.fps = new int[] {24, 25, 30, 60}[i];
                  session.changed();
                  session.showTab(session.tab);
                }));
    session.slider(
        "Bitrate (Mbps)",
        session.project.bitrate / 1000000f,
        1,
        80,
        n -> session.project.bitrate = (int) (n * 1000000));
    session.button(
        "Verificar compatibilidade",
        () -> {
          try {
            session.message(
                "Codec disponível: "
                    + ExportOptions.supportedCodec(session.project)
                    + ". Isto não garante velocidade de exportação.");
          } catch (Exception e) {
            session.message(e.getMessage());
          }
        });
    session.button("⇧ Exportar vídeo", session::exportVideo);
    Button cancel = session.button("Cancelar exportação", () -> {});
    cancel.setOnClickListener(
        v -> {
          if (EditorExportService.engine != null) EditorExportService.engine.cancelled = true;
        });
    if (session.result != null && session.result.isFile()) {
      session.button("Reproduzir MP4", () -> session.shareResult(false));
      session.button("Partilhar MP4", () -> session.shareResult(true));
    }
    if (session.result != null && session.result.isFile())
      session.button(
          "Guardar MP4 no telemóvel", () -> session.createBinary(20, "video/mp4", "sonora.mp4"));
    session.label(
        session.panel,
        "O dispositivo é verificado antes da exportação. 4K/60 FPS podem não ser suportados ou ser"
            + " muito lentos. Mantém a app aberta durante o processamento.",
        12);
  }
}
