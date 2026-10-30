package pt.sonora.offline;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int AUDIO=1,IMAGE=2,SAVE=3,SRT=4;
    final Options options=new Options();
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ArrayList<View> editing=new ArrayList<>();
    MediaIo.Audio audio;AudioAnalysis analysis;MediaPlayer player;
    Uri audioUri;String audioName="",outputPath="";
    LinearLayout root,content,styleGrid;TextView audioLabel,status,timeLabel,subtitleLabel;EditText title,subtitleOffset;
    SubtitleTrack subtitleTrack;String subtitleName="";CheckBox showSubtitles;
    Button chooseAudio,play,export,save,share,open,cancel;SeekBar seek;Spinner format,resolution,bgSelect;
    Preview preview;FrameLayout previewStage;EditorControls editor;volatile boolean busy=false,destroyed=false,seeking=false,starting=false,updatingSeek=false;
    String lastError="";Bitmap imageBitmap;
    final Runnable tick=new Runnable(){public void run(){
        if(destroyed)return;
        if(starting&&(!ExportService.result.isEmpty()||!ExportService.error.isEmpty()))starting=false;
        if(ExportService.running){starting=false;status.setText(ExportService.phase+" · "+ExportService.percent+"%");setEnabled(false);cancel.setVisibility(View.VISIBLE);}
        else if(!starting){
            cancel.setVisibility(View.GONE);setEnabled(!busy);
            if(!ExportService.result.isEmpty()&&!ExportService.result.equals(outputPath)){
                outputPath=ExportService.result;getPreferences(0).edit().putString("output",outputPath).apply();status.setText("Vídeo pronto. Guarda o MP4 ou partilha-o.");updateOutput();
            }
            if(!ExportService.error.isEmpty()&&!ExportService.error.equals(lastError)){lastError=ExportService.error;status.setText(lastError);}
        }
        if(player!=null&&audio!=null&&!seeking){try{
            int pos=player.getCurrentPosition();updatingSeek=true;seek.setProgress(pos);updatingSeek=false;
            timeLabel.setText(clock(pos)+" / "+clock((int)(audio.seconds()*1000)));play.setText(player.isPlaying()?"Pausar pré-visualização":"Ouvir e pré-visualizar ▶");
        }catch(IllegalStateException ignored){}}
        if(editor!=null)editor.tick();handler.postDelayed(this,100);
    }};
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String value,int sp,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(6),0,dp(6));return v;}
    GradientDrawable panel(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));d.setStroke(dp(1),0xff354361);return d;}
    void label(String value){content.addView(text(value,16,0xffe9edff));}
    void note(String value){content.addView(text(value,12,0xffbec8e0));}
    void gap(){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(16)));}
    Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(0xff0c1830);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff86c6ff));b.setOnClickListener(v->action.run());content.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;}
    Spinner spinner(String[] values,int selected,java.util.function.IntConsumer change){
        Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(selected);
        content.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));editing.add(s);
        s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){change.accept(pos);refresh();}});return s;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        android.content.SharedPreferences prefs=getPreferences(0);
        options.title=prefs.getString("title","");options.showTitle=prefs.getBoolean("showTitle",true);
        options.enhancedEffects=prefs.getBoolean("enhancedEffects",true);
        options.showSubtitles=prefs.getBoolean("showSubtitles",true);options.subtitleOffsetMs=prefs.getLong("subtitleOffsetMs",0);
        options.subtitleSize=prefs.getInt("subtitleSize",1);options.subtitlePosition=prefs.getInt("subtitlePosition",0);
        options.subtitlePath=prefs.getString("subtitlePath","");subtitleName=prefs.getString("subtitleName","");
        options.style=prefs.getInt("style",0);options.color=prefs.getInt("color",0xff339dff);
        options.intensity=prefs.getFloat("intensity",1);options.speed=prefs.getFloat("speed",1);options.full=prefs.getBoolean("full",false);
        options.background=prefs.getString("bg","black");options.imagePath=new File(getFilesDir(),"background.image").getAbsolutePath();
        editor=new EditorControls(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff090d19);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(18),dp(20),dp(32));scroll.addView(content);setContentView(root);
        content.addView(text("♫ sonora",32,0xff86c6ff));content.addView(text("O teu som, em movimento.",20,0xfff1f3ff));
        note("100% offline · Sem conta · Sem marca de água\nÁudio e imagens ficam no teu dispositivo.");gap();
        label("01 · Escolher música");chooseAudio=button("Escolher ficheiro de áudio",()->pick(AUDIO,"audio/*"));editing.add(chooseAudio);
        audioLabel=text("MP3, M4A, WAV e outros formatos suportados pelo Android. Até 10 minutos.",13,0xffbec8e0);content.addView(audioLabel);gap();
        label("02 · Título (opcional)");title=new EditText(this);title.setSingleLine(true);title.setTextColor(0xfff3f5ff);title.setHintTextColor(0xffa8b9d5);title.setHint("Sem título, o vídeo não apresenta texto");title.setText(options.title);title.setTextSize(15);title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});content.addView(title);editing.add(title);
        title.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){options.title=s.toString();refresh();}public void afterTextChanged(Editable e){}});
        CheckBox show=new CheckBox(this);show.setText("Mostrar título no vídeo");show.setTextColor(0xffeef2ff);show.setChecked(options.showTitle);show.setOnCheckedChangeListener((v,checked)->{options.showTitle=checked;refresh();});content.addView(show);editing.add(show);gap();
        label("Legendas · Ficheiro SRT (opcional)");
        Button importSrt=button("Importar legendas .srt",()->pick(SRT,"*/*"));editing.add(importSrt);
        subtitleLabel=text("Sem ficheiro SRT",13,0xffbec8e0);content.addView(subtitleLabel);
        showSubtitles=new CheckBox(this);showSubtitles.setText("Mostrar legendas na pré-visualização e no MP4");showSubtitles.setTextColor(0xffeef2ff);showSubtitles.setChecked(options.showSubtitles);
        showSubtitles.setOnCheckedChangeListener((v,checked)->{options.showSubtitles=checked;refresh();});content.addView(showSubtitles);editing.add(showSubtitles);
        label("Ajuste de sincronização (segundos)");subtitleOffset=new EditText(this);subtitleOffset.setSingleLine(true);subtitleOffset.setTextColor(0xfff3f5ff);subtitleOffset.setHintTextColor(0xffa8b9d5);
        subtitleOffset.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        subtitleOffset.setText(String.format(java.util.Locale.ROOT,"%.3f",options.subtitleOffsetMs/1000.0));content.addView(subtitleOffset);editing.add(subtitleOffset);
        subtitleOffset.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int b,int c){}public void onTextChanged(CharSequence t,int a,int b,int c){validateSubtitleOffset();refresh();}public void afterTextChanged(Editable e){}});
        note("Positivo atrasa as legendas; negativo adianta. Os tempos do SRT seguem a música, independentemente da velocidade dos efeitos. Não é feita transcrição automática.");
        label("Tamanho das legendas");spinner(new String[]{"Pequenas","Médias","Grandes"},options.subtitleSize,i->options.subtitleSize=i);
        label("Posição das legendas");spinner(new String[]{"Em baixo · Zona segura","Ao centro","Em cima"},options.subtitlePosition,i->options.subtitlePosition=i);
        Button removeSrt=button("Remover legendas",()->removeSubtitles());editing.add(removeSrt);gap();
        label("03 · Escolher visualização");note("Miniaturas ilustrativas. A pré-visualização abaixo reage à tua música.");
        for(int row=0;row<4;row++){
            LinearLayout line=new LinearLayout(this);content.addView(line);
            for(int col=0;col<2;col++){int style=row*2+col;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setBackground(panel(0xff142038));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(110),1);cp.setMargins(dp(3),dp(3),dp(3),dp(3));line.addView(card,cp);
                ImageView thumb=new ImageView(this);Bitmap bitmap=Bitmap.createBitmap(240,110,Bitmap.Config.ARGB_8888);AudioAnalysis.Frame f=new AudioAnalysis.Frame();f.energy=.5f;Arrays.fill(f.bands,.5f);
                for(int i=0;i<48;i++)f.levels[i]=.2f+.6f*(float)Math.abs(Math.sin(i*.8));for(int i=0;i<256;i++)f.wave[i]=(float)Math.sin(i*.1)*.6f;
                Options o=options.copy();o.style=style;o.title="";o.background="black";o.showVisualization=true;new SceneRenderer().draw(new Canvas(bitmap),240,110,o,f,2);thumb.setImageBitmap(bitmap);card.addView(thumb,new LinearLayout.LayoutParams(-1,dp(54)));
                TextView name=text(Options.NAMES[style],12,0xffeaf2ff);name.setGravity(Gravity.CENTER);card.addView(name);card.setContentDescription(Options.NAMES[style]);card.setFocusable(true);card.setClickable(true);
                card.setOnClickListener(v->{options.style=style;selectStyle();refresh();});card.setTag(style);editing.add(card);
            }
        }selectStyle();gap();
        label("Acabamento dos efeitos");spinner(new String[]{"Detalhado · Brilho e profundidade","Leve · Aspeto original"},options.enhancedEffects?0:1,i->options.enhancedEffects=i==0);
        label("Cor");spinner(new String[]{"Azul elétrico","Violeta","Ciano","Rosa","Dourado","Verde"},colorIndex(),i->options.color=new int[]{0xff339dff,0xffae8cff,0xff00e5ff,0xffff65bf,0xffffca55,0xff55e8ad}[i]);
        slider("Intensidade",options.intensity,v->options.intensity=v);slider("Velocidade",options.speed,v->options.speed=v);
        label("Fundo");bgSelect=spinner(new String[]{"Preto","Imagem personalizada","Animado","Sequência de imagens","Os meus vídeos"},"image".equals(options.background)?1:"animated".equals(options.background)?2:"slides".equals(options.background)?3:"video".equals(options.background)?4:0,i->options.background=new String[]{"black","image","animated","slides","video"}[i]);
        Button image=button("Escolher imagem de fundo",()->pick(IMAGE,"image/*"));editing.add(image);
        editor.addControls();
        label("Composição");spinner(new String[]{"Centralizada","Ocupar todo o ecrã"},options.full?1:0,i->options.full=i==1);
        gap();label("04 · Formato e qualidade");
        format=spinner(new String[]{"Horizontal · 16:9","Vertical · 9:16","Quadrado · 1:1"},prefs.getInt("format",0),i->updateSize());
        resolution=spinner(new String[]{"720p · Recomendado","1080p · Se suportado pelo dispositivo"},prefs.getInt("resolution",0),i->updateSize());
        note("24 imagens por segundo. Efeitos otimizados e ampliados para a resolução escolhida. A exportação pode demorar e continua com o ecrã desligado.");gap();
        label("Pré-visualização com áudio real");previewStage=editor.stage();preview=new Preview();previewStage.addView(preview,new FrameLayout.LayoutParams(-1,-1));content.addView(previewStage,new LinearLayout.LayoutParams(-1,dp(230)));
        timeLabel=text("00:00 / 00:00",13,0xffc8d6ed);content.addView(timeLabel);seek=new SeekBar(this);content.addView(seek);editing.add(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){seeking=true;}public void onStopTrackingTouch(SeekBar s){seeking=false;if(player!=null)player.seekTo(s.getProgress());}public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&audio!=null){timeLabel.setText(clock(value)+" / "+clock((int)(audio.seconds()*1000)));preview.invalidate();}}});
        play=button("Ouvir e pré-visualizar ▶",()->togglePlay());editing.add(play);gap();
        export=button("Gerar vídeo MP4",()->startExport());editing.add(export);
        cancel=button("Cancelar exportação",()->{ExportService.cancelled=true;status.setText("A cancelar…");});cancel.setVisibility(View.GONE);
        status=text("Escolhe uma música para começar.",14,0xffcbdfff);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(status);
        open=button("Reproduzir MP4 gerado",()->openVideo());save=button("Guardar MP4 no dispositivo",()->saveVideo());share=button("Partilhar vídeo",()->shareVideo());
        note("Sonora Offline · Versão de teste 0.3 · Editor offline\nUsa músicas e imagens que tens autorização para utilizar.");
        outputPath=prefs.getString("output","");updateOutput();restoreAudio();updateSize();loadSavedImage();restoreSubtitles();
    }
    int colorIndex(){int[] colors={0xff339dff,0xffae8cff,0xff00e5ff,0xffff65bf,0xffffca55,0xff55e8ad};for(int i=0;i<colors.length;i++)if(options.color==colors[i])return i;return 0;}
    void slider(String name,float initial,java.util.function.Consumer<Float> action){
        TextView label=text(name+String.format(java.util.Locale.ROOT," · %.2f×",initial),15,0xffeaf1ff);content.addView(label);SeekBar s=new SeekBar(this);s.setMax(175);s.setProgress((int)((initial-.25f)*100));content.addView(s);editing.add(s);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){float v=.25f+value/100f;label.setText(name+String.format(java.util.Locale.ROOT," · %.2f×",v));action.accept(v);refresh();}});
    }
    void selectStyle(){for(View v:editing)if(v.getTag() instanceof Integer){boolean selected=(Integer)v.getTag()==options.style;v.setBackground(panel(selected?0xff254d76:0xff142038));v.setSelected(selected);}}
    void updateSize(){if(format==null||resolution==null)return;int edge=resolution.getSelectedItemPosition()==1?1080:720;int shape=format.getSelectedItemPosition();options.width=shape==1?edge:shape==2?edge:edge*16/9;options.height=shape==0?edge:shape==2?edge:edge*16/9;
        if(preview!=null){int available=getResources().getDisplayMetrics().widthPixels-dp(40);int height=Math.min(dp(420),available*options.height/options.width);previewStage.setLayoutParams(new LinearLayout.LayoutParams(-1,height));editor.layoutVideo();preview.invalidate();}}
    void refresh(){if(preview!=null)preview.invalidate();}
    void setEnabled(boolean enabled){for(View v:editing)v.setEnabled(enabled);if(export!=null)export.setEnabled(enabled&&audio!=null);if(play!=null)play.setEnabled(enabled&&player!=null);}
    void pick(int code,String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,code);}catch(ActivityNotFoundException e){status.setText("Não existe um seletor de ficheiros instalado neste dispositivo.");}}
    String name(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "A tua música";}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result==RESULT_OK&&data!=null&&editor.handle(request,data))return;if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==AUDIO){try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}importAudio(uri);}
        else if(request==IMAGE)importImage(uri);
        else if(request==SRT)importSubtitles(uri);
        else if(request==SAVE){File source=new File(outputPath);status.setText("A guardar…");worker.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("Destino indisponível.");copy(in,out,Long.MAX_VALUE);ui(()->status.setText("MP4 guardado no destino escolhido."));}catch(Exception e){ui(()->status.setText("Não foi possível guardar: "+e.getMessage()));}});}
    }
    void ui(Runnable r){handler.post(()->{if(!destroyed)r.run();});}
    void releasePlayer(){if(player!=null){player.release();player=null;}}
    void importAudio(Uri uri){
        if(busy||ExportService.running)return;busy=true;setEnabled(false);releasePlayer();
        String display=name(uri);status.setText("A preparar análise offline…");
        File target=new File(getFilesDir(),"audio-"+System.currentTimeMillis()+".pcm");
        worker.execute(()->{try{
            if(getFilesDir().getUsableSpace()<250L*1024*1024)throw new IOException("Liberta pelo menos 250 MB para importar e exportar.");
            MediaIo.Audio decoded=MediaIo.decode(this,uri,target,(phase,pct)->ui(()->status.setText(phase+" · "+pct+"%")));
            AudioAnalysis next=new AudioAnalysis(target,decoded.rate,decoded.channels);
            ui(()->{if(analysis!=null)try{analysis.close();}catch(Exception ignored){}if(audio!=null&&!audio.path.equals(decoded.path))new File(audio.path).delete();analysis=next;audio=decoded;audioUri=uri;audioName=display;
                getPreferences(0).edit().putString("pcm",audio.path).putInt("rate",audio.rate).putInt("channels",audio.channels).putLong("frames",audio.frames).putString("uri",uri.toString()).putString("audioName",audioName).apply();
                busy=false;audioLabel.setText(audioName+" · "+clock((int)(audio.seconds()*1000)));seek.setMax((int)(audio.seconds()*1000));seek.setProgress(0);status.setText("Música pronta. Escolhe o efeito e ouve a pré-visualização.");preparePlayer();setEnabled(true);refresh();
            });
        }catch(Exception e){target.delete();ui(()->{busy=false;status.setText("Não foi possível importar: "+e.getMessage());if(audio!=null)preparePlayer();setEnabled(true);});}});
    }
    void restoreAudio(){android.content.SharedPreferences p=getPreferences(0);String file=p.getString("pcm","");if(!new File(file).isFile())return;
        try{audio=new MediaIo.Audio();audio.path=file;audio.rate=p.getInt("rate",0);audio.channels=p.getInt("channels",0);audio.frames=p.getLong("frames",0);if(audio.rate<=0||audio.channels<=0)throw new IOException();
            analysis=new AudioAnalysis(new File(file),audio.rate,audio.channels);audioUri=Uri.parse(p.getString("uri",""));audioName=p.getString("audioName","");audioLabel.setText(audioName);seek.setMax((int)(audio.seconds()*1000));status.setText("Música recuperada. Tudo é processado offline.");preparePlayer();
        }catch(Exception e){audio=null;status.setText("Volta a escolher a música para ouvir a pré-visualização.");}}
    void preparePlayer(){releasePlayer();try{
        MediaPlayer m=new MediaPlayer();player=m;m.setDataSource(this,audioUri);m.setOnPreparedListener(p->{if(!destroyed&&player==p){play.setEnabled(!busy&&!ExportService.running);}});m.setOnSeekCompleteListener(p->preview.invalidate());m.setOnCompletionListener(p->{play.setText("Ouvir e pré-visualizar ▶");preview.invalidate();});m.setOnErrorListener((p,what,extra)->{ui(()->{play.setEnabled(false);status.setText("Volta a escolher o ficheiro para ouvir a pré-visualização. A exportação offline continua disponível.");});return true;});m.prepareAsync();
    }catch(Exception e){releasePlayer();status.setText("A pré-visualização não conseguiu abrir o áudio original. Volta a selecionar a música.");}}
    void togglePlay(){if(player==null)return;try{if(player.isPlaying())player.pause();else{if(player.getCurrentPosition()>=player.getDuration()-50)player.seekTo(0);player.start();}preview.invalidate();}catch(Exception e){status.setText("Aguarda a preparação do áudio ou volta a escolhê-lo.");}}
    void importImage(Uri uri){busy=true;setEnabled(false);File dest=new File(getFilesDir(),"new-background.image");
        worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)){
            if(in==null)throw new IOException("Imagem indisponível.");copy(in,out,25L*1024*1024);out.flush();Bitmap b=VideoExport.loadImage(dest.getAbsolutePath(),1280);
            // Apply EXIF orientation once, keeping the preview and export identical.
            int orient=1;try{android.media.ExifInterface exif=new android.media.ExifInterface(dest.getAbsolutePath());orient=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            Matrix matrix=new Matrix();if(orient==3)matrix.postRotate(180);else if(orient==6)matrix.postRotate(90);else if(orient==8)matrix.postRotate(270);else if(orient==2)matrix.setScale(-1,1);else if(orient==4)matrix.setScale(1,-1);else if(orient==5){matrix.setScale(-1,1);matrix.postRotate(270);}else if(orient==7){matrix.setScale(-1,1);matrix.postRotate(90);}
            Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);if(rotated!=b)b.recycle();
            File stored=new File(options.imagePath);try(OutputStream imageOut=new FileOutputStream(stored)){rotated.compress(Bitmap.CompressFormat.JPEG,92,imageOut);}dest.delete();
            ui(()->{if(imageBitmap!=null)imageBitmap.recycle();imageBitmap=rotated;preview.renderer.setBackground(imageBitmap);options.background="image";bgSelect.setSelection(1);busy=false;setEnabled(true);status.setText("Imagem pronta.");refresh();});
        }catch(Exception e){dest.delete();ui(()->{busy=false;setEnabled(true);status.setText("Imagem inválida: "+e.getMessage());});}});
    }
    boolean validateSubtitleOffset(){
        if(subtitleOffset==null)return true;
        String value=subtitleOffset.getText().toString().trim().replace(',','.');
        try{
            java.math.BigDecimal seconds=value.isEmpty()?java.math.BigDecimal.ZERO:new java.math.BigDecimal(value);
            if(seconds.abs().compareTo(new java.math.BigDecimal("600"))>0)throw new NumberFormatException();
            options.subtitleOffsetMs=seconds.multiply(new java.math.BigDecimal("1000")).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();subtitleOffset.setError(null);return true;
        }catch(ArithmeticException|NumberFormatException e){subtitleOffset.setError("Usa um valor entre -600 e 600 segundos.");return false;}
    }
    void subtitleSummary(){
        if(subtitleTrack==null){subtitleLabel.setText("Sem ficheiro SRT");return;}
        subtitleLabel.setText(subtitleName+" · "+subtitleTrack.size()+" legendas · "+clock((int)Math.min(Integer.MAX_VALUE,subtitleTrack.lastEndMs())));
    }
    void persistSubtitles(){getPreferences(0).edit().putString("subtitlePath",options.subtitlePath).putString("subtitleName",subtitleName).putBoolean("showSubtitles",options.showSubtitles).apply();}
    void restoreSubtitles(){
        if(options.subtitlePath.isEmpty())return;
        try{subtitleTrack=SubtitleTrack.load(new File(options.subtitlePath));preview.renderer.setSubtitles(subtitleTrack);subtitleSummary();}
        catch(IOException e){options.subtitlePath="";subtitleTrack=null;subtitleName="";persistSubtitles();subtitleLabel.setText("Volta a importar o SRT: "+e.getMessage());}
    }
    void importSubtitles(Uri uri){
        if(busy||ExportService.running||starting)return;
        busy=true;setEnabled(false);String display=name(uri);status.setText("A ler os tempos do SRT…");
        File temp=new File(getCacheDir(),"import-subtitles.srt");
        worker.execute(()->{
            try{
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IOException("Ficheiro indisponível.");copy(in,out,SubtitleTrack.MAX_BYTES);
                }
                SubtitleTrack track=SubtitleTrack.load(temp);
                File target=new File(getFilesDir(),"subtitles.srt");android.util.AtomicFile stored=new android.util.AtomicFile(target);
                FileOutputStream out=null;
                try(InputStream in=new FileInputStream(temp)){
                    out=stored.startWrite();copy(in,out,SubtitleTrack.MAX_BYTES);stored.finishWrite(out);out=null;
                }catch(IOException e){if(out!=null)stored.failWrite(out);throw e;}
                ui(()->{subtitleTrack=track;subtitleName=display;options.subtitlePath=target.getAbsolutePath();options.showSubtitles=true;showSubtitles.setChecked(true);
                    preview.renderer.setSubtitles(track);subtitleSummary();persistSubtitles();busy=false;setEnabled(true);
                    status.setText("SRT pronto. Ouve a música e ajusta a sincronização se necessário."+(audio!=null&&track.firstStartMs()>=audio.seconds()*1000?" Atenção: todas as legendas começam depois do fim desta música.":""));refresh();});
            }catch(Exception e){ui(()->{busy=false;setEnabled(true);status.setText("Não foi possível importar o SRT: "+e.getMessage()+" As legendas anteriores foram mantidas.");});}
            finally{temp.delete();}
        });
    }
    void removeSubtitles(){
        if(busy||ExportService.running||starting)return;
        if(!options.subtitlePath.isEmpty())new File(options.subtitlePath).delete();
        options.subtitlePath="";subtitleTrack=null;subtitleName="";preview.renderer.setSubtitles(null);subtitleSummary();persistSubtitles();status.setText("Legendas removidas.");refresh();
    }
    void loadSavedImage(){if(!new File(options.imagePath).isFile())return;try{imageBitmap=VideoExport.loadImage(options.imagePath,1280);preview.renderer.setBackground(imageBitmap);}catch(Exception ignored){}}
    static void copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] buffer=new byte[65536];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("O ficheiro é demasiado grande.");out.write(buffer,0,n);}}
    void startExport(){
        if(audio==null||busy||ExportService.running||starting)return;updateSize();
        if(!editor.valid())return;
        if(!validateSubtitleOffset()){status.setText("Corrige o ajuste de sincronização das legendas.");return;}
        if("image".equals(options.background)&&!new File(options.imagePath).isFile()){status.setText("Escolhe uma imagem ou muda o fundo para preto ou animado.");return;}
        if(VideoExport.encoderName(options.width,options.height)==null){status.setText("Este dispositivo não suporta o formato escolhido. Experimenta 720p ou outro formato.");return;}
        if(player!=null)try{player.pause();}catch(Exception ignored){}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
        Intent intent=new Intent(this,ExportService.class);intent.putExtra("audio",audio);intent.putExtra("options",options.copy());
        editor.suspendPreview();ExportService.result="";ExportService.error="";lastError="";starting=true;setEnabled(false);status.setText("A iniciar exportação offline…");
        try{startForegroundService(intent);}catch(Exception e){starting=false;status.setText("Não foi possível iniciar: "+e.getMessage());setEnabled(true);}
    }
    void updateOutput(){boolean ready=!outputPath.isEmpty()&&new File(outputPath).isFile();open.setVisibility(ready?View.VISIBLE:View.GONE);save.setVisibility(ready?View.VISIBLE:View.GONE);share.setVisibility(ready?View.VISIBLE:View.GONE);}
    Uri videoUri(){return Uri.parse("content://pt.sonora.offline.videos/"+new File(outputPath).getName());}
    void openVideo(){Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(videoUri(),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(Exception e){status.setText("Não existe um leitor de vídeo instalado. Guarda o MP4 para o abrir noutra aplicação.");}}
    void shareVideo(){Intent i=new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM,videoUri()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Vídeo Sonora",videoUri()));try{startActivity(Intent.createChooser(i,"Partilhar MP4"));}catch(Exception e){status.setText("Não existe uma aplicação disponível para partilhar.");}}
    void saveVideo(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("video/mp4").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,new File(outputPath).getName());try{startActivityForResult(i,SAVE);}catch(Exception e){status.setText("Não existe um seletor de destino instalado.");}}
    static String clock(int ms){int seconds=ms/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    @Override protected void onStart(){super.onStart();handler.post(tick);}
    @Override protected void onStop(){super.onStop();handler.removeCallbacks(tick);if(editor!=null)editor.pause();if(player!=null)try{player.pause();}catch(Exception ignored){}saveOptions();}
    void saveOptions(){if(editor!=null)editor.persist();getPreferences(0).edit().putBoolean("enhancedEffects",options.enhancedEffects).putBoolean("showSubtitles",options.showSubtitles).putLong("subtitleOffsetMs",options.subtitleOffsetMs).putInt("subtitleSize",options.subtitleSize).putInt("subtitlePosition",options.subtitlePosition).putString("subtitlePath",options.subtitlePath).putString("subtitleName",subtitleName).putString("title",options.title).putBoolean("showTitle",options.showTitle).putInt("style",options.style).putInt("color",options.color).putFloat("intensity",options.intensity).putFloat("speed",options.speed).putBoolean("full",options.full).putString("bg",options.background).putInt("format",format.getSelectedItemPosition()).putInt("resolution",resolution.getSelectedItemPosition()).apply();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(editor!=null)editor.close();releasePlayer();worker.shutdownNow();if(analysis!=null)try{analysis.close();}catch(Exception ignored){}if(imageBitmap!=null)imageBitmap.recycle();super.onDestroy();}
    final class Preview extends View {
        final SceneRenderer renderer=new SceneRenderer();final AudioAnalysis.Frame empty=new AudioAnalysis.Frame();
        Preview(){super(MainActivity.this);setContentDescription("Pré-visualização de "+Options.NAMES[options.style]);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);if(editor!=null)editor.layoutVideo();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);double seconds=seek!=null?seek.getProgress()/1000.0:0;
            if(player!=null&&!seeking)try{seconds=player.getCurrentPosition()/1000.0;}catch(Exception ignored){}
            if(editor!=null)editor.beforeDraw(seconds);if("image".equals(options.background))renderer.setBackground(imageBitmap);
            AudioAnalysis.Frame f=empty;if(analysis!=null)try{f=analysis.at(seconds);}catch(IOException ignored){}
            float ratio=options.width/(float)options.height;int vw=getWidth(),vh=(int)(vw/ratio);if(vh>getHeight()){vh=getHeight();vw=(int)(vh*ratio);}
            canvas.drawColor(0xff10182b);canvas.save();canvas.translate((getWidth()-vw)/2f,(getHeight()-vh)/2f);canvas.clipRect(0,0,vw,vh);renderer.draw(canvas,vw,vh,options,f,seconds);canvas.restore();
            if(player!=null)try{if(player.isPlaying())postInvalidateDelayed(33);}catch(Exception ignored){}
        }
    }
}
