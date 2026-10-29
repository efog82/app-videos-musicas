package pt.sonora.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    Uri audio, image;
    File result;
    TextView status, musicLabel, imageLabel;
    EditText title;
    Spinner format;
    Button generate, save, chooseAudio, chooseImage;
    boolean busy;
    int pad = 24;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9,13,25));
        getWindow().setNavigationBarColor(Color.rgb(9,13,25));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad,48,pad,48);
        layout.setBackgroundColor(Color.rgb(9,13,25));
        scroll.addView(layout); setContentView(scroll);
        text(layout,"♫ sonora",30,Color.rgb(183,164,255));
        text(layout,"A tua música,\nem vídeo.",34,Color.WHITE);
        text(layout,"Cria MP4 no teu Android, sem conta e sem internet. Até 5 minutos por música.",16,Color.LTGRAY);
        chooseAudio=button(layout,"1 · Escolher música", v -> pick(10,"audio/*"));
        musicLabel=text(layout,"Nenhuma música selecionada",14,Color.LTGRAY);
        title=new EditText(this); title.setSingleLine(true); title.setTextColor(Color.WHITE); title.setHintTextColor(Color.GRAY); title.setHint("Título da música (opcional)");
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(42)});layout.addView(title);
        chooseImage=button(layout,"2 · Imagem de fundo (opcional)",v -> pick(11,"image/*"));
        imageLabel=text(layout,"Sem imagem: usa fundo escuro",14,Color.LTGRAY);
        format=new Spinner(this);
        format.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Vertical · 720 × 1280","Horizontal · 1280 × 720"}));layout.addView(format);
        generate=button(layout,"3 · Gerar vídeo MP4", v -> start());
        status=text(layout,"Os teus ficheiros ficam no telemóvel. Durante a conversão, mantém esta aplicação aberta.",15,Color.LTGRAY);
        save=button(layout,"Guardar vídeo…",v -> save());save.setEnabled(false);
        text(layout,"Versão experimental · Sem marca de água\nUsa conteúdos que tens autorização para utilizar.",12,Color.GRAY);
    }
    TextView text(LinearLayout box,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,12,0,16);box.addView(t);return t;}
    Button button(LinearLayout box,String label,View.OnClickListener listener){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(listener);box.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    void pick(int code,String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType(type);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    @Override protected void onActivityResult(int request,int response,Intent data){super.onActivityResult(request,response,data);if(response!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==10){audio=uri;musicLabel.setText("Música selecionada ✓");}else if(request==11){image=uri;imageLabel.setText("Imagem selecionada ✓");}else if(request==12&&result!=null){new Thread(()->{try(InputStream in=new FileInputStream(result);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->status.setText("Vídeo guardado! Abre-o na pasta que escolheste."));}catch(Exception e){runOnUiThread(()->status.setText("Não foi possível guardar. Tenta outra pasta."));}}).start();}}
    void save(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("video/mp4");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"sonora-"+System.currentTimeMillis()+".mp4");startActivityForResult(i,12);}
    void enabled(boolean on){chooseAudio.setEnabled(on);chooseImage.setEnabled(on);generate.setEnabled(on);format.setEnabled(on);title.setEnabled(on);}
    void start(){if(busy)return;if(audio==null){status.setText("Escolhe primeiro uma música.");return;}busy=true;enabled(false);save.setEnabled(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);String label=title.getText().toString();boolean vertical=format.getSelectedItemPosition()==0;Uri a=audio,img=image;new Thread(()->{try{File output=new File(getCacheDir(),"sonora.mp4");new Converter(this).convert(a,img,label,vertical,output,message->runOnUiThread(()->status.setText(message)));result=output;runOnUiThread(()->{status.setText("O teu vídeo está pronto! Toca em Guardar vídeo para escolher a pasta.");save.setEnabled(true);});}catch(Exception e){android.util.Log.e("Sonora","Conversion failed",e);runOnUiThread(()->status.setText("Não foi possível criar o vídeo: "+e.getMessage()));}finally{runOnUiThread(()->{busy=false;enabled(true);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}}).start();}
    @Override public void onBackPressed(){if(busy){Toast.makeText(this,"Aguarda o fim da conversão.",Toast.LENGTH_SHORT).show();return;}super.onBackPressed();}
}
