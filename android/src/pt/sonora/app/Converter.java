package pt.sonora.app;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.opengl.*;
import android.view.Surface;
import java.io.*;
import java.nio.*;
import java.util.*;

class Converter {
  interface Progress {
    void update(String message);
  }

  final Context context;
  int sampleRate = 44100, channels = 2;
  long pcmBytes;
  final ArrayList<Float> amplitudes = new ArrayList<>();

  Converter(Context c) {
    context = c;
  }

  void convert(Uri audio, Uri image, String title, boolean vertical, File output, Progress progress)
      throws Exception {
    File pcm = new File(context.getCacheDir(), "sound.pcm"),
        aac = new File(context.getCacheDir(), "sound.m4a"),
        video = new File(context.getCacheDir(), "picture.mp4");
    try {
      progress.update("A ler a música…");
      decode(audio, pcm);
      progress.update("A preparar o áudio…");
      encodeAudio(pcm, aac);
      progress.update("A criar o vídeo…");
      encodeVideo(pcm, image, title, vertical, video, progress);
      progress.update("A finalizar o MP4…");
      mux(video, aac, output);
    } finally {
      pcm.delete();
      aac.delete();
      video.delete();
    }
  }

  void decode(Uri uri, File pcm) throws Exception {
    MediaExtractor ex = new MediaExtractor();
    MediaCodec decoder = null;
    try {
      ex.setDataSource(context, uri, null);
      int track = -1;
      for (int i = 0; i < ex.getTrackCount(); i++)
        if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          track = i;
          break;
        }
      if (track < 0) throw new IOException("este ficheiro não contém áudio.");
      ex.selectTrack(track);
      MediaFormat f = ex.getTrackFormat(track);
      if (f.containsKey(MediaFormat.KEY_DURATION)
          && f.getLong(MediaFormat.KEY_DURATION) > 600000000L)
        throw new IOException("a música deve ter até 10 minutos.");
      decoder = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));
      decoder.configure(f, null, null, 0);
      decoder.start();
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      boolean inputEnd = false, outputEnd = false;
      long last = System.currentTimeMillis();
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(pcm))) {
        while (!outputEnd) {
          if (System.currentTimeMillis() - last > 30000)
            throw new IOException("o descodificador de áudio não respondeu.");
          if (!inputEnd) {
            int i = decoder.dequeueInputBuffer(10000);
            if (i >= 0) {
              ByteBuffer b = decoder.getInputBuffer(i);
              b.clear();
              int n = ex.readSampleData(b, 0);
              if (n < 0) {
                decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                inputEnd = true;
              } else {
                decoder.queueInputBuffer(i, 0, n, ex.getSampleTime(), 0);
                ex.advance();
              }
            }
          }
          int i = decoder.dequeueOutputBuffer(info, 10000);
          if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat o = decoder.getOutputFormat();
            sampleRate = o.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = o.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (o.containsKey(MediaFormat.KEY_PCM_ENCODING)
                && o.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT)
              throw new IOException("formato de áudio não suportado.");
          }
          if (i >= 0) {
            last = System.currentTimeMillis();
            if (info.size > 0) {
              ByteBuffer b = decoder.getOutputBuffer(i);
              b.position(info.offset);
              b.limit(info.offset + info.size);
              byte[] bytes = new byte[info.size];
              b.get(bytes);
              out.write(bytes);
              pcmBytes += bytes.length;
              if (pcmBytes > sampleRate * channels * 2L * 600)
                throw new IOException("a música deve ter até 10 minutos.");
            }
            outputEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            decoder.releaseOutputBuffer(i, false);
          }
        }
      }
      if (pcmBytes < sampleRate * channels) throw new IOException("a música é demasiado curta.");
    } finally {
      if (decoder != null) {
        decoder.stop();
        decoder.release();
      }
      ex.release();
    }
  }

  void encodeAudio(File pcm, File file) throws Exception {
    MediaFormat f = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels);
    f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
    f.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
    f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
    MediaCodec codec = MediaCodec.createEncoderByType("audio/mp4a-latm");
    MediaMuxer mux = new MediaMuxer(file.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    boolean started = false;
    try (InputStream in = new BufferedInputStream(new FileInputStream(pcm))) {
      codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      codec.start();
      int track = -1;
      boolean inputEnd = false, end = false;
      long bytes = 0, last = System.currentTimeMillis();
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      while (!end) {
        if (System.currentTimeMillis() - last > 30000)
          throw new IOException("o codificador de áudio não respondeu.");
        if (!inputEnd) {
          int i = codec.dequeueInputBuffer(10000);
          if (i >= 0) {
            ByteBuffer b = codec.getInputBuffer(i);
            b.clear();
            byte[] data =
                new byte[Math.min(b.remaining(), 16384) / (channels * 2) * (channels * 2)];
            int n = in.read(data);
            long pts = bytes * 1000000L / (sampleRate * channels * 2L);
            if (n < 0) {
              codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputEnd = true;
            } else {
              b.put(data, 0, n);
              codec.queueInputBuffer(i, 0, n, pts, 0);
              bytes += n;
            }
          }
        }
        int i = codec.dequeueOutputBuffer(info, 10000);
        if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          track = mux.addTrack(codec.getOutputFormat());
          mux.start();
          started = true;
        }
        if (i >= 0) {
          last = System.currentTimeMillis();
          ByteBuffer b = codec.getOutputBuffer(i);
          if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
            b.position(info.offset);
            b.limit(info.offset + info.size);
            mux.writeSampleData(track, b, info);
          }
          end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          codec.releaseOutputBuffer(i, false);
        }
      }
    } finally {
      codec.stop();
      codec.release();
      if (started) mux.stop();
      mux.release();
    }
  }

  void encodeVideo(
      File pcm, Uri image, String title, boolean vertical, File output, Progress progress)
      throws Exception {
    int w = vertical ? 720 : 1280, h = vertical ? 1280 : 720, fps = 24;
    long duration = pcmBytes * 1000000L / (sampleRate * channels * 2L);
    int frames = (int) ((duration * fps + 999999) / 1000000);
    // Measure each frame's audio energy for the animated waveform.
    try (InputStream in = new BufferedInputStream(new FileInputStream(pcm))) {
      byte[] bytes = new byte[(sampleRate / fps) * channels * 2];
      int n;
      while ((n = in.read(bytes)) > 0) {
        long sum = 0;
        int count = 0;
        for (int i = 0; i + 1 < n; i += 2) {
          int v = (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
          sum += (long) v * v;
          count++;
        }
        amplitudes.add((float) Math.min(1, Math.sqrt(sum / (double) Math.max(1, count)) / 12000));
      }
    }
    Bitmap background = null;
    if (image != null) {
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      try (InputStream in = context.getContentResolver().openInputStream(image)) {
        BitmapFactory.decodeStream(in, null, opts);
      }
      if (opts.outWidth <= 0) throw new IOException("a imagem não é válida.");
      opts.inJustDecodeBounds = false;
      opts.inSampleSize = 1;
      while (opts.outWidth / opts.inSampleSize > 2048 || opts.outHeight / opts.inSampleSize > 2048)
        opts.inSampleSize *= 2;
      try (InputStream in = context.getContentResolver().openInputStream(image)) {
        background = BitmapFactory.decodeStream(in, null, opts);
      }
      if (background == null) throw new IOException("não foi possível abrir a imagem.");
    }
    MediaFormat f = MediaFormat.createVideoFormat("video/avc", w, h);
    f.setInteger(
        MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    f.setInteger(MediaFormat.KEY_BIT_RATE, 2500000);
    f.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
    f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
    MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
    MediaMuxer mux = new MediaMuxer(output.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    Surface surface = null;
    Painter painter = null;
    boolean started = false;
    try {
      codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      surface = codec.createInputSurface();
      codec.start();
      painter = new Painter(surface, w, h, background, title);
      int track = -1;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      for (int frame = 0; frame <= frames; frame++) {
        if (frame < frames) {
          float energy = amplitudes.get(Math.min(frame, amplitudes.size() - 1));
          painter.draw(frame / (float) fps, energy, frame * 1000000000L / fps);
          if (frame % fps == 0) progress.update("A criar o vídeo… " + (frame * 100 / frames) + "%");
        } else codec.signalEndOfInputStream();
        boolean ended = false;
        long last = System.currentTimeMillis();
        while (true) {
          int i = codec.dequeueOutputBuffer(info, frame == frames ? 10000 : 0);
          if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (frame < frames) break;
            if (System.currentTimeMillis() - last > 30000)
              throw new IOException("o codificador de vídeo não respondeu.");
            continue;
          }
          if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            track = mux.addTrack(codec.getOutputFormat());
            mux.start();
            started = true;
          }
          if (i >= 0) {
            last = System.currentTimeMillis();
            ByteBuffer b = codec.getOutputBuffer(i);
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
              b.position(info.offset);
              b.limit(info.offset + info.size);
              mux.writeSampleData(track, b, info);
            }
            ended = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            codec.releaseOutputBuffer(i, false);
            if (ended) break;
          }
        }
        if (ended) break;
      }
    } finally {
      if (painter != null) painter.close();
      codec.stop();
      codec.release();
      if (surface != null) surface.release();
      if (started) mux.stop();
      mux.release();
      if (background != null) background.recycle();
    }
  }

  void mux(File video, File audio, File output) throws Exception {
    MediaMuxer mux = new MediaMuxer(output.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    MediaExtractor v = new MediaExtractor(), a = new MediaExtractor();
    boolean started = false;
    try {
      v.setDataSource(video.toString());
      a.setDataSource(audio.toString());
      int vt = mux.addTrack(v.getTrackFormat(0)), at = mux.addTrack(a.getTrackFormat(0));
      mux.start();
      started = true;
      copy(v, mux, vt);
      copy(a, mux, at);
    } finally {
      v.release();
      a.release();
      if (started) mux.stop();
      mux.release();
    }
  }

  void copy(MediaExtractor ex, MediaMuxer mux, int track) throws Exception {
    ex.selectTrack(0);
    ByteBuffer b = ByteBuffer.allocateDirect(4 * 1024 * 1024);
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    while (true) {
      b.clear();
      int n = ex.readSampleData(b, 0);
      if (n < 0) break;
      info.set(0, n, ex.getSampleTime(), ex.getSampleFlags());
      mux.writeSampleData(track, b, info);
      ex.advance();
    }
  }
}
