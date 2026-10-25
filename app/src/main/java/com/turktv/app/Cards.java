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

        // Beyaz zemin üzerinde kanal logosu; kartta yazı yok (logo yoksa kanal adı koyu renkle yazılır)
        final float radius = Math.max(10, h * 0.15f);
        hd.bg = Ui.rounded(ctx, Ui.WHITE, radius);
        card.setBackground(hd.bg);
        card.setClipToOutline(true);

        final TextView big = Ui.text(ctx, c.name, Math.max(14, h * 0.2f), Ui.BOLD, 0xFF1A1D24);
        big.setGravity(Gravity.CENTER);
        big.setMaxLines(2);
        big.setEllipsize(TextUtils.TruncateAt.END);
        big.setPadding(Ui.px(ctx, 10), 0, Ui.px(ctx, 10), 0);
        card.addView(big, new FrameLayout.LayoutParams(-1, -1));

        final ImageView logo = new ImageView(ctx);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams lLp = new FrameLayout.LayoutParams(-1, -1);
        lLp.setMargins(Ui.px(ctx, w * 0.14f), Ui.px(ctx, h * 0.16f), Ui.px(ctx, w * 0.14f), Ui.px(ctx, h * 0.16f));
        card.addView(logo, lLp);

        android.graphics.Bitmap own = Logos.bundled(ctx, c.name);
        if (own != null) {
            logo.setImageBitmap(own);
            big.setVisibility(View.GONE);
        } else {
            // gömülü logo yoksa listedeki logo adresi denenir; açık renkli logo koyu zemine alınır
            Logos.into(ctx, c.logo, logo, big, 0xFF1F2430, color -> {
                if (Logos.isLight(c.logo)) hd.bg.setColor(0xFF1F2430);
            });
        }

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
