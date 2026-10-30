package pt.sonora.app;

import java.io.*;
import java.util.*;
import java.util.regex.*;

final class Subtitles {
  static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})");

  static long time(String value) throws IOException {
    Matcher m = TIME.matcher(value.trim());
    if (!m.matches()) throw new IOException("Tempo inválido: " + value);
    int minutes = Integer.parseInt(m.group(2)), seconds = Integer.parseInt(m.group(3));
    if (minutes > 59 || seconds > 59) throw new IOException("Tempo inválido");
    return Long.parseLong(m.group(1)) * 3600000
        + minutes * 60000
        + seconds * 1000
        + Long.parseLong(m.group(4));
  }

  static ArrayList<Project.Caption> convert(pt.sonora.offline.SubtitleTrack track) {
    ArrayList<Project.Caption> captions = new ArrayList<>();
    for (pt.sonora.offline.SubtitleTrack.Cue cue : track.entries())
      captions.add(new Project.Caption(cue.startMs, cue.endMs, cue.text));
    return captions;
  }

  static ArrayList<Project.Caption> read(InputStream in) throws IOException {
    return convert(pt.sonora.offline.SubtitleTrack.read(in));
  }

  static ArrayList<Project.Caption> parse(String text) throws IOException {
    return convert(pt.sonora.offline.SubtitleTrack.parse(text));
  }

  static String format(long ms) {
    return String.format(
        Locale.ROOT,
        "%02d:%02d:%02d,%03d",
        ms / 3600000,
        (ms / 60000) % 60,
        (ms / 1000) % 60,
        ms % 1000);
  }

  static String write(List<Project.Caption> captions) {
    ArrayList<Project.Caption> sorted = new ArrayList<>(captions);
    Collections.sort(sorted, (a, b) -> Long.compare(a.startMs, b.startMs));
    StringBuilder out = new StringBuilder();
    int i = 1;
    for (Project.Caption c : sorted)
      out.append(i++)
          .append('\n')
          .append(format(c.startMs))
          .append(" --> ")
          .append(format(c.endMs))
          .append('\n')
          .append(c.text)
          .append("\n\n");
    return out.toString();
  }
}
