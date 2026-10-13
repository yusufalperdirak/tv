package com.turktv.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Tam ekran izleme. Altta ince bilgi şeridi (logo, kanal, şimdi/sonra, butonlar), hızlı kanal
 * listesi, otomatik yeniden bağlanma, uyku zamanlayıcısı. Kanal değişirken son kare donuk bekler.
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_INDEX = "index";

    private static final long STALL_MS = 20000;   // bu kadar süre görüntü gelmezse yeniden bağlan
    private static final long RECHECK_MS = 60000; // açılamayan yayını bu aralıkla tekrar dene
    private static final int MAX_RETRIES = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Channel> channels;
    private int index;

    private StreamPlayer stream;
    private int state = -1;
    private long stateSince;
    private boolean userPaused;
    private int retries;
    private boolean stopped;
    private long errorSince;
    private boolean sleeping;

    private View sink;
    private TextView msg;
    private LinearLayout clockBox;
    private TextView clockLocal, clockTr;
    private FrameLayout osd;
    private FrameLayout logoSlot;
    private TextView osdNum, osdName, osdLoading;
    private LinearLayout epgRow;
    private TextView nowTitle, nowTime, nextTitle;
    private View progressBar, progressFill;
    private TextView hint;
    private LinearLayout btnFav, btnRefresh, btnSleep, btnList;
    private LinearLayout errorPanel;
    private TextView errorText;
    private LinearLayout errRefresh;
    private FrameLayout zapPanel;
    private TextView zapTitle, zapCount;
    private LinearLayout zapList;
    private ScrollView zapScroll;
    private final List<String> zapCats = new ArrayList<>();
    private int zapCat;
    private TextView numberEntry;
    private final StringBuilder digits = new StringBuilder();
    private LinearLayout sleepPanel;
    private TextView sleepText;

    private final Runnable hideOsd = this::hideOsd;
    private final Runnable commitDigits = this::commitDigits;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            onTick();
            ui.postDelayed(this, 1000);
        }
    };

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        channels = Channel.loadAll(this);
        Epg.ensureLoaded(this);
        index = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_INDEX, 0), channels.size() - 1));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        stream = new StreamPlayer(this);
        root.addView(stream.view, new FrameLayout.LayoutParams(-1, -1));
        stream.setListener(new StreamPlayer.Listener() {
            @Override
            public void onState(int s) {
                onPlayerState(s);
            }

            @Override
            public void onError() {
                reconnect();
            }
        });

        sink = new View(this);
        sink.setFocusable(true);
        root.addView(sink, new FrameLayout.LayoutParams(1, 1));

        msg = Ui.text(this, "", 17, Ui.MEDIUM, Ui.SOFT);
        msg.setPadding(px(22), px(12), px(22), px(12));
        msg.setBackground(Ui.rounded(this, 0xB30B0D12, 22));
        msg.setVisibility(View.GONE);
        root.addView(msg, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        buildClock(root);
        buildOsd(root);
        buildError(root);
        buildZap(root);
        buildSleep(root);

        numberEntry = Ui.text(this, "", 52, Ui.BOLD, Ui.TEXT);
        numberEntry.setLetterSpacing(0.1f);
        numberEntry.setPadding(px(22), px(12), px(22), px(12));
        numberEntry.setBackground(Ui.rounded(this, 0xE60B0D12, 18));
        numberEntry.setVisibility(View.GONE);
        FrameLayout.LayoutParams nLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        nLp.setMargins(0, px(90), px(48), 0);
        root.addView(numberEntry, nLp);

        setContentView(root);
        sink.requestFocus();
        changeChannel(index);
    }

    // ------------------------------------------------------------------ arayüz

    private void buildClock(FrameLayout root) {
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.HORIZONTAL);
        clockBox.setGravity(Gravity.BOTTOM);
        clockLocal = Ui.text(this, "", 22, Ui.MEDIUM, Ui.TEXT);
        clockLocal.setShadowLayer(px(8), 0, px(1), 0x99000000);
        clockBox.addView(clockLocal);
        clockTr = Ui.text(this, "", 13, Ui.MEDIUM, Ui.SOFT);
        clockTr.setShadowLayer(px(8), 0, px(1), 0x99000000);
        clockBox.addView(clockTr, Ui.margins(this, -2, -2, 10, 0, 0, 2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, px(32), px(48), 0);
        root.addView(clockBox, lp);
    }

    private void buildOsd(FrameLayout root) {
        osd = new FrameLayout(this);
        osd.setClipChildren(false);
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xEB000000, 0xB8000000, 0x00000000}));
        osd.addView(scrim, new FrameLayout.LayoutParams(-1, px(300), Gravity.BOTTOM));

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.BOTTOM);
        strip.setClipChildren(false);
        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        sLp.setMargins(px(56), 0, px(56), px(44));
        osd.addView(strip, sLp);

        logoSlot = new FrameLayout(this);
        strip.addView(logoSlot, Ui.margins(this, px(112), px(66), 0, 0, 28, 0));

        // Orta: kanal adı, şimdi/sonra, ilerleme
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        strip.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        osdNum = Ui.text(this, "", 20, Ui.MEDIUM, 0xFFA8B1BF);
        r1.addView(osdNum, Ui.margins(this, -2, -2, 0, 0, 14, 0));
        osdName = Ui.text(this, "", 34, Ui.BOLD, Ui.TEXT);
        osdName.setSingleLine(true);
        osdName.setEllipsize(TextUtils.TruncateAt.END);
        r1.addView(osdName);
        osdLoading = Ui.text(this, "", 13, Ui.BOLD, 0xFFE3E7ED);
        osdLoading.setPadding(px(11), px(5), px(11), px(5));
        osdLoading.setBackground(Ui.rounded(this, 0x24F2F4F7, 12));
        r1.addView(osdLoading, Ui.margins(this, -2, -2, 14, 0, 0, 0));
        mid.addView(r1);

        epgRow = new LinearLayout(this);
        epgRow.setOrientation(LinearLayout.HORIZONTAL);
        epgRow.setGravity(Gravity.BOTTOM);
        TextView nowL = Ui.text(this, "ŞİMDİ", 12, Ui.BOLD, 0xFFFF6B70);
        nowL.setLetterSpacing(0.06f);
        epgRow.addView(nowL, Ui.margins(this, -2, -2, 0, 0, 10, 1));
        nowTitle = Ui.text(this, "", 17, Ui.BOLD, Ui.TEXT);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowTitle.setMaxWidth(px(380));
        epgRow.addView(nowTitle);
        nowTime = Ui.text(this, "", 13, Ui.REGULAR, 0xFFA8B1BF);
        epgRow.addView(nowTime, Ui.margins(this, -2, -2, 10, 0, 14, 1));
        TextView nextL = Ui.text(this, "SONRA", 12, Ui.BOLD, Ui.MUTED);
        nextL.setLetterSpacing(0.06f);
        epgRow.addView(nextL, Ui.margins(this, -2, -2, 0, 0, 10, 1));
        nextTitle = Ui.text(this, "", 15, Ui.MEDIUM, Ui.SOFT);
        nextTitle.setSingleLine(true);
        nextTitle.setEllipsize(TextUtils.TruncateAt.END);
        nextTitle.setMaxWidth(px(300));
        epgRow.addView(nextTitle);
        mid.addView(epgRow, Ui.margins(this, -1, -2, 0, 10, 0, 0));

        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.rounded(this, 0x33F2F4F7, 2));
        progressFill = new View(this);
        progressFill.setBackground(Ui.rounded(this, Ui.TEXT, 2));
        bar.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        progressBar = bar;
        mid.addView(bar, Ui.margins(this, px(560), px(4), 0, 12, 0, 0));

        // Sağ: butonlar
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setClipChildren(false);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        btnFav = Ui.button(this, R.drawable.ic_heart, null, 48, 0x24F2F4F7);
        btnFav.setOnClickListener(v -> {
            boolean now = Prefs.toggleFavorite(this, cur());
            Toast.makeText(this, cur().name + (now ? " favorilere eklendi" : " favorilerden çıkarıldı"), Toast.LENGTH_SHORT).show();
            updateOsd();
            showOsd();
        });
        btns.addView(btnFav);
        btnRefresh = Ui.button(this, R.drawable.ic_refresh, "Yenile", 48, 0x24F2F4F7);
        btnRefresh.setOnClickListener(v -> refresh());
        addBtn(btns, btnRefresh);
        btnSleep = Ui.button(this, R.drawable.ic_moon, null, 48, 0x24F2F4F7);
        btnSleep.setOnClickListener(v -> cycleSleep());
        addBtn(btns, btnSleep);
        btnList = Ui.button(this, R.drawable.ic_list, null, 48, 0x24F2F4F7);
        btnList.setOnClickListener(v -> showZap());
        addBtn(btns, btnList);
        right.addView(btns);
        hint = Ui.text(this, "", 13, Ui.MEDIUM, Ui.MUTED);
        right.addView(hint, Ui.margins(this, -2, -2, 0, 12, 0, 0));
        strip.addView(right, Ui.margins(this, -2, -2, 24, 0, 0, 0));

        root.addView(osd, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addBtn(LinearLayout row, LinearLayout b) {
        row.addView(b);
        ((LinearLayout.LayoutParams) b.getLayoutParams()).setMargins(px(10), 0, 0, 0);
    }

    private void buildError(FrameLayout root) {
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        errorPanel.setPadding(px(36), px(30), px(36), px(30));
        errorPanel.setBackground(Ui.rounded(this, 0xF2151922, 22));
        errorPanel.setClipChildren(false);
        errorText = Ui.text(this, "", 19, Ui.MEDIUM, Ui.TEXT);
        errorText.setGravity(Gravity.CENTER);
        errorText.setLineSpacing(px(6), 1f);
        errorText.setMaxWidth(px(620));
        errorPanel.addView(errorText);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        errorPanel.addView(buttons, Ui.margins(this, -2, -2, 0, 24, 0, 0));
        errRefresh = Ui.button(this, R.drawable.ic_refresh, "Yenile", 50, Ui.SURFACE2);
        errRefresh.setOnClickListener(v -> refresh());
        buttons.addView(errRefresh);
        LinearLayout errNext = Ui.button(this, 0, "Sonraki kanal ▲", 50, Ui.SURFACE2);
        errNext.setOnClickListener(v -> changeChannel(index + 1));
        addBtn(buttons, errNext);
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void buildZap(FrameLayout root) {
        zapPanel = new FrameLayout(this);
        zapPanel.setBackgroundColor(0xF20B0D12);
        zapPanel.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(28), px(32), px(22), px(26));
        zapPanel.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(arrowBox("◀"));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        zapTitle = Ui.text(this, "", 22, Ui.BOLD, Ui.TEXT);
        mid.addView(zapTitle);
        zapCount = Ui.text(this, "", 12, Ui.MEDIUM, Ui.MUTED);
        mid.addView(zapCount, Ui.margins(this, -2, -2, 0, 4, 0, 0));
        head.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(arrowBox("▶"));
        col.addView(head);

        zapScroll = new ScrollView(this);
        zapScroll.setVerticalScrollBarEnabled(false);
        zapList = new LinearLayout(this);
        zapList.setOrientation(LinearLayout.VERTICAL);
        zapList.setPadding(0, px(6), 0, px(6));
        zapScroll.addView(zapList);
        col.addView(zapScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        ((LinearLayout.LayoutParams) zapScroll.getLayoutParams()).setMargins(0, px(18), 0, px(14));
        col.addView(Ui.text(this, "▲▼ gez    ◀▶ kategori    OK izle    Geri kapat", 13, Ui.MEDIUM, Ui.MUTED));

        zapPanel.setVisibility(View.GONE);
        root.addView(zapPanel, new FrameLayout.LayoutParams(px(460), -1, Gravity.START));
    }

    private TextView arrowBox(String s) {
        TextView t = Ui.text(this, s, 13, Ui.MEDIUM, Ui.MUTED);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rounded(this, Ui.SURFACE2, 10));
        t.setLayoutParams(new LinearLayout.LayoutParams(px(32), px(32)));
        return t;
    }

    private void buildSleep(FrameLayout root) {
        sleepPanel = new LinearLayout(this);
        sleepPanel.setOrientation(LinearLayout.VERTICAL);
        sleepPanel.setGravity(Gravity.CENTER);
        sleepPanel.setPadding(px(36), px(28), px(36), px(28));
        sleepPanel.setBackground(Ui.rounded(this, 0xF2151922, 22));
        ImageView moon = Ui.icon(this, R.drawable.ic_moon, 40, Ui.TEXT);
        sleepPanel.addView(moon);
        sleepText = Ui.text(this, "", 20, Ui.MEDIUM, Ui.TEXT);
        sleepText.setGravity(Gravity.CENTER);
        sleepText.setLineSpacing(px(6), 1f);
        sleepPanel.addView(sleepText, Ui.margins(this, -2, -2, 0, 16, 0, 0));
        sleepPanel.setVisibility(View.GONE);
        root.addView(sleepPanel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    // ------------------------------------------------------------------ oynatma

    private Channel cur() {
        return channels.get(index);
    }

    private int wrap(int i) {
        int n = channels.size();
        return ((i % n) + n) % n;
    }

    private void changeChannel(int i) {
        retries = 0;
        playChannel(i, false);
    }

    private void refresh() {
        Toast.makeText(this, cur().name + " yenileniyor", Toast.LENGTH_SHORT).show();
        retries = 0;
        playChannel(index, true);
    }

    private void playChannel(int i, boolean force) {
        index = wrap(i);
        Prefs.last(this, index);
        hideError();
        userPaused = false;
        setState(-1);
        stream.play(cur(), false, force);
        updateOsd();
        showOsd();
        updateLoading();
    }

    private void setState(int s) {
        state = s;
        stateSince = System.currentTimeMillis();
    }

    /** "Açılıyor" işareti; ilk açılışta (donuk kare yokken) ortada da yazı gösterilir. */
    private void updateLoading() {
        boolean loading = state != 1 && state != 2 && errorPanel.getVisibility() != View.VISIBLE;
        osdLoading.setText(retries > 0 ? "• • •  Yeniden bağlanıyor" : "• • •  Açılıyor");
        osdLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading && !stream.isFrozen()) {
            msg.setText(retries > 0 ? "Bağlantı yeniden kuruluyor…" : "Yükleniyor…");
            msg.setVisibility(View.VISIBLE);
        } else {
            msg.setVisibility(View.GONE);
        }
        if (state == 2) {
            osdLoading.setText("❚❚  Duraklatıldı");
            osdLoading.setVisibility(View.VISIBLE);
        }
    }

    private void onPlayerState(int s) {
        setState(s);
        if (s == 1) {
            userPaused = false;
            retries = 0;
            Health.report(cur(), true);
        } else if (s == 0) {
            reconnect();
            return;
        }
        updateLoading();
    }

    /** Otomatik yeniden bağlanma: birkaç kez dener, olmazsa Yenile ekranını gösterir. */
    private void reconnect() {
        if (stopped || sleeping) return;
        retries++;
        if (retries > MAX_RETRIES) {
            Health.report(cur(), false);
            showError();
            return;
        }
        playChannel(index, true);
    }

    private void onTick() {
        long now = System.currentTimeMillis();
        updateClock();
        if (!stopped && !sleeping) {
            boolean errorShown = errorPanel.getVisibility() == View.VISIBLE;
            if (!errorShown && !userPaused && state != 1 && state != 2 && now - stateSince > STALL_MS) {
                reconnect();
            }
            if (errorShown && now - errorSince > RECHECK_MS) {
                errorSince = now;
                retries = 0;
                playChannel(index, true);
            }
        }
        if (now % 20000 < 1000) updateEpg();

        if (Prefs.sleepAt > 0 && !sleeping) {
            long left = Prefs.sleepAt - now;
            if (left <= 0) {
                goSleep();
            } else if (left <= 60000) {
                sleepText.setText("Uyku zamanlayıcısı: yayın " + ((left + 999) / 1000)
                        + " saniye içinde kapanacak\nİptal etmek için herhangi bir tuşa bas");
                sleepPanel.setVisibility(View.VISIBLE);
            }
        }
    }

    // ------------------------------------------------------------------ bilgi şeridi

    private void updateOsd() {
        Channel c = cur();
        logoSlot.removeAllViews();
        logoSlot.addView(Cards.make(this, c, 112, 66, false, null), new FrameLayout.LayoutParams(-1, -1));
        osdNum.setText(String.valueOf(c.number));
        osdName.setText(c.name);
        Ui.setButtonIcon(btnFav, Prefs.isFavorite(this, c) ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        updateEpg();
        updateHint();
        updateClock();
    }

    private void updateEpg() {
        Channel c = cur();
        Epg.Prog now = Epg.now(c);
        Epg.Prog next = Epg.next(c);
        if (now == null && next == null) {
            epgRow.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            return;
        }
        epgRow.setVisibility(View.VISIBLE);
        nowTitle.setText(now != null ? now.title : "—");
        nowTime.setText(now != null ? Epg.hm(now.start) + " – " + Epg.hm(now.end) : "");
        nextTitle.setText(next != null ? next.title + "  " + Epg.hm(next.start) : "—");
        if (now != null) {
            progressBar.setVisibility(View.VISIBLE);
            progressFill.getLayoutParams().width = (int) (px(560) * now.progress(System.currentTimeMillis()));
            progressFill.requestLayout();
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateHint() {
        String h = "▲▼ kanal  ·  OK liste  ·  0–9 numara";
        long now = System.currentTimeMillis();
        if (Prefs.sleepAt > now) h += "  ·  Uyku: " + ((Prefs.sleepAt - now + 59999) / 60000) + " dk";
        hint.setText(h);
    }

    private void updateClock() {
        clockLocal.setText(Actions.localTime());
        String tr = Actions.turkeyTime();
        clockTr.setText(tr == null ? "" : "TR " + tr);
        clockTr.setVisibility(tr == null ? View.GONE : View.VISIBLE);
    }

    private void showOsd() {
        osd.setVisibility(View.VISIBLE);
        clockBox.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOsd);
        ui.postDelayed(hideOsd, osd.hasFocus() ? 8000 : 5000);
    }

    private void hideOsd() {
        ui.removeCallbacks(hideOsd);
        if (errorPanel.getVisibility() == View.VISIBLE) return;
        boolean hadFocus = osd.hasFocus();
        osd.setVisibility(View.GONE);
        clockBox.setVisibility(View.GONE);
        if (hadFocus) sink.requestFocus();
    }

    private void showError() {
        errorSince = System.currentTimeMillis();
        stream.stopKeepFrame();
        msg.setVisibility(View.GONE);
        errorText.setText("Bu kanalın yayını şu an açılamıyor.\n"
                + "Yenile'ye basabilir veya başka kanala geçebilirsin. Bir dakika içinde kendiliğinden tekrar denenecek.");
        osdLoading.setText("Yayın yok");
        osdLoading.setVisibility(View.VISIBLE);
        errorPanel.setVisibility(View.VISIBLE);
        osd.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOsd);
        errRefresh.requestFocus();
    }

    private void hideError() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            errorPanel.setVisibility(View.GONE);
            sink.requestFocus();
        }
    }

    // ------------------------------------------------------------------ hızlı kanal listesi

    private void showZap() {
        zapCats.clear();
        boolean anyFav = !Prefs.favorites(this).isEmpty();
        if (anyFav) zapCats.add("Favoriler");
        zapCats.addAll(Channel.categories(channels));
        zapCat = Prefs.isFavorite(this, cur()) && anyFav ? 0 : Math.max(0, zapCats.indexOf(cur().category));
        hideOsd();
        zapPanel.setVisibility(View.VISIBLE);
        buildZapRows(true);
    }

    private void hideZap() {
        zapPanel.setVisibility(View.GONE);
        zapList.removeAllViews();
        sink.requestFocus();
    }

    private void zapCategory(int delta) {
        int n = zapCats.size();
        zapCat = ((zapCat + delta) % n + n) % n;
        buildZapRows(false);
    }

    private void buildZapRows(boolean focusCurrent) {
        String cat = zapCats.get(zapCat);
        zapTitle.setText(cat);
        zapCount.setText((zapCat + 1) + " / " + zapCats.size() + " kategori");
        zapList.removeAllViews();
        List<Channel> list = new ArrayList<>();
        if ("Favoriler".equals(cat)) {
            for (String n : Prefs.favorites(this)) {
                for (Channel c : channels) {
                    if (c.name.equals(n)) {
                        list.add(c);
                        break;
                    }
                }
            }
        } else {
            for (Channel c : channels) if (c.category.equals(cat)) list.add(c);
        }
        View toFocus = null;
        for (Channel c : list) {
            View row = zapRow(c);
            zapList.addView(row);
            if (toFocus == null || (focusCurrent && c.index == index)) toFocus = row;
        }
        zapScroll.scrollTo(0, 0);
        if (toFocus != null) toFocus.requestFocus();
    }

    private View zapRow(final Channel c) {
        final boolean current = c.index == index;
        final boolean offline = Health.status(c) == Health.OFFLINE;
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(px(10), 0, px(14), 0);
        final GradientDrawable bg = Ui.rounded(this, current ? Ui.SURFACE2 : 0x00000000, 14);
        row.setBackground(bg);

        row.addView(Cards.make(this, c, 64, 38, false, null), Ui.margins(this, px(64), px(38), 0, 0, 14, 0));
        final TextView num = Ui.text(this, String.valueOf(c.number), 15, Ui.MEDIUM, Ui.MUTED);
        row.addView(num, new LinearLayout.LayoutParams(px(36), -2));
        final TextView name = Ui.text(this, c.name, 18, Ui.BOLD, Ui.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        String st = current ? "İZLENİYOR" : (offline ? "Kapalı" : "");
        final TextView badge = Ui.text(this, st, 12, Ui.BOLD, current ? Ui.WHITE : Ui.MUTED);
        badge.setPadding(px(9), px(4), px(9), px(4));
        if (current) badge.setBackground(Ui.rounded(this, Ui.ACCENT, 8));
        row.addView(badge);

        row.setAlpha(offline && !current ? 0.5f : 1f);
        row.setLayoutParams(Ui.margins(this, -1, px(56), 0, 0, 0, 6));
        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Ui.WHITE : (current ? Ui.SURFACE2 : 0x00000000));
            name.setTextColor(has ? Ui.BG : Ui.TEXT);
            num.setTextColor(has ? Ui.BG : Ui.MUTED);
            if (!current) badge.setTextColor(has ? Ui.BG : Ui.MUTED);
            v.setAlpha(has || !offline || current ? 1f : 0.5f);
        });
        row.setOnClickListener(v -> {
            hideZap();
            if (c.index != index) changeChannel(c.index);
        });
        return row;
    }

    // ------------------------------------------------------------------ uyku zamanlayıcısı

    private void cycleSleep() {
        long now = System.currentTimeMillis();
        int curMin = Prefs.sleepAt > now ? (int) ((Prefs.sleepAt - now + 59999) / 60000) : 0;
        int nextMin = curMin == 0 ? 30 : curMin <= 30 ? 60 : curMin <= 60 ? 90 : 0;
        Prefs.sleepAt = nextMin == 0 ? 0 : now + nextMin * 60000L;
        Toast.makeText(this, nextMin == 0 ? "Uyku zamanlayıcısı kapatıldı"
                : "Yayın " + nextMin + " dakika sonra kapanacak", Toast.LENGTH_SHORT).show();
        updateHint();
        showOsd();
    }

    private void cancelSleep() {
        Prefs.sleepAt = 0;
        sleepPanel.setVisibility(View.GONE);
        Toast.makeText(this, "Uyku zamanlayıcısı iptal edildi", Toast.LENGTH_SHORT).show();
        updateHint();
    }

    private void goSleep() {
        Prefs.sleepAt = 0;
        sleeping = true;
        stream.stop();
        hideZap();
        hideError();
        msg.setVisibility(View.GONE);
        hideOsd();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sleepText.setText("Uyku modu\nDevam etmek için herhangi bir tuşa bas");
        sleepPanel.setVisibility(View.VISIBLE);
        sleepPanel.setAlpha(0.6f);
    }

    private void wake() {
        sleeping = false;
        sleepPanel.setVisibility(View.GONE);
        sleepPanel.setAlpha(1f);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        changeChannel(index);
    }

    // ------------------------------------------------------------------ kumanda

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (sleeping) {
            wake();
            return true;
        }
        if (sleepPanel.getVisibility() == View.VISIBLE) {
            cancelSleep();
            return true;
        }
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) {
            addDigit(k - KeyEvent.KEYCODE_0);
            return true;
        }
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) {
            addDigit(k - KeyEvent.KEYCODE_NUMPAD_0);
            return true;
        }

        if (zapPanel.getVisibility() == View.VISIBLE) {
            switch (k) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_GUIDE:
                    hideZap();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    zapCategory(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    zapCategory(1);
                    return true;
                default:
                    return super.dispatchKeyEvent(e);
            }
        }

        switch (k) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
                changeChannel(index + 1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                changeChannel(index - 1);
                return true;
            case KeyEvent.KEYCODE_GUIDE:
                showZap();
                return true;
        }

        if (errorPanel.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(e);

        boolean barFocused = osd.getVisibility() == View.VISIBLE && osd.hasFocus();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (barFocused) {
                    showOsd();
                    return super.dispatchKeyEvent(e);
                }
                showZap();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showOsd();
                if (!barFocused) {
                    btnRefresh.requestFocus();
                    return true;
                }
                return super.dispatchKeyEvent(e);
            case KeyEvent.KEYCODE_BACK:
                if (barFocused) {
                    hideOsd();
                    return true;
                }
                return super.dispatchKeyEvent(e);
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                userPaused = state == 1;
                stream.toggle();
                showOsd();
                return true;
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_MENU:
                showOsd();
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void addDigit(int d) {
        if (digits.length() >= 3) digits.setLength(0);
        digits.append(d);
        numberEntry.setText(digits.toString());
        numberEntry.setVisibility(View.VISIBLE);
        ui.removeCallbacks(commitDigits);
        ui.postDelayed(commitDigits, 1500);
    }

    private void commitDigits() {
        numberEntry.setVisibility(View.GONE);
        if (digits.length() == 0) return;
        int n = Integer.parseInt(digits.toString());
        digits.setLength(0);
        if (n >= 1 && n <= channels.size()) {
            if (zapPanel.getVisibility() == View.VISIBLE) hideZap();
            changeChannel(n - 1);
        } else {
            Toast.makeText(this, "Kanal " + n + " yok", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onStart() {
        super.onStart();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        stopped = false;
        if (!sleeping) changeChannel(index);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopped = true;
        ui.removeCallbacks(tick);
        stream.detach(); // geri dönülürse ana ekran yayını kesmeden devralır
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        stream.release();
        super.onDestroy();
    }
}
