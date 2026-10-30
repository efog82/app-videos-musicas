package pt.sonora.offline;

import android.graphics.*;
import android.media.*;
import android.view.Surface;
import java.io.*;
import java.nio.*;

final class VideoExport {
    static final int FPS=24;
    static String encoderName(int width,int height){
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
            if(!info.isEncoder())continue;
            for(String type:info.getSupportedTypes())if("video/avc".equalsIgnoreCase(type)){
                try{
                    MediaCodecInfo.CodecCapabilities caps=info.getCapabilitiesForType(type);boolean surface=false;
                    for(int color:caps.colorFormats)if(color==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)surface=true;
                    if(surface&&caps.getVideoCapabilities().areSizeAndRateSupported(width,height,FPS))return info.getName();
                }catch(Exception ignored){}
            }
        }return null;
    }
    static Bitmap loadImage(String path,int max){
        if(path==null||path.isEmpty())return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("Imagem inválida. Usa JPG, PNG ou WebP.");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>max*2)opts.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeFile(path,opts);
        if(b==null)throw new IllegalArgumentException("Não foi possível abrir a imagem.");return b;
    }
    private static final class Drain {
        final MediaCodec codec;final MediaMuxer mux;
        boolean started=false,ended=false;int track=-1;
        final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        Drain(MediaCodec c,MediaMuxer m){codec=c;mux=m;}
        void run(boolean finish,MediaIo.Progress progress) throws Exception {
            long deadline=System.nanoTime()+60_000_000_000L;
            while(!ended){
                int idx=codec.dequeueOutputBuffer(info,finish?10000:0);
                if(idx==MediaCodec.INFO_TRY_AGAIN_LATER){if(!finish)return;if(System.nanoTime()>deadline)throw new IOException("O codificador deixou de responder.");progress.update("A concluir vídeo",99);}
                else if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(started)throw new IOException("Formato de vídeo alterado.");track=mux.addTrack(codec.getOutputFormat());mux.start();started=true;}
                else if(idx>=0){
                    if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                    if(info.size>0){if(!started)throw new IOException("Codificador sem formato de vídeo.");ByteBuffer b=codec.getOutputBuffer(idx);b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info);}
                    ended=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(idx,false);
                }
            }
        }
    }
    static void render(MediaIo.Audio audio,Options options,File output,File tempAudio,MediaIo.Progress progress) throws Exception {
        String name=encoderName(options.width,options.height);
        if(name==null)throw new IOException("Este dispositivo não suporta este formato. Experimenta 720p ou outro formato.");
        long estimated=(long)(audio.seconds()*((options.width*options.height>1280*720?8_000_000:4_000_000)/8+24000)*1.2)+32L*1024*1024;
        if(output.getParentFile().getUsableSpace()<estimated)throw new IOException("Espaço insuficiente. Liberta cerca de "+(estimated/(1024*1024))+" MB para este vídeo.");
        SubtitleTrack subtitles=options.showSubtitles&&!options.subtitlePath.isEmpty()?SubtitleTrack.load(new File(options.subtitlePath)):null;
        MediaIo.audioAac(audio,tempAudio,progress);
        MediaCodec codec=null;MediaMuxer mux=null;MediaExtractor soundtrack=null;Surface input=null;GlEncoder gl=null;VideoFrames videoFrames=null;MediaSequence.Slides slides=null;Bitmap bitmap=null,bg=null;boolean completed=false;
        try(AudioAnalysis analysis=new AudioAnalysis(new File(audio.path),audio.rate,audio.channels)){
            codec=MediaCodec.createByCodecName(name);
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",options.width,options.height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            int target=options.width*options.height>1280*720?8_000_000:4_000_000;
            target=codec.getCodecInfo().getCapabilitiesForType("video/avc").getVideoCapabilities().getBitrateRange().clamp(target);
            format.setInteger(MediaFormat.KEY_BIT_RATE,target);format.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            if(android.os.Build.VERSION.SDK_INT>=29)format.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);input=codec.createInputSurface();codec.start();
            soundtrack=new MediaExtractor();soundtrack.setDataSource(tempAudio.getAbsolutePath());soundtrack.selectTrack(0);
            mux=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int audioTrack=mux.addTrack(soundtrack.getTrackFormat(0));Drain drain=new Drain(codec,mux);
            gl=new GlEncoder(input,options.width,options.height);
            if("video".equals(options.background))videoFrames=new VideoFrames(options.videosPath);
            if("slides".equals(options.background))slides=new MediaSequence.Slides(options.slidesPath);
            float scale=640f/Math.max(options.width,options.height);int rw=Math.max(2,(int)(options.width*scale)/2*2),rh=Math.max(2,(int)(options.height*scale)/2*2);
            bitmap=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);SceneRenderer renderer=new SceneRenderer();renderer.setSubtitles(subtitles);
            if("image".equals(options.background)){bg=loadImage(options.imagePath,1280);renderer.setBackground(bg);}
            int count=(int)Math.ceil(audio.seconds()*FPS);
            for(int i=0;i<count;i++){
                progress.update("A gerar vídeo",i*100/count);drain.run(false,progress);
                if(slides!=null){long time=i*1000L/FPS,duration=(long)(audio.seconds()*1000);Bitmap first=slides.at(time,duration);float blend=options.dissolve?slides.blend(time,duration):0;renderer.setSlide(first,blend>0?slides.next():null,blend);}
                if(videoFrames!=null)videoFrames.at(i*1_000_000L/FPS,progress);
                renderer.draw(canvas,rw,rh,options,analysis.at(i/(double)FPS),i/(double)FPS);
                gl.draw(bitmap,i*1_000_000_000L/FPS,videoFrames,options.mediaFit);drain.run(false,progress);
            }
            codec.signalEndOfInputStream();drain.run(true,progress);
            ByteBuffer buffer=ByteBuffer.allocateDirect(512*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long last=-1;
            while(true){buffer.clear();int n=soundtrack.readSampleData(buffer,0);if(n<0)break;
                long pts=soundtrack.getSampleTime();
                if(pts>=0&&pts>last&&pts<(long)(audio.seconds()*1_000_000)){
                    info.set(0,n,pts,soundtrack.getSampleFlags());buffer.position(0);buffer.limit(n);mux.writeSampleData(audioTrack,buffer,info);last=pts;
                }
                soundtrack.advance();
            }
            mux.stop();completed=true;
        }finally{
            if(videoFrames!=null)videoFrames.close();if(slides!=null)slides.close();if(gl!=null)gl.close();if(input!=null)input.release();
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
            if(soundtrack!=null)soundtrack.release();if(mux!=null)mux.release();
            if(bitmap!=null)bitmap.recycle();if(bg!=null)bg.recycle();tempAudio.delete();if(!completed)output.delete();
        }
    }
}
