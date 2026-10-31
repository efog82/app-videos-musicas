package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Preview-only sequential decoding. Export keeps its deterministic frame renderer. */
final class PreviewVideoDecoder implements AutoCloseable {
  final MediaExtractor extractor = new MediaExtractor();
  MediaCodec codec;
  boolean inputEnd, outputEnd;
  long lastUs = -1, requestedUs = -1;
  Bitmap last;
  int rotation;

  PreviewVideoDecoder(Context context, String uri) throws Exception {
    try {
      extractor.setDataSource(context, Uri.parse(uri), null);
      MediaFormat format = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat candidate = extractor.getTrackFormat(i);
        if (candidate.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
          extractor.selectTrack(i);
          format = candidate;
          break;
        }
      }
      if (format == null) throw new IOException("O ficheiro não contém vídeo.");
      rotation =
          format.containsKey(MediaFormat.KEY_ROTATION)
              ? format.getInteger(MediaFormat.KEY_ROTATION)
              : 0;
      format.setInteger(
          MediaFormat.KEY_COLOR_FORMAT,
          MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();
    } catch (Exception error) {
      close();
      throw error;
    }
  }

  Bitmap at(long targetUs, int width, int height) throws Exception {
    if (requestedUs < 0 || targetUs < requestedUs || targetUs - requestedUs > 1500000) {
      extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      codec.flush();
      inputEnd = outputEnd = false;
      lastUs = -1;
    }
    requestedUs = targetUs;
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    long deadline = SystemClock.elapsedRealtime() + (last == null ? 500 : 80);
    while (!outputEnd && lastUs < targetUs && SystemClock.elapsedRealtime() < deadline) {
      if (!inputEnd) {
        int input = codec.dequeueInputBuffer(0);
        if (input >= 0) {
          ByteBuffer bytes = codec.getInputBuffer(input);
          bytes.clear();
          int count = extractor.readSampleData(bytes, 0);
          if (count < 0) {
            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            inputEnd = true;
          } else {
            codec.queueInputBuffer(input, 0, count, extractor.getSampleTime(), 0);
            extractor.advance();
          }
        }
      }
      int output = codec.dequeueOutputBuffer(info, 2000);
      if (output >= 0) {
        try {
          outputEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          if (info.size > 0) {
            lastUs = info.presentationTimeUs;
            // Skip obsolete decoded frames while catching up with the project clock.
            if (lastUs >= targetUs || last == null) {
              Image image = codec.getOutputImage(output);
              if (image == null)
                throw new IOException("Descodificador sem saída YUV para pré-visualização.");
              try {
                Bitmap next = bitmap(image, width, height);
                if (last != null) last.recycle();
                last = next;
              } finally {
                image.close();
              }
            }
          }
        } finally {
          codec.releaseOutputBuffer(output, false);
        }
      }
    }
    return last == null ? null : last.copy(Bitmap.Config.ARGB_8888, false);
  }

  Bitmap bitmap(Image image, int maxWidth, int maxHeight) {
    Rect crop = image.getCropRect();
    float scale =
        Math.min(1f, Math.max(maxWidth, maxHeight) / (float) Math.max(crop.width(), crop.height()));
    int width = Math.max(2, (int) (crop.width() * scale)),
        height = Math.max(2, (int) (crop.height() * scale));
    int[] pixels = new int[width * height];
    Image.Plane[] planes = image.getPlanes();
    ByteBuffer y = planes[0].getBuffer(), u = planes[1].getBuffer(), v = planes[2].getBuffer();
    int yp = y.position(), up = u.position(), vp = v.position();
    for (int row = 0; row < height; row++) {
      int sy = crop.top + row * crop.height() / height;
      for (int col = 0; col < width; col++) {
        int sx = crop.left + col * crop.width() / width;
        int yy =
            (y.get(yp + sy * planes[0].getRowStride() + sx * planes[0].getPixelStride()) & 255)
                - 16;
        int uu =
            (u.get(up + (sy / 2) * planes[1].getRowStride() + (sx / 2) * planes[1].getPixelStride())
                    & 255)
                - 128;
        int vv =
            (v.get(vp + (sy / 2) * planes[2].getRowStride() + (sx / 2) * planes[2].getPixelStride())
                    & 255)
                - 128;
        int red = Math.max(0, Math.min(255, (298 * yy + 409 * vv + 128) >> 8));
        int green = Math.max(0, Math.min(255, (298 * yy - 100 * uu - 208 * vv + 128) >> 8));
        int blue = Math.max(0, Math.min(255, (298 * yy + 516 * uu + 128) >> 8));
        pixels[row * width + col] = 0xff000000 | red << 16 | green << 8 | blue;
      }
    }
    Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    if (rotation != 0) {
      Matrix transform = new Matrix();
      transform.postRotate(rotation);
      Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, transform, true);
      if (rotated != bitmap) bitmap.recycle();
      bitmap = rotated;
    }
    return bitmap;
  }

  public void close() {
    if (codec != null) {
      try {
        codec.stop();
      } catch (Exception ignored) {
      }
      codec.release();
      codec = null;
    }
    extractor.release();
    if (last != null) {
      last.recycle();
      last = null;
    }
  }
}
