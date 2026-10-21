package pt.sonora.offline;

import android.graphics.SurfaceTexture;
import android.media.*;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.nio.ByteBuffer;

/** Sequential hardware decoding directly to an external GL texture; source audio is never read. */
final class VideoFrames implements AutoCloseable {
    final MediaSequence sequence;
    final int texture;
    final float[] transform=new float[16];
    private final SurfaceTexture frames;
    private final Surface surface;
    private final HandlerThread callbacks=new HandlerThread("Sonora-video-frames");
    private final Object ready=new Object();
    private boolean available=false,inputEnd=false,outputEnd=false;
    private MediaExtractor extractor;private MediaCodec decoder;
    private int selected=-1;private long lastTarget=-1;
    MediaSequence.Item current;
    VideoFrames(String manifest)throws Exception{
        sequence=MediaSequence.load(manifest);int[] id=new int[1];GLES20.glGenTextures(1,id,0);texture=id[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        frames=new SurfaceTexture(texture);surface=new Surface(frames);callbacks.start();
        frames.setOnFrameAvailableListener(value->{synchronized(ready){available=true;ready.notifyAll();}},new Handler(callbacks.getLooper()));
    }
    void at(long musicUs,MediaIo.Progress progress)throws Exception{
        int index=sequence.videoIndex(musicUs/1000);long target=sequence.videoTimeMs(musicUs/1000)*1000;
        if(index!=selected){releaseDecoder();selected=index;current=sequence.items.get(index);extractor=new MediaExtractor();extractor.setDataSource(current.file);
            MediaFormat format=null;for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("video/")){extractor.selectTrack(i);format=f;break;}}
            if(format==null)throw new IOException("O vídeo não contém imagens.");
            format.setInteger(MediaFormat.KEY_ROTATION,0);decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));decoder.configure(format,surface,null,0);decoder.start();
            extractor.seekTo(target,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);inputEnd=outputEnd=false;lastTarget=-1;
        }else if(target<lastTarget){extractor.seekTo(target,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);decoder.flush();inputEnd=outputEnd=false;}
        if(target==lastTarget)return;lastTarget=target;
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long deadline=SystemClock.elapsedRealtime()+60000;
        while(!outputEnd){
            if(ExportService.cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Exportação cancelada.");
            if(SystemClock.elapsedRealtime()>deadline)throw new IOException("O descodificador do vídeo deixou de responder.");
            if(!inputEnd){int in=decoder.dequeueInputBuffer(10000);if(in>=0){ByteBuffer buffer=decoder.getInputBuffer(in);buffer.clear();int count=extractor.readSampleData(buffer,0);
                if(count<0){decoder.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
                else{decoder.queueInputBuffer(in,0,count,extractor.getSampleTime(),extractor.getSampleFlags());extractor.advance();}
            }}
            int out=decoder.dequeueOutputBuffer(info,10000);
            if(out>=0){boolean render=info.size>0&&info.presentationTimeUs>=target;outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                synchronized(ready){available=false;}decoder.releaseOutputBuffer(out,render);
                if(render){synchronized(ready){while(!available){long wait=deadline-SystemClock.elapsedRealtime();if(wait<=0)throw new IOException("O vídeo não devolveu uma imagem.");ready.wait(Math.min(wait,100));if(ExportService.cancelled)throw new InterruptedIOException("Exportação cancelada.");}}
                    frames.updateTexImage();frames.getTransformMatrix(transform);return;
                }
            }
        }
    }
    private void releaseDecoder(){if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();decoder=null;}if(extractor!=null){extractor.release();extractor=null;}}
    public void close(){releaseDecoder();surface.release();frames.release();callbacks.quitSafely();GLES20.glDeleteTextures(1,new int[]{texture},0);}
}
