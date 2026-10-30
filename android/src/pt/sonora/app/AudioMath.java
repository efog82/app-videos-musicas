package pt.sonora.app;

final class AudioMath {
  static float gain(Project.Clip c, long ms) {
    if (c.muted || !c.active(ms) || (c.type.equals("video") && !c.originalAudio)) return 0;
    double t = ms - c.startMs, remaining = c.endMs() - ms;
    return (float)
        (c.volume
            * Math.min(1, c.fadeInMs == 0 ? 1 : t / c.fadeInMs)
            * Math.min(1, c.fadeOutMs == 0 ? 1 : remaining / c.fadeOutMs));
  }

  static short clamp(double value) {
    return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
  }
}
