package com.turktv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Uygulamalara geçince arkada görünen koyu arka plan: siyah zemin üzerinde yavaşça dolaşan
 * koyu yeşil / camgöbeği ışık bulutları (Apple TV havasında, kendi çizimimiz).
 */
final class AerialView extends View {

    private static final int BASE = 0xFF030605;
    // {renk, yarıçap (yüksekliğe oran), merkez x, merkez y, salınım periyodu sn}
    private static final float[][] BLOBS = {
            {0, 0.95f, 0.22f, 0.30f, 47},
            {1, 0.80f, 0.78f, 0.62f, 61},
            {2, 0.70f, 0.55f, 0.15f, 73},
            {3, 0.60f, 0.12f, 0.85f, 53},
    };
    private static final int[] COLORS = {0x8C0E4D3A, 0x7A0B3F44, 0x6618563F, 0x5C0A2E35};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[BLOBS.length];
    private final Matrix m = new Matrix();
    private Shader vignette;
    private int builtW, builtH;
    private long startedAt;
    private boolean running;

    AerialView(Context c) {
        super(c);
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

    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        for (int i = 0; i < BLOBS.length; i++) {
            int col = COLORS[(int) BLOBS[i][0]];
            shaders[i] = new RadialGradient(0, 0, h * BLOBS[i][1], new int[]{col, col & 0x33FFFFFF, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
        vignette = new LinearGradient(0, 0, 0, h, new int[]{0x00000000, 0x00000000, 0x99000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (w != builtW || h != builtH) build(w, h);
        c.drawColor(BASE);
        float t = (System.currentTimeMillis() - startedAt) / 1000f;
        for (int i = 0; i < BLOBS.length; i++) {
            float[] b = BLOBS[i];
            double ph = 2 * Math.PI * t / b[4];
            float x = w * (b[2] + 0.10f * (float) Math.sin(ph));
            float y = h * (b[3] + 0.08f * (float) Math.cos(ph * 0.8 + i));
            float s = 1f + 0.12f * (float) Math.sin(ph * 1.3 + i * 2);
            m.setScale(s, s);
            m.postTranslate(x, y);
            shaders[i].setLocalMatrix(m);
            paint.setShader(shaders[i]);
            c.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(vignette);
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
