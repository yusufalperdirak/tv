package com.turktv.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Açılış: siyah ekranda "DIRAK" bulanıktan netleşerek belirir, üzerinden cam parıltısı geçer,
 * cam tınısı çalar, sonra bulanıklaşarak kaybolur. Bu sırada son kanal arka planda hazırlanır.
 */
public class SplashActivity extends Activity {

    private static final long IN_MS = 1300, HOLD_MS = 1100, OUT_MS = 750;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean done;
    private TextView word;
    private StreamPlayer warm;
    private final Matrix shineMatrix = new Matrix();
    private LinearGradient shine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // arkadaki yumuşak ışık
        View glow = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x2EAABEE6, 0x00AABEE6});
        g.setGradientRadius(Ui.px(this, 380));
        glow.setBackground(g);
        glow.setAlpha(0f);
        root.addView(glow, new FrameLayout.LayoutParams(Ui.px(this, 900), Ui.px(this, 420), Gravity.CENTER));

        word = new TextView(this);
        word.setText("DIRAK");
        word.setTextColor(0xFFD3D8E0);
        word.setTypeface(Ui.THIN);
        word.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(this, 116));
        word.setIncludeFontPadding(false);
        word.setLetterSpacing(0.55f);
        word.setGravity(Gravity.CENTER);
        word.setAlpha(0f);
        word.setScaleX(1.06f);
        word.setScaleY(1.06f);
        root.addView(word, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        setContentView(root);

        // Kanal listesi ve son kanalın yayını bu sırada hazırlansın ("ışık hızında açılış")
        Health.init(this);
        try {
            List<Channel> list = Lineup.get(this);
            Channel last = list.get(Lineup.lastIndex(this));
            if (last.youtube.isEmpty()) {
                warm = new StreamPlayer(this);
                warm.play(last, true);
            }
        } catch (Exception ignored) {
        }

        if (Prefs.splashSound(this)) Chime.play(650);

        PathInterpolator ease = new PathInterpolator(0.22f, 0.7f, 0.2f, 1f);
        word.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(IN_MS).setInterpolator(ease).start();
        glow.animate().alpha(1f).setDuration(IN_MS + 300).start();

        ValueAnimator in = ValueAnimator.ofFloat(0f, 1f);
        in.setDuration(IN_MS);
        in.setInterpolator(ease);
        in.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            word.setLetterSpacing(0.55f - 0.17f * f);
            setBlur(30f * (1 - f));
        });
        in.start();

        // cam parıltısı
        word.post(() -> {
            int w = Math.max(1, word.getWidth());
            shine = new LinearGradient(0, 0, w * 0.35f, 0,
                    new int[]{0xFFD3D8E0, 0xFFFFFFFF, 0xFFD3D8E0}, new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            ValueAnimator s = ValueAnimator.ofFloat(-0.4f, 1.2f);
            s.setStartDelay(IN_MS - 300);
            s.setDuration(1200);
            s.addUpdateListener(a -> {
                shineMatrix.setTranslate(w * (float) a.getAnimatedValue(), 0);
                shine.setLocalMatrix(shineMatrix);
                word.getPaint().setShader(shine);
                word.invalidate();
            });
            s.start();
        });

        ui.postDelayed(() -> {
            word.animate().alpha(0f).scaleX(0.985f).scaleY(0.985f).setDuration(OUT_MS).setInterpolator(ease).start();
            glow.animate().alpha(0f).setDuration(OUT_MS).start();
            ValueAnimator out = ValueAnimator.ofFloat(0f, 1f);
            out.setDuration(OUT_MS);
            out.addUpdateListener(a -> setBlur(22f * (float) a.getAnimatedValue()));
            out.start();
        }, IN_MS + HOLD_MS);
        ui.postDelayed(this::go, IN_MS + HOLD_MS + OUT_MS);
    }

    private void setBlur(float r) {
        if (Build.VERSION.SDK_INT < 31) return;
        if (r < 0.5f) word.setRenderEffect(null);
        else word.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL));
    }

    private void go() {
        if (done || isFinishing()) return;
        done = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (warm != null) warm.release(); // oynatıcı kısa süre açık kalır, ana ekran devralır
        super.onDestroy();
    }
}
