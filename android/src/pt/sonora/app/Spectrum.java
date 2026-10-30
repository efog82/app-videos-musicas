package pt.sonora.app;

/** Small FFT with explicit frequency bands. No network and no synthetic 'beat' claims. */
final class Spectrum {
  static final class Features {
    float energy, bass, mid, treble, beat;
    final float[] bins = new float[48], wave = new float[256];
  }

  static Features analyse(short[] mono, int sampleRate, float previousBass) {
    int n = 2048;
    double[] real = new double[n], imag = new double[n];
    double energy = 0;
    for (int i = 0; i < n; i++) {
      double x = i < mono.length ? mono[i] / 32768.0 : 0;
      energy += x * x;
      real[i] = x * (.5 - .5 * Math.cos(2 * Math.PI * i / (n - 1)));
    }
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double t = real[i];
        real[i] = real[j];
        real[j] = t;
      }
    }
    for (int len = 2; len <= n; len <<= 1) {
      double angle = -2 * Math.PI / len;
      for (int start = 0; start < n; start += len)
        for (int k = 0; k < len / 2; k++) {
          double a = Math.cos(angle * k), b = Math.sin(angle * k);
          int u = start + k, v = u + len / 2;
          double vr = real[v] * a - imag[v] * b, vi = real[v] * b + imag[v] * a;
          real[v] = real[u] - vr;
          imag[v] = imag[u] - vi;
          real[u] += vr;
          imag[u] += vi;
        }
    }
    Features f = new Features();
    for (int i = 0; i < 256; i++) f.wave[i] = i * 8 < mono.length ? mono[i * 8] / 32768f : 0;
    f.energy = (float) Math.min(1, Math.sqrt(energy / n) * 3);
    double lo = 0, mid = 0, hi = 0;
    for (int k = 1; k < n / 2; k++) {
      double hz = k * sampleRate / (double) n, mag = Math.hypot(real[k], imag[k]) / n;
      int bin = Math.min(47, (int) (Math.log(1 + hz / 40) / Math.log(1 + sampleRate / 80.0) * 48));
      f.bins[bin] = Math.max(f.bins[bin], (float) Math.min(1, mag * 12));
      if (hz < 250) lo += mag * mag;
      else if (hz < 4000) mid += mag * mag;
      else hi += mag * mag;
    }
    f.bass = (float) Math.min(1, Math.sqrt(lo) * 8);
    f.mid = (float) Math.min(1, Math.sqrt(mid) * 8);
    f.treble = (float) Math.min(1, Math.sqrt(hi) * 8);
    f.beat = Math.max(0, f.bass - previousBass) * 2;
    return f;
  }
}
