package com.turktv.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cihazda yüklü uygulamalar ve ana ekrandaki uygulama kartları. */
final class Apps {
    private Apps() {}

    static final class Entry {
        String label;
        Drawable banner;   // TV uygulamalarının yatay afişi (varsa)
        Drawable icon;
        Intent launch;     // yüklü uygulama
        int builtinIcon;   // bizim bölümlerimiz (Ara, Tarayıcı…)
        int color;
        Runnable action;
        String pkg;
    }

    interface Callback {
        void done(List<Entry> list);
    }

    private static final Handler ui = new Handler(Looper.getMainLooper());

    /** Yüklü uygulamaları arka planda okur (TV uygulamaları önce, sonra diğerleri; alfabetik). */
    static void load(Context ctx, Callback cb) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            List<Entry> out = new ArrayList<>();
            try {
                PackageManager pm = c.getPackageManager();
                Set<String> seen = new HashSet<>();
                seen.add(c.getPackageName());
                addAll(pm, out, seen, Intent.CATEGORY_LEANBACK_LAUNCHER);
                addAll(pm, out, seen, Intent.CATEGORY_LAUNCHER);
            } catch (Exception ignored) {
            }
            ui.post(() -> cb.done(out));
        }, "apps").start();
    }

    private static void addAll(PackageManager pm, List<Entry> out, Set<String> seen, String category) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> l = pm.queryIntentActivities(q, 0);
        List<Entry> part = new ArrayList<>();
        for (ResolveInfo r : l) {
            ActivityInfo a = r.activityInfo;
            if (a == null || !seen.add(a.packageName)) continue;
            if (hidden(a.packageName, String.valueOf(r.loadLabel(pm)))) continue;
            Entry e = new Entry();
            e.pkg = a.packageName;
            e.label = String.valueOf(r.loadLabel(pm));
            try {
                e.banner = a.loadBanner(pm);
            } catch (Exception ignored) {
            }
            try {
                e.icon = r.loadIcon(pm);
            } catch (Exception ignored) {
            }
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(a.packageName, a.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            e.launch = li;
            e.color = colorFor(e.label);
            part.add(e);
        }
        Collections.sort(part, (x, y) -> x.label.compareToIgnoreCase(y.label));
        out.addAll(part);
    }

    /** Ana ekranda gösterilmeyen uygulamalar (ör. Launcher Manager). */
    private static boolean hidden(String pkg, String label) {
        String p = pkg.toLowerCase(java.util.Locale.ROOT), l = label.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        return l.contains("launchermanager") || p.contains("launchermanager");
    }

    private static final int[] PALETTE = {0xFF0E7490, 0xFF4B5563, 0xFF2563EB, 0xFF6D28D9, 0xFFBE185D,
            0xFF15803D, 0xFF0F766E, 0xFFB45309, 0xFF9A3412, 0xFF4338CA, 0xFF334155, 0xFF0369A1};

    static int colorFor(String s) {
        return PALETTE[Math.abs(s.hashCode()) % PALETTE.length];
    }

    static Entry builtin(String label, int icon, int color, Runnable action) {
        Entry e = new Entry();
        e.label = label;
        e.builtinIcon = icon;
        e.color = color;
        e.action = action;
        return e;
    }

    // ------------------------------------------------------------------ kart

    interface OnFocus {
        void focused(Entry e, View card, boolean has);
    }

    /** Uygulama kartı (tasarım px: w×h). Altına odaklanınca adı yazılır (label). */
    static FrameLayout card(Context ctx, Entry e, float w, float h, OnFocus onFocus) {
        final FrameLayout card = new FrameLayout(ctx);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(e);
        final android.graphics.drawable.GradientDrawable bg = Ui.rounded(ctx, e.color, 30); // daha yuvarlak köşeler
        card.setBackground(bg);
        card.setClipToOutline(true);

        if (e.banner != null) {
            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(e.banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(ctx);
            if (e.builtinIcon != 0) {
                iv.setImageResource(e.builtinIcon);
            } else if (e.icon != null) {
                iv.setImageDrawable(e.icon);
                Integer c = dominant(e.icon);
                if (c != null) bg.setColor(c);
            }
            int s = Ui.px(ctx, h * 0.42f);
            col.addView(iv, new LinearLayout.LayoutParams(s, s));
            card.setContentDescription(e.label);
            if (e.builtinIcon != 0) { // sistem kartları: kırmızı zemin, beyaz simge, yazısız
                iv.setColorFilter(Ui.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                card.addView(col, new FrameLayout.LayoutParams(-1, -1));
                card.setOnFocusChangeListener((v, has) -> {
                    Ui.focusLift(ctx, v, has);
                    if (onFocus != null) onFocus.focused(e, v, has);
                });
                return card;
            }
            TextView t = Ui.text(ctx, e.label, 13, Ui.BOLD, Ui.WHITE);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.px(ctx, 8), 0, Ui.px(ctx, 8), 0);
            col.addView(t, Ui.margins(ctx, -1, -2, 0, 6, 0, 0));
            card.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }

        card.setOnFocusChangeListener((v, has) -> {
            Ui.focusLift(ctx, v, has);
            if (onFocus != null) onFocus.focused(e, v, has);
        });
        return card;
    }

    /** Simgenin baskın rengini koyulaştırıp zemin yapar (ikonlu kartlar için). */
    private static Integer dominant(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 16, 16);
            d.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = b.getPixel(x, y);
                    if ((p >>> 24) < 128) continue;
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    bl += p & 0xFF;
                    n++;
                }
            }
            b.recycle();
            if (n == 0) return null;
            int rr = (int) (r / n * 0.45), gg = (int) (g / n * 0.45), bb = (int) (bl / n * 0.45);
            return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        } catch (Exception e) {
            return null;
        }
    }
}
