package com.turktv.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Ayarlar: az satır, büyük yazı, üç başlık altında. Her satırda başlık ve tek satırlık açıklama;
 * sağda açık/kapalı anahtarı ya da o anki değer.
 */
public class SettingsActivity extends Activity {

    private interface Getter { boolean get(); }
    private interface Setter { void set(boolean v); }

    private static final class Row {
        String title, sub;
        int icon;
        Getter toggleGet;  // açma/kapama satırı
        Setter toggleSet;
        Runnable action;   // işlem satırı
        TextView valueView, subView, titleView;
        ImageView iconView;
        View track, knob;
        LinearLayout view;
    }

    private final List<Row> rows = new ArrayList<>();
    private Row editRow;

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(Ui.edgeX(this)), 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.BOTTOM);
        header.addView(Ui.text(this, "Ayarlar", 40, Ui.BOLD, Ui.TEXT));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(Ui.text(this, "DIRAK " + BuildConfig.VERSION_NAME, 17, Ui.REGULAR, Ui.MUTED));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(0, px(8), 0, px(Ui.edgeY(this) + 40));
        scroll.addView(list);
        root.addView(scroll, Ui.margins(this, -1, 0, 0, 20, 0, 0));
        ((LinearLayout.LayoutParams) scroll.getLayoutParams()).weight = 1f;

        // ---- İzleme
        section(list, "İzleme");
        Row r = new Row();
        r.title = "Son kanalla başla";
        r.sub = "Uygulama açılınca en son izlediğin kanal oynasın";
        r.icon = R.drawable.ic_play;
        r.toggleGet = () -> Prefs.openLast(this);
        r.toggleSet = v -> Prefs.openLast(this, v);
        addRow(list, r);

        r = new Row();
        r.title = "Önizlemede ses";
        r.sub = "Ana ekrandaki kanal önizlemesi sesli oynasın";
        r.icon = R.drawable.ic_sound;
        r.toggleGet = () -> Prefs.previewSound(this);
        r.toggleSet = v -> Prefs.previewSound(this, v);
        addRow(list, r);

        final Row night = new Row();
        night.title = "Gece modu";
        night.sub = "Ses en fazla %35, görüntü biraz kısık · her basışta değişir";
        night.icon = R.drawable.ic_moon;
        night.action = () -> {
            // Kapalı → 22–07 → 23–07 → 00–08 → Kapalı
            if (!Prefs.nightMode(this)) {
                Prefs.nightMode(this, true);
                Prefs.nightHours(this, 0);
            } else if (Prefs.nightHours(this) < Prefs.NIGHT_HOURS.length - 1) {
                Prefs.nightHours(this, Prefs.nightHours(this) + 1);
            } else {
                Prefs.nightMode(this, false);
            }
            night.valueView.setText(nightLabel());
        };
        addRow(list, night);
        night.valueView.setText(nightLabel());

        r = new Row();
        r.title = "Hâlâ izliyor musun?";
        r.sub = "3 saat tuşa basılmazsa sorar, cevap yoksa yayını durdurur";
        r.icon = R.drawable.ic_power;
        r.toggleGet = () -> Prefs.stillWatching(this);
        r.toggleSet = v -> Prefs.stillWatching(this, v);
        addRow(list, r);

        // ---- Kanallar
        section(list, "Kanallar");
        final Row edit = new Row();
        edit.title = "Kanalları düzenle";
        edit.sub = "Kanalları gizle ya da sırasını değiştir";
        edit.icon = R.drawable.ic_edit;
        edit.action = () -> startActivity(new android.content.Intent(this, EditChannelsActivity.class));
        addRow(list, edit);
        editRow = edit;

        r = new Row();
        r.title = "Kapalı kanalları gizle";
        r.sub = "O an açılmayan kanallar listede görünmesin";
        r.icon = R.drawable.ic_list;
        r.toggleGet = () -> Prefs.hideOffline(this);
        r.toggleSet = v -> Prefs.hideOffline(this, v);
        addRow(list, r);

        final Row scan = new Row();
        scan.title = "Kanalları tara";
        scan.sub = scanSub();
        scan.icon = R.drawable.ic_scan;
        scan.action = () -> {
            scan.valueView.setText("Taranıyor…");
            ScanJob.runNow(this, (broken, alt) -> {
                if (isFinishing()) return;
                scan.valueView.setText("Tara");
                scan.subView.setText(scanSub());
                Toast.makeText(this, broken == 0 ? "Bütün kanallar çalışıyor"
                        : broken + " kanal açılmıyor" + (alt > 0 ? ", " + alt + " tanesi için yedek bulundu" : ""), Toast.LENGTH_LONG).show();
            });
        };
        addRow(list, scan);
        scan.valueView.setText("Tara");

        final Row update = new Row();
        update.title = "Kanal listesini güncelle";
        update.sub = listSub();
        update.icon = R.drawable.ic_refresh;
        update.action = () -> {
            update.valueView.setText("Güncelleniyor…");
            RemoteList.update(this, (changed, err) -> {
                update.valueView.setText(channelCount());
                update.subView.setText(listSub());
                String m = err != null ? err : (changed ? "Kanal listesi güncellendi" : "Kanal listesi zaten güncel");
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            });
        };
        addRow(list, update);
        update.valueView.setText(channelCount());

        // ---- Uygulama
        section(list, "Uygulama");
        final Row up = new Row();
        up.title = "Güncellemeleri denetle";
        up.sub = "Şu anki sürüm " + BuildConfig.VERSION_NAME;
        up.icon = R.drawable.ic_update;
        up.action = () -> Updater.check(this, true);
        addRow(list, up);
        up.valueView.setText("Denetle");

        setContentView(root);
        rows.get(0).view.requestFocus();
    }

    private void section(LinearLayout list, String title) {
        TextView t = Ui.text(this, title.toUpperCase(Actions.TR), 15, Ui.MEDIUM, Ui.MUTED);
        t.setLetterSpacing(0.08f);
        list.addView(t, Ui.margins(this, -2, -2, 8, list.getChildCount() == 0 ? 6 : 26, 0, 12));
    }

    private String nightLabel() {
        if (!Prefs.nightMode(this)) return "Kapalı";
        int[] h = Prefs.NIGHT_HOURS[Prefs.nightHours(this)];
        return String.format(Actions.TR, "%02d:00 – %02d:00", h[0], h[1]);
    }

    private String scanSub() {
        long t = Prefs.scanAt(this);
        if (t == 0) return "Açılmayan kanalları bulur · TV boştayken kendiliğinden yapılır";
        int n = Prefs.broken(this).size();
        return "Son tarama " + new SimpleDateFormat("d MMMM HH:mm", Actions.TR).format(new Date(t))
                + " · " + (n == 0 ? "hepsi çalışıyor" : n + " kanal kapalı");
    }

    private String channelCount() {
        return Channel.loadAll(this).size() + " kanal";
    }

    private String listSub() {
        long t = Prefs.listUpdated(this);
        if (!RemoteList.enabled()) return "Uzaktan güncelleme kapalı";
        if (t == 0) return "Her açılışta kendiliğinden güncellenir";
        return "Son güncelleme " + new SimpleDateFormat("d MMMM HH:mm", Actions.TR).format(new Date(t));
    }

    private void addRow(LinearLayout list, final Row r) {
        final LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFocusable(true);
        v.setClickable(true);
        v.setPadding(px(24), px(16), px(28), px(16));
        v.setMinimumHeight(px(92));

        FrameLayout circle = new FrameLayout(this);
        r.iconView = Ui.icon(this, r.icon, 26, Ui.TEXT);
        circle.addView(r.iconView, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        circle.setTag("circle");
        v.addView(circle, new LinearLayout.LayoutParams(px(52), px(52)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        r.titleView = Ui.text(this, r.title, 24, Ui.BOLD, Ui.TEXT);
        texts.addView(r.titleView);
        r.subView = Ui.text(this, r.sub, 17, Ui.REGULAR, Ui.SOFT);
        r.subView.setSingleLine(true);
        r.subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(r.subView, Ui.margins(this, -1, -2, 0, 6, 0, 0));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, -2, 1f);
        tLp.leftMargin = px(20);
        tLp.rightMargin = px(16);
        v.addView(texts, tLp);

        if (r.toggleGet != null) {
            FrameLayout track = new FrameLayout(this);
            View knob = new View(this);
            GradientDrawable kd = new GradientDrawable();
            kd.setShape(GradientDrawable.OVAL);
            kd.setColor(Ui.WHITE);
            knob.setBackground(kd);
            track.addView(knob, new FrameLayout.LayoutParams(px(30), px(30), Gravity.CENTER_VERTICAL));
            v.addView(track, new LinearLayout.LayoutParams(px(64), px(36)));
            r.track = track;
            r.knob = knob;
            v.setOnClickListener(x -> {
                r.toggleSet.set(!r.toggleGet.get());
                styleRow(r, true);
            });
        } else {
            r.valueView = Ui.text(this, "", 20, Ui.MEDIUM, Ui.SOFT);
            v.addView(r.valueView);
            v.setOnClickListener(x -> r.action.run());
        }

        v.setLayoutParams(Ui.margins(this, -1, -2, 0, 0, 0, 10));
        v.setOnFocusChangeListener((x, has) -> styleRow(r, has));
        r.view = v;
        rows.add(r);
        list.addView(v);
        styleRow(r, false);
    }

    private void styleRow(Row r, boolean focus) {
        r.view.setBackground(Ui.rounded(this, focus ? 0xFFFFFFFF : Ui.SURFACE, 22));
        r.titleView.setTextColor(focus ? 0xFF0B0D12 : Ui.TEXT);
        r.subView.setTextColor(focus ? 0xFF3D4452 : Ui.SOFT);
        r.iconView.setColorFilter(focus ? 0xFFFFFFFF : Ui.TEXT);
        View circle = r.view.getChildAt(0);
        GradientDrawable cd = new GradientDrawable();
        cd.setShape(GradientDrawable.OVAL);
        cd.setColor(focus ? Ui.ACCENT : 0x1FFFFFFF);
        circle.setBackground(cd);
        if (r.valueView != null) r.valueView.setTextColor(focus ? 0xFF0B0D12 : Ui.SOFT);
        if (r.toggleGet != null) {
            boolean on = r.toggleGet.get();
            int trackColor = on ? 0xFF34C759 : (focus ? 0xFFC2C8D2 : 0xFF3A4150);
            r.track.setBackground(Ui.rounded(this, trackColor, 18));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) r.knob.getLayoutParams();
            lp.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
            lp.setMargins(px(3), 0, px(3), 0);
            r.knob.setLayoutParams(lp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (HomeWatch.hasUsage(this)) WakeService.start(this);
        if (editRow != null) editRow.valueView.setText(Lineup.get(this).size() + " kanal");
        View f = getCurrentFocus();
        for (Row r : rows) styleRow(r, r.view == f);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
