package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns fullscreen presentation while retaining the existing player instance. */
final class PlayerPanel {
  final EditorController session;

  PlayerPanel(EditorController controller) {
    session = controller;
  }

  void fullscreen() {
    if (session.fullscreenDialog != null) return;
    session.previewHome = (ViewGroup) session.preview.getParent();
    session.previewHomeIndex = session.previewHome.indexOfChild(session.preview);
    session.previewHomeParams = session.preview.getLayoutParams();
    session.previewHomeVisibility = session.preview.getVisibility();
    session.previewHome.removeView(session.preview);
    session.preview.setVisibility(View.VISIBLE);
    session.fullscreenDialog =
        new Dialog(session, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    session.fullscreenHost = new LinearLayout(session);
    session.fullscreenHost.setOrientation(LinearLayout.VERTICAL);
    session.fullscreenHost.setBackgroundColor(Color.BLACK);
    session.fullscreenHost.addView(session.preview, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout controls = session.row(session.fullscreenHost);
    session.fullscreenPlay =
        session.icon(
            controls,
            session.preview.playing ? "Ⅱ" : "▶",
            "Reproduzir ou pausar em ecrã inteiro",
            v -> session.togglePlayback());
    session.fullscreenTime = session.label(controls, session.time.getText().toString(), 12);
    Button close = new Button(session);
    close.setText("Voltar ao editor");
    close.setAllCaps(false);
    close.setContentDescription("Sair do ecrã inteiro");
    close.setOnClickListener(
        v -> {
          if (session.fullscreenDialog != null) session.fullscreenDialog.dismiss();
        });
    controls.addView(close, new LinearLayout.LayoutParams(0, session.dp(48), 1));
    session.fullscreenSeek = new SeekBar(session);
    session.fullscreenSeek.setMax((int) session.project.durationMs());
    session.fullscreenSeek.setProgress((int) session.position);
    session.fullscreenSeek.setContentDescription("Posição da pré-visualização em ecrã inteiro");
    session.fullscreenHost.addView(
        session.fullscreenSeek, new LinearLayout.LayoutParams(-1, session.dp(48)));
    session.fullscreenSeek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}

          public void onProgressChanged(SeekBar b, int ms, boolean user) {
            if (user) {
              session.position = ms;
              session.preview.seek(ms);
              session.updateClock();
            }
          }
        });
    session.fullscreenDialog.setContentView(session.fullscreenHost);
    session.fullscreenDialog.setOnDismissListener(
        d -> {
          session.fullscreenHost.removeView(session.preview);
          session.previewHome.addView(
              session.preview, session.previewHomeIndex, session.previewHomeParams);
          session.preview.setVisibility(session.previewHomeVisibility);
          session.fullscreenDialog = null;
          session.fullscreenHost = null;
          session.fullscreenTime = null;
          session.fullscreenPlay = null;
          session.fullscreenSeek = null;
          session.setRequestedOrientation(
              android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
          session.preview.render();
        });
    session.fullscreenDialog.show();
    session.fullscreenDialog.getWindow().setLayout(-1, -1);
    session
        .fullscreenDialog
        .getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    session.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    session.preview.render();
  }
}
