package com.turktv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Hava kartının arkasındaki canlı sahne: açık havada dönen ışınlı güneş, gece ay ve parlayan yıldızlar,
 * süzülen bulutlar, yağan yağmur ya da kar, fırtınada şimşek, siste kayan pus bantları.
 * Yazıların okunması için sahne kartın sağ üstünde yoğun, aşağı doğru hafif.
 */
final class WeatherAnim extends View {
    private static final int SUN = 0, MOON = 1, CLOUD = 2, RAIN = 3, SNOW = 4, STORM = 5, FOG = 6;

    private final int kind;
    private final boolean day, sunPeek;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float s;
    private final float[][] parts;      // yağmur/kar/yıldız parçacıkları: x, y, hız, faz (0..1 oranlı)
    private boolean running;
    private final long t0 = SystemClock.uptimeMillis();
    private long nextFlash;
    private float flash;

    WeatherAnim(Context c, int code, boolean isDay) {
        super(c);
        s = Ui.px(c, 1000) / 1000f;
        day = isDay;
        if (code == 0 || code == 1) kind = isDay ? SUN : MOON;
        else if (code == 45 || code == 48) kind = FOG;
        else if (code >= 95) kind = STORM;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) kind = SNOW;
        else if (code >= 51) kind = RAIN;
        else kind = CLOUD;
        sunPeek = kind == CLOUD && code == 2 && isDay;
        Random r = new Random(7);
        int n = kind == RAIN || kind == STORM ? 46 : kind == SNOW ? 34 : kind == MOON ? 26 : 0;
        parts = new float[n][4];
        for (float[] q : parts) {
            q[0] = r.nextFloat();
            q[1] = r.nextFloat();
            q[2] = 0.6f + r.nextFloat() * 0.8f;
            q[3] = r.nextFloat();
        }
        nextFlash = t0 + 2500;
    }

    void setRunning(boolean on) {
        running = on;
        if (on) postInvalidateOnAnimation();
    }

    boolean isRunning() {
        return running;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0) return;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float cx = w * 0.74f, cy = h * 0.2f;

        switch (kind) {
            case SUN: sun(c, cx, cy, t, 1f); break;
            case MOON: stars(c, w, h, t); moon(c, cx, cy); break;
            case CLOUD:
                if (sunPeek) sun(c, cx + w * 0.06f, cy - h * 0.03f, t, 0.8f);
                clouds(c, w, h, t, 0xFFFFFFFF, day ? 0.22f : 0.16f);
                break;
            case RAIN: clouds(c, w, h, t, 0xFFB8C2D4, 0.22f); rain(c, w, h, t); break;
            case STORM: storm(c, w, h, t); break;
            case SNOW: clouds(c, w, h, t, 0xFFE6ECF5, 0.2f); snow(c, w, h, t); break;
            case FOG: fog(c, w, h, t); break;
        }
        if (running) postInvalidateOnAnimation();
    }

    private void sun(Canvas c, float cx, float cy, float t, float k) {
        float r = 44 * s * k;
        float pulse = 1f + 0.06f * (float) Math.sin(t * 1.6);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 3.2f * pulse, 0x66FFC94D, 0x00FFC94D, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 3.2f * pulse, p);
        p.setShader(null);
        // dönen ışınlar
        p.setColor(0x99FFD36A);
        p.setStrokeWidth(5 * s * k);
        p.setStrokeCap(Paint.Cap.ROUND);
        float a0 = t * 12f;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(a0 + i * 30);
            float r1 = r * 1.35f, r2 = r * (i % 2 == 0 ? 1.85f : 1.65f);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
        }
        p.setColor(0xFFFFC94D);
        c.drawCircle(cx, cy, r, p);
    }

    private void moon(Canvas c, float cx, float cy) {
        float r = 40 * s;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 2.6f, 0x40DDE6FF, 0x00DDE6FF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 2.6f, p);
        p.setShader(null);
        p.setColor(0xFFEDEFF7);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xEB12151C); // kart rengiyle oyulmuş hilal
        c.drawCircle(cx + r * 0.45f, cy - r * 0.25f, r * 0.85f, p);
    }

    private void stars(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        for (float[] q : parts) {
            float a = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * 2f * q[2] + q[3] * 6.28f));
            p.setColor(((int) (a * 200) << 24) | 0xFFFFFF);
            c.drawCircle(q[0] * w, q[1] * h * 0.6f, (1.2f + q[2]) * s, p);
        }
    }

    /** Üç bulut: farklı hız ve boyutta yatayda süzülür, kartın kenarından çıkıp diğer kenarından girer. */
    private void clouds(Canvas c, float w, float h, float t, int color, float alpha) {
        p.setStyle(Paint.Style.FILL);
        float[][] cl = {{0.55f, 0.16f, 1.15f, 9f}, {0.15f, 0.30f, 0.85f, 6f}, {0.85f, 0.38f, 0.7f, 4.5f}};
        for (int i = 0; i < cl.length; i++) {
            float span = w * 1.6f;
            float x = ((cl[i][0] * w + t * cl[i][3] * s) % span) - w * 0.3f;
            float y = cl[i][1] * h, k = cl[i][2];
            int a = (int) (255 * alpha * (i == 0 ? 1.4f : 1f));
            p.setColor((Math.min(255, a) << 24) | (color & 0xFFFFFF));
            cloud(c, x, y, 52 * s * k);
        }
    }

    private void cloud(Canvas c, float x, float y, float r) {
        c.drawCircle(x, y, r, p);
        c.drawCircle(x - r * 1.05f, y + r * 0.35f, r * 0.72f, p);
        c.drawCircle(x + r * 1.1f, y + r * 0.3f, r * 0.8f, p);
        c.drawRoundRect(x - r * 1.75f, y + r * 0.2f, x + r * 1.9f, y + r * 1.02f, r * 0.5f, r * 0.5f, p);
    }

    private void rain(Canvas c, float w, float h, float t) {
        p.setColor(0x8CA9C8FF);
        p.setStrokeWidth(2.4f * s);
        p.setStrokeCap(Paint.Cap.ROUND);
        float len = 18 * s;
        for (float[] q : parts) {
            float y = ((q[1] + t * 0.55f * q[2]) % 1f) * h;
            float x = q[0] * w - y * 0.12f;
            c.drawLine(x, y, x - len * 0.12f, y + len, p);
        }
    }

    private void snow(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCCFFFFFF);
        for (float[] q : parts) {
            float y = ((q[1] + t * 0.08f * q[2]) % 1f) * h;
            float x = q[0] * w + (float) Math.sin(t * 1.2f + q[3] * 6.28f) * 10 * s;
            c.drawCircle(x, y, (2f + q[2] * 2f) * s, p);
        }
    }

    private void storm(Canvas c, float w, float h, float t) {
        long now = SystemClock.uptimeMillis();
        if (now > nextFlash) {
            flash = 1f;
            nextFlash = now + 3500 + (long) (Math.random() * 4500);
        }
        clouds(c, w, h, t, 0xFF8E98AC, 0.3f);
        rain(c, w, h, t);
        if (flash > 0.02f) {
            // şimşek: kısa beyaz parlama ve zikzak çizgi
            c.drawColor(((int) (flash * 70) << 24) | 0xFFFFFF);
            p.setColor(((int) (flash * 255) << 24) | 0xFFF3C4);
            p.setStrokeWidth(4 * s);
            float x = w * 0.72f, y = h * 0.28f, d = 26 * s;
            c.drawLine(x, y, x - d, y + d * 1.6f, p);
            c.drawLine(x - d, y + d * 1.6f, x + d * 0.4f, y + d * 1.9f, p);
            c.drawLine(x + d * 0.4f, y + d * 1.9f, x - d * 0.6f, y + d * 3.6f, p);
            flash *= 0.88f;
        }
    }

    private void fog(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            float y = h * (0.12f + i * 0.17f);
            float x = (float) Math.sin(t * 0.25f + i) * w * 0.15f;
            p.setColor(0x24FFFFFF);
            c.drawRoundRect(x - w * 0.2f, y, x + w * 1.2f, y + 22 * s, 11 * s, 11 * s, p);
        }
    }
}
