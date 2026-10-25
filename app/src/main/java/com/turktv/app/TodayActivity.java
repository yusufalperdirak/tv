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
        dim.setBackgroundColor(0x8C06080C);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        float ex = Ui.edgeX(this), ey = Ui.edgeY(this);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.text(this, "Bugün", 40, Ui.BOLD, Ui.TEXT));
        String date = new SimpleDateFormat("d MMMM EEEE", new Locale("tr")).format(new Date());
        String city = Info.city(this);
        head.addView(Ui.text(this, city != null ? date + " · " + city : date, 17, Ui.MEDIUM, Ui.SOFT),
                Ui.margins(this, -2, -2, 0, 4, 0, 0));
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hLp.setMargins(px(ex), px(ey - 4), 0, 0);
        root.addView(head, hLp);

        clock = new ClockPill(this);
        clock.setFocusable(false);
        clock.setActive(true);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cLp.setMargins(0, px(ey), px(ex), 0);
        root.addView(clock, cLp);

        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(-1, px(486), Gravity.TOP);
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

    private LinearLayout.LayoutParams tileLp(boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.rightMargin = last ? 0 : px(28);
        return lp;
    }

    private LinearLayout tile(int icon, String title) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = Ui.rounded(this, 0x800E1016, 34);
        d.setStroke(Math.max(1, px(1)), 0x14FFFFFF);
        t.setBackground(d);
        t.setPadding(px(28), px(28), px(28), px(28));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout circle = new FrameLayout(this);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(0xDBFFFFFF);
        circle.setBackground(c);
        ImageView iv = Ui.icon(this, icon, 30, 0xFF151922);
        circle.addView(iv, new FrameLayout.LayoutParams(px(30), px(30), Gravity.CENTER));
        row.addView(circle, new LinearLayout.LayoutParams(px(56), px(56)));
        row.addView(Ui.text(this, title, 19, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 14, 0, 0, 0));
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

    private View weatherTile() {
        Info.Weather w = Info.weather(this);
        LinearLayout t = tile(w != null ? Info.icon(w) : R.drawable.ic_w_cloud, "Hava durumu");
        if (w == null) {
            t.addView(Ui.text(this, "Bilgi alınıyor…", 18, Ui.MEDIUM, Ui.SOFT), Ui.margins(this, -2, -2, 0, 46, 0, 0));
            return t;
        }
        TextView big = Ui.text(this, deg(w.temp), 104, Ui.THIN, Ui.TEXT);
        big.setIncludeFontPadding(false);
        t.addView(big, Ui.margins(this, -2, -2, 0, 40, 0, 0));
        t.addView(Ui.text(this, Info.label(w.code), 22, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 0, 10, 0, 0));
        double[] mm = Info.todayMaxMin(this);
        if (mm != null) t.addView(Ui.text(this, "En yüksek " + deg(mm[0]) + " · En düşük " + deg(mm[1]), 16, Ui.MEDIUM, Ui.SOFT),
                Ui.margins(this, -2, -2, 0, 8, 0, 0));
        t.addView(spacer());
        String city = Info.city(this);
        t.addView(Ui.text(this, city != null ? city : "", 14, Ui.REGULAR, Ui.MUTED));
        return t;
    }

    private View rateRow(String sym, String name, double v) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.rounded(this, 0x12FFFFFF, 22));
        r.setPadding(px(20), px(18), px(20), px(18));
        TextView s = Ui.text(this, sym, 20, Ui.BOLD, Ui.TEXT);
        s.setGravity(Gravity.CENTER);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(0x24FFFFFF);
        s.setBackground(c);
        r.addView(s, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView n = Ui.text(this, name, 17, Ui.BOLD, Ui.SOFT);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, -2, 1f);
        nLp.leftMargin = px(14);
        r.addView(n, nLp);
        r.addView(Ui.text(this, String.format(new Locale("tr"), "₺ %.2f", v), 26, Ui.BOLD, Ui.TEXT));
        return r;
    }

    private View ratesTile() {
        LinearLayout t = tile(R.drawable.ic_swap, "Döviz kuru");
        double[] r = Info.rates(this);
        if (r == null) {
            t.addView(Ui.text(this, "Bilgi alınıyor…", 18, Ui.MEDIUM, Ui.SOFT), Ui.margins(this, -2, -2, 0, 46, 0, 0));
            return t;
        }
        t.addView(rateRow("€", "1 Euro", r[0]), Ui.margins(this, -1, -2, 0, 40, 0, 0));
        t.addView(rateRow("$", "1 Dolar", r[1]), Ui.margins(this, -1, -2, 0, 14, 0, 0));
        t.addView(spacer());
        long at = Info.ratesAt(this);
        if (at > 0) t.addView(Ui.text(this, "Güncelleme " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(at)),
                14, Ui.REGULAR, Ui.MUTED));
        return t;
    }

    private View prayerTile() {
        LinearLayout t = tile(R.drawable.ic_crescent, "Ezan vakitleri");
        String[] times = Info.prayerTimes(this);
        if (times == null) {
            t.addView(Ui.text(this, "Bilgi alınıyor…", 18, Ui.MEDIUM, Ui.SOFT), Ui.margins(this, -2, -2, 0, 46, 0, 0));
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
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < times.length; i++) {
            if (i == next) {
                LinearLayout hi = new LinearLayout(this);
                hi.setOrientation(LinearLayout.VERTICAL);
                hi.setBackground(Ui.rounded(this, 0xFFFFFFFF, 18));
                hi.setPadding(px(16), px(10), px(16), px(10));
                hi.addView(line(names[i], times[i], 19, Ui.BOLD, 0xFF0B0D12));
                String in = nextIn >= 60 ? (nextIn / 60) + " sa " + (nextIn % 60) + " dk sonra" : nextIn + " dk sonra";
                hi.addView(Ui.text(this, in, 13, Ui.BOLD, 0xFF4B5260));
                list.addView(hi, Ui.margins(this, -1, -2, 0, 3, 0, 3));
            } else {
                View l = line(names[i], times[i], 17, Ui.MEDIUM, i < next || next < 0 ? Ui.MUTED : Ui.SOFT);
                l.setPadding(px(16), px(8), px(16), px(8));
                list.addView(l);
            }
        }
        t.addView(list, Ui.margins(this, -1, -2, 0, 22, 0, 0));
        return t;
    }

    private LinearLayout line(String a, String b, float size, android.graphics.Typeface tf, int color) {
        LinearLayout l = new LinearLayout(this);
        TextView n = Ui.text(this, a, size, tf, color);
        l.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        l.addView(Ui.text(this, b, size, tf, color));
        return l;
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
