package com.turktv.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Açılıştaki cam tınısı: kayıt dosyası yok, ses burada üretilir.
 * İki nota; her biri birkaç uyumsuz kısmi tondan oluşur (cama vurulmuş gibi), yavaşça söner.
 */
final class Chime {
    private Chime() {}

    private static final int RATE = 44100;

    static void play(final long delayMs) {
        new Thread(() -> {
            try {
                short[] pcm = render();
                if (delayMs > 0) Thread.sleep(delayMs);
                AudioTrack t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(pcm.length * 2)
                        .build();
                t.write(pcm, 0, pcm.length);
                t.play();
                Thread.sleep(pcm.length * 1000L / RATE + 200);
                t.release();
            } catch (Throwable ignored) {
            }
        }, "chime").start();
    }

    private static short[] render() {
        double seconds = 3.2;
        int n = (int) (RATE * seconds);
        double[] buf = new double[n];
        // {frekans çarpanı, genlik, sönüm süresi (sn)}
        double[][] partials = {{1, 1.0, 2.8}, {2.76, 0.42, 1.6}, {5.40, 0.18, 0.9}, {8.93, 0.07, 0.5}};
        double[][] notes = {{1568.0, 0.0}, {2349.3, 0.16}}; // G6, D7
        for (double[] note : notes) {
            int start = (int) (note[1] * RATE);
            for (double[] p : partials) {
                double f = note[0] * p[0];
                double tau = p[2] / 6.9; // genlik ~0.001'e iner
                double detune = 1 + (Math.random() - 0.5) * 0.0007;
                for (int i = start; i < n; i++) {
                    double t = (i - start) / (double) RATE;
                    double attack = Math.min(1, t / 0.006);
                    double env = attack * Math.exp(-t / tau);
                    if (env < 1e-4 && t > 0.05) break;
                    buf[i] += p[1] * 0.5 * env * Math.sin(2 * Math.PI * f * detune * t);
                }
            }
        }
        double peak = 1e-9;
        for (double v : buf) peak = Math.max(peak, Math.abs(v));
        double gain = 0.30 / peak;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) Math.round(buf[i] * gain * 32767);
        return out;
    }
}
