package pt.sonora.app;

import android.app.Dialog;
import android.widget.*;

final class MediaPickerSheet {
  static void show(EditorController c) {
    if (c.busy) {
      c.message("Aguarda a operação atual.");
      return;
    }
    LinearLayout body = UiTheme.column(c);
    final Dialog[] dialog = new Dialog[1];
    String[] names = {"Música", "Vídeos", "Imagens", "Legendas SRT"},
        icons = {"audio", "play", "media", "subtitle"};
    int[] codes = {10, 11, 12, 14};
    String[] types = {"audio/*", "video/*", "image/*", "*/*"};
    for (int i = 0; i < names.length; i++) {
      int index = i;
      UiTheme.item(
          body,
          UiTheme.primary(
              c,
              names[i],
              () -> {
                dialog[0].dismiss();
                c.pick(codes[index], types[index], true);
              }),
          -1,
          56);
    }
    UiTheme.item(
        body,
        UiTheme.text(
            c,
            "Podes selecionar vários ficheiros. O áudio original dos vídeos é mantido por defeito."
                + " Os originais nunca são apagados.",
            13,
            UiTheme.MUTED),
        -1,
        -2);
    dialog[0] = UiTheme.sheet(c, "Adicionar média", body);
  }
}
