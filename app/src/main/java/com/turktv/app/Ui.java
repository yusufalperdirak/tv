package com.turktv.app;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ortak renkler ve küçük arayüz yardımcıları. Ölçüler 1280 px genişlikli tasarıma göre ölçeklenir. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0B0D12;
    static final int RAIL = 0xFF0F1218;
    static final int LINE = 0xFF1C212B;
    static final int SURFACE = 0xFF151922;
    static final int SURFACE2 = 0xFF1E2430;
    static final int KEYCAP = 0xFF252B37;
    static final int TEXT = 0xFFF2F4F7;
    static final int MUTED = 0xFF8B95A7;
    static final int SOFT = 0xFFC9D0DB;
    static final int ACCENT = 0xFFE30A17;
    static final int LIVE = 0xFFFF5A5F;
    static final int OFF = 0xFF9AA3B2;
    static final int SCRIM = 0xD10B0D12;
    static final int WHITE = 0xFFFFFFFF;

    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    /**
     * Güvenli kenar boşluğu (tasarım px). Birçok TV görüntünün kenarlarını kırpar (overscan);
     * menüler bu boşluğun içinde kalır.
     */
    static float edgeX(Context c) {
        int l = Prefs.edge(c);
        return l == 0 ? 56 : l == 2 ? 120 : 88;
    }

    static float edgeY(Context c) {
        int l = Prefs.edge(c);
        return l == 0 ? 26 : l == 2 ? 64 : 42;
    }

    /** Tasarımdaki piksel değerini bu ekrana çevirir (tasarım 1280 px genişlik). */
    static int px(Context c, float designPx) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels);
        return Math.round(designPx * w / 1280f);
    }

    static TextView text(Context c, CharSequence s, float size, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, size));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable rounded(Context c, int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(c, radius));
        return d;
    }

    static ImageView icon(Context c, int res, int sizePx, int color) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        iv.setLayoutParams(new LinearLayout.LayoutParams(px(c, sizePx), px(c, sizePx)));
        return iv;
    }

    /**
     * Kumandayla seçilebilen yuvarlak buton: odaklanınca beyaz zemin, siyah yazı.
     * icon 0 ise ikon yok; label null ise sadece ikon.
     */
    static LinearLayout button(Context c, int iconRes, String label, float height, int normalBg) {
        final LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        int padH = label == null ? 0 : px(c, 22);
        b.setPadding(padH, 0, padH, 0);
        b.setMinimumWidth(px(c, height));
        final GradientDrawable bg = rounded(c, normalBg, height / 2f);
        b.setBackground(bg);
        final ImageView iv = iconRes != 0 ? icon(c, iconRes, 20, TEXT) : null;
        final TextView tv = label != null ? text(c, label, 16, BOLD, TEXT) : null;
        if (iv != null) b.addView(iv);
        if (tv != null) {
            if (iv != null) tv.setPadding(px(c, 10), 0, 0, 0);
            b.addView(tv);
        }
        b.setLayoutParams(new LinearLayout.LayoutParams(
                label == null ? px(c, height) : LinearLayout.LayoutParams.WRAP_CONTENT, px(c, height)));
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? WHITE : normalBg);
            int fg = has ? BG : TEXT;
            if (iv != null) iv.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            if (tv != null) tv.setTextColor(fg);
            float s = has ? 1.05f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(110).start();
        });
        return b;
    }

    /** Butonun yazısını değiştirir (Ui.button ile yapılmış olmalı). */
    static void setButtonLabel(LinearLayout b, String label) {
        for (int i = 0; i < b.getChildCount(); i++) {
            View v = b.getChildAt(i);
            if (v instanceof TextView) ((TextView) v).setText(label);
        }
    }

    /** Butonun ikonunu değiştirir. */
    static void setButtonIcon(LinearLayout b, int res) {
        for (int i = 0; i < b.getChildCount(); i++) {
            View v = b.getChildAt(i);
            if (v instanceof ImageView) ((ImageView) v).setImageResource(res);
        }
    }

    static LinearLayout.LayoutParams margins(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(px(c, l), px(c, t), px(c, r), px(c, b));
        return lp;
    }
}
