package com.turktv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Uygulamalar satırındayken arkada görünen, yavaşça kayan manzara (havadan çekim hissi).
 * Görüntü dosyası yok; katmanlar burada çizilir. Her açılışta bir sonraki manzaraya geçer.
 */
final class AerialView extends View {

    private static final class Scene {
        int[] sky;          // yukarıdan aşağı
        float[] skyPos;
        int sun;            // güneş/ay parıltısı rengi
        float sunX, sunY;   // 0..1
        int[] layers;       // uzaktan yakına dağ renkleri
        Scene(int[] sky, float[] skyPos, int sun, float sx, float sy, int[] layers) {
            this.sky = sky; this.skyPos = skyPos; this.sun = sun; sunX = sx; sunY = sy; this.layers = layers;
        }
    }

    private static final Scene[] SCENES = {
            // alacakaranlıkta dağlar
            new Scene(new int[]{0xFF233B5E, 0xFF6A6F8F, 0xFFE3A57A, 0xFFF4C99A}, new float[]{0f, 0.38f, 0.58f, 0.66f},
                    0xD9FFE2B4, 0.60f, 0.44f, new int[]{0xFF6E6A86, 0xFF4A4B64, 0xFF2E3145, 0xFF1B1E2C}),
            // gün ortası, yeşil tepeler
            new Scene(new int[]{0xFF3D79B8, 0xFF7FB0DA, 0xFFC9E0EE}, new float[]{0f, 0.45f, 0.7f},
                    0x99FFFFFF, 0.25f, 0.22f, new int[]{0xFF8FA9B8, 0xFF5E8A6E, 0xFF3F6B4C, 0xFF24452F}),
            // gece, ay ışığında göl kıyısı
            new Scene(new int[]{0xFF050A18, 0xFF13213F, 0xFF2B3A5E}, new float[]{0f, 0.5f, 0.68f},
                    0xB3DCE4FF, 0.72f, 0.24f, new int[]{0xFF2A3452, 0xFF1C243C, 0xFF121829, 0xFF0A0E19}),
    };
    private static int next;

    private final Scene scene;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] paths = new Path[4];
    private int builtW, builtH;
    private long startedAt;
    private Shader skyShader;
    private RadialGradient sunShader;
    private final android.graphics.Matrix sunMatrix = new android.graphics.Matrix();
    private boolean running;

    AerialView(Context c) {
        super(c);
        scene = SCENES[next++ % SCENES.length];
        for (int i = 0; i < paths.length; i++) paths[i] = new Path();
    }

    void start() {
        if (running) return;
        running = true;
        startedAt = System.currentTimeMillis();
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
    }

    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        skyShader = new LinearGradient(0, 0, 0, h, scene.sky, scene.skyPos, Shader.TileMode.CLAMP);
        sunShader = new RadialGradient(0, 0, h * 0.45f, new int[]{scene.sun, scene.sun & 0x40FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP);
        // dağ sırtları: [taban yüksekliği (0..1), tepe sayısı, tepe genliği]
        float[][] spec = {{0.47f, 7, 0.10f}, {0.58f, 6, 0.09f}, {0.71f, 5, 0.08f}, {0.84f, 4, 0.07f}};
        for (int l = 0; l < 4; l++) {
            Path p = paths[l];
            p.reset();
            float base = spec[l][0] * h, amp = spec[l][2] * h;
            int peaks = (int) spec[l][1];
            float ww = w * 1.3f, x0 = -w * 0.15f;
            p.moveTo(x0, h);
            p.lineTo(x0, base);
            int steps = peaks * 2;
            for (int i = 1; i <= steps; i++) {
                float x = x0 + ww * i / steps;
                // tepe/vadi sırayla, hafif düzensiz
                float jitter = (float) Math.sin(i * 12.9898 + l * 78.233) * 0.35f;
                float y = (i % 2 == 1) ? base - amp * (0.75f + jitter * 0.5f) : base + amp * 0.15f * jitter;
                p.lineTo(x, y);
            }
            p.lineTo(x0 + ww, h);
            p.close();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (w != builtW || h != builtH) build(w, h);

        float t = running ? (System.currentTimeMillis() - startedAt) / 1000f : 0f;
        // 70 sn'lik yavaş salınım + hafif yakınlaşma
        float phase = (float) Math.sin(t * 2 * Math.PI / 70.0);
        float scale = 1.08f + 0.03f * (float) Math.sin(t * 2 * Math.PI / 110.0);

        c.save();
        c.scale(scale, scale, w / 2f, h / 2f);

        paint.setShader(skyShader);
        c.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, paint);

        float sx = w * scene.sunX + phase * w * 0.01f, sy = h * scene.sunY;
        sunMatrix.setTranslate(sx, sy);
        sunShader.setLocalMatrix(sunMatrix);
        paint.setShader(sunShader);
        c.drawCircle(sx, sy, h * 0.45f, paint);
        paint.setShader(null);

        for (int l = 0; l < 4; l++) {
            paint.setColor(scene.layers[l]);
            float dx = phase * w * (0.008f + 0.012f * l); // yakın katman daha çok kayar (derinlik)
            c.save();
            c.translate(dx, 0);
            c.drawPath(paths[l], paint);
            c.restore();
        }
        c.restore();

        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }
}
