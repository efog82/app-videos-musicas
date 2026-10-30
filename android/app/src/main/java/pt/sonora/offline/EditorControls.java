package pt.sonora.offline;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Additive photo/video background editing; the existing music/export flow remains the clock. */
final class EditorControls implements AutoCloseable {
    static final int PHOTOS=101,VIDEOS=102;
    final MainActivity m;
    private TextView summary;private Button cancel;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private MediaSequence photos,videos;
    private MediaSequence.Slides slides;
    private TextureView texture;
    private MediaPlayer videoPlayer;private boolean prepared=false,seekPending=false;
    private int playingClip=-1;private long lastSeek=-1;
    EditorControls(MainActivity activity){
        m=activity;android.content.SharedPreferences prefs=m.getPreferences(0);
        m.options.slidesPath=prefs.getString("slidesPath","");m.options.videosPath=prefs.getString("videosPath","");
        m.options.showVisualization=prefs.getBoolean("showVisualization",true);m.options.dissolve=prefs.getBoolean("dissolve",true);m.options.mediaFit=prefs.getBoolean("mediaFit",false);
        try{if(!m.options.slidesPath.isEmpty())photos=MediaSequence.load(m.options.slidesPath);}catch(Exception ignored){m.options.slidesPath="";}
        try{if(!m.options.videosPath.isEmpty())videos=MediaSequence.load(m.options.videosPath);}catch(Exception ignored){m.options.videosPath="";}
    }
    void addControls(){
        m.label("Editor · Fotografias e vídeos");
        addButton("Adicionar imagens · Seleção múltipla",()->pick(PHOTOS,"image/*"));
        addButton("Adicionar vídeos · Som original removido",()->pick(VIDEOS,"video/*"));
        summary=m.text("",13,0xffbec8e0);m.content.addView(summary);updateSummary();
        addButton("Ordenar / remover imagens",()->manage(false));addButton("Ordenar / cortar vídeos",()->manage(true));
        cancel=m.button("Cancelar importação",()->{cancelled.set(true);m.status.setText("A cancelar importação…");});cancel.setVisibility(View.GONE);
        CheckBox visualization=new CheckBox(m);visualization.setText("Mostrar visualização musical sobre o fundo");visualization.setTextColor(0xffeef2ff);visualization.setChecked(m.options.showVisualization);visualization.setOnCheckedChangeListener((v,b)->{m.options.showVisualization=b;m.refresh();});m.content.addView(visualization);m.editing.add(visualization);
        m.label("Enquadramento das imagens e vídeos");m.spinner(new String[]{"Preencher · Cortar margens","Ajustar · Mostrar imagem completa"},m.options.mediaFit?1:0,i->{m.options.mediaFit=i==1;layoutVideo();});
        m.label("Transição entre fotografias");m.spinner(new String[]{"Dissolução suave","Corte direto"},m.options.dissolve?0:1,i->m.options.dissolve=i==0);
        m.note("Sem limite fixo de quantidade de imagens: depende do espaço livre e do seletor Android. A duração de cada imagem é distribuída pela música. Os vídeos são unidos pela ordem escolhida e repetem se forem mais curtos que a música. O som dos vídeos não é usado; ouve-se a música selecionada. Podes exportar apenas imagens/vídeos com legendas, ocultando a visualização.");
    }
    private void addButton(String title,Runnable action){Button b=m.button(title,action);m.editing.add(b);}
    FrameLayout stage(){FrameLayout parent=new FrameLayout(m);parent.setBackgroundColor(android.graphics.Color.BLACK);texture=new TextureView(m);texture.setVisibility(View.GONE);parent.addView(texture,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){tick();layoutVideo();}public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){layoutVideo();}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){releaseVideo();return true;}public void onSurfaceTextureUpdated(SurfaceTexture s){}});return parent;
    }
    private void pick(int code,String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{m.startActivityForResult(i,code);}catch(Exception e){m.status.setText("Não foi possível abrir o seletor de ficheiros.");}}
    boolean handle(int code,Intent data){if(code!=PHOTOS&&code!=VIDEOS)return false;if(m.busy||ExportService.running||m.starting)return true;
        ArrayList<Uri> selected=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());
        if(!selected.isEmpty())importItems(selected,code==VIDEOS);return true;
    }
    private void importItems(ArrayList<Uri> selected,boolean isVideo){
        m.busy=true;m.setEnabled(false);cancelled.set(false);cancel.setVisibility(View.VISIBLE);m.status.setText("A importar "+selected.size()+" ficheiros…");
        final MediaSequence previousSequence=isVideo?videos:photos;
        File directory=new File(m.getFilesDir(),"media-"+System.currentTimeMillis());directory.mkdirs();
        m.worker.execute(()->{
            try{
                MediaSequence sequence=new MediaSequence(new File(directory,"sequence.json"));
                if(previousSequence!=null)sequence.items.addAll(previousSequence.items);
                for(int i=0;i<selected.size();i++){
                    checkCancelled();final int done=i;m.ui(()->m.status.setText("A importar "+(done+1)+" / "+selected.size()+"…"));Uri uri=selected.get(i);File raw=new File(directory,"source-"+i);
                    try(InputStream in=m.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(raw)){
                        if(in==null)throw new IOException("Ficheiro indisponível.");byte[] buffer=new byte[65536];int n;long count=0;
                        while((n=in.read(buffer))!=-1){checkCancelled();if(directory.getUsableSpace()<32L*1024*1024)throw new IOException("Espaço livre insuficiente.");count+=n;if(!isVideo&&count>25L*1024*1024)throw new IOException("Cada imagem deve ter até 25 MB.");out.write(buffer,0,n);}
                    }
                    MediaSequence.Item item;
                    if(isVideo){item=MediaSequence.probeVideo(raw,m.name(uri));}
                    else{Bitmap bitmap=normalImage(raw);File normalized=new File(directory,"photo-"+i+".jpg");try(OutputStream out=new FileOutputStream(normalized)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,out))throw new IOException("Falha ao guardar imagem.");}finally{bitmap.recycle();}raw.delete();item=new MediaSequence.Item();item.file=normalized.getAbsolutePath();item.name=m.name(uri);}
                    sequence.items.add(item);
                }
                sequence.save();checkCancelled();m.ui(()->{
                    if(isVideo){MediaSequence previous=videos;releaseVideo();videos=sequence;m.options.videosPath=sequence.manifest.getAbsolutePath();if(previous!=null)previous.manifest.delete();m.options.background="video";m.bgSelect.setSelection(4);}
                    else{MediaSequence previous=photos;closeSlides();photos=sequence;m.options.slidesPath=sequence.manifest.getAbsolutePath();if(previous!=null)previous.manifest.delete();m.options.background="slides";m.bgSelect.setSelection(3);}
                    persist();updateSummary();m.busy=false;cancel.setVisibility(View.GONE);m.setEnabled(true);m.status.setText(isVideo?"Vídeos prontos. O som original foi removido; escolhe a música para a banda sonora.":"Imagens prontas. Cada imagem ocupa uma parte igual da duração da música.");m.refresh();tick();
                });
            }catch(Exception e){MediaSequence.deleteTree(directory);m.ui(()->{m.busy=false;cancel.setVisibility(View.GONE);m.setEnabled(true);m.status.setText("Importação interrompida: "+e.getMessage()+" A sequência anterior foi mantida.");});}
        });
    }
    private void checkCancelled()throws IOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Importação cancelada.");}
    private Bitmap normalImage(File raw)throws Exception{
        Bitmap b=VideoExport.loadImage(raw.getAbsolutePath(),1280);int orientation=1;try{android.media.ExifInterface exif=new android.media.ExifInterface(raw.getAbsolutePath());orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
        Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.postRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setScale(-1,1);matrix.postRotate(270);break;case 6:matrix.postRotate(90);break;case 7:matrix.setScale(-1,1);matrix.postRotate(90);break;case 8:matrix.postRotate(270);break;}
        Bitmap result=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);if(result!=b)b.recycle();return result;
    }
    private boolean editable(){return !m.busy&&!ExportService.running&&!m.starting;}
    private void manage(boolean video){if(!editable())return;MediaSequence sequence=video?videos:photos;if(sequence==null){m.status.setText("Importa primeiro os ficheiros.");return;}
        String[] names=new String[sequence.items.size()];for(int i=0;i<names.length;i++)names[i]=(i+1)+" · "+sequence.items.get(i).name+(video?" · "+MainActivity.clock((int)sequence.items.get(i).length()):"");
        new AlertDialog.Builder(m).setTitle(video?"Vídeos pela ordem de reprodução":"Imagens pela ordem de apresentação").setItems(names,(dialog,index)->{
            String[] actions=video?new String[]{"Mover para cima","Mover para baixo","Cortar início / fim","Remover"}:new String[]{"Mover para cima","Mover para baixo","Remover"};
            new AlertDialog.Builder(m).setTitle(sequence.items.get(index).name).setItems(actions,(d,action)->{
                if(!editable())return;
                if(video&&action==2){trim(sequence,index);return;}
                if(action==0&&index>0)Collections.swap(sequence.items,index,index-1);
                else if(action==1&&index+1<sequence.items.size())Collections.swap(sequence.items,index,index+1);
                else if(action==(video?3:2)){sequence.items.remove(index);if(sequence.items.isEmpty()){if(video){releaseVideo();videos=null;m.options.videosPath="";}else{closeSlides();photos=null;m.options.slidesPath="";}if(m.options.background.equals(video?"video":"slides")){m.options.background="black";m.bgSelect.setSelection(0);}persist();updateSummary();m.refresh();m.status.setText("Elemento removido. Os ficheiros originais foram preservados.");return;}}
                saveSequence(sequence,video);manage(video);
            }).show();
        }).setNegativeButton("Fechar",null).show();
    }
    private void trim(MediaSequence sequence,int index){MediaSequence.Item item=sequence.items.get(index);LinearLayout box=new LinearLayout(m);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(m.dp(20),0,m.dp(20),0);
        box.addView(m.text("Início em segundos (duração original: "+MainActivity.clock((int)item.durationMs)+")",14,0xffeef2ff));EditText start=secondsInput(item.startMs);box.addView(start);box.addView(m.text("Fim em segundos",14,0xffeef2ff));EditText end=secondsInput(item.endMs);box.addView(end);
        AlertDialog dialog=new AlertDialog.Builder(m).setTitle("Cortar vídeo").setView(box).setPositiveButton("Aplicar",null).setNegativeButton("Cancelar",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{if(!editable()){dialog.dismiss();return;}try{
            long from=millis(start.getText().toString()),to=millis(end.getText().toString());if(from<0||to>item.durationMs||to-from<100)throw new IllegalArgumentException();long oldStart=item.startMs,oldEnd=item.endMs;item.startMs=from;item.endMs=to;
            try{sequence.save();releaseVideo();updateSummary();dialog.dismiss();}catch(Exception e){item.startMs=oldStart;item.endMs=oldEnd;throw e;}
        }catch(Exception e){end.setError("Usa início < fim, dentro da duração original (mínimo 0,1 s).");}}));dialog.show();
    }
    private EditText secondsInput(long ms){EditText e=new EditText(m);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setText(String.format(java.util.Locale.ROOT,"%.3f",ms/1000.0));return e;}
    private static long millis(String text){return new java.math.BigDecimal(text.trim().replace(',','.')).multiply(new java.math.BigDecimal("1000")).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();}
    private void saveSequence(MediaSequence sequence,boolean video){try{sequence.save();if(video)releaseVideo();else closeSlides();updateSummary();m.refresh();}catch(Exception e){m.status.setText("Não foi possível guardar a ordem: "+e.getMessage());}}
    private void updateSummary(){if(summary!=null)summary.setText((photos==null?"Sem sequência de imagens":photos.items.size()+" imagens · Duração automática")+"\n"+(videos==null?"Sem vídeos":videos.items.size()+" vídeos · "+MainActivity.clock((int)Math.min(Integer.MAX_VALUE,videos.totalMs()))+" por sequência · Sem som original"));}
    boolean valid(){if("slides".equals(m.options.background)&&photos==null){m.status.setText("Importa uma sequência de imagens.");return false;}if("video".equals(m.options.background)&&videos==null){m.status.setText("Importa os teus vídeos.");return false;}return true;}
    void persist(){m.getPreferences(0).edit().putString("slidesPath",m.options.slidesPath).putString("videosPath",m.options.videosPath).putBoolean("showVisualization",m.options.showVisualization).putBoolean("dissolve",m.options.dissolve).putBoolean("mediaFit",m.options.mediaFit).apply();}
    void beforeDraw(double seconds){
        if(!"slides".equals(m.options.background)||photos==null)return;
        try{if(slides==null)slides=new MediaSequence.Slides(m.options.slidesPath);long time=(long)(seconds*1000),duration=m.audio!=null?(long)(m.audio.seconds()*1000):Math.max(1000,photos.items.size()*3000L);
            Bitmap first=slides.at(time,duration);float blend=m.options.dissolve?slides.blend(time,duration):0;m.preview.renderer.setSlide(first,blend>0?slides.next():null,blend);
        }catch(Exception e){m.status.setText("Não foi possível abrir uma imagem da sequência.");}
    }
    private long musicMs(){if(m.player!=null)try{return m.player.getCurrentPosition();}catch(Exception ignored){}return m.seek!=null?m.seek.getProgress():0;}
    void suspendPreview(){releaseVideo();if(texture!=null)texture.setVisibility(View.GONE);}
    void tick(){if(texture==null)return;if(ExportService.running||m.starting){suspendPreview();return;}boolean visible="video".equals(m.options.background)&&videos!=null;texture.setVisibility(visible?View.VISIBLE:View.GONE);
        if(!visible){pause();return;}if(!texture.isAvailable())return;
        long music=musicMs();int clip=videos.videoIndex(music);long desired=videos.videoTimeMs(music);
        if(playingClip!=clip||videoPlayer==null){releaseVideo();playingClip=clip;MediaSequence.Item item=videos.items.get(clip);
            try{MediaPlayer p=new MediaPlayer();videoPlayer=p;p.setDataSource(item.file);Surface surface=new Surface(texture.getSurfaceTexture());p.setSurface(surface);surface.release();p.setVolume(0,0);
                p.setOnPreparedListener(player->{if(videoPlayer==player){prepared=true;layoutVideo();tick();}});p.setOnSeekCompleteListener(player->seekPending=false);p.setOnErrorListener((player,what,extra)->{prepared=false;m.status.setText("O Android não conseguiu pré-visualizar este vídeo. Experimenta outro ficheiro.");return true;});p.prepareAsync();
            }catch(Exception e){releaseVideo();m.status.setText("Não foi possível abrir o vídeo: "+e.getMessage());}return;
        }
        if(!prepared)return;
        try{boolean playing=m.player!=null&&m.player.isPlaying();int position=videoPlayer.getCurrentPosition();
            if(!seekPending&&(Math.abs(position-desired)>180||(!playing&&Math.abs(desired-lastSeek)>40))){seekPending=true;lastSeek=desired;videoPlayer.seekTo(desired,MediaPlayer.SEEK_CLOSEST);}
            if(playing&&!videoPlayer.isPlaying())videoPlayer.start();else if(!playing&&videoPlayer.isPlaying())videoPlayer.pause();
        }catch(Exception ignored){}
    }
    void layoutVideo(){if(texture==null||m.preview==null||m.preview.getWidth()==0)return;int w=m.preview.getWidth(),h=m.preview.getHeight();float ratio=m.options.width/(float)m.options.height;int vw=w,vh=(int)(vw/ratio);if(vh>h){vh=h;vw=(int)(vh*ratio);}texture.setLayoutParams(new FrameLayout.LayoutParams(vw,vh,Gravity.CENTER));
        if(videos==null||playingClip<0||playingClip>=videos.items.size())return;MediaSequence.Item item=videos.items.get(playingClip);float sw=item.rotation%180==0?item.width:item.height,sh=item.rotation%180==0?item.height:item.width;
        float source=sw/sh,dest=vw/(float)Math.max(1,vh),sx=1,sy=1;
        if(m.options.mediaFit){if(source>dest)sy=dest/source;else sx=source/dest;}else{if(source>dest)sx=source/dest;else sy=dest/source;}
        Matrix matrix=new Matrix();matrix.setScale(sx,sy,vw/2f,vh/2f);texture.setTransform(matrix);
    }
    void pause(){if(videoPlayer!=null&&prepared)try{videoPlayer.pause();}catch(Exception ignored){}}
    private void releaseVideo(){prepared=false;seekPending=false;playingClip=-1;lastSeek=-1;if(videoPlayer!=null){videoPlayer.release();videoPlayer=null;}}
    private void closeSlides(){if(slides!=null){slides.close();slides=null;}}
    public void close(){cancelled.set(true);releaseVideo();closeSlides();}
}
