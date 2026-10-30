package pt.sonora.offline;

import android.graphics.*;

/** Same bounded Canvas renderer for thumbnails, live preview and MP4 frames. */
public final class SceneRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final float[] hsv=new float[3];
    private Bitmap background, nextBackground;
    private float backgroundMix;
    public void setSlide(Bitmap first,Bitmap second,float blend){background=first;nextBackground=second;backgroundMix=blend;}
    private EffectLayers effects;
    private SubtitleRenderer subtitles;
    public void setSubtitles(SubtitleTrack track){if(subtitles==null)subtitles=new SubtitleRenderer();subtitles.setTrack(track);}
    public void setBackground(Bitmap b){background=b;nextBackground=null;backgroundMix=0;}
    private int color(int base,float shift,float brightness){
        Color.colorToHSV(base,hsv);hsv[0]=(hsv[0]+shift*360)%360;hsv[2]*=Math.min(1,brightness);
        return Color.HSVToColor(hsv);
    }
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
    private void image(Canvas c,int w,int h,Bitmap bitmap,boolean fit,int opacity){
        float scale=fit?Math.min((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight()):Math.max((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());
        float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;rect.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);
        p.setStyle(Paint.Style.FILL);p.setAlpha(opacity);c.drawBitmap(bitmap,null,rect,p);p.setAlpha(255);
    }
    public void draw(Canvas c,int w,int h,Options o,AudioAnalysis.Frame f,double seconds) {
        c.drawColor("video".equals(o.background)?Color.TRANSPARENT:Color.BLACK,PorterDuff.Mode.SRC);
        if(("image".equals(o.background)||"slides".equals(o.background))&&background!=null){
            image(c,w,h,background,o.mediaFit,255);
            if(nextBackground!=null&&backgroundMix>0)image(c,w,h,nextBackground,o.mediaFit,(int)(backgroundMix*255));
        }
        float cx=w/2f,cy=h/2f,r=Math.min(w,h)*(o.full?.48f:.32f);
        if(o.full){c.save();float stretch=Math.max(w,h)/(float)Math.min(w,h);c.scale(w>h?stretch:1,h>w?stretch:1,cx,cy);}
        float t=(float)seconds*o.speed, bass=f.bands[0],gain=o.intensity;
        if(o.showVisualization&&"animated".equals(o.background)){
            for(int j=0;j<7;j++){float rr=r*(.4f+j*.27f+.05f*(float)Math.sin(t+j));
                p.setColor(color(o.color,j*.05f,.10f+f.energy*.16f));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);c.drawCircle(cx,cy,rr,p);}
        }
        int col=o.color;float thick=Math.max(1,w/320f);
        if(o.showVisualization){if(o.enhancedEffects){
            if(effects==null)effects=new EffectLayers();effects.draw(c,w,h,o,f,seconds);
        }else switch(o.style){
        case 0:
            p.setStyle(Paint.Style.FILL);p.setColor(col);
            for(int i=0;i<48;i++){float x=cx-r+2*r*i/48,hh=Math.max(1,f.levels[i]*r*gain);c.drawRoundRect(x,cy-hh,x+Math.max(1,2*r/48-2),cy+hh,2,2,p);}break;
        case 1:
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thick);p.setColor(col);c.drawCircle(cx,cy,r*.5f,p);
            for(int i=0;i<48;i++){double a=i*Math.PI*2/48;float rr=r*(.51f+f.levels[i]*gain*.65f);
                line(c,cx+r*.5f*(float)Math.cos(a),cy+r*.5f*(float)Math.sin(a),cx+rr*(float)Math.cos(a),cy+rr*(float)Math.sin(a),color(col,i/150f,1),thick*2);}break;
        case 2: case 7:
            if(o.style==7){
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(cx,cy,r,new int[]{color(col,.1f,.22f+f.energy*.3f),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,p);p.setShader(null);
            }
            for(int i=0;i<240;i++){
                float rr=r*(float)Math.sqrt((i+.5f)/240)*(1+bass*gain*.25f);
                double a=i*2.399963+t*(o.style==7?.08:.14)+(o.style==7?rr/r*5:0);
                float x=cx+rr*(float)Math.cos(a),y=cy+rr*(float)Math.sin(a);
                if(o.style==2)y+=Math.sin(t+i)*f.bands[1]*gain*r*.12;
                p.setStyle(Paint.Style.FILL);p.setColor(color(col,(i%7)*.025f,.25f+.75f*Math.min(1,f.energy*gain+f.bands[2])));
                c.drawCircle(x,y,thick*(.65f+f.energy*gain*(i%5==0?2:0)),p);
            }break;
        case 3:
            for(int j=0;j<5;j++){
                path.reset();for(int i=0;i<160;i++){float x=cx-r+2*r*i/159;
                    float y=cy+(float)Math.sin(i/159.0*Math.PI*4+t+j*.6)*r*(.02f+f.bands[j%3]*gain*.6f)+(j-2)*r*.13f;
                    if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                p.setColor(color(col,j*.08f,1));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thick);c.drawPath(path,p);
            }break;
        case 4:
            for(int j=0;j<16;j++){float depth=(j/16f+t*.18f)%1;depth*=depth;float rr=r*depth*(1+bass*gain*.3f);
                path.reset();for(int i=0;i<=8;i++){double a=i*Math.PI/4+t*.12+j*.04;float x=cx+rr*(float)Math.cos(a),y=cy+rr*(float)Math.sin(a);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                p.setColor(color(col,j*.035f,.2f+.8f*depth));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thick);c.drawPath(path,p);
            }break;
        case 5:
            for(int j=0;j<6;j++){path.reset();for(int i=0;i<=180;i++){
                double a=i*Math.PI*2/180;float rr=r*(.25f+j*.12f)*(1+.18f*gain*f.bands[j%3]*(float)Math.cos(a*(6+j*2)));
                double angle=a+t*.12*(j%2==0?1:-1);float x=cx+rr*(float)Math.cos(angle),y=cy+rr*(float)Math.sin(angle);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                p.setColor(color(col,j*.07f,1));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thick);c.drawPath(path,p);
            }break;
        case 6:
            path.reset();for(int i=0;i<256;i++){float x=cx-r+2*r*i/255,y=cy-f.wave[i]*r*gain;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
            p.setColor(col);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thick);c.drawPath(path,p);break;
        }
        }
        if(o.full)c.restore();
        String title=o.title.trim();
        if(o.showTitle&&!title.isEmpty()){
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(w/24f);
            while(p.measureText(title)>w*.9f)p.setTextSize(p.getTextSize()*.95f);
            p.setShadowLayer(4,0,1,Color.BLACK);p.setColor(Color.WHITE);c.drawText(title,(w-p.measureText(title))/2,h*.21f,p);p.clearShadowLayer();
        }
        if(subtitles!=null)subtitles.draw(c,w,h,o,seconds);
    }
}
