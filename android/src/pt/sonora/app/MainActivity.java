package pt.sonora.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

/** Android lifecycle boundary; editing and UI components live outside the Activity. */
public final class MainActivity extends Activity {
  EditorController editor;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    getWindow().setNavigationBarColor(UiTheme.BG);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    editor = new EditorController(this);
    editor.create(state);
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    editor.onActivityResult(code, result, data);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] names, int[] grants) {
    super.onRequestPermissionsResult(code, names, grants);
    editor.onRequestPermissionsResult(code, names, grants);
  }

  @Override
  protected void onPause() {
    if (editor != null) editor.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (editor != null) editor.onDestroy();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (editor == null || !editor.back()) super.onBackPressed();
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    if (editor != null) editor.configuration();
  }
}
