package pt.sonora.offline;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Immutable SRT timeline, independent of playback and of animation speed. */
public final class SubtitleTrack {
    public static final int MAX_BYTES=2*1024*1024, MAX_CUES=10000;
    private static final Pattern TIMING=Pattern.compile("^\\s*(\\d{1,4}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,4}):(\\d{2}):(\\d{2})[,.](\\d{1,3})(?:\\s+.*)?$");
    private static final Pattern ENTITY=Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);");
    public static final class Cue {
        public final long startMs,endMs; public final String text;
        Cue(long start,long end,String text){startMs=start;endMs=end;this.text=text;}
    }
    private final Cue[] cues;
    private final long[] maxEnd;
    private SubtitleTrack(List<Cue> entries)throws IOException{
        entries.sort(Comparator.comparingLong(c->c.startMs));
        PriorityQueue<Cue> active=new PriorityQueue<>(Comparator.comparingLong(c->c.endMs));int characters=0;
        for(Cue cue:entries){while(!active.isEmpty()&&active.peek().endMs<=cue.startMs)characters-=active.remove().text.length();
            characters+=cue.text.length();active.add(cue);
            if(active.size()>8||characters>6000)throw new IOException("O SRT tem demasiadas legendas sobrepostas no mesmo instante.");
        }
        cues=entries.toArray(new Cue[0]);maxEnd=new long[cues.length];
        long max=0;for(int i=0;i<cues.length;i++){max=Math.max(max,cues[i].endMs);maxEnd[i]=max;}
    }
    public int size(){return cues.length;}
    public long firstStartMs(){return cues[0].startMs;}
    public long lastEndMs(){return maxEnd[maxEnd.length-1];}
    public static SubtitleTrack load(File file)throws IOException{
        try(InputStream in=new FileInputStream(file)){return read(in);}
    }
    public static SubtitleTrack read(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("O SRT deve ter até 2 MB.");out.write(buffer,0,n);}
        byte[] bytes=out.toByteArray();String text;
        if(bytes.length>=2&&((bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)||(bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)))text=new String(bytes,StandardCharsets.UTF_16);
        else{
            try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
            catch(CharacterCodingException e){text=new String(bytes,Charset.forName("windows-1252"));}
        }
        return parse(text);
    }
    public static SubtitleTrack parse(String content)throws IOException{
        if(content==null||content.length()>MAX_BYTES)throw new IOException("O SRT deve ter até 2 MB.");
        String normalized=content.replace("\ufeff","").replace("\r\n","\n").replace('\r','\n').trim();
        if(normalized.isEmpty())throw new IOException("O ficheiro SRT está vazio.");
        String[] blocks=normalized.split("\\n[\\t ]*\\n+");ArrayList<Cue> entries=new ArrayList<>();int block=0;
        for(String value:blocks){
            block++;String[] lines=value.trim().split("\n");int timing=0;
            if(lines.length>0&&lines[0].trim().matches("\\d+"))timing=1;
            if(timing>=lines.length)throw invalid(block);
            Matcher match=TIMING.matcher(lines[timing]);if(!match.matches())throw invalid(block);
            long start=timestamp(match,1,block),end=timestamp(match,5,block);
            if(end<=start)throw new IOException("SRT: o fim deve ser posterior ao início no bloco "+block+".");
            StringBuilder text=new StringBuilder();
            for(int j=timing+1;j<lines.length;j++){if(text.length()>0)text.append('\n');text.append(lines[j].trim());}
            String cleaned=plainText(text.toString());
            if(cleaned.isEmpty())throw new IOException("SRT: falta o texto no bloco "+block+".");
            if(cleaned.length()>2000)throw new IOException("SRT: texto demasiado longo no bloco "+block+" (máximo 2000 caracteres).");
            entries.add(new Cue(start,end,cleaned));if(entries.size()>MAX_CUES)throw new IOException("O SRT tem demasiadas legendas (máximo 10000).");
        }
        return new SubtitleTrack(entries);
    }
    private static IOException invalid(int block){return new IOException("SRT inválido no bloco "+block+". Usa tempos como 00:01:23,450 --> 00:01:26,000.");}
    private static long timestamp(Matcher m,int start,int block)throws IOException{
        int hours=Integer.parseInt(m.group(start)),minutes=Integer.parseInt(m.group(start+1)),seconds=Integer.parseInt(m.group(start+2));
        String fraction=m.group(start+3);int ms=Integer.parseInt(fraction)*(fraction.length()==1?100:fraction.length()==2?10:1);
        if(minutes>59||seconds>59)throw invalid(block);
        return ((hours*60L+minutes)*60+seconds)*1000+ms;
    }
    private static String plainText(String s){
        s=s.replaceAll("(?i)<br\\s*/?>","\n").replaceAll("<[^>]*>","").replaceAll("\\{\\\\[^}]*\\}","");
        Matcher m=ENTITY.matcher(s);StringBuffer out=new StringBuffer();
        while(m.find()){
            String entity=m.group(1),replacement=m.group();
            if(entity.startsWith("#")){try{int cp=Integer.parseInt(entity.substring(entity.startsWith("#x")?2:1),entity.startsWith("#x")?16:10);if(Character.isValidCodePoint(cp)&&!(cp>=0xd800&&cp<=0xdfff)&&cp>0)replacement=new String(Character.toChars(cp));}catch(IllegalArgumentException ignored){}}
            else{switch(entity){case "amp":replacement="&";break;case "lt":replacement="<";break;case "gt":replacement=">";break;case "quot":replacement="\"";break;case "apos":replacement="'";break;case "nbsp":replacement=" ";break;}}
            m.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }m.appendTail(out);return out.toString().replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]","").trim();
    }
    /** Start inclusive, end exclusive. Positive offset displays captions later. */
    public String textAt(long musicMs,long offsetMs){
        long t=musicMs-offsetMs;if(t<0)return "";
        int low=0,high=cues.length;
        while(low<high){int middle=(low+high)>>>1;if(cues[middle].startMs<=t)low=middle+1;else high=middle;}
        int end=low,begin=end;
        while(begin>0&&maxEnd[begin-1]>t)begin--;
        StringBuilder text=new StringBuilder();
        for(int i=begin;i<end;i++)if(cues[i].endMs>t){if(text.length()>0)text.append('\n');text.append(cues[i].text);}
        return text.toString();
    }
}
