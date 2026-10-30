package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared touch targets, typography, shapes and a small original vector icon set. */
final class UiTheme {
  static final int BG = 0xff0b0d14,
      SURFACE = 0xff141824,
      RAISED = 0xff202638,
      TEXT = 0xfff0f2fa,
      MUTED = 0xff99a3bc,
      BLUE = 0xff6da9ff,
      VIOLET = 0xffac8cff;

  static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable shape(Context c, int color, float radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(c, radius));
    return g;
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }

  static void inset(View v, int padding) {
    v.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          view.setPadding(
              dp(view.getContext(), padding) + insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              dp(view.getContext(), padding) + insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
  }

  static Button primary(Context c, String label, Runnable action) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(BG);
    b.setTypeface(Typeface.create("sans-serif-medium", 0));
    b.setBackgroundTintList(ColorStateList.valueOf(BLUE));
    b.setMinHeight(dp(c, 48));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  static View action(Context c, String icon, String label, Runnable callback) {
    LinearLayout box = column(c);
    box.setGravity(Gravity.CENTER);
    box.setPadding(dp(c, 5), dp(c, 4), dp(c, 5), dp(c, 4));
    box.setMinimumWidth(dp(c, 64));
    box.setMinimumHeight(dp(c, 56));
    box.setBackground(shape(c, SURFACE, 10));
    Icon art = new Icon(c, icon);
    box.addView(art, new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
    TextView name = text(c, label, 11, TEXT);
    name.setGravity(Gravity.CENTER);
    name.setMaxLines(1);
    box.addView(name, new LinearLayout.LayoutParams(-1, dp(c, 23)));
    box.setContentDescription(label);
    box.setFocusable(true);
    box.setClickable(true);
    box.setOnClickListener(v -> callback.run());
    return box;
  }

  static View icon(Context c, String icon, String label, Runnable callback) {
    View v = new Icon(c, icon);
    v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    v.setContentDescription(label);
    v.setFocusable(true);
    v.setClickable(true);
    v.setOnClickListener(x -> callback.run());
    v.setMinimumHeight(dp(c, 48));
    v.setMinimumWidth(dp(c, 48));
    return v;
  }

  static void item(LinearLayout box, View child, int width, int height) {
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(
            width < 0 ? width : dp(box.getContext(), width),
            height < 0 ? height : dp(box.getContext(), height));
    p.setMargins(
        dp(box.getContext(), 3),
        dp(box.getContext(), 3),
        dp(box.getContext(), 3),
        dp(box.getContext(), 3));
    box.addView(child, p);
  }

  static void actions(LinearLayout parent, View... actions) {
    HorizontalScrollView scroll = new HorizontalScrollView(parent.getContext());
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = row(parent.getContext());
    for (View a : actions) item(row, a, 76, 64);
    scroll.addView(row);
    parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
  }

  static Dialog sheet(Context context, String title, View body) {
    Dialog dialog = new Dialog(context);
    LinearLayout root = column(context);
    root.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 12));
    root.setBackground(shape(context, SURFACE, 20));
    LinearLayout header = row(context);
    TextView name = text(context, title, 18, TEXT);
    name.setTypeface(Typeface.create("sans-serif-medium", 0));
    header.addView(name, new LinearLayout.LayoutParams(0, dp(context, 48), 1));
    header.addView(
        icon(context, "close", "Fechar painel", dialog::dismiss),
        new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
    root.addView(header);
    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(false);
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    dialog.setContentView(root);
    Window window = dialog.getWindow();
    window.setBackgroundDrawableResource(android.R.color.transparent);
    window.setGravity(Gravity.BOTTOM);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.show();
    window.setLayout(-1, (int) (context.getResources().getDisplayMetrics().heightPixels * .70));
    return dialog;
  }

  static void notice(Context c, String title, String message) {
    new AlertDialog.Builder(c)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Fechar", null)
        .show();
  }

  static final class Icon extends View {
    final String kind;
    final Paint p = new Paint(3);

    Icon(Context c, String k) {
      super(c);
      kind = k;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      float size = Math.min(getWidth(), getHeight()) * .52f;
      canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
      canvas.scale(size / 24, size / 24);
      p.setColor(VIOLET);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.8f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      switch (kind) {
        case "plus":
          canvas.drawLine(12, 3, 12, 21, p);
          canvas.drawLine(3, 12, 21, 12, p);
          break;
        case "play":
          {
            Path path = new Path();
            path.moveTo(7, 3);
            path.lineTo(21, 12);
            path.lineTo(7, 21);
            path.close();
            canvas.drawPath(path, p);
            break;
          }
        case "pause":
          canvas.drawLine(8, 4, 8, 20, p);
          canvas.drawLine(16, 4, 16, 20, p);
          break;
        case "back":
          canvas.drawLine(18, 12, 5, 12, p);
          canvas.drawLine(5, 12, 11, 6, p);
          canvas.drawLine(5, 12, 11, 18, p);
          break;
        case "close":
          canvas.drawLine(5, 5, 19, 19, p);
          canvas.drawLine(19, 5, 5, 19, p);
          break;
        case "export":
          canvas.drawLine(12, 16, 12, 2, p);
          canvas.drawLine(12, 2, 6, 8, p);
          canvas.drawLine(12, 2, 18, 8, p);
          canvas.drawLine(4, 14, 4, 21, p);
          canvas.drawLine(4, 21, 20, 21, p);
          canvas.drawLine(20, 21, 20, 14, p);
          break;
        case "fullscreen":
          canvas.drawLines(
              new float[] {
                2, 9, 2, 2, 2, 2, 9, 2, 15, 2, 22, 2, 22, 2, 22, 9, 22, 15, 22, 22, 22, 22, 15, 22,
                9, 22, 2, 22, 2, 22, 2, 15
              },
              p);
          break;
        case "audio":
        case "volume":
          for (int i = 0; i < 5; i++) {
            float h = new float[] {4, 8, 11, 6, 3}[i];
            canvas.drawLine(4 + i * 4, 12 - h, 4 + i * 4, 12 + h, p);
          }
          break;
        case "delete":
          canvas.drawRect(6, 7, 18, 22, p);
          canvas.drawLine(3, 5, 21, 5, p);
          canvas.drawLine(9, 2, 15, 2, p);
          canvas.drawLine(10, 10, 10, 18, p);
          canvas.drawLine(14, 10, 14, 18, p);
          break;
        case "copy":
          canvas.drawRoundRect(7, 7, 22, 22, 2, 2, p);
          canvas.drawLines(new float[] {3, 17, 3, 2, 3, 2, 17, 2}, p);
          break;
        case "split":
        case "trim":
          canvas.drawLine(12, 2, 12, 22, p);
          canvas.drawLine(4, 7, 8, 12, p);
          canvas.drawLine(8, 12, 4, 17, p);
          canvas.drawLine(20, 7, 16, 12, p);
          canvas.drawLine(16, 12, 20, 17, p);
          break;
        case "text":
          canvas.drawLine(3, 4, 21, 4, p);
          canvas.drawLine(12, 4, 12, 21, p);
          canvas.drawLine(8, 21, 16, 21, p);
          break;
        case "subtitle":
          canvas.drawRoundRect(2, 4, 22, 20, 3, 3, p);
          canvas.drawLine(6, 10, 11, 10, p);
          canvas.drawLine(14, 10, 18, 10, p);
          canvas.drawLine(6, 15, 18, 15, p);
          break;
        case "effect":
          {
            Path path = new Path();
            path.moveTo(12, 1);
            path.lineTo(15, 9);
            path.lineTo(23, 12);
            path.lineTo(15, 15);
            path.lineTo(12, 23);
            path.lineTo(9, 15);
            path.lineTo(1, 12);
            path.lineTo(9, 9);
            path.close();
            canvas.drawPath(path, p);
            break;
          }
        case "format":
          canvas.drawRoundRect(5, 2, 19, 22, 2, 2, p);
          canvas.drawLine(9, 18, 15, 18, p);
          break;
        case "media":
          canvas.drawRoundRect(2, 4, 22, 20, 2, 2, p);
          canvas.drawCircle(8, 9, 2, p);
          canvas.drawLines(
              new float[] {3, 18, 10, 12, 10, 12, 14, 16, 14, 16, 18, 11, 18, 11, 22, 15}, p);
          break;
        case "save":
          canvas.drawRoundRect(3, 2, 21, 22, 2, 2, p);
          canvas.drawRect(7, 2, 17, 9, p);
          canvas.drawRect(7, 15, 17, 22, p);
          break;
        case "settings":
          canvas.drawCircle(12, 12, 7, p);
          canvas.drawCircle(12, 12, 2, p);
          for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            canvas.drawLine(
                12 + 7 * (float) Math.cos(a),
                12 + 7 * (float) Math.sin(a),
                12 + 10 * (float) Math.cos(a),
                12 + 10 * (float) Math.sin(a),
                p);
          }
          break;
        case "minus":
          canvas.drawLine(4, 12, 20, 12, p);
          break;
        default:
          canvas.drawCircle(5, 12, 1, p);
          canvas.drawCircle(12, 12, 1, p);
          canvas.drawCircle(19, 12, 1, p);
      }
      canvas.restore();
    }
  }
}
