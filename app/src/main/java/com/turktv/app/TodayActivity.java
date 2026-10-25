package com.turktv.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Bugün: hava durumu, döviz kuru ve ezan vakitleri; yuvarlak ikonlu üç kart. Geri ile kapanır. */
public class TodayActivity extends Activity {

    private ClockPill clock;
    private LinearLayout tiles;

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0E1A16);

        // arka plan: İstanbul fotoğrafı, bulanık ve karartılmış
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try (InputStream in = getAssets().open("bg/istanbul.jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 16; // küçük çöz, büyütünce yumuşak görünür
            Bitmap bm = BitmapFactory.decodeStream(in, null, o);
            bg.setImageBitmap(bm);
        } catch (Exception ignored) {
        }
        if (Build.VERSION.SDK_INT >= 31) {
            bg.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(px(24), px(24),
                    android.graphics.Shader.TileMode.CLAMP));
        }
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this);
        dim.setBackgroundColor(0xB306080C);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        float ex = Ui.edgeX(this), ey = Ui.edgeY(this);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.text(this, "Bugün", 46, Ui.BOLD, Ui.TEXT));
        String date = new SimpleDateFormat("d MMMM EEEE", new Locale("tr")).format(new Date());
        String city = Info.city(this);
        head.addView(Ui.text(this, city != null ? date + " · " + city : date, 22, Ui.BOLD, 0xFFC9D0DB),
                Ui.margins(this, -2, -2, 0, 4, 0, 0));
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hLp.setMargins(px(ex), px(ey - 4), 0, 0);
        root.addView(head, hLp);

        clock = new ClockPill(this);
        clock.setFocusable(false);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cLp.setMargins(0, px(ey), px(ex), 0);
        root.addView(clock, cLp);

        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(-1, px(512), Gravity.TOP);
        tLp.setMargins(px(ex), px(150), px(ex), 0);
        root.addView(tiles, tLp);
        fill();

        setContentView(root);
        Info.refresh(this, this::fill);
    }

    private void fill() {
        tiles.removeAllViews();
        tiles.addView(weatherTile(), tileLp(false));
        tiles.addView(ratesTile(), tileLp(false));
        tiles.addView(prayerTile(), tileLp(true));
    }

    // Uzaktan okunur tasarım: büyük ve kalın yazılar, koyu ve neredeyse opak kartlar, az ama net bilgi.
    private static final int TILE_BG = 0xEB12151C, DIM = 0xFFAEB6C4;

    private LinearLayout.LayoutParams tileLp(boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.rightMargin = last ? 0 : px(28);
        return lp;
    }

    private LinearLayout tile(int icon, String title) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = Ui.rounded(this, TILE_BG, 36);
        d.setStroke(Math.max(1, px(1)), 0x1FFFFFFF);
        t.setBackground(d);
        t.setPadding(px(32), px(30), px(32), px(30));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout circle = new FrameLayout(this);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(0xFFFFFFFF);
        circle.setBackground(c);
        ImageView iv = Ui.icon(this, icon, 34, 0xFF12151C);
        circle.addView(iv, new FrameLayout.LayoutParams(px(34), px(34), Gravity.CENTER));
        row.addView(circle, new LinearLayout.LayoutParams(px(64), px(64)));
        row.addView(Ui.text(this, title, 25, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 18, 0, 0, 0));
        t.addView(row);
        return t;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        return v;
    }

    private String deg(double d) {
        return Math.round(d) + "°";
    }

    private void waiting(LinearLayout t) {
        t.addView(Ui.text(this, "Bilgi alınıyor…", 24, Ui.MEDIUM, DIM), Ui.margins(this, -2, -2, 0, 48, 0, 0));
    }

    private View weatherTile() {
        Info.Weather w = Info.weather(this);
        LinearLayout t = tile(w != null ? Info.icon(w) : R.drawable.ic_w_cloud, "Hava");
        if (w == null) {
            waiting(t);
            return t;
        }
        TextView big = Ui.text(this, deg(w.temp), 140, Ui.MEDIUM, Ui.TEXT);
        big.setIncludeFontPadding(false);
        big.setLetterSpacing(-0.03f);
        t.addView(big, Ui.margins(this, -2, -2, 0, 34, 0, 0));
        t.addView(Ui.text(this, Info.label(w.code), 32, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 0, 14, 0, 0));
        t.addView(spacer());
        double[] mm = Info.todayMaxMin(this);
        if (mm != null) {
            LinearLayout hl = new LinearLayout(this);
            hl.addView(Ui.text(this, "↑ " + deg(mm[0]), 30, Ui.BOLD, Ui.TEXT));
            hl.addView(Ui.text(this, "↓ " + deg(mm[1]), 30, Ui.BOLD, DIM), Ui.margins(this, -2, -2, 28, 0, 0, 0));
            t.addView(hl);
        }
        return t;
    }

    private View rateRow(String sym, String name, double v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView s = Ui.text(this, sym, 24, Ui.BOLD, 0xFF12151C);
        s.setGravity(Gravity.CENTER);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(0xFFE8ECF2);
        s.setBackground(c);
        top.addView(s, new LinearLayout.LayoutParams(px(46), px(46)));
        top.addView(Ui.text(this, name, 24, Ui.BOLD, DIM), Ui.margins(this, -2, -2, 14, 0, 0, 0));
        r.addView(top);
        TextView val = Ui.text(this, String.format(new Locale("tr"), "%.2f ₺", v), 60, Ui.BOLD, Ui.TEXT);
        val.setIncludeFontPadding(false);
        r.addView(val, Ui.margins(this, -2, -2, 0, 10, 0, 0));
        return r;
    }

    private View ratesTile() {
        LinearLayout t = tile(R.drawable.ic_swap, "Döviz");
        double[] r = Info.rates(this);
        if (r == null) {
            waiting(t);
            return t;
        }
        t.addView(rateRow("€", "Euro", r[0]), Ui.margins(this, -1, -2, 0, 40, 0, 0));
        View line = new View(this);
        line.setBackgroundColor(0x24FFFFFF);
        t.addView(line, Ui.margins(this, -1, Math.max(1, px(1)), 0, 26, 0, 26));
        t.addView(rateRow("$", "Dolar", r[1]));
        return t;
    }

    private View prayerTile() {
        LinearLayout t = tile(R.drawable.ic_crescent, "Ezan");
        String[] times = Info.prayerTimes(this);
        if (times == null) {
            waiting(t);
            return t;
        }
        String[] names = Info.prayerNames();
        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int next = -1, nextIn = 0;
        for (int i = 0; i < times.length; i++) {
            int m = Integer.parseInt(times[i].substring(0, 2)) * 60 + Integer.parseInt(times[i].substring(3, 5));
            if (m > nowMin) {
                next = i;
                nextIn = m - nowMin;
                break;
            }
        }
        t.addView(spacer()); // vakitler kartın dikeyde ortasında dursun
        // sıradaki vakit büyük ve beyaz kartta
        if (next >= 0) {
            LinearLayout hi = new LinearLayout(this);
            hi.setOrientation(LinearLayout.VERTICAL);
            hi.setBackground(Ui.rounded(this, 0xFFFFFFFF, 24));
            hi.setPadding(px(22), px(16), px(22), px(16));
            LinearLayout l1 = new LinearLayout(this);
            l1.setGravity(Gravity.BOTTOM);
            l1.addView(Ui.text(this, names[next], 30, Ui.BOLD, 0xFF12151C), new LinearLayout.LayoutParams(0, -2, 1f));
            l1.addView(Ui.text(this, times[next], 44, Ui.BOLD, 0xFF12151C));
            hi.addView(l1);
            String in = nextIn >= 60 ? (nextIn / 60) + " sa " + (nextIn % 60) + " dk sonra" : nextIn + " dk sonra";
            hi.addView(Ui.text(this, in, 22, Ui.BOLD, 0xFFC20812), Ui.margins(this, -2, -2, 0, 2, 0, 0));
            t.addView(hi, Ui.margins(this, -1, -2, 0, 0, 0, 14));
        }
        // diğer vakitler: iki eşit sütun, her hücre kendi içinde ortalı
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        int col = 0;
        for (int i = 0; i < times.length; i++) {
            if (i == next) continue;
            if (col % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                g.addView(row, Ui.margins(this, -1, -2, 0, col == 0 ? 0 : 8, 0, 0));
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, px(8), 0, px(8));
            cell.setBackground(Ui.rounded(this, 0x14FFFFFF, 18));
            boolean past = next < 0 || i < next;
            TextView n = Ui.text(this, names[i], 18, Ui.BOLD, past ? 0xFF7E8796 : DIM);
            n.setGravity(Gravity.CENTER);
            cell.addView(n);
            TextView tv = Ui.text(this, times[i], 28, Ui.BOLD, past ? 0xFF7E8796 : Ui.TEXT);
            tv.setGravity(Gravity.CENTER);
            cell.addView(tv, Ui.margins(this, -2, -2, 0, 4, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (col % 2 == 0) lp.rightMargin = px(5); else lp.leftMargin = px(5);
            row.addView(cell, lp);
            col++;
        }
        // tek kalan vakit tüm satırı kaplar, ortada durur
        if (col % 2 == 1 && row != null && row.getChildCount() == 1)
            ((LinearLayout.LayoutParams) row.getChildAt(0).getLayoutParams()).rightMargin = 0;
        t.addView(g, new LinearLayout.LayoutParams(-1, -2));
        t.addView(spacer());
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        clock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clock.stop();
    }
}
