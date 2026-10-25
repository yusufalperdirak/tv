package com.turktv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Programın ne kadarının geçtiğini gösteren halka; ortada kalan dakika. */
final class ProgRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float progress;
    private String value = "", unit = "";

    ProgRing(Context c) {
        super(c);
        float s = Ui.px(c, 1000) / 1000f;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(7 * s);
        track.setColor(0xFFE4E7EC);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(7 * s);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(Ui.LIVE);
        big.setTypeface(Ui.BOLD);
        big.setTextSize(24 * s);
        big.setColor(0xFF0B0D12);
        big.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Ui.BOLD);
        small.setTextSize(11.5f * s);
        small.setColor(0xFF5A6272);
        small.setTextAlign(Paint.Align.CENTER);
    }

    void set(float p, String value, String unit) {
        progress = Math.max(0f, Math.min(1f, p));
        this.value = value;
        this.unit = unit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), sw = track.getStrokeWidth();
        r.set(sw / 2 + 1, sw / 2 + 1, w - sw / 2 - 1, h - sw / 2 - 1);
        c.drawOval(r, track);
        if (progress > 0) c.drawArc(r, -90, 360 * progress, false, arc);
        float cy = h / 2f;
        c.drawText(value, w / 2f, cy + big.getTextSize() * 0.22f, big);
        c.drawText(unit, w / 2f, cy + big.getTextSize() * 0.22f + small.getTextSize() * 1.35f, small);
    }
}
