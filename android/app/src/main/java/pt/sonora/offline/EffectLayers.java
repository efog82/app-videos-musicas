package pt.sonora.offline;

import android.graphics.*;

/** Bounded layered artwork. Geometry is deterministic at any audio timestamp. */
final class EffectLayers {
    private static final float TAU=(float)(Math.PI*2);
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path path=new Path(),other=new Path();
    private final RectF rect=new RectF();
    private final float[] hsv=new float[3];
    private final int[] palette=new int[64];
    private final Bitmap[] stars=new Bitmap[3];
    private int cachedColor,cachedW,cachedH;
    private Shader vertical,diagonal;
    private final float[] seed=new float[240],depth=new float[240];
    EffectLayers(){for(int i=0;i<240;i++){seed[i]=seed(i+17);depth[i]=seed(i+1549);}}
    private static float seed(int value){int n=value*0x9e3779b9;n^=n>>>16;n*=0x7feb352d;n^=n>>>15;n*=0x846ca68b;n^=n>>>16;return (n&0x7fffffff)/2147483648f;}
    private static float fraction(float n){return n-(float)Math.floor(n);}
    private int alpha(int color,int a){return (color&0x00ffffff)|(Math.max(0,Math.min(255,a))<<24);}
    private void cache(int color,int w,int h){
        if(color==cachedColor&&w==cachedW&&h==cachedH)return;
        boolean newColor=color!=cachedColor||stars[0]==null;cachedColor=color;cachedW=w;cachedH=h;
        Color.colorToHSV(color,hsv);float hue=hsv[0],sat=hsv[1];
        for(int i=0;i<64;i++)palette[i]=Color.HSVToColor(new float[]{(hue+i*1.5f)%360,Math.max(.45f,sat),1});
        vertical=new LinearGradient(0,0,w*.2f,h,new int[]{Color.WHITE,palette[0],palette[36]},null,Shader.TileMode.CLAMP);
        diagonal=new LinearGradient(0,h,w,0,new int[]{palette[48],palette[0],Color.WHITE},null,Shader.TileMode.CLAMP);
        if(newColor)for(int i=0;i<3;i++){
            if(stars[i]!=null)stars[i].recycle();stars[i]=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(stars[i]);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(255);
            int col=i==2?Color.WHITE:palette[i==0?0:32];
            p.setShader(new RadialGradient(16,16,16,new int[]{Color.WHITE,Color.WHITE,alpha(col,160),Color.TRANSPARENT},new float[]{0,.18f,.45f,1},Shader.TileMode.CLAMP));
            c.drawCircle(16,16,16,p);p.setShader(null);
        }
    }
    private void stroke(Canvas c,Path shape,int color,float width,int opacity){
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(width*5);p.setColor(alpha(color,opacity/12));c.drawPath(shape,p);
        p.setStrokeWidth(width*2.5f);p.setColor(alpha(color,opacity/4));c.drawPath(shape,p);
        p.setStrokeWidth(width);p.setColor(alpha(color,opacity));c.drawPath(shape,p);
    }
    private void ring(Canvas c,float x,float y,float r,int color,float width,int opacity){
        if(r<=0)return;path.reset();path.addCircle(x,y,r,Path.Direction.CW);stroke(c,path,color,width,opacity);
    }
    private void star(Canvas c,float x,float y,float radius,int variant,int opacity){
        rect.set(x-radius,y-radius,x+radius,y+radius);p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(Math.min(255,opacity));c.drawBitmap(stars[variant],null,rect,p);p.setAlpha(255);
    }
    private void aura(Canvas c,float cx,float cy,float radius,int color,int opacity){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(255);
        p.setShader(new RadialGradient(cx,cy,Math.max(1,radius),new int[]{alpha(color,opacity),alpha(color,opacity/3),Color.TRANSPARENT},new float[]{0,.5f,1},Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,radius,p);p.setShader(null);
    }
    void draw(Canvas c,int w,int h,Options o,AudioAnalysis.Frame f,double seconds){
        cache(o.color,w,h);p.setShader(null);p.setAlpha(255);
        float x=w/2f,y=h/2f,r=Math.min(w,h)*(o.full?.46f:.34f),s=Math.max(.65f,Math.min(w,h)/360f);
        float t=(float)seconds*o.speed,g=o.intensity,b=f.bands[0]*g,m=f.bands[1]*g,a=f.bands[2]*g,e=f.energy*g;
        // A local bloom belongs to the visualisation; the surrounding black/image remains intact.
        aura(c,x,y,r*1.3f,palette[18],(int)(10+Math.min(1,e)*30));
        switch(o.style){
            case 0: equalizer(c,x,y,r,s,g,f);break;
            case 1: spectrum(c,x,y,r,s,t,g,f);break;
            case 2: cosmos(c,w,h,x,y,r,s,t,b,m,a,e);break;
            case 3: waves(c,x,y,r,s,t,g,f);break;
            case 4: tunnel(c,x,y,r,s,t,b,m,e);break;
            case 5: mandala(c,x,y,r,s,t,b,m,a);break;
            case 6: scope(c,x,y,r,s,g,f);break;
            case 7: galaxy(c,x,y,r,s,t,b,m,a,e);break;
        }
        p.setShader(null);p.setAlpha(255);
    }
    private void equalizer(Canvas c,float cx,float cy,float r,float s,float gain,AudioAnalysis.Frame f){
        float gap=2*r/48,baseline=cy+r*.35f;
        for(int j=0;j<4;j++){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.5f);p.setColor(alpha(palette[0],18));c.drawLine(cx-r,baseline-j*r*.3f,cx+r,baseline-j*r*.3f,p);
        }
        for(int i=0;i<48;i++){
            float value=(float)Math.pow(f.levels[i],.7)*gain;
            float height=Math.max(s*1.5f,Math.min(r*1.35f,value*r*1.15f)),x=cx-r+i*gap,bw=Math.max(s,gap*.68f);
            p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(alpha(palette[i],35));c.drawRoundRect(x-s*2,baseline-height-s*2,x+bw+s*2,baseline+s*2,s*2,s*2,p);
            p.setColor(Color.WHITE);p.setShader(vertical);c.drawRoundRect(x,baseline-height,x+bw,baseline,s,s,p);p.setShader(null);
            p.setColor(alpha(Color.WHITE,210));c.drawRect(x,baseline-height,x+bw,baseline-height+s,p);
            // Faint mirrored reflection, deliberately much shorter than the main bars.
            p.setShader(vertical);p.setAlpha(45);c.drawRoundRect(x,baseline+s*3,x+bw,baseline+s*3+height*.32f,s,s,p);p.setShader(null);p.setAlpha(255);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s);p.setColor(alpha(palette[0],100));c.drawLine(cx-r,baseline+s,cx+r,baseline+s,p);
    }
    private void spectrum(Canvas c,float x,float y,float r,float s,float t,float g,AudioAnalysis.Frame f){
        float core=r*(.43f+f.bands[0]*g*.075f);
        aura(c,x,y,core*1.4f,palette[0],50);ring(c,x,y,core,palette[0],s*1.3f,220);ring(c,x,y,core*.9f,palette[32],s*.6f,105);
        for(int i=0;i<96;i++){
            float position=i*48f/96;int bin=(int)position;float level=f.levels[bin]*(1-(position-bin))+f.levels[(bin+1)%48]*(position-bin);
            float rr=core+r*(.025f+(float)Math.pow(level,.7)*g*.6f);double angle=i*TAU/96-Math.PI/2;
            path.reset();path.moveTo(x+core*(float)Math.cos(angle),y+core*(float)Math.sin(angle));path.lineTo(x+rr*(float)Math.cos(angle),y+rr*(float)Math.sin(angle));
            stroke(c,path,palette[i%64],s*1.15f,230);
        }
        for(int j=0;j<3;j++){
            rect.set(x-core*.72f,y-core*.72f,x+core*.72f,y+core*.72f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s);p.setColor(alpha(palette[25+j*10],160));
            c.drawArc(rect,t*18+j*120,65+f.bands[1]*g*30,false,p);
        }
        star(c,x,y,s*(4+f.bands[0]*g*7),2,200);
        for(int i=0;i<8;i++){double angle=t*.16+i*TAU/8;float rr=r*(1.03f+f.bands[2]*g*.08f);star(c,x+rr*(float)Math.cos(angle),y+rr*(float)Math.sin(angle),s*3.2f,1,110);}
    }
    private void cosmos(Canvas c,int w,int h,float x,float y,float r,float s,float t,float bass,float mid,float high,float energy){
        for(int i=0;i<240;i++){
            float z=fraction(depth[i]+t*.035f),spread=(float)Math.pow(z,.65);
            double angle=seed[i]*TAU+t*.055+depth[i]*2;
            float rr=r*1.6f*spread*(1+bass*.16f),xx=x+rr*(float)Math.cos(angle),yy=y+rr*(float)Math.sin(angle);
            float opacity=Math.min(1,z*3)*(1-z*.4f)*(.4f+Math.min(1,energy)*.6f);
            star(c,xx,yy,s*(2.5f+z*z*5.5f+high*(i%9==0?2:0)),i%7==0?2:i%2,(int)(opacity*255));
            if(i%12==0&&energy>.12f){
                float tail=(3+energy*10)*s*z;double direction=angle;
                p.setColor(alpha(palette[i%64],(int)(opacity*65)));p.setStrokeWidth(s*.65f);p.setStyle(Paint.Style.STROKE);
                c.drawLine(xx,yy,xx-tail*(float)Math.cos(direction),yy-tail*(float)Math.sin(direction),p);
            }
        }
        float pulse=Math.min(1,bass);
        if(pulse>.1f){ring(c,x,y,r*(.35f+pulse*.6f),palette[28],s*.8f,(int)(pulse*100));ring(c,x,y,r*(.3f+pulse*.36f),palette[0],s*.6f,(int)(pulse*65));}
        star(c,x,y,s*(5+pulse*12),0,(int)(80+pulse*150));
    }
    private void waves(Canvas c,float x,float y,float r,float s,float t,float g,AudioAnalysis.Frame f){
        for(int layer=5;layer>=0;layer--){
            float amplitude=r*(.015f+(float)Math.pow(f.bands[layer%3],.75)*g*.53f);
            float phase=t*(.7f+layer*.1f)+layer*.64f,offset=(layer-2.5f)*r*.08f;
            path.reset();other.reset();
            for(int i=0;i<=144;i++){
                float u=i/144f,xx=x-r*1.25f+u*r*2.5f,envelope=(float)Math.pow(Math.sin(u*Math.PI),.7);
                float wave=(float)(Math.sin(u*TAU*1.4+phase)*.73+Math.sin(u*TAU*3-phase*.65)*.27);
                float yy=y+offset+wave*amplitude*envelope+f.wave[Math.min(255,(int)(u*255))]*r*.04f*g;
                if(i==0){path.moveTo(xx,yy);other.moveTo(xx,yy);}else{path.lineTo(xx,yy);other.lineTo(xx,yy);}
            }
            other.lineTo(x+r*1.25f,y+offset);other.lineTo(x-r*1.25f,y+offset);other.close();
            p.setStyle(Paint.Style.FILL);p.setShader(diagonal);p.setColor(Color.WHITE);p.setAlpha(30+layer*4);c.drawPath(other,p);p.setShader(null);p.setAlpha(255);
            stroke(c,path,palette[layer*10],s*(layer==0?1.25f:.8f),200);
        }
    }
    private void tunnel(Canvas c,float x,float y,float r,float s,float t,float bass,float mid,float energy){
        float turn=t*.09f;
        for(int j=23;j>=0;j--){
            float z=fraction(j/24f+t*.11f),rr=r*1.6f*z*z*(1+bass*.16f);if(rr<s)continue;
            int sides=j%3==0?6:12;float twist=turn+(1-z)*.75f+mid*.1f;
            path.reset();
            for(int i=0;i<=sides;i++){double a=i*TAU/sides+twist;float px=x+rr*(float)Math.cos(a),py=y+rr*(float)Math.sin(a);if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}
            stroke(c,path,palette[(j*3)%64],s*(.4f+z*.8f),(int)(30+z*190));
            if(j%4==0){
                for(int k=0;k<6;k++){
                    double a=k*TAU/6+twist;float px=x+rr*(float)Math.cos(a),py=y+rr*(float)Math.sin(a);
                    star(c,px,py,s*(2+z*2),1,(int)(z*170));
                }
            }
        }
        // Perspective rails converge into the same vanishing point as the rings.
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.55f);p.setColor(alpha(palette[16],45));
        for(int i=0;i<12;i++){double a=i*TAU/12+turn;float co=(float)Math.cos(a),si=(float)Math.sin(a);c.drawLine(x+r*.04f*co,y+r*.04f*si,x+r*1.6f*co,y+r*1.6f*si,p);}
        star(c,x,y,s*(3+Math.min(1,energy)*5),2,210);
    }
    private void mandala(Canvas c,float x,float y,float r,float s,float t,float bass,float mid,float high){
        float breath=1+bass*.15f;
        for(int layer=0;layer<8;layer++){
            float radius=r*(.16f+layer*.105f)*breath;int petals=6+layer*2;float influence=layer%3==0?bass:layer%3==1?mid:high;
            float rotate=t*.065f*(layer%2==0?1:-1)+layer*.13f;
            path.reset();
            for(int i=0;i<=192;i++){
                double angle=i*TAU/192;float wave=(float)Math.cos(angle*petals);
                float rr=radius*(1+(.11f+influence*.12f)*wave+.045f*(float)Math.sin(angle*petals*2));
                float xx=x+rr*(float)Math.cos(angle+rotate),yy=y+rr*(float)Math.sin(angle+rotate);if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);
            }
            path.close();p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(alpha(palette[layer*7],8));c.drawPath(path,p);
            stroke(c,path,palette[layer*7],s*(layer%2==0?.85f:.55f),200);
        }
        for(int i=0;i<12;i++){double a=i*TAU/12+t*.07;float rr=r*.9f*breath;star(c,x+rr*(float)Math.cos(a),y+rr*(float)Math.sin(a),s*(2.5f+high),i%2,140);}
        star(c,x,y,s*(5+bass*6),2,230);
    }
    private void scope(Canvas c,float x,float y,float r,float s,float g,AudioAnalysis.Frame f){
        p.setStyle(Paint.Style.STROKE);p.setColor(alpha(palette[0],18));p.setStrokeWidth(s*.5f);
        for(int i=-4;i<=4;i++)c.drawLine(x-r,y+i*r*.2f,x+r,y+i*r*.2f,p);
        for(int i=-6;i<=6;i++)c.drawLine(x+i*r/6,y-r*.8f,x+i*r/6,y+r*.8f,p);
        path.reset();path.moveTo(x-r,y-f.wave[0]*r*g*.85f);
        float lastX=x-r,lastY=y-f.wave[0]*r*g*.85f;
        for(int i=1;i<256;i++){
            float xx=x-r+2*r*i/255,yy=y-f.wave[i]*r*g*.85f;
            path.quadTo(lastX,lastY,(lastX+xx)/2,(lastY+yy)/2);lastX=xx;lastY=yy;
        }path.lineTo(lastX,lastY);stroke(c,path,palette[0],s*1.1f,240);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.35f);p.setColor(alpha(Color.WHITE,190));c.drawPath(path,p);
    }
    private void galaxy(Canvas c,float x,float y,float r,float s,float t,float bass,float mid,float high,float energy){
        c.save();c.rotate(-18,x,y);c.scale(1,.72f,x,y);
        aura(c,x,y,r*1.15f,palette[40],(int)(28+Math.min(1,energy)*40));
        for(int arm=0;arm<3;arm++){
            path.reset();
            for(int i=0;i<=120;i++){
                float u=i/120f,rr=r*(.08f+u)* (1+bass*.1f);double a=arm*TAU/3+u*6+t*.065;
                float xx=x+rr*(float)Math.cos(a),yy=y+rr*(float)Math.sin(a);if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);
            }
            stroke(c,path,palette[arm*18],s*(5+mid*3),(int)(22+Math.min(1,energy)*28));
        }
        for(int i=0;i<240;i++){
            float u=(i+.5f)/240,rr=r*(.06f+(float)Math.sqrt(u))*(1+bass*.13f);
            double angle=(i%3)*TAU/3+Math.sqrt(u)*6+t*.065+(seed[i]-.5f)*.42;
            float xx=x+rr*(float)Math.cos(angle),yy=y+rr*(float)Math.sin(angle);
            int opacity=(int)(85+Math.min(1,energy+high*.25f)*155);
            star(c,xx,yy,s*(1.4f+depth[i]*1.7f+high*(i%11==0?1.7f:0)),i%7==0?2:i%2,opacity);
        }
        aura(c,x,y,r*.3f,palette[0],120);star(c,x,y,s*(12+bass*8),2,245);c.restore();
    }
}
