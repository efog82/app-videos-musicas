package pt.sonora.offline;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.*;
import java.util.*;

public class SonoraTests extends Instrumentation {
    int passed=0;boolean particlesOnly=false,editorOnly=false,rotationOnly=false,uiOnly=false,effectsOnly=false;
    void check(boolean ok,String msg)throws IOException{if(!ok)throw new IOException(msg);}
    void report(String msg){Bundle b=new Bundle();b.putString("stream",msg+"\n");sendStatus(0,b);android.util.Log.i("SonoraTest",msg);}
    @Override public void onCreate(Bundle args){super.onCreate(args);effectsOnly=args!=null&&"true".equals(args.getString("effectsOnly"));uiOnly=args!=null&&"true".equals(args.getString("uiOnly"));rotationOnly=args!=null&&"true".equals(args.getString("rotationOnly"));editorOnly=args!=null&&"true".equals(args.getString("editorOnly"));particlesOnly=args!=null&&"true".equals(args.getString("particlesOnly"));start();}
    @Override public void onStart(){
        try{runTests();Bundle b=new Bundle();b.putString("stream","PASS: "+passed+" verificações Android offline\n");finish(Activity.RESULT_OK,b);}
        catch(Throwable e){android.util.Log.e("SonoraTest","FAIL",e);Bundle b=new Bundle();b.putString("stream","FAIL após "+passed+": "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,b);}
    }
    File file(String name){return new File(getTargetContext().getFilesDir(),name);}
    void pcm(File path,int frequency,double duration)throws Exception{
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(path))){for(int i=0;i<(int)(48000*duration);i++){short sample=(short)(Math.sin(2*Math.PI*frequency*i/48000)*22000);out.write(sample&255);out.write((sample>>8)&255);}}
    }
    void testSubtitles()throws Exception{
        String srt="1\n00:00:00,083 --> 00:00:00,375\nCanção &amp; coração\nSegunda linha\n\n2\n00:00:00,400 --> 00:00:00,500\nFinal";
        try(OutputStream out=new FileOutputStream(file("test-subtitles.srt"))){out.write(srt.getBytes("UTF-8"));}
        SubtitleTrack track=SubtitleTrack.load(file("test-subtitles.srt"));
        check(track.textAt(82,0).isEmpty()&&!track.textAt(83,0).isEmpty()&&track.textAt(375,0).isEmpty(),"Tempos SRT incorretos");passed++;
        SceneRenderer scene=new SceneRenderer();scene.setSubtitles(track);Options options=new Options();AudioAnalysis.Frame silence=new AudioAnalysis.Frame();
        for(int[] dimensions:new int[][]{{640,360},{360,640},{640,640}}){
            int w=dimensions[0],h=dimensions[1];Bitmap empty=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888),caption=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            options.showSubtitles=false;scene.draw(new Canvas(empty),w,h,options,silence,.2);
            options.showSubtitles=true;scene.draw(new Canvas(caption),w,h,options,silence,.2);check(!empty.sameAs(caption),"Legenda não desenhada");
            options.subtitleOffsetMs=1000;scene.draw(new Canvas(caption),w,h,options,silence,.2);check(empty.sameAs(caption),"Atraso SRT ignorado");
            options.subtitleOffsetMs=0;scene.draw(new Canvas(caption),w,h,options,silence,.375);check(empty.sameAs(caption),"Legenda permanece depois do fim");
            check(empty.getPixel(w/2,h/10)==caption.getPixel(w/2,h/10),"Legenda fora da zona segura");empty.recycle();caption.recycle();passed++;
        }
        // Both renderers remain usable; each refined style reacts to actual audio values.
        for(int style=0;style<8;style++){
            options=new Options();options.style=style;Bitmap a=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);
            SceneRenderer renderer=new SceneRenderer();AudioAnalysis.Frame energy=new AudioAnalysis.Frame();energy.energy=.7f;Arrays.fill(energy.levels,.7f);Arrays.fill(energy.bands,.7f);for(int i=0;i<256;i++)energy.wave[i]=(float)Math.sin(i*.12)*.7f;
            renderer.draw(new Canvas(a),320,240,options,silence,2);renderer.draw(new Canvas(b),320,240,options,energy,2);check(!a.sameAs(b),"Efeito não reage ao áudio: "+style);
            options.enhancedEffects=false;renderer.draw(new Canvas(a),320,240,options,energy,2);check(!a.sameAs(b),"Modo leve não preservado: "+style);passed++;
            a.recycle();b.recycle();
        }
        report("SRT: tempos, atraso, ocultação, três formatos e oito efeitos reativos: OK");
    }
    void copyFixture(String name)throws Exception{try(InputStream in=getContext().getAssets().open(name);OutputStream out=new FileOutputStream(file(name))){MainActivity.copy(in,out,Long.MAX_VALUE);}}
    MediaSequence.Item photo(String name,int color)throws Exception{Bitmap b=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);b.eraseColor(color);File path=file(name);try(OutputStream out=new FileOutputStream(path)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();MediaSequence.Item item=new MediaSequence.Item();item.file=path.getAbsolutePath();item.name=name;return item;}
    void external(File source)throws Exception{try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),source.getName()))){MainActivity.copy(in,out,Long.MAX_VALUE);}}
    Bitmap frame(File video,long time)throws Exception{MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(video.getAbsolutePath());Bitmap b=r.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST);check(b!=null,"Frame indisponível: "+video.getName());return b;}finally{r.release();}}
    void red(int color)throws Exception{check(Color.red(color)>150&&Color.blue(color)<90,"Esperava vermelho; recebi "+Integer.toHexString(color));}
    void blue(int color)throws Exception{check(Color.blue(color)>150&&Color.red(color)<90,"Esperava azul; recebi "+Integer.toHexString(color));}
    void green(int color)throws Exception{check(Color.green(color)>150&&Color.red(color)<90,"Esperava verde; recebi "+Integer.toHexString(color));}
    void testEditorExport(MediaIo.Audio audio)throws Exception{
        report("Editor: a testar fotografias e vídeos com música substituída…");
        if(rotationOnly){photo("test-red.png",Color.RED);photo("test-green.png",Color.GREEN);photo("test-blue.png",Color.BLUE);copyFixture("red-blue.mp4");copyFixture("rotated.mp4");testRotation(audio);return;}
        MediaSequence photos=new MediaSequence(file("test-slides.json"));photos.items.add(photo("test-red.png",Color.RED));photos.items.add(photo("test-green.png",Color.GREEN));photos.items.add(photo("test-blue.png",Color.BLUE));photos.save();
        try(MediaSequence.Slides slides=new MediaSequence.Slides(photos.manifest.getAbsolutePath())){red(slides.at(0,500).getPixel(10,10));green(slides.at(250,500).getPixel(10,10));blue(slides.at(450,500).getPixel(10,10));passed++;}
        Options opts=new Options();opts.width=320;opts.height=240;opts.showVisualization=false;opts.showSubtitles=false;opts.background="slides";opts.slidesPath=photos.manifest.getAbsolutePath();opts.dissolve=false;
        File output=file("test-editor-slides.mp4");VideoExport.render(audio,opts,output,file("test-editor-aac.mp4"),(phase,pct)->{});external(output);
        Bitmap b=frame(output,0);red(b.getPixel(10,10));b.recycle();b=frame(output,250000);green(b.getPixel(10,10));b.recycle();b=frame(output,450000);blue(b.getPixel(10,10));b.recycle();passed++;report("Sequência de imagens até ao fim da música: OK");
        copyFixture("red-blue.mp4");copyFixture("green.mp4");copyFixture("rotated.mp4");
        MediaSequence videos=new MediaSequence(file("test-videos.json"));MediaSequence.Item first=MediaSequence.probeVideo(file("red-blue.mp4"),"Primeiro");first.startMs=100;first.endMs=300;videos.items.add(first);
        MediaSequence.Item second=MediaSequence.probeVideo(file("green.mp4"),"Segundo");second.startMs=0;second.endMs=100;videos.items.add(second);videos.save();
        check(videos.videoIndex(250)==1&&videos.videoIndex(350)==0&&videos.videoTimeMs(350)==150,"Tempos/loop dos vídeos incorretos");passed++;
        opts.background="video";opts.videosPath=videos.manifest.getAbsolutePath();output=file("test-editor-videos.mp4");VideoExport.render(audio,opts,output,file("test-editor-aac.mp4"),(phase,pct)->{});external(output);
        b=frame(output,50000);red(b.getPixel(10,b.getHeight()/4));blue(b.getPixel(10,b.getHeight()*3/4));b.recycle();
        b=frame(output,250000);green(b.getPixel(10,10));b.recycle();b=frame(output,400000);red(b.getPixel(10,b.getHeight()/4));b.recycle();passed++;report("Vídeos: ordem, corte, orientação e repetição: OK");
        MediaIo.Audio replaced=MediaIo.decode(getTargetContext(),Uri.fromFile(output),file("test-replaced.pcm"),(phase,pct)->{});
        try(AudioAnalysis analyzer=new AudioAnalysis(new File(replaced.path),replaced.rate,replaced.channels)){AudioAnalysis.Frame f=analyzer.at(.2);check(f.bands[1]>f.bands[0]*5,"O som original dos vídeos não foi removido");passed++;}report("Banda sonora: apenas a música selecionada, sem som original: OK");
        testRotation(audio);
    }
    void testRotation(MediaIo.Audio audio)throws Exception{
        MediaSequence rotated=new MediaSequence(file("test-rotation.json"));rotated.items.add(MediaSequence.probeVideo(file("rotated.mp4"),"Rodado"));rotated.save();check(rotated.items.get(0).rotation!=0,"Fixture sem metadados de rotação");report("Rotação da origem: "+rotated.items.get(0).rotation);
        Options opts=new Options();opts.background="video";opts.showVisualization=false;opts.showSubtitles=false;opts.videosPath=rotated.manifest.getAbsolutePath();opts.width=240;opts.height=320;opts.mediaFit=true;
        File output=file("test-editor-rotated.mp4");VideoExport.render(audio,opts,output,file("test-editor-aac.mp4"),(phase,pct)->{});external(output);
        Bitmap b=frame(output,100000);red(b.getPixel(b.getWidth()/4,b.getHeight()/2));blue(b.getPixel(b.getWidth()*3/4,b.getHeight()/2));b.recycle();passed++;report("Rotação do vídeo original: OK");
    }
    void waitImport(MainActivity main)throws Exception{long end=android.os.SystemClock.elapsedRealtime()+60000;while(main.busy&&android.os.SystemClock.elapsedRealtime()<end)Thread.sleep(100);check(!main.busy,"Importação não terminou");}
    void testEditorImport(MainActivity main)throws Exception{
        photo("test-red.png",Color.RED);photo("test-green.png",Color.GREEN);photo("test-blue.png",Color.BLUE);copyFixture("red-blue.mp4");
        Intent data=new Intent();ClipData files=ClipData.newRawUri("Imagens",Uri.fromFile(file("test-red.png")));files.addItem(new ClipData.Item(Uri.fromFile(file("test-green.png"))));data.setClipData(files);
        runOnMainSync(()->main.editor.handle(EditorControls.PHOTOS,data));waitImport(main);check(!main.options.slidesPath.isEmpty(),"Importação de imagens falhou");int count=MediaSequence.load(main.options.slidesPath).items.size();
        Intent one=new Intent().setData(Uri.fromFile(file("test-blue.png")));runOnMainSync(()->main.editor.handle(EditorControls.PHOTOS,one));waitImport(main);check(MediaSequence.load(main.options.slidesPath).items.size()==count+1,"Imagens novas substituíram as anteriores");passed++;
        Intent movie=new Intent().setData(Uri.fromFile(file("red-blue.mp4")));runOnMainSync(()->main.editor.handle(EditorControls.VIDEOS,movie));waitImport(main);check(!main.options.videosPath.isEmpty(),"Importação de vídeo falhou");passed++;report("Editor na interface: várias imagens, acrescentar e importar vídeos: OK");
    }
    void runTests()throws Exception{
        Context context=getTargetContext();
        testSubtitles();
        PackageInfo pkg=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
        check(pkg.requestedPermissions==null||!Arrays.asList(pkg.requestedPermissions).contains("android.permission.INTERNET"),"APK pede internet");passed++;report("Sem permissão de internet: OK");
        for(int band=0;band<3;band++){
            File tone=file("test-tone.pcm");pcm(tone,new int[]{96,1008,6000}[band],.3);
            try(AudioAnalysis analysis=new AudioAnalysis(tone,48000,1)){
                AudioAnalysis.Frame f=analysis.at(.15);int max=0;for(int i=1;i<3;i++)if(f.bands[i]>f.bands[max])max=i;
                check(max==band&&f.energy>.5,"FFT não distingue banda "+band);passed++;
            }tone.delete();
        }report("FFT: graves, médios e agudos: OK");
        Options o=new Options();SceneRenderer renderer=new SceneRenderer();AudioAnalysis.Frame zero=new AudioAnalysis.Frame();
        Bitmap a=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(a),320,180,o,zero,0);o.title="Título oculto";o.showTitle=false;renderer.draw(new Canvas(b),320,180,o,zero,0);
        check(a.sameAs(b),"Título oculto aparece");passed++;o.title="";o.showTitle=true;renderer.draw(new Canvas(b),320,180,o,zero,0);check(a.sameAs(b),"Título automático aparece");passed++;
        o.title="Canção";renderer.draw(new Canvas(b),320,180,o,zero,0);check(!a.sameAs(b),"Título manual ausente");passed++;
        a.recycle();b.recycle();report("Títulos vazios, ocultos e manuais: OK");
        File source=file("test-source.pcm");pcm(source,440,.5);
        // Feed a real WAV through Android's extractor and audio decoder.
        File wav=file("test-source.wav");try(OutputStream out=new FileOutputStream(wav);InputStream in=new FileInputStream(source)){
            int length=(int)source.length();ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes("US-ASCII")).putInt(36+length).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(48000).putInt(96000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(length);
            out.write(header.array());MainActivity.copy(in,out,Long.MAX_VALUE);
        }
        MediaIo.Audio audio=MediaIo.decode(context,Uri.fromFile(wav),file("test-decoded.pcm"),(phase,pct)->{});
        check(Math.abs(audio.seconds()-.5)<.01,"Descodificação WAV altera duração");passed++;report("Descodificação de áudio real: OK");
        if(!uiOnly&&!effectsOnly)testEditorExport(audio);
        File last=null;
        for(int style=0;style<8;style++){
            if(editorOnly||rotationOnly||uiOnly)continue;
            if(particlesOnly&&style!=2&&style!=3&&style!=7)continue;
            Options opts=new Options();opts.style=style;
            if(style==0){opts.width=1280;opts.height=720;}
            else if(style==1){opts.width=720;opts.height=1280;}
            else if(style==2){opts.width=720;opts.height=720;}
            else if(style==7&&VideoExport.encoderName(1920,1080)!=null){opts.width=1920;opts.height=1080;}
            else{opts.width=320;opts.height=240;}
            if(VideoExport.encoderName(opts.width,opts.height)==null){
                report("720p neste formato não anunciado pelo emulador; a testar a mesma proporção a resolução reduzida.");
                if(style==1){opts.width=360;opts.height=640;}else if(style==2){opts.width=360;opts.height=360;}else{opts.width=640;opts.height=360;}
            }
            opts.subtitlePath=file("test-subtitles.srt").getAbsolutePath();opts.subtitleSize=1;
            opts.background=style%2==0?"black":"animated";opts.title=style==1?"Canção: 100% d'amor":"";
            if(style==3){Bitmap image=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);image.eraseColor(Color.BLUE);File path=file("test-background.png");try(OutputStream out=new FileOutputStream(path)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();opts.background="image";opts.imagePath=path.getAbsolutePath();}
            Bitmap artwork=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);AudioAnalysis.Frame spectrum=new AudioAnalysis.Frame();spectrum.energy=.62f;spectrum.bands[0]=.72f;spectrum.bands[1]=.4f;spectrum.bands[2]=.3f;
            for(int i=0;i<48;i++)spectrum.levels[i]=.12f+.65f*(float)Math.abs(Math.sin(i*.7));for(int i=0;i<256;i++)spectrum.wave[i]=.6f*(float)Math.sin(i*.1);
            SceneRenderer art=new SceneRenderer();Options artOpts=opts.copy();artOpts.background="black";artOpts.full=false;artOpts.title="";art.draw(new Canvas(artwork),640,360,artOpts,spectrum,3.2);
            try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"effect-"+style+".png"))){artwork.compress(Bitmap.CompressFormat.PNG,100,out);}artwork.recycle();
            report("A exportar "+Options.NAMES[style]+" · "+opts.width+"×"+opts.height);
            File video=file("test-style-"+style+".mp4");
            VideoExport.render(audio,opts,video,file("test-aac.mp4"),(phase,pct)->{});
            check(video.length()>1000,"MP4 vazio");
            try(InputStream in=new FileInputStream(video);OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"sample-style-"+style+".mp4"))){MainActivity.copy(in,out,Long.MAX_VALUE);}
            MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(video.getAbsolutePath());check(extractor.getTrackCount()==2,"MP4 sem dois tracks");boolean hasAudio=false,hasVideo=false;long vd=0,ad=0;
                for(int i=0;i<2;i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                    if(mime.startsWith("video/")){hasVideo=true;check(f.getInteger(MediaFormat.KEY_WIDTH)==opts.width&&f.getInteger(MediaFormat.KEY_HEIGHT)==opts.height,"Dimensões erradas");vd=f.getLong(MediaFormat.KEY_DURATION);}
                    if(mime.startsWith("audio/")){hasAudio=true;check(mime.equals("audio/mp4a-latm"),"Áudio não AAC");ad=f.getLong(MediaFormat.KEY_DURATION);}
                }check(hasAudio&&hasVideo,"MP4 incompleto");check(Math.abs(vd-ad)<100000,"Áudio dessincronizado: "+vd+"/"+ad);
            }finally{extractor.release();}
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{retriever.setDataSource(video.getAbsolutePath());Bitmap frame=retriever.getFrameAtTime(0);if(frame==null&&opts.width*opts.height>1280*720){report("O leitor do emulador não devolveu frame de 1080p; validar também o MP4 copiado com FFmpeg.");}
                else{check(frame!=null,"Vídeo sem frame descodificável");frame.recycle();}
                if(style==0){Bitmap caption=retriever.getFrameAtTime(200000,MediaMetadataRetriever.OPTION_CLOSEST);check(caption!=null,"Não foi possível ler a legenda no MP4");
                    int white=0;for(int yy=(int)(caption.getHeight()*.65);yy<(int)(caption.getHeight()*.85);yy++)for(int xx=caption.getWidth()/10;xx<caption.getWidth()*9/10;xx++){int pixel=caption.getPixel(xx,yy);if(Color.red(pixel)>205&&Color.green(pixel)>205&&Color.blue(pixel)>205)white++;}
                    check(white>50,"Legendas não foram incorporadas no MP4");caption.recycle();passed++;
                }}finally{retriever.release();}
            passed++;last=video;report("MP4 validado: "+Options.NAMES[style]);
        }
        report(editorOnly||rotationOnly||particlesOnly||uiOnly?"Verificações gráficas e de áudio: OK":"Oito efeitos, três formatos, imagem, fundo animado e AAC: OK");
        if(effectsOnly)return;
        Intent launch=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);context.startActivity(launch);Activity activity=waitForMonitorWithTimeout(monitor,60000);removeMonitor(monitor);check(activity!=null,"Interface não abre");passed++;report("Interface instalada e aberta: OK");
        MainActivity main=(MainActivity)activity;
        testEditorImport(main);
        runOnMainSync(()->main.importSubtitles(Uri.fromFile(file("test-subtitles.srt"))));
        long deadline=android.os.SystemClock.elapsedRealtime()+60000;while(main.busy&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
        check(main.subtitleTrack!=null&&main.subtitleTrack.size()==2,"Importação de SRT pela interface falhou");passed++;
        File bad=file("test-invalid.srt");try(OutputStream out=new FileOutputStream(bad)){out.write("inválido".getBytes("UTF-8"));}
        SubtitleTrack previous=main.subtitleTrack;runOnMainSync(()->main.importSubtitles(Uri.fromFile(bad)));
        deadline=android.os.SystemClock.elapsedRealtime()+60000;while(main.busy&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
        check(main.subtitleTrack==previous,"SRT inválido apagou legendas anteriores");passed++;
        report("Interface: importação e preservação das legendas anteriores: OK");
        runOnMainSync(()->main.importAudio(Uri.fromFile(file("test-source.wav"))));waitImport(main);
        runOnMainSync(()->{main.options.background="video";main.bgSelect.setSelection(4);main.options.showVisualization=false;main.format.setSelection(0);main.resolution.setSelection(0);main.startExport();});
        long exportDeadline=android.os.SystemClock.elapsedRealtime()+180000;
        while((main.starting||ExportService.running)&&android.os.SystemClock.elapsedRealtime()<exportDeadline)Thread.sleep(100);
        check(!ExportService.result.isEmpty()&&new File(ExportService.result).isFile(),"Exportação pelo botão da interface falhou: "+ExportService.error);passed++;report("Editor: exportação pelo serviço com pré-visualização libertada: OK");
        if(last!=null)report("Exemplo MP4: "+last.getAbsolutePath());
    }
}
