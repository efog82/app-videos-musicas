package pt.sonora.offline;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;

final class MediaIo {
    interface Progress { void update(String phase,int percent) throws IOException; }
    static class Audio implements Serializable {
        private static final long serialVersionUID=1L;
        String path; int rate,channels; long frames;
        double seconds(){return (double)frames/rate;}
    }
    static Audio decode(Context context,Uri uri,File output,Progress progress) throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
        boolean success=false;
        try {
            extractor.setDataSource(context,uri,null);
            int track=-1; MediaFormat input=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;input=f;break;}
            }
            if(track<0)throw new IOException("O ficheiro não contém áudio compatível.");
            long expected=input.containsKey(MediaFormat.KEY_DURATION)?input.getLong(MediaFormat.KEY_DURATION):0;
            if(expected>600_500_000L)throw new IOException("Escolhe uma música com até 10 minutos.");
            extractor.selectTrack(track);
            input.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            decoder=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));
            decoder.configure(input,null,null,0);decoder.start();
            Audio audio=new Audio();audio.path=output.getAbsolutePath();
            audio.rate=input.getInteger(MediaFormat.KEY_SAMPLE_RATE);audio.channels=input.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int pcm=AudioFormat.ENCODING_PCM_16BIT;boolean inEnd=false,outEnd=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long bytesWritten=0,lastProgress=System.nanoTime();
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(output))){
                while(!outEnd){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Importação cancelada.");
                    if(!inEnd){int i=decoder.dequeueInputBuffer(10000);if(i>=0){ByteBuffer b=decoder.getInputBuffer(i);b.clear();int n=extractor.readSampleData(b,0);
                        if(n<0){decoder.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inEnd=true;}
                        else{decoder.queueInputBuffer(i,0,n,extractor.getSampleTime(),0);extractor.advance();}
                    }}
                    int i=decoder.dequeueOutputBuffer(info,10000);
                    if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat f=decoder.getOutputFormat();int rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if(bytesWritten>0&&(rate!=audio.rate||channels!=audio.channels))throw new IOException("O áudio muda de formato durante a reprodução.");
                        audio.rate=rate;audio.channels=channels;pcm=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                        if(channels<1||channels>2)throw new IOException("Usa áudio mono ou estéreo.");
                        if(pcm!=AudioFormat.ENCODING_PCM_16BIT&&pcm!=AudioFormat.ENCODING_PCM_FLOAT)throw new IOException("Formato PCM não suportado.");
                    }else if(i>=0){
                        if(info.size>0){ByteBuffer b=decoder.getOutputBuffer(i).duplicate().order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);
                            byte[] data;
                            if(pcm==AudioFormat.ENCODING_PCM_FLOAT){data=new byte[(info.size/4)*2];for(int j=0;j<data.length;j+=2){int sample=(int)(Math.max(-1,Math.min(1,b.getFloat()))*32767);data[j]=(byte)sample;data[j+1]=(byte)(sample>>8);}}
                            else{data=new byte[info.size];b.get(data);}
                            int skip=info.presentationTimeUs<0?(int)Math.min(data.length,(-info.presentationTimeUs*audio.rate/1_000_000)*audio.channels*2):0;
                            skip-=skip%(audio.channels*2);out.write(data,skip,data.length-skip);bytesWritten+=data.length-skip;
                            long samples=bytesWritten/(audio.channels*2);
                            if(samples>(long)audio.rate*600.5)throw new IOException("Escolhe uma música com até 10 minutos.");
                            if(System.nanoTime()-lastProgress>200_000_000L){progress.update("A analisar áudio",expected>0?(int)Math.min(99,samples*100_000_000L/audio.rate/expected):0);lastProgress=System.nanoTime();}
                        }
                        outEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;decoder.releaseOutputBuffer(i,false);
                    }
                }
            }
            audio.frames=bytesWritten/(audio.channels*2);
            if(audio.frames<=0)throw new IOException("O áudio está vazio.");
            success=true;return audio;
        }finally{
            if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}extractor.release();
            if(!success)output.delete();
        }
    }
    static void audioAac(Audio audio,File output,Progress progress) throws Exception {
        MediaCodec codec=MediaCodec.createEncoderByType("audio/mp4a-latm");MediaMuxer mux=null;boolean started=false;
        try{
            MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",audio.rate,audio.channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE,192000);format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384);
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();
            mux=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnd=false,outputEnd=false;long count=0;int track=-1;
            try(InputStream in=new BufferedInputStream(new FileInputStream(audio.path))){
                while(!outputEnd){
                    progress.update("A preparar áudio",(int)Math.min(99,count*100/(audio.frames*audio.channels*2)));
                    if(!inputEnd){int idx=codec.dequeueInputBuffer(10000);if(idx>=0){ByteBuffer b=codec.getInputBuffer(idx);b.clear();
                        int capacity=Math.min(b.remaining(),16384);capacity-=capacity%(audio.channels*2);
                        byte[] bytes=new byte[capacity];int total=0,n;
                        while(total<capacity&&(n=in.read(bytes,total,capacity-total))>0)total+=n;
                        long pts=count/(audio.channels*2)*1_000_000/audio.rate;
                        if(total==0){codec.queueInputBuffer(idx,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
                        else{b.put(bytes,0,total);codec.queueInputBuffer(idx,0,total,pts,0);count+=total;}
                    }}
                    int idx=codec.dequeueOutputBuffer(info,10000);
                    if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=mux.addTrack(codec.getOutputFormat());mux.start();started=true;}
                    else if(idx>=0){
                        if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                        if(info.size>0&&info.presentationTimeUs>=0){if(!started)throw new IOException("Codificador AAC sem formato.");ByteBuffer b=codec.getOutputBuffer(idx);b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info);}
                        outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(idx,false);
                    }
                }
            }
            if(started){mux.stop();started=false;}
        }finally{try{codec.stop();}catch(Exception ignored){}codec.release();if(mux!=null)mux.release();}
    }
}
