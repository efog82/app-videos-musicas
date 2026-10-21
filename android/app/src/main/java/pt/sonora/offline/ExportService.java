package pt.sonora.offline;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;

public class ExportService extends Service {
    static volatile boolean running=false,cancelled=false;
    static volatile int percent=0;
    static volatile String phase="",error="",result="";
    private static final String CHANNEL="sonora_export";
    private long lastNotification;
    private Notification note(String text,int value){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent cancel=new Intent(this,ExportService.class).setAction("cancel");
        PendingIntent stop=PendingIntent.getService(this,1,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setContentTitle("Sonora · Exportação offline").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pending).setOngoing(true).setProgress(100,value,false)
            .addAction(new Notification.Action.Builder(null,"Cancelar",stop).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"cancel".equals(intent.getAction())){cancelled=true;if(!running)stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Exportação de vídeos",NotificationManager.IMPORTANCE_LOW));
        Notification notification=note("A iniciar…",0);
        if(Build.VERSION.SDK_INT>=35)startForeground(10,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        else if(Build.VERSION.SDK_INT>=29)startForeground(10,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(10,notification);
        running=true;cancelled=false;percent=0;phase="A iniciar";error="";result="";
        if(intent==null){running=false;stopForeground(true);stopSelf();return START_NOT_STICKY;}
        MediaIo.Audio audio=(MediaIo.Audio)intent.getSerializableExtra("audio");Options opts=(Options)intent.getSerializableExtra("options");
        new Thread(()->{
            PowerManager.WakeLock wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Sonora:export");
            wake.acquire(2*60*60*1000L);
            File video=new File(getFilesDir(),"sonora-"+System.currentTimeMillis()+".mp4");
            File temp=new File(getCacheDir(),"export-audio.mp4");
            try{
                if(audio==null||opts==null)throw new IOException("Volta a selecionar a música.");
                VideoExport.render(audio,opts,video,temp,(text,value)->{
                    if(cancelled)throw new InterruptedIOException("Exportação cancelada.");
                    phase=text;percent=value;
                    if(SystemClock.elapsedRealtime()-lastNotification>1000){lastNotification=SystemClock.elapsedRealtime();nm.notify(10,note(text+" · "+value+"%",value));}
                });
                result=video.getAbsolutePath();phase="Vídeo pronto";percent=100;
            }catch(Exception e){video.delete();temp.delete();error=e.getMessage()==null?"Não foi possível exportar. Experimenta 720p.":e.getMessage();phase="Exportação interrompida";android.util.Log.e("Sonora",error,e);}
            finally{if(wake.isHeld())wake.release();running=false;stopForeground(true);stopSelf();}
        },"Sonora-export").start();return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int fgsType){cancelled=true;stopForeground(true);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}
