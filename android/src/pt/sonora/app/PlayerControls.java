package pt.sonora.app;

import android.view.*;
import android.widget.*;

final class PlayerControls extends LinearLayout {
  PlayerControls(EditorController c) {
    super(c);
    setGravity(Gravity.CENTER_VERTICAL);
    Button play = new Button(c);
    play.setText("▶");
    play.setAllCaps(false);
    play.setContentDescription("Reproduzir ou pausar");
    play.setTextSize(18);
    play.setTextColor(UiTheme.TEXT);
    play.setBackground(UiTheme.shape(c, UiTheme.SURFACE, 8));
    play.setOnClickListener(v -> c.togglePlayback());
    addView(play, new LayoutParams(c.dp(48), c.dp(44)));
    c.play = play;
    c.time = UiTheme.text(c, "00:00 / 00:00", 11, UiTheme.MUTED);
    addView(c.time, new LayoutParams(c.dp(108), c.dp(44)));
    c.seek = new SeekBar(c);
    c.seek.setMax((int) c.project.durationMs());
    c.seek.setContentDescription("Procurar na reprodução");
    addView(c.seek, new LayoutParams(0, c.dp(44), 1));
    c.seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}

          public void onProgressChanged(SeekBar s, int value, boolean user) {
            if (user) {
              c.position = value;
              c.preview.seek(value);
              c.updateClock();
            }
          }
        });
    addView(
        UiTheme.icon(c, "fullscreen", "Pré-visualização em ecrã inteiro", c::openFullscreen),
        new LayoutParams(c.dp(48), c.dp(44)));
  }
}
