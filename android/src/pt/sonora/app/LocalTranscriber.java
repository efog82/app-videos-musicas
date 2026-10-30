package pt.sonora.app;

import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.util.*;

/** Only binds Android's on-device recognizer; unknown capability metadata never means missing. */
final class LocalTranscriber implements AutoCloseable {
  interface Result {
    void success(String text, ArrayList<Project.Caption> timed);

    void failure(String error);
  }

  final Context context;
  SpeechRecognizer recognizer;
  ParcelFileDescriptor audio, writer;
  Result callback;
  final StringBuilder segments = new StringBuilder();
  final ArrayList<Project.Caption> timed = new ArrayList<>();
  final Handler handler = new Handler(Looper.getMainLooper());
  long durationMs;
  boolean finished, attemptStarted, retried;
  int generation;
  String language = "pt-PT";
  File source;

  LocalTranscriber(Context c) {
    context = c;
  }

  static boolean available(Context context) {
    return Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
  }

  static String diagnostics(Context c) {
    StringBuilder s =
        new StringBuilder(
            "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
    s.append("Reconhecimento de voz instalado: ")
        .append(SpeechRecognizer.isRecognitionAvailable(c) ? "sim" : "não")
        .append('\n');
    s.append("Reconhecimento local: ")
        .append(
            Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c)
                ? "disponível"
                : "não anunciado pelo Android")
        .append('\n');
    s.append("Entrada de áudio de ficheiro: ")
        .append(Build.VERSION.SDK_INT >= 33 ? "API disponível" : "exige Android 13+")
        .append("\n\n");
    List<ResolveInfo> services =
        c.getPackageManager()
            .queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
    s.append("Serviços de reconhecimento visíveis: ").append(services.size()).append('\n');
    for (ResolveInfo info : services)
      s.append("• ").append(info.loadLabel(c.getPackageManager())).append('\n');
    s.append(
        "\n"
            + "As definições de saída de texto (TTS) servem para ler texto em voz alta. A"
            + " transcrição precisa de um serviço de reconhecimento e de um modelo de português."
            + " Ter uma voz TTS instalada não confirma esses dois requisitos.\n\n"
            + "Não é usado reconhecimento online.");
    return s.toString();
  }

  void start(File file, long duration, Result result) {
    start(file, duration, "pt-PT", result);
  }

  void start(File file, long duration, String locale, Result result) {
    callback = result;
    durationMs = duration;
    source = file;
    language = locale;
    if (!available(context)) {
      fail(diagnostics(context));
      return;
    }
    begin(true);
  }

