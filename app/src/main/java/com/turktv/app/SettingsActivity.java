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

/** Ayarlar ekranı. */
public class SettingsActivity extends Activity {

    private interface Getter { boolean get(); }
    private interface Setter { void set(boolean v); }

    private static final class Row {
        String title, sub, desc, note;
        int icon;
        Getter toggleGet;  // açma/kapama satırı
        Setter toggleSet;
        Runnable action;   // işlem satırı
        TextView valueView, subView, titleView;
        View track, knob;
        LinearLayout view;
    }

    private final List<Row> rows = new ArrayList<>();
    private ImageView asideIcon;
    private TextView asideTitle, asideDesc, asideNote;

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(Ui.edgeX(this)), px(Ui.edgeY(this)));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, "Ayarlar", 34, Ui.BOLD, Ui.TEXT));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(Ui.text(this, "TV · sürüm " + BuildConfig.VERSION_NAME + " · Yusuf Alper Dırak", 14, Ui.REGULAR, Ui.MUTED));
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setClipChildren(false);
        root.addView(body, Ui.margins(this, -1, 0, 0, 28, 0, 0));
        ((LinearLayout.LayoutParams) body.getLayoutParams()).weight = 1f;

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(px(6), px(4), px(6), px(24));
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout aside = new LinearLayout(this);
        aside.setOrientation(LinearLayout.VERTICAL);
        aside.setPadding(px(28), px(28), px(28), px(28));
        aside.setBackground(Ui.rounded(this, Ui.SURFACE, 20));
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(Ui.rounded(this, Ui.ACCENT, 14));
        asideIcon = Ui.icon(this, R.drawable.ic_power, 26, Ui.WHITE);
        iconBox.addView(asideIcon, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        aside.addView(iconBox, new LinearLayout.LayoutParams(px(52), px(52)));
        asideTitle = Ui.text(this, "", 24, Ui.BOLD, Ui.TEXT);
        asideTitle.setLineSpacing(px(4), 1f);
        aside.addView(asideTitle, Ui.margins(this, -1, -2, 0, 16, 0, 0));
        asideDesc = Ui.text(this, "", 15, Ui.REGULAR, Ui.SOFT);
        asideDesc.setLineSpacing(px(6), 1f);
        aside.addView(asideDesc, Ui.margins(this, -1, -2, 0, 14, 0, 0));
        asideNote = Ui.text(this, "", 14, Ui.REGULAR, Ui.MUTED);
        asideNote.setLineSpacing(px(5), 1f);
        aside.addView(asideNote, Ui.margins(this, -1, -2, 0, 16, 0, 0));
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(px(360), -2);
        aLp.setMargins(px(32), 0, 0, 0);
        body.addView(aside, aLp);

        // --- Satırlar
        Row r = new Row();
        r.title = "TV açılınca otomatik başlat";
        r.sub = "Televizyon açıldığında uygulama direkt açılır";
        r.desc = "Televizyonu açtığında başka hiçbir şeye dokunmadan TV uygulaması açılır. "
                + "\"Son izlenen kanalla aç\" da açıksa doğrudan son kanal oynamaya başlar.";
        r.icon = R.drawable.ic_power;
        r.toggleGet = () -> Prefs.autostart(this);
        r.toggleSet = v -> {
            Prefs.autostart(this, v);
            if (v) {
                WakeService.start(this);
                if (!Actions.hasOverlay(this)) Actions.requestOverlay(this);
            } else {
                WakeService.stop(this);
            }
        };
        r.note = "overlay";
        addRow(list, r);

        r = new Row();
        r.title = "Son izlenen kanalla aç";
        r.sub = "Açılışta ana ekran yerine doğrudan yayına geç";
        r.desc = "Uygulama açıldığında ana ekranı atlayıp en son izlediğin kanalı tam ekran başlatır. "
                + "Geri tuşuyla her zaman ana ekrana dönebilirsin.";
        r.icon = R.drawable.ic_play;
        r.toggleGet = () -> Prefs.openLast(this);
        r.toggleSet = v -> Prefs.openLast(this, v);
        addRow(list, r);

        r = new Row();
        r.title = "Önizlemede ses";
        r.sub = "Ana ekrandaki küçük önizleme sesli oynasın";
        r.desc = "Kapalıyken önizleme sessiz oynar; kanallar arasında gezinirken ses karışmaz.";
        r.icon = R.drawable.ic_sound;
        r.toggleGet = () -> Prefs.previewSound(this);
        r.toggleSet = v -> Prefs.previewSound(this, v);
        addRow(list, r);

        r = new Row();
        r.title = "Kapalı kanalları gizle";
        r.sub = "O an yayınına ulaşılamayan kanallar listede görünmesin";
        r.desc = "Uygulama her açılışta kanalların yayınlarına ulaşılabiliyor mu kontrol eder. "
                + "Bu açıkken yalnızca çalışan kanallar gösterilir.";
        r.icon = R.drawable.ic_list;
        r.toggleGet = () -> Prefs.hideOffline(this);
        r.toggleSet = v -> Prefs.hideOffline(this, v);
        addRow(list, r);

        final Row edge = new Row();
        edge.title = "Ekran kenar boşluğu";
        edge.sub = "Menüler ekranın kenarından taşıyorsa artır";
        edge.desc = "Bazı televizyonlar görüntünün kenarlarını biraz keser. Butonlar veya yazılar "
                + "ekrana tam sığmıyorsa bunu \"Çok\" yap; boşluk fazlaysa \"Az\" yap. Her basışta değişir.";
        edge.icon = R.drawable.ic_tv;
        edge.action = () -> {
            Prefs.edge(this, (Prefs.edge(this) + 1) % 3);
            recreate();
        };
        addRow(list, edge);
        edge.valueView.setText(new String[]{"Az", "Normal", "Çok"}[Prefs.edge(this)] + "  ›");

        final Row update = new Row();
        update.title = "Kanal listesini güncelle";
        update.sub = listSub();
        update.desc = RemoteList.enabled()
                ? "Kanal listesi her açılışta internetten otomatik güncellenir. Şimdi güncellemek için OK'e bas."
                : "Uzaktan güncelleme bu derlemede kapalı.";
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

        setContentView(root);
        rows.get(0).view.requestFocus();
        showAside(rows.get(0));
    }

    private String channelCount() {
        return Channel.loadAll(this).size() + " kanal  ›";
    }

    private String listSub() {
        long t = Prefs.listUpdated(this);
        if (!RemoteList.enabled()) return "Uzaktan güncelleme kapalı";
        if (t == 0) return "Henüz güncellenmedi";
        return "Son güncelleme: " + new SimpleDateFormat("d MMMM HH:mm", Actions.TR).format(new Date(t));
    }

    private void addRow(LinearLayout list, final Row r) {
        final LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFocusable(true);
        v.setClickable(true);
        v.setPadding(px(22), px(14), px(22), px(14));
        v.setMinimumHeight(px(72));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        r.titleView = Ui.text(this, r.title, 18, Ui.BOLD, Ui.TEXT);
        texts.addView(r.titleView);
        r.subView = Ui.text(this, r.sub, 14, Ui.REGULAR, Ui.MUTED);
        texts.addView(r.subView, Ui.margins(this, -2, -2, 0, 5, 0, 0));
        v.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        if (r.toggleGet != null) {
            FrameLayout track = new FrameLayout(this);
            View knob = new View(this);
            GradientDrawable kd = new GradientDrawable();
            kd.setShape(GradientDrawable.OVAL);
            kd.setColor(Ui.WHITE);
            knob.setBackground(kd);
            track.addView(knob, new FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER_VERTICAL));
            v.addView(track, new LinearLayout.LayoutParams(px(52), px(30)));
            r.track = track;
            r.knob = knob;
            v.setOnClickListener(x -> {
                r.toggleSet.set(!r.toggleGet.get());
                styleRow(r, true);
            });
        } else {
            r.valueView = Ui.text(this, "", 15, Ui.BOLD, Ui.SOFT);
            v.addView(r.valueView);
            v.setOnClickListener(x -> r.action.run());
        }

        v.setLayoutParams(Ui.margins(this, -1, -2, 0, 0, 0, 10));
        v.setOnFocusChangeListener((x, has) -> {
            styleRow(r, has);
            if (has) showAside(r);
        });
        r.view = v;
        rows.add(r);
        list.addView(v);
        styleRow(r, false);
    }

    private void styleRow(Row r, boolean focus) {
        r.view.setBackground(Ui.rounded(this, focus ? Ui.TEXT : Ui.SURFACE, 16));
        r.titleView.setTextColor(focus ? Ui.BG : Ui.TEXT);
        r.subView.setTextColor(focus ? 0xFF4A5262 : Ui.MUTED);
        if (r.valueView != null) r.valueView.setTextColor(focus ? Ui.BG : Ui.SOFT);
        if (r.toggleGet != null) {
            boolean on = r.toggleGet.get();
            int trackColor = on ? Ui.ACCENT : (focus ? 0xFFC2C8D2 : 0xFF2E3542);
            r.track.setBackground(Ui.rounded(this, trackColor, 15));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) r.knob.getLayoutParams();
            lp.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
            lp.setMargins(px(3), 0, px(3), 0);
            r.knob.setLayoutParams(lp);
        }
    }

    private void showAside(Row r) {
        asideIcon.setImageResource(r.icon);
        asideTitle.setText(r.title);
        asideDesc.setText(r.desc);
        if ("overlay".equals(r.note)) {
            asideNote.setText(Actions.hasOverlay(this)
                    ? "Gerekli izin verilmiş, otomatik açılış hazır."
                    : "Bazı televizyonlarda \"Diğer uygulamaların üzerinde göster\" izni gerekir. "
                    + "Ayarı açtığında izin ekranı gelir; bir kere onaylaman yeterli.");
            asideNote.setVisibility(View.VISIBLE);
        } else {
            asideNote.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        View f = getCurrentFocus();
        for (Row r : rows) {
            styleRow(r, r.view == f);
            if (r.view == f) showAside(r);
        }
    }
}
