package pt.sonora.offline;

import java.io.Serializable;

public class Options implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String[] NAMES = {"Equalizador clássico", "Espectro circular", "Partículas cósmicas", "Ondas de energia", "Túnel psicadélico", "Mandala musical", "Osciloscópio", "Galáxia"};
    public int style=0, color=0xff339dff, width=1280, height=720;
    public float intensity=1, speed=1;
    public boolean full=false, showTitle=true, enhancedEffects=true, showSubtitles=true;
    public String subtitlePath="", slidesPath="", videosPath="";
    public boolean showVisualization=true, dissolve=true, mediaFit=false;
    public long subtitleOffsetMs=0;
    public int subtitleSize=1, subtitlePosition=0;
    public String title="", background="black", imagePath="";
    public Options copy() {
        Options o=new Options(); o.style=style; o.color=color; o.width=width; o.height=height;
        o.intensity=intensity; o.speed=speed; o.full=full; o.showTitle=showTitle;
        o.slidesPath=slidesPath;o.videosPath=videosPath;o.showVisualization=showVisualization;o.dissolve=dissolve;o.mediaFit=mediaFit;
        o.enhancedEffects=enhancedEffects;o.showSubtitles=showSubtitles;o.subtitlePath=subtitlePath;
        o.subtitleOffsetMs=subtitleOffsetMs;o.subtitleSize=subtitleSize;o.subtitlePosition=subtitlePosition;
        o.title=title; o.background=background; o.imagePath=imagePath; return o;
    }
}
