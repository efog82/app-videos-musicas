package pt.sonora.app;

import java.util.*;
import org.json.*;

/** Non-destructive document. Times are milliseconds on a common project clock. */
final class Project {
  static final int SCHEMA = 1;
  static final long MAX_MS = 600000;
  String id = UUID.randomUUID().toString(), name = "Novo projeto", title = "";
  final ArrayList<Clip> clips = new ArrayList<>();
  final ArrayList<Caption> captions = new ArrayList<>();
  String effect = "bars",
      subtitleStyle = "sans",
      subtitleAnimation = "phrase",
      transition = "none",
      syncBand = "energy";
  float intensity = 1, speed = 1, zoom = 1, rotation = 0, distortion;
  int density = 48, color = 0xffb7a4ff, resolution = 720, fps = 24, bitrate = 4000000;
  boolean vertical = true,
      square,
      mediaFit,
      showTitle = true,
      showSubtitles = true,
      enhancedEffects = true,
      fullEffect,
      autoImages = true;
  int subtitleSize = 1, subtitlePosition;
  long subtitleOffsetMs;

  static final class Clip {
    String id = UUID.randomUUID().toString(), uri = "", name = "", type = "audio";
    int track;
    long startMs, inMs, lengthMs, sourceDurationMs;
    float volume = 1;
    boolean muted, originalAudio = true, hasAudio;
    long fadeInMs, fadeOutMs;

    long endMs() {
      return startMs + lengthMs;
    }

    long sourceTime(long projectMs) {
      return inMs + projectMs - startMs;
    }

    boolean active(long ms) {
      return ms >= startMs && ms < endMs();
    }

    JSONObject json() throws JSONException {
      return new JSONObject()
          .put("id", id)
          .put("uri", uri)
          .put("name", name)
          .put("type", type)
          .put("track", track)
          .put("startMs", startMs)
          .put("inMs", inMs)
          .put("lengthMs", lengthMs)
          .put("sourceDurationMs", sourceDurationMs)
          .put("volume", volume)
          .put("muted", muted)
          .put("originalAudio", originalAudio)
          .put("hasAudio", hasAudio)
          .put("fadeInMs", fadeInMs)
          .put("fadeOutMs", fadeOutMs);
    }

    static Clip read(JSONObject o) throws JSONException {
      Clip c = new Clip();
      c.id = o.getString("id");
      c.uri = o.getString("uri");
      c.name = o.optString("name");
      c.type = o.getString("type");
      if (!Arrays.asList("audio", "video", "image").contains(c.type))
        throw new JSONException("Tipo de faixa desconhecido");
      c.track = o.getInt("track");
      c.startMs = o.getLong("startMs");
      c.inMs = o.getLong("inMs");
      c.lengthMs = o.getLong("lengthMs");
      c.sourceDurationMs = o.getLong("sourceDurationMs");
      c.volume = (float) o.optDouble("volume", 1);
      c.muted = o.optBoolean("muted");
      c.originalAudio = o.optBoolean("originalAudio", true);
      c.hasAudio = o.optBoolean("hasAudio");
      c.fadeInMs = o.optLong("fadeInMs");
      c.fadeOutMs = o.optLong("fadeOutMs");
      c.validate();
      return c;
    }

    void validate() throws JSONException {
      if (id.isEmpty()
          || uri.isEmpty()
          || track < 0
          || track > 15
          || startMs < 0
          || inMs < 0
          || lengthMs < 100
          || lengthMs > MAX_MS
          || startMs > MAX_MS - lengthMs
          || sourceDurationMs < 100
          || (type.equals("image") ? false : inMs > sourceDurationMs - lengthMs)
          || !Float.isFinite(volume)
          || volume < 0
          || volume > 2
          || fadeInMs < 0
          || fadeOutMs < 0
          || fadeInMs > lengthMs
          || fadeOutMs > lengthMs) throw new JSONException("Tempos ou volume de faixa inválidos");
    }
  }

  static final class Caption {
    long startMs, endMs;
    String text;

    Caption(long start, long end, String value) {
      startMs = start;
      endMs = end;
      text = value;
    }

    JSONObject json() throws JSONException {
      return new JSONObject().put("startMs", startMs).put("endMs", endMs).put("text", text);
    }
  }

  long durationMs() {
    long end = 0;
    for (Clip c : clips) end = Math.max(end, c.endMs());
    return end == 0 ? 10000 : end;
  }

  boolean removeClip(String id) {
    for (int i = 0; i < clips.size(); i++)
      if (clips.get(i).id.equals(id)) {
        clips.remove(i);
        return true;
      }
    return false;
  }

  Clip find(String id) {
    for (Clip c : clips) if (c.id.equals(id)) return c;
    return null;
  }

  JSONObject json() throws JSONException {
    JSONArray a = new JSONArray(), s = new JSONArray();
    for (Clip c : clips) a.put(c.json());
    for (Caption c : captions) s.put(c.json());
    return new JSONObject()
        .put("schema", SCHEMA)
        .put("id", id)
        .put("name", name)
        .put("title", title)
        .put("clips", a)
        .put("captions", s)
        .put("effect", effect)
        .put("intensity", intensity)
        .put("speed", speed)
        .put("density", density)
        .put("color", color)
        .put("distortion", distortion)
        .put("syncBand", syncBand)
        .put("zoom", zoom)
        .put("rotation", rotation)
        .put("transition", transition)
        .put("subtitleStyle", subtitleStyle)
        .put("subtitleAnimation", subtitleAnimation)
        .put("resolution", resolution)
        .put("fps", fps)
        .put("bitrate", bitrate)
        .put("vertical", vertical)
        .put("square", square)
        .put("mediaFit", mediaFit)
        .put("showTitle", showTitle)
        .put("showSubtitles", showSubtitles)
        .put("enhancedEffects", enhancedEffects)
        .put("fullEffect", fullEffect)
        .put("autoImages", autoImages)
        .put("subtitleSize", subtitleSize)
        .put("subtitlePosition", subtitlePosition)
        .put("subtitleOffsetMs", subtitleOffsetMs);
  }

