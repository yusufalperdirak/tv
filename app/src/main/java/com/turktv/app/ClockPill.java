package com.turktv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;

/**
 * Ana ekranın sağ üstü: yarı saydam beyaz bir hap; saat rakamları ve soldaki takvim ikonu
 * hapın içinden oyulmuştur (tam saydam, arkadaki görüntü görünür). Takvim kısmı Bugün
 * sayfasını açar; odaklanınca hap tam beyaz olur ve hafifçe büyür.
 */
final class ClockPill extends View {

    static final int W = 178, H = 48;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path icon = new Path();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean colonOn = true, running, active;
    private final float s;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            colonOn = !colonOn;
            invalidate();
            if (running) ui.postDelayed(this, 1000);
        }
    };

    ClockPill(Context c) {
        super(c);
        s = Ui.px(c, 1000) / 1000f;
        setLayerType(LAYER_TYPE_HARDWARE, null); // oyma (DST_OUT) için kendi katmanı
        fill.setColor(0xFFFFFFFF);
        PorterDuffXfermode out = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
        cut.setXfermode(out);
        cut.setStyle(Paint.Style.STROKE);
        cut.setStrokeWidth(2.2f * s);
        cut.setStrokeCap(Paint.Cap.ROUND);
        cut.setStrokeJoin(Paint.Join.ROUND);
        cutText.setXfermode(out);
        // ince rakamlar: Inter Thin + hafif kontur ≈ Light kalınlığı (hap içinde okunaklı kalır)
        cutText.setTypeface(Ui.THIN);
        cutText.setStyle(Paint.Style.FILL_AND_STROKE);
        cutText.setStrokeWidth(0.85f * s);
        cutText.setTextSize(27 * s);
        cutText.setLetterSpacing(0.02f);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Bugün");
        // odakta büyümez (kesilme olmasın): yalnızca hap tam beyaz olur
        setOnFocusChangeListener((v, has) -> invalidate());
    }

    /** Bugün sayfası açıkken takvim dairesi dolu görünür. */
    void setActive(boolean a) {
        active = a;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(W * s), Math.round(H * s));
    }

    void start() {
        if (running) return;
        running = true;
        ui.removeCallbacks(tick);
        invalidate();
        ui.postDelayed(tick, 1000);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
    }

    void update() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        fill.setAlpha(isFocused() ? 255 : 209);
        cv.drawRoundRect(rect, h / 2, h / 2, fill);

        if (active) { // Bugün sayfası açık: ikon tam beyaz bir dairenin içinde
            fill.setAlpha(255);
            cv.drawCircle(25 * s, 24 * s, 20 * s, fill);
        }

        // takvim ikonu (24x24 kutu, 13,12'de)
        cv.save();
        cv.translate(13 * s, 12 * s);
        icon.reset();
        RectF r = new RectF(2.5f * s, 4.5f * s, 21.5f * s, 20.5f * s);
        icon.addRoundRect(r, 4 * s, 4 * s, Path.Direction.CW);
        cut.setStyle(Paint.Style.STROKE);
        cut.setAlpha(255);
        cv.drawPath(icon, cut);
        cv.drawLine(2.5f * s, 10 * s, 21.5f * s, 10 * s, cut);
        cv.drawLine(8 * s, 2.5f * s, 8 * s, 6.5f * s, cut);
        cv.drawLine(16 * s, 2.5f * s, 16 * s, 6.5f * s, cut);
        cut.setStyle(Paint.Style.FILL);
        cv.drawCircle(12 * s, 15.2f * s, 1.6f * s, cut);
        cut.setStyle(Paint.Style.STROKE);
        cv.restore();

        // ayraç
        cut.setStyle(Paint.Style.FILL);
        cut.setAlpha(140);
        cv.drawRoundRect(new RectF(50 * s, 13 * s, 52 * s, 35 * s), s, s, cut);
        cut.setStyle(Paint.Style.STROKE);
        cut.setAlpha(255);

        // saat
        Calendar cal = Calendar.getInstance();
        String hh = String.format(Actions.TR, "%02d", cal.get(Calendar.HOUR_OF_DAY));
        String mm = String.format(Actions.TR, "%02d", cal.get(Calendar.MINUTE));
        float x = 66 * s, base = 33 * s;
        cutText.setAlpha(255);
        cv.drawText(hh, x, base, cutText);
        x += cutText.measureText(hh);
        cutText.setAlpha(colonOn ? 255 : 90);
        cv.drawText(":", x, base, cutText);
        x += cutText.measureText(":");
        cutText.setAlpha(255);
        cv.drawText(mm, x, base, cutText);
    }
}
