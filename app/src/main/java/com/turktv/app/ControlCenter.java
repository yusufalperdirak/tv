package com.turktv.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Ana ekranın Kontrol Merkezi (saate basınca sağ üstten açılır). Apple TV'deki gibi sıvı cam kutular:
 * büyük parlaklık karesi, İnternet / Gece modu / Ses / Uyku hapları ve dört yuvarlak kısa yol.
 * Parlaklık ve Ses: OK ile ayar moduna girilir, ▲▼ değiştirir, OK ya da Geri bitirir.
 */
final class ControlCenter extends FrameLayout {

    interface Host {
        void openToday();
        void openSearch();
        void startSaver();
        void cycleSource();
        void closed();
    }

    private static final int GLASS = 0x24FFFFFF, GLASS_STROKE = 0x47FFFFFF, FOCUS_BG = 0xF2FFFFFF, INK = 0xFF15171C;

    private final Context ctx;
    private final Host host;
    private final Window window;
    private final AudioManager audio;
    private final LinearLayout panel;
    private final List<View> tiles = new ArrayList<>();
    private boolean open;

    // ayarlanabilen kutular
    private View brightTile, volTile;
    private View brightFill;
    private TextView brightLabel;
    private TextView netSub, nightSub, volSub, sleepSub;
    private View adjusting; // ayar modundaki kutu (parlaklık ya da ses)
    private TextView hint;  // seçili yuvarlak düğmenin adı

    private int px(float v) {
        return Ui.px(ctx, v);
    }

