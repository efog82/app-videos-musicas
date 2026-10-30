package pt.sonora.offline;

import android.graphics.*;
import android.text.*;

/** Cached wrapping and a safe-area caption box, shared by preview and video. */
final class SubtitleRenderer {
    private final TextPaint textPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint box=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private SubtitleTrack track;
    private StaticLayout layout;
    private String cachedText="";
    private int cachedW,cachedH,cachedSize;
    void setTrack(SubtitleTrack value){track=value;layout=null;cachedText="";}
    void draw(Canvas c,int w,int h,Options options,double seconds){
        if(track==null||!options.showSubtitles)return;
        String text=track.textAt((long)Math.floor(seconds*1000+.0001),options.subtitleOffsetMs);
        if(text.isEmpty())return;
        int available=Math.max(1,(int)(w*.86f));
        if(layout==null||!text.equals(cachedText)||cachedW!=w||cachedH!=h||cachedSize!=options.subtitleSize){
            cachedText=text;cachedW=w;cachedH=h;cachedSize=options.subtitleSize;
            float size=Math.min(w,h)*new float[]{.041f,.051f,.063f}[Math.max(0,Math.min(2,options.subtitleSize))];
            textPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));textPaint.setColor(Color.WHITE);textPaint.setShadowLayer(Math.max(1,size*.13f),0,1,Color.BLACK);
            do{
                textPaint.setTextSize(size);layout=StaticLayout.Builder.obtain(text,0,text.length(),textPaint,available)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0,1.13f)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();size*=.9f;
            }while(layout.getHeight()>h*.48f&&size>Math.min(w,h)*.014f);
        }
        float padding=Math.min(w,h)*.025f;
        float top=options.subtitlePosition==1?(h-layout.getHeight())/2f:options.subtitlePosition==2?h*.12f:h*.84f-layout.getHeight();
        top=Math.max(padding,Math.min(h-layout.getHeight()-padding*2,top));
        float x=(w-available)/2f;
        float left=available,right=0;
        for(int i=0;i<layout.getLineCount();i++){left=Math.min(left,layout.getLineLeft(i));right=Math.max(right,layout.getLineRight(i));}
        rect.set(x+left-padding,top-padding*.65f,x+right+padding,top+layout.getHeight()+padding*.65f);
        box.setColor(0xbc050912);c.drawRoundRect(rect,padding*.55f,padding*.55f,box);
        c.save();c.translate(x,top);layout.draw(c);c.restore();
    }
}
