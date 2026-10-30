package pt.sonora.offline;

import android.graphics.*;
import java.io.*;

/** Additive adapter: the existing 0.3 renderer and decoder remain the source of truth. */
public final class LegacyBridge {
    private final SceneRenderer renderer=new SceneRenderer();
    private final AudioAnalysis.Frame frame=new AudioAnalysis.Frame();
    private final Options options=new Options();
    public static boolean exporting(){return ExportService.running;}
    public static final class Decoded {
        public final int rate,channels;public final long bytes;
        Decoded(MediaIo.Audio audio){rate=audio.rate;channels=audio.channels;bytes=audio.frames*channels*2;}
    }
    public interface Progress {void update(String text)throws IOException;}
    public static Decoded decode(android.content.Context context,android.net.Uri uri,File output,Progress progress)throws Exception{
        return new Decoded(MediaIo.decode(context,uri,output,(text,n)->progress.update(text+" · "+n+"%")));
    }
    public void draw(Canvas c,int w,int h,int style,boolean detailed,boolean full,int color,float intensity,float speed,float energy,float bass,float mid,float treble,float[] levels,float[] wave,double seconds){
        options.background="video";options.style=style;options.enhancedEffects=detailed;options.full=full;options.color=color;options.intensity=intensity;options.speed=speed;options.showTitle=false;options.showSubtitles=false;
        frame.energy=energy;frame.bands[0]=bass;frame.bands[1]=mid;frame.bands[2]=treble;for(int i=0;i<48;i++)frame.levels[i]=levels[Math.min(levels.length-1,i*levels.length/48)];for(int i=0;i<256;i++)frame.wave[i]=wave[Math.min(wave.length-1,i*wave.length/256)];
        int saved=c.saveLayer(0,0,w,h,null);renderer.draw(c,w,h,options,frame,seconds);c.restoreToCount(saved);
    }
}
