package com.turktv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ana ekran: önizleme penceresi, kategoriler ve kanal kutucukları. */
public class MainActivity extends Activity {

    private static final String MODE_ALL = "all";
    private static final String MODE_FAV = "fav";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Channel> channels;
    private Channel selected;
    private String mode = MODE_ALL;
    private boolean builtHideOffline;
    private boolean resumed;
    private int previewToken;

    // kanal -> kutucuk
    private final Map<Channel, LinearLayout> tiles = new HashMap<>();
    private final Map<Channel, TextView> pills = new HashMap<>();
    private final Map<String, TextView> chips = new HashMap<>();

    private LinearLayout railHome, railFav;
    private TextView clockLocal, clockTr, dateText;
    private YtPlayer preview;
    private TextView previewMsg, liveBadge, soundBadge;
    private TextView infoStatus, infoNum, infoName;
    private LinearLayout btnFav;
    private LinearLayout sections;
    private ScrollView sectionScroll;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            ui.postDelayed(this, 15000);
        }
    };

    private final Runnable previewRunnable = this::startPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        channels = Channel.loadAll(this);
        selected = channels.get(clamp(Prefs.last(this)));

        buildUi();
        setMode(MODE_ALL);
        Live.checkAll(channels, (c, id) -> onStatus(c));

        if (Prefs.autostart(this)) WakeService.start(this);

        if (savedInstanceState == null) {
            RemoteList.update(this, (changed, err) -> {
                if (changed && !isFinishing()) {
                    Toast.makeText(this, "Kanal listesi güncellendi", Toast.LENGTH_SHORT).show();
                    recreate();
                }
            });
            if (Prefs.openLast(this)) openChannel(selected);
        }
    }

    private int clamp(int i) {
        return Math.max(0, Math.min(i, channels.size() - 1));
    }

    // ------------------------------------------------------------------ arayüz

    private int px(float v) {
        return Ui.px(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Ui.BG);

        // --- Sol menü
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(Ui.RAIL);
        rail.setPadding(0, px(32), 0, px(28));
        root.addView(rail, new LinearLayout.LayoutParams(px(88), ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(Ui.rounded(this, Ui.ACCENT, 12));
        ImageView logoIcon = Ui.icon(this, R.drawable.ic_play, 18, Ui.WHITE);
        logo.addView(logoIcon, new FrameLayout.LayoutParams(px(18), px(18), Gravity.CENTER));
        rail.addView(logo, Ui.margins(this, px(42), px(42), 0, 0, 0, 34));

        railHome = railButton(R.drawable.ic_home);
        railHome.setOnClickListener(v -> setMode(MODE_ALL));
        rail.addView(railHome);
        railFav = railButton(R.drawable.ic_heart);
        railFav.setOnClickListener(v -> setMode(MODE_FAV));
        rail.addView(railFav);
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout railSettings = railButton(R.drawable.ic_gear);
        railSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        rail.addView(railSettings);

        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        root.addView(line, new LinearLayout.LayoutParams(Math.max(1, px(1)), ViewGroup.LayoutParams.MATCH_PARENT));

        // --- İçerik
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(40), px(28), px(40), 0);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // Başlık ve saat
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        header.addView(Ui.text(this, "TV", 32, Ui.BOLD, Ui.TEXT));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        dateText = Ui.text(this, "", 15, Ui.REGULAR, Ui.MUTED);
        header.addView(dateText, Ui.margins(this, -2, -2, 0, 0, 14, 2));
        clockLocal = Ui.text(this, "", 24, Ui.MEDIUM, Ui.TEXT);
        header.addView(clockLocal);
        clockTr = Ui.text(this, "", 15, Ui.MEDIUM, Ui.MUTED);
        header.addView(clockTr, Ui.margins(this, -2, -2, 12, 0, 0, 2));
        content.addView(header);

        // Önizleme + bilgi
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setClipChildren(false);
        content.addView(hero, Ui.margins(this, -1, px(216), 0, 20, 0, 0));

        FrameLayout pv = new FrameLayout(this);
        GradientDrawable pvBg = Ui.rounded(this, 0xFF161B24, 18);
        pvBg.setStroke(Math.max(1, px(1)), 0xFF262D3A);
        pv.setBackground(pvBg);
        pv.setClipToOutline(true);
        preview = new YtPlayer(this);
        pv.addView(preview.web, new FrameLayout.LayoutParams(-1, -1));
        previewMsg = Ui.text(this, "", 13, Ui.REGULAR, 0xFF8892A3);
        previewMsg.setGravity(Gravity.CENTER);
        previewMsg.setBackgroundColor(0xFF161B24);
        pv.addView(previewMsg, new FrameLayout.LayoutParams(-1, -1));
        liveBadge = Ui.text(this, "● CANLI", 11, Ui.BOLD, Ui.WHITE);
        liveBadge.setLetterSpacing(0.08f);
        liveBadge.setPadding(px(9), px(5), px(9), px(5));
        liveBadge.setBackground(Ui.rounded(this, Ui.ACCENT, 8));
        FrameLayout.LayoutParams lbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lbLp.setMargins(px(12), px(12), 0, 0);
        pv.addView(liveBadge, lbLp);
        soundBadge = Ui.text(this, "", 12, Ui.MEDIUM, Ui.SOFT);
        soundBadge.setPadding(px(9), px(5), px(9), px(5));
        soundBadge.setBackground(Ui.rounded(this, 0xCC0B0D12, 8));
        FrameLayout.LayoutParams sbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        sbLp.setMargins(0, 0, px(12), px(12));
        pv.addView(soundBadge, sbLp);
        hero.addView(pv, new LinearLayout.LayoutParams(px(384), px(216)));

        preview.setListener(new YtPlayer.Listener() {
            @Override
            public void onState(int state) {
                if (state == 1) {
                    previewMsg.setVisibility(View.GONE);
                    liveBadge.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(int code) {
                previewMsg.setText("Önizleme bu kanalda açılamıyor\nTam ekranda veya YouTube'da deneyin");
                previewMsg.setVisibility(View.VISIBLE);
                liveBadge.setVisibility(View.GONE);
            }
        });

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setClipChildren(false);
        info.setClipToPadding(false);
        info.setPadding(px(28), 0, 0, 0);
        hero.addView(info, new LinearLayout.LayoutParams(0, -1, 1f));

        infoStatus = Ui.text(this, "", 14, Ui.MEDIUM, Ui.MUTED);
        info.addView(infoStatus);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.BOTTOM);
        infoNum = Ui.text(this, "", 26, Ui.MEDIUM, Ui.MUTED);
        nameRow.addView(infoNum, Ui.margins(this, -2, -2, 0, 0, 16, 4));
        infoName = Ui.text(this, "", 46, Ui.BOLD, Ui.TEXT);
        infoName.setSingleLine(true);
        infoName.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(infoName);
        info.addView(nameRow, Ui.margins(this, -1, -2, 0, 14, 0, 0));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setClipChildren(false);
        info.addView(btnRow, Ui.margins(this, -1, -2, 0, 20, 0, 0));
        LinearLayout btnWatch = Ui.button(this, R.drawable.ic_play, "Tam ekran izle", 50, Ui.SURFACE2);
        btnWatch.setOnClickListener(v -> openChannel(selected));
        btnRow.addView(btnWatch);
        LinearLayout btnYt = Ui.button(this, R.drawable.ic_youtube, "YouTube'da aç", 50, Ui.SURFACE2);
        btnYt.setOnClickListener(v -> {
            Prefs.last(this, selected.index);
            Actions.openYoutube(this, selected);
        });
        ((LinearLayout.LayoutParams) btnYt.getLayoutParams()).setMargins(px(12), 0, 0, 0);
        btnRow.addView(btnYt);
        btnFav = Ui.button(this, R.drawable.ic_heart, null, 50, Ui.SURFACE2);
        btnFav.setOnClickListener(v -> {
            boolean now = Prefs.toggleFavorite(this, selected);
            Toast.makeText(this, now ? selected.name + " favorilere eklendi" : selected.name + " favorilerden çıkarıldı",
                    Toast.LENGTH_SHORT).show();
            updateInfo();
            if (MODE_FAV.equals(mode)) setMode(MODE_FAV);
        });
        ((LinearLayout.LayoutParams) btnFav.getLayoutParams()).setMargins(px(12), 0, 0, 0);
        btnRow.addView(btnFav);

        TextView hint = Ui.text(this, "Kanalların üzerinde gezdikçe önizleme değişir  ·  OK: tam ekran", 13, Ui.REGULAR, Ui.MUTED);
        info.addView(hint, Ui.margins(this, -2, -2, 0, 14, 0, 0));

        // Kategori sekmeleri
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setClipChildren(false);
        content.addView(chipRow, Ui.margins(this, -1, -2, 0, 22, 0, 0));
        List<String> modes = new ArrayList<>();
        modes.add(MODE_ALL);
        modes.addAll(Channel.categories(channels));
        for (String m : modes) {
            TextView chip = chip(MODE_ALL.equals(m) ? "Tümü" : m, m);
            chipRow.addView(chip);
            chips.put(m, chip);
        }

        // Kanal satırları
        sectionScroll = new ScrollView(this);
        sectionScroll.setVerticalScrollBarEnabled(false);
        sectionScroll.setClipChildren(false);
        sectionScroll.setClipToPadding(false);
        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        sections.setClipToPadding(false);
        sections.setPadding(px(6), px(8), px(6), px(48));
        sectionScroll.addView(sections);
        content.addView(sectionScroll, Ui.margins(this, -1, 0, -6, 8, -6, 0));
        ((LinearLayout.LayoutParams) sectionScroll.getLayoutParams()).weight = 1f;

        setContentView(root);
    }

    private LinearLayout railButton(int iconRes) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        ImageView iv = Ui.icon(this, iconRes, 22, Ui.MUTED);
        b.addView(iv);
        b.setTag(iv);
        b.setLayoutParams(Ui.margins(this, px(48), px(48), 0, 0, 0, 14));
        b.setOnFocusChangeListener((v, has) -> styleRail((LinearLayout) v));
        styleRail(b);
        return b;
    }

    private void styleRail(LinearLayout b) {
        boolean active = (b == railHome && MODE_ALL.equals(mode)) || (b == railFav && MODE_FAV.equals(mode));
        boolean focus = b.hasFocus();
        int bg = focus ? Ui.WHITE : (active ? Ui.SURFACE2 : 0x00000000);
        int fg = focus ? Ui.BG : (active ? Ui.TEXT : Ui.MUTED);
        b.setBackground(Ui.rounded(this, bg, 14));
        ((ImageView) b.getTag()).setColorFilter(fg, PorterDuff.Mode.SRC_IN);
    }

    private TextView chip(String label, String m) {
        TextView t = Ui.text(this, label, 15, Ui.BOLD, Ui.SOFT);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(px(20), 0, px(20), 0);
        t.setTag(m);
        t.setLayoutParams(Ui.margins(this, -2, px(40), 0, 0, 10, 0));
        t.setOnFocusChangeListener((v, has) -> {
            if (has && !m.equals(mode)) setMode(m);
            styleChip((TextView) v);
        });
        t.setOnClickListener(v -> setMode(m));
        styleChip(t);
        return t;
    }

    private void styleChip(TextView t) {
        boolean active = t.getTag().equals(mode);
        boolean focus = t.hasFocus();
        int bg = focus ? Ui.WHITE : (active ? Ui.TEXT : Ui.SURFACE);
        t.setBackground(Ui.rounded(this, bg, 20));
        t.setTextColor(focus || active ? Ui.BG : Ui.SOFT);
        float s = focus ? 1.06f : 1f;
        t.animate().scaleX(s).scaleY(s).setDuration(110).start();
    }

    // ------------------------------------------------------------------ içerik

    private void setMode(String m) {
        mode = m;
        for (TextView c : chips.values()) styleChip(c);
        if (railHome != null) {
            styleRail(railHome);
            styleRail(railFav);
        }
        builtHideOffline = Prefs.hideOffline(this);
        sections.removeAllViews();
        tiles.clear();
        pills.clear();

        if (MODE_ALL.equals(m)) {
            for (String cat : Channel.categories(channels)) addSection(cat, inCategory(cat));
        } else if (MODE_FAV.equals(m)) {
            List<Channel> favs = new ArrayList<>();
            for (Channel c : channels) if (Prefs.isFavorite(this, c)) favs.add(c);
            if (favs.isEmpty()) {
                TextView empty = Ui.text(this,
                        "Henüz favori kanal yok.\nBir kanal seçip ♥ butonuna basarak ekleyebilirsin.",
                        18, Ui.REGULAR, Ui.MUTED);
                empty.setLineSpacing(px(6), 1f);
                sections.addView(empty, Ui.margins(this, -2, -2, 0, 24, 0, 0));
            } else {
                addSection("Favoriler", favs);
            }
        } else {
            addSection(m, inCategory(m));
        }
        sectionScroll.scrollTo(0, 0);
    }

    private List<Channel> inCategory(String cat) {
        List<Channel> out = new ArrayList<>();
        for (Channel c : channels) if (c.category.equals(cat)) out.add(c);
        return out;
    }

    private void addSection(String title, List<Channel> list) {
        TextView h = Ui.text(this, title, 20, Ui.MEDIUM, Ui.TEXT);
        sections.addView(h, Ui.margins(this, -2, -2, 0, 10, 0, 12));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        sections.addView(grid);

        int avail = px(1112);
        int gap = px(16);
        int w = (avail - 5 * gap) / 6;
        int col = 0;
        for (Channel c : list) {
            LinearLayout tile = makeTile(c);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = w;
            lp.height = px(92);
            lp.setMargins(0, 0, (col % 6 == 5) ? 0 : gap, gap);
            grid.addView(tile, lp);
            tiles.put(c, tile);
            updateTile(c);
            col++;
        }
    }

    private LinearLayout makeTile(Channel c) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(px(14), px(12), px(14), px(12));
        GradientDrawable bg = Ui.rounded(this, c.color, 16);
        tile.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView num = Ui.text(this, String.valueOf(c.number), 15, Ui.MEDIUM, 0xCCFFFFFF);
        top.addView(num);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView pill = Ui.text(this, "", 12, Ui.BOLD, Ui.WHITE);
        pill.setPadding(px(8), px(4), px(8), px(4));
        pill.setBackground(Ui.rounded(this, 0x59000000, 10));
        top.addView(pill);
        pills.put(c, pill);
        tile.addView(top);

        tile.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView name = Ui.text(this, c.name, 20, Ui.BOLD, Ui.WHITE);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(name);

        tile.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.07f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            bg.setStroke(has ? px(3) : 0, Ui.WHITE);
            v.setElevation(has ? px(12) : 0);
            v.setAlpha(has || Live.status(c) != Live.OFFLINE ? 1f : 0.55f);
            if (has) select(c);
        });
        tile.setOnClickListener(v -> openChannel(c));
        return tile;
    }

    private void updateTile(Channel c) {
        LinearLayout tile = tiles.get(c);
        TextView pill = pills.get(c);
        if (tile == null || pill == null) return;
        int st = Live.status(c);
        pill.setText(statusText(st, false));
        tile.setAlpha(tile.hasFocus() || st != Live.OFFLINE ? 1f : 0.55f);
        boolean hide = builtHideOffline && st == Live.OFFLINE && !tile.hasFocus();
        tile.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private CharSequence statusText(int st, boolean longForm) {
        String label;
        int dot;
        if (st == Live.LIVE) {
            label = longForm ? "Şu an canlı" : "Canlı";
            dot = Ui.LIVE;
        } else if (st == Live.OFFLINE) {
            label = longForm ? "Şu an canlı yayın yok" : "Yayın yok";
            dot = Ui.OFF;
        } else {
            label = longForm ? "Kontrol ediliyor" : "…";
            dot = Ui.MUTED;
        }
        SpannableString s = new SpannableString("● " + label);
        s.setSpan(new ForegroundColorSpan(dot), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void onStatus(Channel c) {
        if (isFinishing()) return;
        updateTile(c);
        if (c == selected) updateInfo();
    }

    private void select(Channel c) {
        if (c == selected && previewMsg.getVisibility() == View.GONE) return; // zaten oynuyor
        selected = c;
        updateInfo();
        schedulePreview();
    }

    private void updateInfo() {
        Channel c = selected;
        infoNum.setText(String.valueOf(c.number));
        infoName.setText(c.name);
        CharSequence st = statusText(Live.status(c), true);
        infoStatus.setText(TextUtils.concat(st, "  ·  " + c.category));
        Ui.setButtonIcon(btnFav, Prefs.isFavorite(this, c) ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        soundBadge.setText(Prefs.previewSound(this) ? "Sesli" : "Sessiz");
    }

    private void updateClock() {
        dateText.setText(Actions.dateText());
        clockLocal.setText(Actions.localTime());
        String tr = Actions.turkeyTime();
        clockTr.setText(tr == null ? "" : "TR " + tr);
        clockTr.setVisibility(tr == null ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ önizleme

    private void schedulePreview() {
        ui.removeCallbacks(previewRunnable);
        previewToken++;
        preview.stop();
        liveBadge.setVisibility(View.GONE);
        previewMsg.setText("Önizleme yükleniyor…");
        previewMsg.setVisibility(View.VISIBLE);
        if (resumed) ui.postDelayed(previewRunnable, 450);
    }

    private void startPreview() {
        final Channel c = selected;
        final int t = ++previewToken;
        Live.find(c, false, (ch, id) -> {
            if (t != previewToken || !resumed) return;
            onStatus(ch);
            if (id == null) {
                previewMsg.setText("Şu an canlı yayın yok");
            } else {
                preview.play(id, !Prefs.previewSound(this));
            }
        });
    }

    // ------------------------------------------------------------------ gezinme

    private void openChannel(Channel c) {
        Prefs.last(this, c.index);
        if (Prefs.useYoutube(this)) {
            Actions.openYoutube(this, c);
            return;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, c.index);
        startActivity(i);
    }

    private void maybeAskOverlay() {
        if (!Prefs.autostart(this) || Prefs.askedOverlay(this) || Actions.hasOverlay(this)) return;
        Prefs.askedOverlay(this, true);
        new AlertDialog.Builder(this)
                .setTitle("TV açılınca otomatik başlasın mı?")
                .setMessage("Televizyon açıldığında bu uygulamanın kendiliğinden açılabilmesi için "
                        + "\"Diğer uygulamaların üzerinde göster\" iznini bir kez vermen gerekiyor.")
                .setPositiveButton("İzin ver", (d, w) -> Actions.requestOverlay(this))
                .setNegativeButton("Sonra", null)
                .show();
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onResume() {
        super.onResume();
        if (Channel.loadAll(this) != channels) { // kanal listesi güncellendi
            recreate();
            return;
        }
        resumed = true;
        if (builtHideOffline != Prefs.hideOffline(this)) setMode(mode);
        for (Channel c : tiles.keySet()) updateTile(c);

        Channel last = channels.get(clamp(Prefs.last(this)));
        LinearLayout tile = tiles.get(last);
        selected = last;
        updateInfo();
        if (tile != null && tile.getVisibility() == View.VISIBLE) tile.requestFocus();
        schedulePreview();

        ui.removeCallbacks(clockTick);
        clockTick.run();
        maybeAskOverlay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(previewRunnable);
        ui.removeCallbacks(clockTick);
        previewToken++;
        preview.stop();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        preview.destroy();
        super.onDestroy();
    }
}