  void begin(boolean segmented) {
    attemptStarted = false;
    final int attempt = ++generation;
    try {
      recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
      recognizer.setRecognitionListener(
          new RecognitionListener() {
            boolean active() {
              return !finished && attempt == generation;
            }

            public void onReadyForSpeech(Bundle b) {}

            public void onBeginningOfSpeech() {}

            public void onRmsChanged(float n) {}

            public void onBufferReceived(byte[] b) {}

            public void onEndOfSpeech() {}

            public void onPartialResults(Bundle b) {}

            public void onEvent(int t, Bundle b) {}

            public void onError(int code) {
              if (!active()) return;
              if (segmented
                  && !retried
                  && segments.length() == 0
                  && (code == 3 || code == 5 || code == 14)) {
                retried = true;
                releaseAttempt();
                handler.postDelayed(
                    () -> {
                      if (!finished) begin(false);
                    },
                    350);
                return;
              }
              fail(
                  SpeechSupport.error(code)
                      + "\nIdioma pedido: "
                      + language
                      + " (erro "
                      + code
                      + ").\n"
                      + "Usa «Diagnóstico da transcrição» para verificar o serviço e o modelo.");
            }

            public void onResults(Bundle b) {
              if (active()) {
                append(b);
                complete();
              }
            }

            public void onSegmentResults(Bundle b) {
              if (active()) append(b);
            }

            public void onEndOfSegmentedSession() {
              if (active()) complete();
            }
          });
      ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
      audio = pipe[0];
      writer = pipe[1];
      Intent intent =
          new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
              .putExtra(
                  RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
              .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
              .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio)
              .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
              .putExtra(
                  RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                  android.media.AudioFormat.ENCODING_PCM_16BIT)
              .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000);
      if (segmented)
        intent.putExtra(
            RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
      if (Build.VERSION.SDK_INT >= 34)
        intent.putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true);
      Runnable start =
          () -> {
            if (finished || attempt != generation || attemptStarted) return;
            attemptStarted = true;
            try {
              recognizer.startListening(intent);
              ParcelFileDescriptor destination = writer;
              new Thread(
                      () -> {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                            OutputStream out =
                                new ParcelFileDescriptor.AutoCloseOutputStream(destination)) {
                          byte[] buffer = new byte[3200];
                          int n;
                          while (!finished && attempt == generation && (n = in.read(buffer)) != -1)
                            out.write(buffer, 0, n);
                        } catch (IOException e) {
                          android.util.Log.i("SonoraSpeech", "Recognition input closed");
                        }
                      },
                      "Sonora-speech-input")
                  .start();
              handler.postDelayed(
                  () -> {
                    if (!finished && attempt == generation)
                      fail(
                          "O motor local excedeu o tempo de espera. O projeto foi preservado."
                              + " Verifica o serviço no diagnóstico da transcrição.");
                  },
                  Math.max(60000, durationMs * 2));
            } catch (Exception e) {
              fail("Não foi possível iniciar o serviço local: " + e.getMessage());
            }
          };
      // Some Android 13 services implement recognition but not capability queries.
      handler.postDelayed(start, 8000);
      recognizer.checkRecognitionSupport(
          intent,
          context.getMainExecutor(),
          new RecognitionSupportCallback() {
            public void onSupportResult(RecognitionSupport support) {
              if (finished || attempt != generation || attemptStarted) return;
              List<String> installed = support.getInstalledOnDeviceLanguages();
              if (!SpeechSupport.canAttempt(language, installed)) {
                fail(
                    "O serviço anunciou estes idiomas locais: "
                        + installed
                        + ". Não confirmou "
                        + language
                        + ". No diagnóstico podes pedir o modelo ou escolher explicitamente outra"
                        + " variante de português.");
                return;
              }
              start.run();
            }

            public void onError(int code) {
              if (finished || attempt != generation || attemptStarted) return;
              if (code == 12 || code == 13) {
                fail(
                    SpeechSupport.error(code)
                        + " Idioma pedido: "
                        + language
                        + ". Usa a opção de obter o modelo no diagnóstico.");
              } else start.run();
            }
          });
    } catch (Exception e) {
      fail("Não foi possível preparar a transcrição local: " + e.getMessage());
    }
  }

  void append(Bundle data) {
    if (Build.VERSION.SDK_INT >= 34) {
      ArrayList<RecognitionPart> parts =
          data.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart.class);
      if (parts != null)
        for (RecognitionPart part : parts) {
          long start = part.getTimestampMillis();
          String value =
              part.getFormattedText() == null ? part.getRawText() : part.getFormattedText();
          if (start >= 0 && start < durationMs && value != null && !value.trim().isEmpty()) {
            if (!timed.isEmpty() && start > timed.get(timed.size() - 1).startMs)
              timed.get(timed.size() - 1).endMs = start;
            timed.add(new Project.Caption(start, Math.min(durationMs, start + 500), value));
          }
        }
    }
    ArrayList<String> text = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (text != null && !text.isEmpty()) {
      if (segments.length() > 0) segments.append('\n');
      segments.append(text.get(0));
    }
  }

  void complete() {
    if (finished) return;
    if (segments.length() == 0) {
      fail(
          "Não foi reconhecida fala. Revê se a faixa selecionada contém voz; canto e instrumentos"
              + " podem não ser reconhecidos.");
      return;
    }
    String text = segments.toString();
    finished = true;
    close();
    callback.success(text, timed);
  }

  void fail(String message) {
    if (finished) return;
    finished = true;
    close();
    if (callback != null) callback.failure(message);
  }

  void releaseAttempt() {
    generation++;
    if (recognizer != null) {
      recognizer.cancel();
      recognizer.destroy();
      recognizer = null;
    }
    if (audio != null)
      try {
        audio.close();
      } catch (IOException ignored) {
      }
    if (writer != null)
      try {
        writer.close();
      } catch (IOException ignored) {
      }
    audio = null;
    writer = null;
  }

  public void close() {
    finished = true;
    handler.removeCallbacksAndMessages(null);
    releaseAttempt();
  }
}