  Project copy() throws JSONException {
    return read(json().toString());
  }

  static Project read(String text) throws JSONException {
    JSONObject o = new JSONObject(text);
    if (o.getInt("schema") != SCHEMA)
      throw new JSONException("Versão de projeto não suportada; o projeto atual foi preservado");
    Project p = new Project();
    p.id = o.getString("id");
    if (!p.id.matches("[0-9a-fA-F-]{36}")
        || !UUID.fromString(p.id).toString().equalsIgnoreCase(p.id))
      throw new JSONException("Identificador de projeto inválido");
    p.name = o.optString("name", "Projeto");
    p.title = o.optString("title");
    JSONArray a = o.getJSONArray("clips");
    if (a.length() > 10000) throw new JSONException("Documento demasiado grande");
    HashSet<String> ids = new HashSet<>();
    for (int i = 0; i < a.length(); i++) {
      Clip c = Clip.read(a.getJSONObject(i));
      if (!ids.add(c.id)) throw new JSONException("Faixas duplicadas");
      p.clips.add(c);
    }
    JSONArray s = o.optJSONArray("captions");
    if (s != null)
      for (int i = 0; i < s.length(); i++) {
        JSONObject v = s.getJSONObject(i);
        long start = v.getLong("startMs"), end = v.getLong("endMs");
        if (start < 0 || end <= start || end > 36000000000L)
          throw new JSONException("Tempos de legenda inválidos");
        p.captions.add(new Caption(start, end, v.getString("text")));
      }
    p.effect = o.optString("effect", "bars");
    p.intensity = (float) o.optDouble("intensity", 1);
    p.speed = (float) o.optDouble("speed", 1);
    p.density = o.optInt("density", 48);
    p.color = o.optInt("color", 0xffb7a4ff);
    p.zoom = (float) o.optDouble("zoom", 1);
    p.rotation = (float) o.optDouble("rotation", 0);
    p.distortion = (float) o.optDouble("distortion", 0);
    p.syncBand = o.optString("syncBand", "energy");
    p.transition = o.optString("transition", "none");
    p.subtitleStyle = o.optString("subtitleStyle", "sans");
    p.subtitleAnimation = o.optString("subtitleAnimation", "phrase");
    p.vertical = o.optBoolean("vertical", true);
    p.square = o.optBoolean("square");
    p.mediaFit = o.optBoolean("mediaFit");
    p.showTitle = o.optBoolean("showTitle", true);
    p.showSubtitles = o.optBoolean("showSubtitles", true);
    p.enhancedEffects = o.optBoolean("enhancedEffects", true);
    p.fullEffect = o.optBoolean("fullEffect");
    p.autoImages = o.optBoolean("autoImages", true);
    p.subtitleSize = o.optInt("subtitleSize", 1);
    p.subtitlePosition = o.optInt("subtitlePosition");
    p.subtitleOffsetMs = o.optLong("subtitleOffsetMs");
    p.resolution = o.optInt("resolution", 720);
    p.fps = o.optInt("fps", 24);
    p.bitrate = o.optInt("bitrate", 4000000);
    if (!Float.isFinite(p.distortion) || p.distortion < 0 || p.distortion > 1)
      throw new JSONException("Distorção inválida");
    if (p.subtitleSize < 0
        || p.subtitleSize > 2
        || p.subtitlePosition < 0
        || p.subtitlePosition > 2
        || Math.abs(p.subtitleOffsetMs) > 600000)
      throw new JSONException("Definições de legenda inválidas");
    if (p.name.length() > 160
        || p.title.length() > 200
        || !Arrays.asList("energy", "bass", "mid", "treble", "beat").contains(p.syncBand)
        || !Arrays.asList(
                "bars",
                "spectrum",
                "circle",
                "particles",
                "tunnel",
                "kaleidoscope",
                "fractal",
                "fluid",
                "none",
                "legacy-0",
                "legacy-1",
                "legacy-2",
                "legacy-3",
                "legacy-4",
                "legacy-5",
                "legacy-6",
                "legacy-7")
            .contains(p.effect)
        || !Arrays.asList("none", "fade").contains(p.transition)
        || !Arrays.asList("phrase", "fade", "word").contains(p.subtitleAnimation)
        || !Arrays.asList("sans", "serif", "mono").contains(p.subtitleStyle)
        || !Float.isFinite(p.intensity)
        || p.intensity < 0
        || p.intensity > 3
        || !Float.isFinite(p.speed)
        || p.speed < .1
        || p.speed > 4
        || !Float.isFinite(p.zoom)
        || p.zoom < 1
        || p.zoom > 3
        || !Float.isFinite(p.rotation)
        || Math.abs(p.rotation) > 360
        || p.density < 8
        || p.density > 120
        || !Arrays.asList(720, 1080, 1440, 2160).contains(p.resolution)
        || !Arrays.asList(24, 25, 30, 60).contains(p.fps)
        || p.bitrate < 1000000
        || p.bitrate > 80000000) throw new JSONException("Definições de projeto inválidas");
    return p;
  }
}
