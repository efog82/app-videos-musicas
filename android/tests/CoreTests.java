package pt.sonora.app;

import java.util.*;

public final class CoreTests {
  static int count;

  static void check(boolean condition, String message) {
    count++;
    if (!condition) throw new AssertionError(message);
  }

  static Project.Clip clip() {
    Project.Clip c = new Project.Clip();
    c.uri = "content://test/audio";
    c.name = "canção.wav";
    c.lengthMs = 4000;
    c.sourceDurationMs = 5000;
    return c;
  }

  public static void main(String[] args) throws Exception {
    Project p = new Project();
    Project.Clip c = clip();
    c.startMs = 1000;
    c.fadeInMs = 1000;
    c.fadeOutMs = 1000;
    p.clips.add(c);
    p.title = "Canção 100% d'amor";
    p.captions.add(new Project.Caption(1200, 2200, "Olá\nmundo"));
    Project copy = p.copy();
    check(copy.clips.get(0).uri.equals(c.uri), "URI must survive save/reopen");
    check(copy.durationMs() == 5000, "duration from timeline");
    copy.clips.get(0).startMs = 0;
    check(c.startMs == 1000, "project snapshots must not alias clips");
    check(copy.captions.get(0).text.equals("Olá\nmundo"), "subtitle Unicode/newlines");
    String before = p.json().toString();
    try {
      Project.read(before.replace("\"schema\":1", "\"schema\":999"));
      throw new AssertionError("future version accepted");
    } catch (org.json.JSONException expected) {
      count++;
    }
    check(p.json().toString().equals(before), "rejected import preserves live project");
    check(AudioMath.gain(c, 500) == 0, "silent before clip");
    check(Math.abs(AudioMath.gain(c, 1500) - .5) < .001, "fade in");
    check(Math.abs(AudioMath.gain(c, 4500) - .5) < .001, "fade out");
    c.muted = true;
    check(AudioMath.gain(c, 2500) == 0, "mute");
    c.muted = false;
    c.type = "video";
    c.originalAudio = false;
    check(AudioMath.gain(c, 2500) == 0, "disable original video audio");
    c.originalAudio = true;
    check(AudioMath.gain(c, 2500) == 1, "video original audio defaults on");
    check(
        AudioMath.clamp(100000) == 32767 && AudioMath.clamp(-100000) == -32768,
        "mix clamps overflow without wrapping");
    ArrayList<Project.Caption> s =
        Subtitles.parse(
            "\uFEFF1\r\n"
                + "00:00:01,250 --> 00:00:02,500\r\n"
                + "Olá mundo\r\n"
                + "segunda linha\r\n\r\n"
                + "2\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "Outra frase\r\n");
    check(s.size() == 2, "SRT count");
    check(s.get(0).startMs == 1250, "millisecond timing");
    check(Subtitles.parse(Subtitles.write(s)).get(0).text.equals(s.get(0).text), "SRT roundtrip");
    for (String bad :
        new String[] {
          "1\n00:00:03,000 --> 00:00:02,000\nA", "1\n00:90:00,000 --> 00:91:00,000\nA", "oops"
        })
      try {
        Subtitles.parse(bad);
        throw new AssertionError("invalid SRT accepted");
      } catch (java.io.IOException expected) {
        count++;
      }
    short[] silence = new short[2048];
    check(Spectrum.analyse(silence, 12000, 0).energy == 0, "silence has no energy");
    Spectrum.Features bass = tone(93.75), mid = tone(1007.8125), high = tone(5015.625);
    check(bass.bass > bass.mid * 10 && bass.bass > bass.treble * 10, "bass band isolated");
    check(mid.mid > mid.bass * 10 && mid.mid > mid.treble * 10, "mid band isolated");
    check(high.treble > high.mid * 10 && high.treble > high.bass * 10, "high band isolated");
    Project.Clip invalid = clip();
    invalid.startMs = Project.MAX_MS;
    try {
      invalid.validate();
      throw new AssertionError("out of timeline accepted");
    } catch (org.json.JSONException expected) {
      count++;
    }
    System.out.println(
        "PASS: "
            + count
            + " assertions (project recovery, SRT, timing, fades, original audio, saturation, FFT"
            + " bands)");
  }

  static Spectrum.Features tone(double hz) {
    short[] s = new short[2048];
    for (int i = 0; i < s.length; i++)
      s[i] = (short) (Math.sin(i * 2 * Math.PI * hz / 12000) * 12000);
    return Spectrum.analyse(s, 12000, 0);
  }
}
