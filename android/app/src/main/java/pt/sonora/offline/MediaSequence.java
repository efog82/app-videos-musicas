package pt.sonora.offline;

import android.graphics.Bitmap;
import android.media.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** File-backed sequences: only the current images/decoder are loaded, not all media. */
final class MediaSequence {
    static final class Item {
        String file,name;long durationMs,startMs,endMs;int width,height,rotation;
        long length(){return Math.max(1,(endMs>0?endMs:durationMs)-startMs);}
    }
    final File manifest;
    final ArrayList<Item> items=new ArrayList<>();
    MediaSequence(File file){manifest=file;}
    static MediaSequence load(String path)throws Exception{
        File file=new File(path);MediaSequence sequence=new MediaSequence(file);
        StringBuilder json=new StringBuilder();try(Reader reader=new InputStreamReader(new FileInputStream(file),"UTF-8")){char[] b=new char[8192];int n;while((n=reader.read(b))!=-1)json.append(b,0,n);}
        JSONArray array=new JSONArray(json.toString());
        for(int i=0;i<array.length();i++){JSONObject o=array.getJSONObject(i);Item item=new Item();item.file=o.getString("file");item.name=o.optString("name","Ficheiro "+(i+1));item.durationMs=o.optLong("duration",0);item.startMs=o.optLong("start",0);item.endMs=o.optLong("end",item.durationMs);item.width=o.optInt("width",0);item.height=o.optInt("height",0);item.rotation=o.optInt("rotation",0);sequence.items.add(item);}
        if(sequence.items.isEmpty())throw new IOException("A sequência está vazia.");return sequence;
    }
    void save()throws Exception{
        JSONArray array=new JSONArray();for(Item item:items){JSONObject o=new JSONObject();o.put("file",item.file);o.put("name",item.name);o.put("duration",item.durationMs);o.put("start",item.startMs);o.put("end",item.endMs);o.put("width",item.width);o.put("height",item.height);o.put("rotation",item.rotation);array.put(o);}
        AtomicFile file=new AtomicFile(manifest);FileOutputStream out=null;
        try{out=file.startWrite();out.write(array.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    long totalMs(){long total=0;for(Item i:items)total+=i.length();return total;}
    int videoIndex(long musicMs){long time=Math.floorMod(musicMs,totalMs());for(int i=0;i<items.size();i++){long length=items.get(i).length();if(time<length)return i;time-=length;}return items.size()-1;}
    long videoTimeMs(long musicMs){long time=Math.floorMod(musicMs,totalMs());for(Item item:items){if(time<item.length())return item.startMs+time;time-=item.length();}return items.get(items.size()-1).startMs;}
    static Item probeVideo(File file,String name)throws Exception{
        MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(file.getAbsolutePath());
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);if(!f.getString(MediaFormat.KEY_MIME).startsWith("video/"))continue;
                Item item=new Item();item.file=file.getAbsolutePath();item.name=name;item.width=f.getInteger(MediaFormat.KEY_WIDTH);item.height=f.getInteger(MediaFormat.KEY_HEIGHT);item.rotation=f.containsKey(MediaFormat.KEY_ROTATION)?f.getInteger(MediaFormat.KEY_ROTATION):0;
                item.durationMs=f.containsKey(MediaFormat.KEY_DURATION)?f.getLong(MediaFormat.KEY_DURATION)/1000:0;
                if(item.durationMs<=0)throw new IOException("O vídeo não anuncia uma duração válida.");item.endMs=item.durationMs;return item;
            }throw new IOException("O ficheiro não contém um vídeo compatível.");
        }finally{extractor.release();}
    }
    static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
    static final class Slides implements AutoCloseable {
        final MediaSequence sequence;int index=-1,nextIndex=-1;Bitmap current,next;
        Slides(String path)throws Exception{sequence=load(path);}
        Bitmap at(long musicMs,long songMs){int selected=Math.min(sequence.items.size()-1,(int)(Math.max(0,musicMs)*(double)sequence.items.size()/Math.max(1,songMs)));
            if(selected!=index){if(current!=null)current.recycle();current=VideoExport.loadImage(sequence.items.get(selected).file,640);index=selected;}return current;
        }
        Bitmap next(){int selected=Math.min(sequence.items.size()-1,index+1);if(selected==index)return current;
            if(nextIndex!=selected){if(next!=null)next.recycle();next=VideoExport.loadImage(sequence.items.get(selected).file,640);nextIndex=selected;}return next;
        }
        float blend(long musicMs,long songMs){double duration=Math.max(1,songMs)/(double)sequence.items.size(),phase=(musicMs%duration)/duration;double fade=Math.min(.2,350/duration);return index+1<sequence.items.size()?(float)Math.max(0,(phase-(1-fade))/fade):0;}
        public void close(){if(current!=null)current.recycle();if(next!=null)next.recycle();current=next=null;}
    }
}
