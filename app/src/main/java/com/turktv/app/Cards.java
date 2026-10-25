package com.turktv.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Logolu kanal kartı: logo kartın zeminini kaplar, kanal adı altta. */
final class Cards {
    private Cards() {}

    interface OnFocus {
        void focused(Channel c, View card, boolean has);
    }

    static final class Holder {
        Channel channel;
        GradientDrawable bg;
        TextView name;
        boolean moving;
    }

    /** Design px cinsinden boyut. focusable=false ise sadece gösterim (izleme ekranı logosu gibi). */
    static FrameLayout make(Context ctx, Channel c, float w, float h, boolean focusable, OnFocus onFocus) {
        final FrameLayout card = new FrameLayout(ctx);
        final Holder hd = new Holder();
        hd.channel = c;
        card.setTag(hd);
        card.setFocusable(focusable);
        card.setClickable(focusable);
        card.setLongClickable(focusable);

        final float radius = Math.max(10, h * 0.15f);
        final int fallback = c.color;
        Integer known = Logos.cachedBg(c.logo, fallback);
        hd.bg = Ui.rounded(ctx, known != null ? known : fallback, radius);
        card.setBackground(hd.bg);
        card.setClipToOutline(true);

        // Logo yoksa kanal adı büyük yazılır
        TextView big = Ui.text(ctx, c.name, Math.max(14, h * 0.22f), Ui.BOLD, Ui.WHITE);
        big.setGravity(Gravity.CENTER);
        big.setMaxLines(2);
        big.setEllipsize(TextUtils.TruncateAt.END);
        big.setPadding(Ui.px(ctx, 10), 0, Ui.px(ctx, 10), Ui.px(ctx, h * 0.18f));
        card.addView(big, new FrameLayout.LayoutParams(-1, -1));

        ImageView logo = new ImageView(ctx);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams lLp = new FrameLayout.LayoutParams(-1, -1);
        lLp.setMargins(Ui.px(ctx, w * 0.12f), Ui.px(ctx, h * 0.12f), Ui.px(ctx, w * 0.12f), Ui.px(ctx, h * 0.30f));
        card.addView(logo, lLp);

        if (h >= 60) {
            View scrim = new View(ctx);
            GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0x8C000000, 0x00000000});
            scrim.setBackground(sg);
            card.addView(scrim, new FrameLayout.LayoutParams(-1, Ui.px(ctx, h * 0.48f), Gravity.BOTTOM));

            hd.name = Ui.text(ctx, c.name, Math.max(11, h * 0.13f), Ui.BOLD, Ui.WHITE);
            hd.name.setSingleLine(true);
            hd.name.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams nLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            nLp.setMargins(Ui.px(ctx, 12), 0, Ui.px(ctx, 10), Ui.px(ctx, h * 0.08f));
            card.addView(hd.name, nLp);
            hd.name.setVisibility(c.logo.isEmpty() ? View.GONE : View.VISIBLE);
        }

        Logos.into(ctx, c.logo, logo, big, fallback, color -> hd.bg.setColor(color));

        if (focusable) {
            card.setOnFocusChangeListener((v, has) -> {
                style(ctx, v, has);
                if (onFocus != null) onFocus.focused(c, v, has);
            });
        }
        refresh(card);
        return card;
    }

    static Channel channel(View card) {
        Object t = card.getTag();
        return t instanceof Holder ? ((Holder) t).channel : null;
    }

    static void setMoving(Context ctx, View card, boolean moving) {
        Holder hd = (Holder) card.getTag();
        hd.moving = moving;
        style(ctx, card, card.hasFocus());
    }

    static void style(Context ctx, View v, boolean has) {
        Holder hd = (Holder) v.getTag();
        // Seçili kart çerçevesiz, hafifçe büyür ve gölge alır (taşınırken kesik çizgili çerçeve)
        if (hd.moving) {
            v.animate().scaleX(1.10f).scaleY(1.10f).translationY(-Ui.px(ctx, 10)).setDuration(220).setInterpolator(Ui.FOCUS).start();
            hd.bg.setStroke(Ui.px(ctx, 3), Ui.WHITE, Ui.px(ctx, 8), Ui.px(ctx, 5));
        } else {
            hd.bg.setStroke(0, Ui.WHITE);
            Ui.focusLift(ctx, v, has);
        }
        refresh(v);
    }

    /** Yayın durumuna göre soluklaştırır. */
    static void refresh(View v) {
        Holder hd = (Holder) v.getTag();
        boolean off = Health.status(hd.channel) == Health.OFFLINE;
        v.setAlpha(v.hasFocus() || !off ? 1f : 0.4f);
    }
}
