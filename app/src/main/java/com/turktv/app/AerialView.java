package com.turktv.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;

import java.io.InputStream;

/**
 * Uygulamalara geçince arkada görünen arka plan: İstanbul (Kız Kulesi) fotoğrafı, Apple TV'deki
 * gibi çok yavaş yaklaşıp kayar. Fotoğraf ekran çözünürlüğüne göre (4K'ya kadar) yüklenir.
 * Yüklenemezse koyu yeşil ışıklı zemin çizilir.
 */
final class AerialView extends View {

    private static final String ASSET = "bg/istanbul.jpg";
    private static Bitmap photo; // tüm ekranlar paylaşır, bir kez yüklenir

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix m = new Matrix();
    private Shader shade;
    private RadialGradient glow;
    private int builtW, builtH;
    private long startedAt;
    private boolean running;

    AerialView(Context c) {
        super(c);
        if (photo == null) load(c.getApplicationContext());
    }

    private void load(final Context c) {
        new Thread(() -> {
            try {
                DisplayMetrics dm = c.getResources().getDisplayMetrics();
                int screenW = Math.max(dm.widthPixels, dm.heightPixels);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = c.getAssets().open(ASSET)) {
                    BitmapFactory.decodeStream(in, null, o);
                }
                int sample = 1;
                // ekranın yaklaşık %15 fazlası (yavaş yaklaşma için) kadar çözünürlük yeter
                while (o.outWidth / (sample * 2) >= screenW * 1.15f) sample *= 2;
                BitmapFactory.Options d = new BitmapFactory.Options();
                d.inSampleSize = sample;
                d.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap b;
                try (InputStream in = c.getAssets().open(ASSET)) {
                    b = BitmapFactory.decodeStream(in, null, d);
                }
                final Bitmap fb = b;
                new Handler(Looper.getMainLooper()).post(() -> {
                    photo = fb;
                    invalidate();
                });
            } catch (Throwable ignored) {
            }
        }, "arka-plan").start();
    }

    void start() {
        if (running) return;
        running = true;
        if (startedAt == 0) startedAt = System.currentTimeMillis();
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (w != builtW || h != builtH) {
            builtW = w;
            builtH = h;
            // alt kısım hafifçe kararır: uygulama kartları okunaklı kalsın
            shade = new LinearGradient(0, 0, 0, h, new int[]{0x26000000, 0x00000000, 0x8C000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            glow = new RadialGradient(w * 0.3f, h * 0.35f, h, new int[]{0x8C0E4D3A, 0x00000000},
                    null, Shader.TileMode.CLAMP);
        }
        float t = (System.currentTimeMillis() - startedAt) / 1000f;
        Bitmap b = photo;
        if (b != null && !b.isRecycled()) {
            // Ken Burns: 90 sn'de bir gidip gelen çok yavaş yaklaşma ve kayma
            double ph = 2 * Math.PI * t / 90.0;
            float zoom = 1.06f + 0.05f * (float) (0.5 - 0.5 * Math.cos(ph));
            float base = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * zoom;
            float dw = b.getWidth() * base, dh = b.getHeight() * base;
            float dx = (w - dw) / 2f + (float) Math.sin(ph * 0.7) * (dw - w) * 0.35f;
            float dy = (h - dh) / 2f + (float) Math.sin(ph * 0.5) * (dh - h) * 0.25f;
            m.setScale(base, base);
            m.postTranslate(dx, dy);
            paint.setShader(null);
            c.drawBitmap(b, m, paint);
        } else {
            c.drawColor(0xFF030605);
            paint.setShader(glow);
            c.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(shade);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }
}
