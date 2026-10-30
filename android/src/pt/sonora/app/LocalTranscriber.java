package pt.sonora.app;

import android.content.*;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.util.*;

/** Uses only an Android on-device recognition service. Never falls back to the network. */
final class LocalTranscriber implements AutoCloseable {
  interface Result {
    void success(String text, ArrayList<Project.Caption> timed);

    void failure(String error);
  }

  final Context context;
  SpeechRecognizer recognizer;
  ParcelFileDescriptor audio;
  Result callback;
  final StringBuilder segments = new StringBuilder();
  final ArrayList<Project.Caption> timed = new ArrayList<>();
  long durationMs;
  boolean finished;
  final Handler handler = new Handler(Looper.getMainLooper());

  LocalTranscriber(Context c) {
    context = c;
  }

  static boolean available(Context c) {
    return Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c);
  }

  void start(File file, long duration, Result result) {
    callback = result;
    durationMs = duration;
    if (!available(context)) {
      fail("Sem motor local para transcrever ficheiros neste Android.");
      return;
    }
    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
    recognizer.setRecognitionListener(
        new RecognitionListener() {
          public void onReadyForSpeech(Bundle b) {}

          public void onBeginningOfSpeech() {}

          public void onRmsChanged(float n) {}

          public void onBufferReceived(byte[] b) {}

          public void onEndOfSpeech() {}

          public void onPartialResults(Bundle b) {}

          public void onEvent(int type, Bundle b) {}

          public void onError(int error) {
            fail(
                "O reconhecimento local não concluiu (código "
                    + error
                    + "). Confirma o pacote de idioma pt-PT nas definições do Android. Podes usar"
                    + " edição manual ou importar SRT.");
          }

          public void onResults(Bundle b) {
            append(b);
            complete();
          }

          public void onSegmentResults(Bundle b) {
            append(b);
          }

          public void onEndOfSegmentedSession() {
            complete();
          }
        });
    Intent intent =
        new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    if (Build.VERSION.SDK_INT >= 34)
      intent.putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true);
    try {
      audio = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio);
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
      intent.putExtra(
          RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
          android.media.AudioFormat.ENCODING_PCM_16BIT);
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000);
      intent.putExtra(
          RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
      recognizer.checkRecognitionSupport(
          intent,
          context.getMainExecutor(),
          new RecognitionSupportCallback() {
            public void onSupportResult(RecognitionSupport support) {
              boolean installed = false;
              for (String language : support.getInstalledOnDeviceLanguages())
                if (language.replace('_', '-').equalsIgnoreCase("pt-PT")) installed = true;
              if (!installed) {
                fail(
                    "O modelo local de português de Portugal (pt-PT) não está instalado. Instala-o"
                        + " nas definições de voz do Android. Nenhum serviço online foi usado.");
                return;
              }
              if (!finished) {
                recognizer.startListening(intent);
                handler.postDelayed(
                    () ->
                        fail("O motor local excedeu o tempo de espera. O projeto foi preservado."),
                    Math.max(60000, duration * 2));
              }
            }

            public void onError(int code) {
              fail(
                  "Este motor não confirmou suporte a ficheiros de áudio pt-PT (código "
                      + code
                      + "). Usa SRT ou edição manual.");
            }
          });
    } catch (Exception e) {
      fail("Não foi possível iniciar a transcrição local: " + e.getMessage());
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
          if (start >= 0 && start < durationMs && !value.trim().isEmpty()) {
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
      fail("O motor não reconheceu voz. Música e canto podem não ser reconhecidos com precisão.");
      return;
    }
    finished = true;
    String text = segments.toString();
    close();
    callback.success(text, timed);
  }

  void fail(String message) {
    if (finished) return;
    finished = true;
    close();
    if (callback != null) callback.failure(message);
  }

  public void close() {
    finished = true;
    handler.removeCallbacksAndMessages(null);
    if (recognizer != null) {
      recognizer.destroy();
      recognizer = null;
    }
    if (audio != null)
      try {
        audio.close();
      } catch (IOException ignored) {
      }
    audio = null;
  }
}