    ControlCenter(Context c, Window w, Host h) {
        super(c);
        ctx = c;
        host = h;
        window = w;
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        setVisibility(GONE);
        setClickable(true);
        setClipChildren(false);

        // sağ üst köşe hafifçe kararır
        View dim = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x8C000000, 0x00000000});
        g.setGradientRadius(px(620));
        g.setGradientCenter(1f, 0f);
        dim.setBackground(g);
        addView(dim, new LayoutParams(-1, -1));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        LayoutParams pLp = new LayoutParams(px(292), -2, Gravity.TOP | Gravity.END);
        pLp.setMargins(0, px(Ui.edgeY(c) + 60), px(Ui.edgeX(c) - 14), 0);
        addView(panel, pLp);

        int gap = px(12);
        // satır 1-2: büyük parlaklık karesi + sağda iki hap
        LinearLayout top = row();
        brightTile = buildBright();
        top.addView(brightTile, new LinearLayout.LayoutParams(px(140), px(140)));
        LinearLayout rightCol = new LinearLayout(c);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setClipChildren(false);
        View net = pill(R.drawable.ic_wifi, 0xFF5AE0F0, false, "İnternet", "", v -> openWifi());
        netSub = (TextView) net.getTag();
        rightCol.addView(net, new LinearLayout.LayoutParams(-1, px(64)));
        View night = pill(R.drawable.ic_moon, 0xFF3A3F52, true, "Gece modu", "", v -> toggleNight());
        nightSub = (TextView) night.getTag();
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(-1, px(64));
        nLp.topMargin = gap;
        rightCol.addView(night, nLp);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(0, -2, 1f);
        rcLp.leftMargin = gap;
        top.addView(rightCol, rcLp);
        panel.addView(top);

        // satır 3: ses + uyku
        LinearLayout mid = row();
        volTile = pill(R.drawable.ic_sound, 0xFF5AE0F0, false, "Ses", "", v -> startAdjust(volTile));
        volSub = (TextView) volTile.getTag();
        mid.addView(volTile, new LinearLayout.LayoutParams(0, px(64), 1f));
        View sleep = pill(R.drawable.ic_timer, 0xFF3A3F52, true, "Uyku", "", v -> cycleSleep());
        sleepSub = (TextView) sleep.getTag();
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, px(64), 1f);
        sLp.leftMargin = gap;
        mid.addView(sleep, sLp);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(-1, -2);
        midLp.topMargin = gap;
        panel.addView(mid, midLp);

        // satır 4: yuvarlak kısa yollar
        LinearLayout circles = row();
        circles.setGravity(Gravity.CENTER_VERTICAL);
        int[] icons = {R.drawable.ic_swap, R.drawable.ic_tv, R.drawable.ic_calendar, R.drawable.ic_search};
        String[] names = {"Kaynak değiştir", "Ekran koruyucu", "Bugün", "Ara"};
        Runnable[] acts = {
                () -> { close(); host.cycleSource(); },
                () -> { close(); host.startSaver(); },
                () -> { close(); host.openToday(); },
                () -> { close(); host.openSearch(); }};
        for (int i = 0; i < icons.length; i++) {
            View cv = circle(icons[i], names[i], acts[i]);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(px(62), px(62));
            if (i > 0) cLp.leftMargin = (px(292) - 4 * px(62)) / 3;
            circles.addView(cv, cLp);
        }
        LinearLayout.LayoutParams cirLp = new LinearLayout.LayoutParams(-1, -2);
        cirLp.topMargin = gap;
        panel.addView(circles, cirLp);
        hint = Ui.text(c, "", 15, Ui.BOLD, 0xFFFFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setShadowLayer(px(6), 0, 0, 0x80000000);
        panel.addView(hint, Ui.margins(c, -1, -2, 0, 12, 0, 0));
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        return r;
    }

    private GradientDrawable glass(float radius, boolean focus) {
        GradientDrawable d = Ui.rounded(ctx, focus ? FOCUS_BG : GLASS, radius);
        d.setStroke(Math.max(1, px(1)), focus ? 0xFFFFFFFF : GLASS_STROKE);
        return d;
    }

    /** Odakta tvOS gibi: beyazlaşır, büyür, gölge alır; içindeki simge ve yazılar koyulaşır. */
    private void focusStyle(View v, float radius, boolean has, List<ImageView> icons, List<TextView> texts, int iconColor) {
        v.setBackground(glass(radius, has));
        v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? px(14) : 0)
                .setDuration(has ? 260 : 200).setInterpolator(Ui.FOCUS).start();
        for (ImageView i : icons) i.setColorFilter(has ? INK : iconColor);
        for (TextView t : texts) t.setTextColor(has ? INK : (t.getTag() == null ? 0xFFFFFFFF : 0xBFFFFFFF));
    }

    private View buildBright() {
        final FrameLayout f = new FrameLayout(ctx);
        f.setBackground(glass(30, false));
        f.setClipToOutline(true);
        f.setFocusable(true);
        f.setClickable(true);
        brightFill = new View(ctx);
        brightFill.setBackgroundColor(0x38FFFFFF);
        brightFill.setPivotY(px(140));
        f.addView(brightFill, new LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        final ImageView ic = Ui.icon(ctx, R.drawable.ic_brightness, 52, 0xFFFFFFFF);
        col.addView(ic, new LinearLayout.LayoutParams(px(52), px(52)));
        brightLabel = Ui.text(ctx, "", 15, Ui.BOLD, 0xFFFFFFFF);
        col.addView(brightLabel, Ui.margins(ctx, -2, -2, 0, 10, 0, 0));
        f.addView(col, new LayoutParams(-1, -1));
        final List<ImageView> is = new ArrayList<>();
        is.add(ic);
        final List<TextView> ts = new ArrayList<>();
        ts.add(brightLabel);
        f.setOnFocusChangeListener((v, has) -> {
            if (!has && adjusting == v) adjusting = null;
            focusStyle(v, 30, has, is, ts, 0xFFFFFFFF);
            brightFill.setBackgroundColor(has ? 0x1F15171C : 0x38FFFFFF);
        });
        f.setOnClickListener(v -> startAdjust(f));
        tiles.add(f);
        return f;
    }

    /** Hap: solda simge (ya da beyaz daire içinde simge), sağda başlık ve değer. Değer yazısı tag olarak döner. */
    private View pill(int icon, int iconColor, boolean inDisc, String title, String sub, OnClickListener click) {
        LinearLayout p = new LinearLayout(ctx);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(px(14), 0, px(12), 0);
        p.setBackground(glass(32, false));
        p.setFocusable(true);
        p.setClickable(true);
        final List<ImageView> is = new ArrayList<>();
        ImageView ic;
        if (inDisc) {
            FrameLayout disc = new FrameLayout(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xE6FFFFFF);
            disc.setBackground(d);
            ic = Ui.icon(ctx, icon, 18, iconColor);
            disc.addView(ic, new LayoutParams(px(18), px(18), Gravity.CENTER));
            p.addView(disc, new LinearLayout.LayoutParams(px(32), px(32)));
        } else {
            ic = Ui.icon(ctx, icon, 26, iconColor);
            p.addView(ic, new LinearLayout.LayoutParams(px(26), px(26)));
            is.add(ic);
        }
        LinearLayout tx = new LinearLayout(ctx);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(ctx, title, 15, Ui.BOLD, 0xFFFFFFFF);
        t.setSingleLine(true);
        tx.addView(t);
        TextView s = Ui.text(ctx, sub, 13, Ui.REGULAR, 0xBFFFFFFF);
        s.setSingleLine(true);
        s.setTag("sub");
        tx.addView(s);
        LinearLayout.LayoutParams txLp = new LinearLayout.LayoutParams(0, -2, 1f);
        txLp.leftMargin = px(10);
        p.addView(tx, txLp);
        final List<TextView> ts = new ArrayList<>();
        ts.add(t);
        ts.add(s);
        final int ink = iconColor;
        p.setOnFocusChangeListener((v, has) -> {
            if (!has && adjusting == v) adjusting = null;
            focusStyle(v, 32, has, is, ts, ink);
        });
        p.setOnClickListener(click);
        p.setTag(s);
        tiles.add(p);
        return p;
    }

    private View circle(int icon, String desc, Runnable act) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackground(glass(31, false));
        f.setFocusable(true);
        f.setClickable(true);
        f.setContentDescription(desc);
        ImageView ic = Ui.icon(ctx, icon, 26, 0xFFFFFFFF);
        f.addView(ic, new LayoutParams(px(26), px(26), Gravity.CENTER));
        final List<ImageView> is = new ArrayList<>();
        is.add(ic);
        f.setOnFocusChangeListener((v, has) -> {
            focusStyle(v, 31, has, is, new ArrayList<>(), 0xFFFFFFFF);
            if (has) hint.setText(desc);
            else if (desc.contentEquals(hint.getText())) hint.setText("");
        });
        f.setOnClickListener(v -> act.run());
        tiles.add(f);
        return f;
    }

    // ------------------------------------------------------------------ değerler

    private void refresh() {
        int b = Prefs.brightness(ctx);
        brightLabel.setText("Parlaklık · " + b + "%");
        brightFill.setScaleY(b / 100f);
        netSub.setText(netStatus());
        if (Prefs.nightMode(ctx)) {
            int[] h = Prefs.NIGHT_HOURS[Prefs.nightHours(ctx)];
            nightSub.setText(String.format(Actions.TR, "%02d:00 – %02d:00", h[0], h[1]));
        } else nightSub.setText("Kapalı");
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        volSub.setText(max > 0 ? String.valueOf(Math.round(cur * 100f / max)) : "–");
        long now = System.currentTimeMillis();
        sleepSub.setText(Prefs.sleepAt > now ? ((Prefs.sleepAt - now + 59_999) / 60_000) + " dk sonra" : "Kapalı");
    }

    private String netStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "Bağlı değil";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Kablolu";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            return "Bağlı";
        } catch (Exception e) {
            return "Bağlı";
        }
    }

    private void openWifi() {
        try {
            ctx.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                ctx.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    private void toggleNight() {
        Prefs.nightMode(ctx, !Prefs.nightMode(ctx));
        refresh();
    }

    private void cycleSleep() {
        long now = System.currentTimeMillis();
        long left = Prefs.sleepAt > now ? (Prefs.sleepAt - now) / 60_000 : 0;
        int next = left <= 0 ? 30 : left <= 30 ? 60 : left <= 60 ? 90 : 0;
        Prefs.sleepAtProgramEnd = false;
        Prefs.sleepAt = next == 0 ? 0 : now + next * 60_000L;
        refresh();
    }

    private void startAdjust(View tile) {
        adjusting = adjusting == tile ? null : tile;
        tile.animate().scaleX(adjusting != null ? 1.12f : 1.08f).scaleY(adjusting != null ? 1.12f : 1.08f).setDuration(180).start();
        if (adjusting != null) Toast.makeText(ctx, "▲ ▼ ile ayarla, OK ile bitir", Toast.LENGTH_SHORT).show();
    }

    private void adjust(int dir) {
        if (adjusting == brightTile) {
            int b = Math.max(10, Math.min(100, Prefs.brightness(ctx) + dir * 10));
            Prefs.brightness(ctx, b);
            applyBrightness(ctx, window);
            brightFill.animate().scaleY(b / 100f).setDuration(180).start();
            brightLabel.setText("Parlaklık · " + b + "%");
        } else if (adjusting == volTile) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            refresh();
        }
    }

    /**
     * Parlaklığı uygular: izin verildiyse TV'nin kendi ışığı (sistem ayarı), her durumda uygulama penceresi.
     */
    static void applyBrightness(Context c, Window w) {
        int b = Prefs.brightness(c);
        if (w != null) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenBrightness = b >= 100 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : b / 100f;
            w.setAttributes(lp);
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && Settings.System.canWrite(c)) {
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.round(b * 2.55f));
            }
        } catch (Exception ignored) {
        }
    }

    /** İlk parlaklık ayarında, TV'nin ışığını da değiştirebilmek için sistem izni bir kez istenir. */
    private void askWritePermissionOnce() {
        if (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(ctx) || Prefs.askedWriteSettings(ctx)) return;
        Prefs.askedWriteSettings(ctx, true);
        try {
            ctx.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Toast.makeText(ctx, "TV ışığını da ayarlayabilmek için izni aç", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ açma / kapama

    boolean isOpen() {
        return open;
    }

    void open() {
        if (open) return;
        open = true;
        adjusting = null;
        refresh();
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(300).start();
        // panel sağ üstten yaylanarak iner, kutular sırayla yerine oturur
        panel.setPivotX(px(292));
        panel.setPivotY(0);
        panel.setScaleX(0.86f);
        panel.setScaleY(0.86f);
        panel.setTranslationY(-px(24));
        panel.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            t.setAlpha(0f);
            t.setScaleX(0.7f);
            t.setScaleY(0.7f);
            t.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(40 + i * 28L).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.3f)).start();
        }
        brightTile.requestFocus();
    }

    void close() {
        if (!open) return;
        open = false;
        adjusting = null;
        panel.animate().scaleX(0.9f).scaleY(0.9f).translationY(-px(16)).setDuration(220).setInterpolator(Ui.EASE).start();
        animate().alpha(0f).setDuration(220).withEndAction(() -> {
            setVisibility(GONE);
            for (View t : tiles) {
                t.animate().cancel();
                t.setScaleX(1f);
                t.setScaleY(1f);
                t.setAlpha(1f);
            }
        }).start();
        host.closed();
    }

    /** Ana ekran tuşlarını önce buraya verir; true dönerse tuş tüketilmiştir. */
    boolean handleKey(KeyEvent e) {
        if (!open) return false;
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return k == KeyEvent.KEYCODE_BACK;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (adjusting != null) startAdjust(adjusting);
            else close();
            return true;
        }
        if (adjusting != null) {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (adjusting == brightTile) askWritePermissionOnce();
                adjust(1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT) {
                adjust(-1);
                return true;
            }
        }
        return false;
    }
}
