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

    static final int H = 48, PAD = 24;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean colonOn = true, running;
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
        // ince rakamlar: Inter Thin + hafif kontur ≈ Light kalınlığı (hap içinde okunaklı kalır)
        cutText.setTypeface(Ui.REGULAR); // koyu zeminde net okunan beyaz rakamlar
        cutText.setStyle(Paint.Style.FILL);
        cutText.setTextSize(28 * s);
        cutText.setLetterSpacing(0.02f);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Saat · Bugün sayfasını açar");
        // odakta büyümez (kesilme olmasın): yalnızca hap tam beyaz olur
        setOnFocusChangeListener((v, has) -> invalidate());
    }

    @Override
    protected void onMeasure(int w, int h) {
        // yalnızca saat: iki yanda eşit boşluk
        float tw = cutText.measureText("00:00");
        setMeasuredDimension(Math.round(tw + 2 * PAD * s), Math.round(H * s));
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
        // yarı saydam siyah zemin, beyaz rakamlar; seçiliyken beyaz zemin, siyah rakamlar
        boolean f = isFocused();
        fill.setColor(f ? 0xFFFFFFFF : 0x8C000000);
        cv.drawRoundRect(rect, h / 2, h / 2, fill);
        cutText.setColor(f ? 0xFF0B0D12 : 0xFFFFFFFF);

        // saat
        Calendar cal = Calendar.getInstance();
        String hh = String.format(Actions.TR, "%02d", cal.get(Calendar.HOUR_OF_DAY));
        String mm = String.format(Actions.TR, "%02d", cal.get(Calendar.MINUTE));
        Paint.FontMetrics fm = cutText.getFontMetrics();
        // saat hapın tam ortasında
        float tw = cutText.measureText(hh + ":" + mm);
        float x = (w - tw) / 2f, base = h / 2f - (fm.ascent + fm.descent) / 2f; // dikeyde tam ortada
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
