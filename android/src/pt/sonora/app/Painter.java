package pt.sonora.app;

import android.graphics.*;
import android.opengl.*;
import android.view.Surface;
import java.nio.*;

class Painter {
  EGLDisplay display;
  EGLContext context;
  EGLSurface surface;
  Bitmap bitmap, background;
  Canvas canvas;
  Paint paint = new Paint(3);
  int w, h, program, texture;
  String title;
  FloatBuffer vertices;

  Painter(Surface output, int width, int height, Bitmap image, String label) {
    w = width;
    h = height;
    background = image;
    title = label.trim().isEmpty() ? "A minha música" : label;
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    int[] ver = new int[2];
    if (!EGL14.eglInitialize(display, ver, 0, ver, 1))
      throw new RuntimeException("Não foi possível iniciar o motor gráfico.");
    int[] config = {
      EGL14.EGL_RED_SIZE,
      8,
      EGL14.EGL_GREEN_SIZE,
      8,
      EGL14.EGL_BLUE_SIZE,
      8,
      EGL14.EGL_ALPHA_SIZE,
      8,
      EGL14.EGL_RENDERABLE_TYPE,
      EGL14.EGL_OPENGL_ES2_BIT,
      0x3142,
      1,
      EGL14.EGL_NONE
    };
    EGLConfig[] configs = new EGLConfig[1];
    int[] count = new int[1];
    if (!EGL14.eglChooseConfig(display, config, 0, configs, 0, 1, count, 0) || count[0] == 0)
      throw new RuntimeException("Este Android não suporta o formato gráfico.");
    context =
        EGL14.eglCreateContext(
            display,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE},
            0);
    surface =
        EGL14.eglCreateWindowSurface(display, configs[0], output, new int[] {EGL14.EGL_NONE}, 0);
    if (!EGL14.eglMakeCurrent(display, surface, surface, context))
      throw new RuntimeException("Não foi possível preparar o vídeo.");
    program = GLES20.glCreateProgram();
    GLES20.glAttachShader(
        program,
        shader(
            GLES20.GL_VERTEX_SHADER,
            "attribute vec2 pos; attribute vec2 uv; varying vec2 tex; void"
                + " main(){gl_Position=vec4(pos,0.,1.);tex=uv;}"));
    GLES20.glAttachShader(
        program,
        shader(
            GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; varying vec2 tex; uniform sampler2D img; void"
                + " main(){gl_FragColor=texture2D(img,tex);}"));
    GLES20.glLinkProgram(program);
    int[] linked = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
    if (linked[0] == 0) throw new RuntimeException("Não foi possível preparar as cores do vídeo.");
    vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    vertices.put(new float[] {-1, -1, 0, 1, 1, -1, 1, 1, -1, 1, 0, 0, 1, 1, 1, 0}).position(0);
    int[] textures = new int[1];
    GLES20.glGenTextures(1, textures, 0);
    texture = textures[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    canvas = new Canvas(bitmap);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
  }

  int shader(int type, String source) {
    int s = GLES20.glCreateShader(type);
    GLES20.glShaderSource(s, source);
    GLES20.glCompileShader(s);
    int[] ok = new int[1];
    GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
    if (ok[0] == 0)
      throw new RuntimeException("Este dispositivo não conseguiu preparar a animação.");
    return s;
  }

  void draw(float seconds, float energy, long timestamp) {
    canvas.drawColor(Color.rgb(13, 18, 36));
    if (background != null) {
      float scale = Math.max(w / (float) background.getWidth(), h / (float) background.getHeight());
      float bw = background.getWidth() * scale, bh = background.getHeight() * scale;
      paint.setAlpha(180);
      canvas.drawBitmap(
          background,
          null,
          new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2),
          paint);
      paint.setAlpha(255);
      canvas.drawColor(0x55000000);
    }
    paint.setColor(Color.WHITE);
    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(42);
    while (paint.measureText(title) > w - 90) paint.setTextSize(paint.getTextSize() - 1);
    canvas.drawText(title, w / 2f, h * .32f, paint);
    paint.setColor(Color.rgb(183, 164, 255));
    float gap = w / 60f, center = h * .64f;
    for (int i = 0; i < 51; i++) {
      float movement =
          (float)
              (.25
                  + .75
                      * Math.abs(
                          Math.sin(i * .67 + seconds * 4) * Math.cos(i * .19 - seconds * 2)));
      float size = 12 + 160 * energy * movement;
      float x = w / 2f + (i - 25) * gap;
      canvas.drawRoundRect(x - 3, center - size, x + 3, center + size, 3, 3, paint);
    }
    GLES20.glViewport(0, 0, w, h);
    GLES20.glUseProgram(program);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
    GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap);
    int pos = GLES20.glGetAttribLocation(program, "pos"),
        uv = GLES20.glGetAttribLocation(program, "uv");
    vertices.position(0);
    GLES20.glEnableVertexAttribArray(pos);
    GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, vertices);
    vertices.position(2);
    GLES20.glEnableVertexAttribArray(uv);
    GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices);
    GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "img"), 0);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    EGLExt.eglPresentationTimeANDROID(display, surface, timestamp);
    if (!EGL14.eglSwapBuffers(display, surface))
      throw new RuntimeException("A criação do vídeo foi interrompida.");
  }

  void drawFrame(Bitmap frame, long timestamp) {
    GLES20.glViewport(0, 0, w, h);
    GLES20.glUseProgram(program);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
    GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frame);
    int pos = GLES20.glGetAttribLocation(program, "pos"),
        uv = GLES20.glGetAttribLocation(program, "uv");
    vertices.position(0);
    GLES20.glEnableVertexAttribArray(pos);
    GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, vertices);
    vertices.position(2);
    GLES20.glEnableVertexAttribArray(uv);
    GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices);
    GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "img"), 0);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    EGLExt.eglPresentationTimeANDROID(display, surface, timestamp);
    if (!EGL14.eglSwapBuffers(display, surface))
      throw new RuntimeException("A criação do vídeo foi interrompida.");
  }

  void close() {
    if (bitmap != null) bitmap.recycle();
    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    EGL14.eglDestroySurface(display, surface);
    EGL14.eglDestroyContext(display, context);
    EGL14.eglTerminate(display);
  }
}
