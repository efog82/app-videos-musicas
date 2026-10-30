package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;

/**
 * Foreground export survives Activity closure, preserving the existing background-export workflow.
 */
public class EditorExportService extends Service {
  static volatile boolean running;
  static volatile String phase = "", error = "", output = "";
  static volatile int percent;
  static EditorExporter engine;
  static final String CHANNEL = "sonora_editor_export";
  long last;

  Notification notification() {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            1,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent cancel =
        PendingIntent.getService(
            this,
            2,
            new Intent(this, EditorExportService.class).setAction("cancel"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, CHANNEL)
        .setContentTitle("Sonora Lab · Exportação local")
        .setContentText(phase)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentIntent(open)
        .setOngoing(true)
        .setProgress(100, percent, false)
        .addAction(new Notification.Action.Builder(null, "Cancelar", cancel).build())
        .build();
  }

  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent != null && "cancel".equals(intent.getAction())) {
      if (engine != null) engine.cancelled = true;
      return START_NOT_STICKY;
    }
    if (running || intent == null) return START_NOT_STICKY;
    phase = "A iniciar exportação…";
    percent = 0;
    error = "";
    output = "";
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            CHANNEL, "Exportação do editor", NotificationManager.IMPORTANCE_LOW));
    if (Build.VERSION.SDK_INT >= 35)
      startForeground(31, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
    else if (Build.VERSION.SDK_INT >= 29)
      startForeground(31, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    else startForeground(31, notification());
    running = true;
    engine = new EditorExporter(this);
    String path = intent.getStringExtra("projectPath");
    new Thread(
            () -> {
              PowerManager.WakeLock wake =
                  getSystemService(PowerManager.class)
                      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sonora:editorExport");
              wake.acquire(2 * 60 * 60 * 1000L);
              File result =
                  new File(getFilesDir(), "sonora-" + System.currentTimeMillis() + ".mp4");
              try {
                File document = new File(path);
                if (!document
                        .getParentFile()
                        .getCanonicalPath()
                        .equals(getFilesDir().getCanonicalPath())
                    || !document.getName().matches("export-project-[a-f0-9-]+\\.json"))
                  throw new IOException("Documento de exportação inválido.");
                Project project;
                try (InputStream in = new FileInputStream(document)) {
                  project = Project.read(ProjectStore.read(in));
                } finally {
                  document.delete();
                }
                engine.export(
                    project,
                    result,
                    message -> {
                      phase = message;
                      java.util.regex.Matcher m =
                          java.util.regex.Pattern.compile("(\\d+)%").matcher(message);
                      if (m.find()) percent = Integer.parseInt(m.group(1));
                      if (SystemClock.elapsedRealtime() - last > 1000) {
                        last = SystemClock.elapsedRealtime();
                        manager.notify(31, notification());
                      }
                    });
                output = result.getAbsolutePath();
                getSharedPreferences("editor-export", 0).edit().putString("output", output).apply();
                phase = "Vídeo pronto";
                percent = 100;
              } catch (Exception e) {
                result.delete();
                error =
                    e.getMessage() == null
                        ? "Falha na exportação. O projeto foi preservado."
                        : e.getMessage();
                android.util.Log.e("SonoraEditor", error, e);
              } finally {
                if (wake.isHeld()) wake.release();
                running = false;
                engine = null;
                stopForeground(true);
                stopSelf();
              }
            },
            "Sonora-editor-export")
        .start();
    return START_NOT_STICKY;
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    if (engine != null) engine.cancelled = true;
    stopForeground(true);
    stopSelf();
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}
