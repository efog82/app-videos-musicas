package pt.sonora.app;

import java.util.*;
import org.json.*;

/** Validated document operations; never opens, deletes or rewrites original media. */
final class ProjectActions {
  static Project duplicate(Project source) throws JSONException {
    Project result = source.copy();
    result.id = UUID.randomUUID().toString();
    result.name =
        (source.name.length() > 145 ? source.name.substring(0, 145) : source.name) + " · cópia";
    return result;
  }

  static Project.Clip duplicateClip(Project project, Project.Clip clip) throws JSONException {
    Project.Clip copy = Project.Clip.read(clip.json());
    copy.id = UUID.randomUUID().toString();
    copy.startMs = clip.endMs();
    copy.name = clip.name + " · cópia";
    copy.validate();
    project.clips.add(copy);
    project.autoImages = false;
    return copy;
  }

  static Project.Clip split(Project project, Project.Clip clip, long at) throws JSONException {
    if (at - clip.startMs < 100 || clip.endMs() - at < 100)
      throw new JSONException(
          "Coloca a linha de reprodução dentro do clip, pelo menos 0,1 s de cada extremidade.");
    Project.Clip left = Project.Clip.read(clip.json()), right = Project.Clip.read(clip.json());
    long first = at - clip.startMs;
    left.lengthMs = first;
    left.fadeInMs = Math.min(left.fadeInMs, first);
    left.fadeOutMs = 0;
    right.id = UUID.randomUUID().toString();
    right.name = clip.name + " · 2";
    right.startMs = at;
    right.inMs = clip.inMs + first;
    right.lengthMs = clip.lengthMs - first;
    right.fadeInMs = 0;
    right.fadeOutMs = Math.min(right.fadeOutMs, right.lengthMs);
    left.validate();
    right.validate();
    int index = project.clips.indexOf(clip);
    if (index < 0) throw new JSONException("O clip já não pertence ao projeto.");
    project.clips.set(index, left);
    project.clips.add(index + 1, right);
    project.autoImages = false;
    return right;
  }

  static void trim(Project.Clip clip, long in, long length) throws JSONException {
    Project.Clip test = Project.Clip.read(clip.json());
    test.inMs = in;
    test.lengthMs = length;
    test.fadeInMs = Math.min(test.fadeInMs, length);
    test.fadeOutMs = Math.min(test.fadeOutMs, length);
    test.validate();
    clip.inMs = in;
    clip.lengthMs = length;
    clip.fadeInMs = test.fadeInMs;
    clip.fadeOutMs = test.fadeOutMs;
  }

  /** Append visual media; music remains free to overlap the montage. */
  static void appendVisual(Project project, Project.Clip clip) throws JSONException {
    long end = 0;
    for (Project.Clip existing : project.clips)
      if (!existing.type.equals("audio")) end = Math.max(end, existing.endMs());
    if (end + clip.lengthMs > Project.MAX_MS)
      throw new JSONException(
          "A sequência excede o limite de 10 minutos. Encurta os clips primeiro.");
    clip.startMs = end;
    clip.track = 0;
    clip.validate();
    project.autoImages = false;
    project.clips.add(clip);
  }

  static void sequenceVisual(Project project) throws JSONException {
    long total = 0;
    for (Project.Clip clip : project.clips) if (!clip.type.equals("audio")) total += clip.lengthMs;
    if (total > Project.MAX_MS)
      throw new JSONException("A sequência excede 10 minutos. Encurta os clips primeiro.");
    long position = 0;
    for (Project.Clip clip : project.clips)
      if (!clip.type.equals("audio")) {
        clip.startMs = position;
        clip.track = 0;
        position += clip.lengthMs;
      }
    project.autoImages = false;
  }

  /** Replace only the project reference, retaining source URI and clip timing. */
  static Project.Clip audioOnly(Project project, Project.Clip video) throws JSONException {
    if (!video.type.equals("video") || !video.hasAudio)
      throw new JSONException("Este vídeo não tem áudio para extrair.");
    int index = project.clips.indexOf(video);
    if (index < 0) throw new JSONException("O vídeo já não pertence ao projeto.");
    Project.Clip audio = Project.Clip.read(video.json());
    audio.type = "audio";
    audio.name = "Áudio · " + video.name;
    audio.originalAudio = true;
    audio.track = 2;
    audio.validate();
    project.clips.set(index, audio);
    project.autoImages = false;
    return audio;
  }

  /** Non-overlapping clips share a lane; overlaps retain separate visible lanes. */
  static int[] lanes(java.util.List<Project.Clip> clips) {
    int[] result = new int[clips.size()];
    ArrayList<Long> ends = new ArrayList<>();
    ArrayList<Integer> tracks = new ArrayList<>();
    for (int i = 0; i < clips.size(); i++) {
      Project.Clip clip = clips.get(i);
      int lane = 0;
      while (lane < ends.size()
          && (tracks.get(lane) != clip.track || ends.get(lane) > clip.startMs)) lane++;
      if (lane == ends.size()) {
        ends.add(clip.endMs());
        tracks.add(clip.track);
      } else ends.set(lane, clip.endMs());
      result[i] = lane;
    }
    return result;
  }

  static long estimatedBytes(Project project) {
    return (long) Math.ceil(project.durationMs() / 1000.0 * (project.bitrate + 128000) / 8 * 1.03);
  }
}
