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

  static ArrayList<Project.Caption> parse(String value) throws IOException {
    String text = value.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').trim();
    ArrayList<Project.Caption> result = new ArrayList<>();
    if (text.isEmpty()) return result;
    for (String block : text.split("\n\\s*\n")) {
      String[] lines = block.split("\n");
      int i = lines[0].contains("-->") ? 0 : 1;
      if (i >= lines.length) throw new IOException("Bloco SRT sem tempos");
      String[] times = lines[i].split("\\s*-->\\s*");
      if (times.length != 2) throw new IOException("Separador SRT inválido");
      long start = time(times[0]), end = time(times[1]);
      if (start < 0 || end <= start || end > Project.MAX_MS)
        throw new IOException("Legendas devem estar entre 0 e 5 minutos");
      StringBuilder words = new StringBuilder();
      for (int j = i + 1; j < lines.length; j++) {
        if (words.length() > 0) words.append('\n');
        words.append(lines[j]);
      }
      if (words.length() == 0) throw new IOException("Legenda sem texto");
      result.add(new Project.Caption(start, end, words.toString()));
      if (result.size() > 3000) throw new IOException("Demasiadas legendas");
    }
    Collections.sort(result, (a, b) -> Long.compare(a.startMs, b.startMs));
    return result;
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
