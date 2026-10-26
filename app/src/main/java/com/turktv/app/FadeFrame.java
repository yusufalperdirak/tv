package com.turktv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * Üst kenarı yumuşakça kaybolan alan: içindeki satırlar yukarı kaydıkça sert bir çizgiyle
 * kesilmez, ekranın üstüne doğru giderek silikleşir.
 */
final class FadeFrame extends FrameLayout {
    private final Paint mask = new Paint();
    private final int from, to; // bu iki yükseklik arasında saydamdan tama geçer (px)
    private int lastH = -1;

    FadeFrame(Context c, int fromPx, int toPx) {
        super(c);
        from = fromPx;
        to = toPx;
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setWillNotDraw(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (lastH != getHeight()) {
            lastH = getHeight();
            mask.setShader(new LinearGradient(0, from, 0, to, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, 0, getWidth(), to, mask);
    }
}
