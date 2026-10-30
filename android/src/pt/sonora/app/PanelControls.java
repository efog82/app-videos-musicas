package pt.sonora.app;

import android.widget.*;
import java.util.function.Consumer;

final class PanelControls {
  static void button(EditorController c, LinearLayout body, String label, Runnable callback) {
    UiTheme.item(
        body,
        UiTheme.primary(
            c,
            label,
            () -> {
              if (!c.busy) callback.run();
              else c.message("Aguarda a operação atual.");
            }),
        -1,
        48);
  }

  static void note(EditorController c, LinearLayout body, String text) {
    UiTheme.item(body, UiTheme.text(c, text, 13, UiTheme.MUTED), -1, -2);
  }

  static void range(
      EditorController c,
      LinearLayout body,
      String label,
      float value,
      float min,
      float max,
      Consumer<Float> callback) {
    TextView caption =
        UiTheme.text(
            c, label + String.format(java.util.Locale.ROOT, " · %.2f", value), 14, UiTheme.TEXT);
    UiTheme.item(body, caption, -1, 32);
    SeekBar slider = new SeekBar(c);
    slider.setContentDescription(label);
    slider.setMax(100);
    slider.setProgress((int) ((value - min) / Math.max(.001f, max - min) * 100));
    UiTheme.item(body, slider, -1, 48);
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {}

          public void onProgressChanged(SeekBar s, int n, boolean user) {
            caption.setText(
                label
                    + String.format(java.util.Locale.ROOT, " · %.2f", min + (max - min) * n / 100));
          }

          public void onStopTrackingTouch(SeekBar s) {
            if (!c.busy) callback.accept(min + (max - min) * s.getProgress() / 100);
          }
        });
  }
}
