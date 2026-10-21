package pt.sonora.offline;

import java.io.*;
import java.util.Arrays;

/** PCM is read in small timestamped windows. No recording permission or fake audio. */
public final class AudioAnalysis implements Closeable {
    public static final int N=2048;
    public static class Frame {
        public final float[] wave=new float[256], levels=new float[48], bands=new float[3];
        public float energy;
    }
    private final RandomAccessFile file;
    private final int rate, channels;
    private final long frames;
    private final byte[] bytes;
    private final double[] real=new double[N], imag=new double[N], window=new double[N];
    private final Frame result=new Frame();
    public AudioAnalysis(File pcm, int rate, int channels) throws IOException {
        file=new RandomAccessFile(pcm,"r"); this.rate=rate; this.channels=channels;
        frames=file.length()/(channels*2); bytes=new byte[N*channels*2];
        for(int i=0;i<N;i++) window[i]=.5-.5*Math.cos(2*Math.PI*i/(N-1));
    }
    public synchronized Frame at(double seconds) throws IOException {
        Arrays.fill(bytes,(byte)0); Arrays.fill(imag,0);
        long start=Math.max(0,(long)(seconds*rate)-N/2);
        if(start<frames){file.seek(start*channels*2); int total=0, read;
            while(total<bytes.length && (read=file.read(bytes,total,bytes.length-total))>0) total+=read;}
        double rms=0;
        for(int i=0;i<N;i++) {
            float sample=0;
            for(int c=0;c<channels;c++){int p=(i*channels+c)*2; sample+=(short)((bytes[p]&255)|(bytes[p+1]<<8))/32768f;}
            sample/=channels; rms+=sample*sample; real[i]=sample*window[i];
            if(i%8==0) result.wave[i/8]=sample;
        }
        result.energy=(float)Math.min(1,Math.sqrt(rms/N)*3);
        fft(real,imag);
        Arrays.fill(result.levels,0); Arrays.fill(result.bands,0);
        for(int i=1;i<=N/2;i++) {
            double freq=(double)i*rate/N, power=(real[i]*real[i]+imag[i]*imag[i])/(N*N);
            int band=freq<250?0:freq<4000?1:2;
            if(freq>=20) result.bands[band]+=power;
            if(freq>=24 && freq<=Math.min(16000,rate/2)) {
                int k=(int)(Math.log(freq/24)/Math.log(Math.min(16000,rate/2)/24.0)*48);
                result.levels[Math.min(47,Math.max(0,k))]+=power;
            }
        }
        for(int i=0;i<48;i++)result.levels[i]=(float)Math.min(1,Math.sqrt(result.levels[i])*6);
        for(int i=0;i<3;i++)result.bands[i]=(float)Math.min(1,Math.sqrt(result.bands[i])*6);
        return result;
    }
    static void fft(double[] re,double[] im) {
        int n=re.length;
        for(int i=1,j=0;i<n;i++){int bit=n>>1; for(;(j&bit)!=0;bit>>=1)j^=bit; j^=bit;
            if(i<j){double t=re[i];re[i]=re[j];re[j]=t;t=im[i];im[i]=im[j];im[j]=t;}}
        for(int len=2;len<=n;len<<=1){double angle=-2*Math.PI/len;
            double wr0=Math.cos(angle),wi0=Math.sin(angle);
            for(int i=0;i<n;i+=len){double wr=1,wi=0;
                for(int j=0;j<len/2;j++){int a=i+j,b=a+len/2;
                    double vr=re[b]*wr-im[b]*wi,vi=re[b]*wi+im[b]*wr;
                    re[b]=re[a]-vr;im[b]=im[a]-vi;re[a]+=vr;im[a]+=vi;
                    double next=wr*wr0-wi*wi0;wi=wr*wi0+wi*wr0;wr=next;}
            }
        }
    }
    @Override public void close() throws IOException {file.close();}
}
