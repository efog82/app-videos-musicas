package pt.sonora.offline;

import android.graphics.Bitmap;
import android.opengl.*;
import android.view.Surface;
import java.nio.*;

/** Bitmap upload at 640px, hardware scaling and H.264 surface encoding. */
final class GlEncoder implements AutoCloseable {
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface=EGL14.EGL_NO_SURFACE;
    private int program,texture,position,uv,sampler;
    private int videoProgram;private final int outputWidth,outputHeight;
    private final float[] rotation=new float[16],combined=new float[16];
    private final FloatBuffer videoVertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    GlEncoder(Surface input,int width,int height){
        outputWidth=width;outputHeight=height;
        vertices.put(new float[]{-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0}).position(0);
        try{
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version=new int[2];if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("Não foi possível iniciar OpenGL.");
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            int[] attributes={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE};
            if(!EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("OpenGL não suporta gravação neste dispositivo.");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreateWindowSurface(display,configs[0],input,new int[]{EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("Falha ao preparar o vídeo.");
            int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 a;attribute vec2 u;varying vec2 v;void main(){gl_Position=vec4(a,0.,1.);v=u;}");
            int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float;varying vec2 v;uniform sampler2D s;void main(){gl_FragColor=texture2D(s,v);}");
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
            int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);GLES20.glUseProgram(program);
            position=GLES20.glGetAttribLocation(program,"a");uv=GLES20.glGetAttribLocation(program,"u");sampler=GLES20.glGetUniformLocation(program,"s");
            int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glViewport(0,0,width,height);
        }catch(RuntimeException ex){close();throw ex;}
    }
    private int shader(int type,String code){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    void draw(Bitmap bitmap,long nanos){draw(bitmap,nanos,null,false);}
    void draw(Bitmap bitmap,long nanos,VideoFrames video,boolean fit){
        GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glDisable(GLES20.GL_BLEND);
        if(video!=null)drawVideo(video,fit);
        GLES20.glUseProgram(program);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);GLES20.glUniform1i(sampler,0);
        vertices.position(0);GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(position);
        vertices.position(2);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(uv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("Falha gráfica durante a exportação.");
        EGLExt.eglPresentationTimeANDROID(display,surface,nanos);
        if(!EGL14.eglSwapBuffers(display,surface))throw new IllegalStateException("Não foi possível enviar uma imagem ao codificador.");
    }
    private void drawVideo(VideoFrames video,boolean fit){
        if(videoProgram==0){
            int v=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 a;attribute vec2 u;uniform mat4 m;varying vec2 v;void main(){gl_Position=vec4(a,0.,1.);v=(m*vec4(u,0.,1.)).xy;}");
            int f=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 v;uniform samplerExternalOES s;void main(){gl_FragColor=texture2D(s,v);}");
            videoProgram=GLES20.glCreateProgram();GLES20.glAttachShader(videoProgram,v);GLES20.glAttachShader(videoProgram,f);GLES20.glLinkProgram(videoProgram);
            int[] ok=new int[1];GLES20.glGetProgramiv(videoProgram,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Falha na composição do vídeo.");GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        }
        MediaSequence.Item item=video.current;float sourceWidth=item.rotation%180==0?item.width:item.height,sourceHeight=item.rotation%180==0?item.height:item.width;
        float scale=fit?Math.min(outputWidth/sourceWidth,outputHeight/sourceHeight):Math.max(outputWidth/sourceWidth,outputHeight/sourceHeight);
        float sx=sourceWidth*scale/outputWidth,sy=sourceHeight*scale/outputHeight;
        videoVertices.clear();videoVertices.put(new float[]{-sx,-sy,0,0,sx,-sy,1,0,-sx,sy,0,1,sx,sy,1,1});videoVertices.position(0);
        Matrix.setIdentityM(rotation,0);Matrix.translateM(rotation,0,.5f,.5f,0);Matrix.rotateM(rotation,0,item.rotation,0,0,1);Matrix.translateM(rotation,0,-.5f,-.5f,0);Matrix.multiplyMM(combined,0,video.transform,0,rotation,0);
        GLES20.glUseProgram(videoProgram);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,video.texture);
        int pos=GLES20.glGetAttribLocation(videoProgram,"a"),tex=GLES20.glGetAttribLocation(videoProgram,"u");
        GLES20.glUniform1i(GLES20.glGetUniformLocation(videoProgram,"s"),0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(videoProgram,"m"),1,false,combined,0);
        videoVertices.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,videoVertices);GLES20.glEnableVertexAttribArray(pos);
        videoVertices.position(2);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,16,videoVertices);GLES20.glEnableVertexAttribArray(tex);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    }
    public void close(){
        if(display!=EGL14.EGL_NO_DISPLAY){
            if(videoProgram!=0)GLES20.glDeleteProgram(videoProgram);if(program!=0)GLES20.glDeleteProgram(program);if(texture!=0)GLES20.glDeleteTextures(1,new int[]{texture},0);
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglReleaseThread();EGL14.eglTerminate(display);display=EGL14.EGL_NO_DISPLAY;
        }
    }
}
