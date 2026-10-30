package pt.sonora.app;

import android.media.*;
import java.util.*;

final class ExportOptions {
  static int width(Project p) {
    return p.vertical ? p.resolution : p.resolution * 16 / 9;
  }

  static int height(Project p) {
    return p.vertical ? p.resolution * 16 / 9 : p.resolution;
  }

  static String supportedCodec(Project p) throws Exception {
    int w = width(p), h = height(p);
    long required = w * (long) h * 16 + 32 * 1024 * 1024L;
    long available =
        Runtime.getRuntime().maxMemory()
            - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    if (required > available)
      throw new Exception("Memória insuficiente para este perfil. Escolhe uma resolução inferior.");
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
      if (!info.isEncoder()) continue;
      try {
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/avc");
        boolean surface = false;
        for (int format : caps.colorFormats)
          surface |= format == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        MediaCodecInfo.VideoCapabilities v = caps.getVideoCapabilities();
        if (surface
            && v.areSizeAndRateSupported(w, h, p.fps)
            && v.getBitrateRange().contains(p.bitrate)) return info.getName();
      } catch (IllegalArgumentException ignored) {
      }
    }
    throw new Exception(
        "Este aparelho não anuncia suporte H.264 para "
            + w
            + "×"
            + h
            + " a "
            + p.fps
            + " FPS e "
            + (p.bitrate / 1000000)
            + " Mbps. Escolhe uma qualidade inferior.");
  }
}
