package pt.sonora.offline;

import java.io.*;
import java.nio.charset.*;

/** Runs the production parser on the JVM; no substitute implementation. */
public final class SubtitleTrackHostTest {
    private static int checks;
    private static void equal(Object actual,Object expected){checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void invalid(String text)throws Exception{checks++;try{SubtitleTrack.parse(text);throw new AssertionError("Invalid SRT was accepted");}catch(IOException expected){}}
    public static void main(String[] args)throws Exception{
        String content="\ufeff1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Canção &amp; coração</i>\r\nSegunda linha\r\n\r\n2\r\n00:00:03,500 --> 00:00:05,000\r\nFim\r\n";
        SubtitleTrack t=SubtitleTrack.parse(content);equal(t.size(),2);equal(t.firstStartMs(),1000L);equal(t.lastEndMs(),5000L);
        equal(t.textAt(0,0),"");equal(t.textAt(999,0),"");equal(t.textAt(1000,0),"Canção & coração\nSegunda linha");
        equal(t.textAt(3499,0),"Canção & coração\nSegunda linha");equal(t.textAt(3500,0),"Fim");equal(t.textAt(5000,0),"");
        equal(t.textAt(1000,500),"");equal(t.textAt(1500,500),"Canção & coração\nSegunda linha");equal(t.textAt(500,-500),"Canção & coração\nSegunda linha");
        equal(t.textAt(4000,1000),"Canção & coração\nSegunda linha");equal(t.textAt(0,1000),"");
        // Lookups may go backwards while seeking, with no stale cached subtitle.
        equal(t.textAt(4000,0),"Fim");equal(t.textAt(1500,0),"Canção & coração\nSegunda linha");
        SubtitleTrack overlap=SubtitleTrack.parse("2\n00:00:02.000 --> 00:00:04.000 X1:0\nB\n\n1\n00:00:01,000 --> 00:00:03,000\nA");
        equal(overlap.textAt(2500,0),"A\nB");equal(overlap.textAt(3000,0),"B");equal(overlap.textAt(1500,0),"A");
        SubtitleTrack noIndex=SubtitleTrack.parse("00:00:01.5 --> 00:00:02.75\n&#x1f3b5; &quot;Olá&quot;<br/>Mundo");
        equal(noIndex.firstStartMs(),1500L);equal(noIndex.lastEndMs(),2750L);equal(noIndex.textAt(1500,0),"🎵 \"Olá\"\nMundo");
        String accents="1\n00:00:00,000 --> 00:00:01,000\nAção, coração, €";
        for(Charset charset:new Charset[]{StandardCharsets.UTF_8,StandardCharsets.UTF_16,Charset.forName("windows-1252")}){
            SubtitleTrack encoded=SubtitleTrack.read(new ByteArrayInputStream(accents.getBytes(charset)));equal(encoded.textAt(0,0),"Ação, coração, €");
        }
        equal(SubtitleTrack.parse("1\n00:00:00,000 --> 00:00:01,000\n{\\an8}Olá").textAt(0,0),"Olá");
        invalid("");invalid("1\n00:01:60,000 --> 00:02:02,000\nA");invalid("1\n00:60:00,000 --> 01:01:00,000\nA");
        invalid("1\n00:00:02,000 --> 00:00:01,000\nA");invalid("1\n00:00:01,000 --> 00:00:01,000\nA");invalid("1\n00:00:01,000 --> 00:00:02,000");
        invalid("1\nBad timestamp\nA");invalid("1\n00:00:00,000 --> 00:00:01,000\nA\n\n2\ninvalid\nB");
        StringBuilder many=new StringBuilder();for(int i=0;i<9;i++)many.append(i+1).append("\n00:00:00,000 --> 00:00:01,000\nA\n\n");invalid(many.toString());
        checks++;try{SubtitleTrack.read(new ByteArrayInputStream(new byte[SubtitleTrack.MAX_BYTES+1]));throw new AssertionError("Oversize input accepted");}catch(IOException expected){}
        System.out.println("PASS: "+checks+" SRT checks (timing, offsets, seeking, overlaps, encodings, malformed input and limits)");
    }
}
