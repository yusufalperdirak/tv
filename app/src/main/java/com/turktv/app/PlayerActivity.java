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
import android.view.ViewGroup;
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
 * Tam ekran izleme: video oynatıcı, kanal bilgisi, hızlı kanal listesi,
 * otomatik yeniden bağlanma ve uyku zamanlayıcısı.
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_INDEX = "index";

    private static final int ERR_FAILED = 3;
    private static final long STALL_MS = 20000;   // bu kadar süre görüntü gelmezse yeniden bağlan
    private static final long RECHECK_MS = 60000; // yayın yoksa bu aralıkla tekrar kontrol et
    private static final int MAX_RETRIES = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Channel> channels;
    private int index;

    // oynatma durumu
    private StreamPlayer stream;
    private int state = -1;
    private long stateSince;
    private boolean userPaused;
    private int retries;
    private boolean stopped;
    private int errorKind;
    private long errorSince;
    private boolean sleeping;

    // görünümler
    private View sink;
    private TextView msg;
    private LinearLayout osdTop, bottomBar, clockBox;
    private TextView osdNum, osdName, osdLive, osdSub, clockLocal, clockTr;
    private TextView nextLabel, prevLabel;
    private LinearLayout btnFav, btnSleep, btnList;
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
                onPlayerError();
            }
        });

        sink = new View(this);
        sink.setFocusable(true);
        root.addView(sink, new FrameLayout.LayoutParams(1, 1));

        msg = Ui.text(this, "", 18, Ui.MEDIUM, Ui.SOFT);
        msg.setPadding(px(22), px(14), px(22), px(14));
        msg.setBackground(Ui.rounded(this, Ui.SCRIM, 16));
        root.addView(msg, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

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
        nLp.setMargins(0, px(110), px(40), 0);
        root.addView(numberEntry, nLp);

        setContentView(root);
        sink.requestFocus();
        changeChannel(index);
    }

    // ------------------------------------------------------------------ arayüz kurulumu

    private void buildOsd(FrameLayout root) {
        // Sol üst: kanal kartı
        osdTop = new LinearLayout(this);
        osdTop.setOrientation(LinearLayout.HORIZONTAL);
        osdTop.setGravity(Gravity.CENTER_VERTICAL);
        osdTop.setPadding(px(18), px(18), px(26), px(18));
        osdTop.setBackground(Ui.rounded(this, Ui.SCRIM, 20));
        osdNum = Ui.text(this, "", 28, Ui.BOLD, Ui.WHITE);
        osdNum.setGravity(Gravity.CENTER);
        osdTop.addView(osdNum, Ui.margins(this, px(64), px(64), 0, 0, 20, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        osdName = Ui.text(this, "", 32, Ui.BOLD, Ui.TEXT);
        col.addView(osdName);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        osdLive = Ui.text(this, "● CANLI", 12, Ui.BOLD, Ui.WHITE);
        osdLive.setLetterSpacing(0.08f);
        osdLive.setPadding(px(10), px(5), px(10), px(5));
        osdLive.setBackground(Ui.rounded(this, Ui.ACCENT, 8));
        row.addView(osdLive, Ui.margins(this, -2, -2, 0, 0, 12, 0));
        osdSub = Ui.text(this, "", 14, Ui.REGULAR, 0xFFA8B1BF);
        row.addView(osdSub);
        col.addView(row, Ui.margins(this, -2, -2, 0, 10, 0, 0));
        osdTop.addView(col);
        FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tLp.setMargins(px(40), px(36), 0, 0);
        root.addView(osdTop, tLp);

        // Sağ üst: saat (yerel + Türkiye)
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        clockBox.setPadding(px(18), px(10), px(18), px(10));
        clockBox.setBackground(Ui.rounded(this, Ui.SCRIM, 14));
        clockLocal = Ui.text(this, "", 24, Ui.MEDIUM, Ui.TEXT);
        clockBox.addView(clockLocal);
        clockTr = Ui.text(this, "", 13, Ui.MEDIUM, Ui.MUTED);
        clockBox.addView(clockTr, Ui.margins(this, -2, -2, 0, 4, 0, 0));
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cLp.setMargins(0, px(40), px(40), 0);
        root.addView(clockBox, cLp);

        // Alt çubuk
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setClipChildren(false);
        nextLabel = Ui.text(this, "", 16, Ui.BOLD, Ui.TEXT);
        bottomBar.addView(zapHintCard("▲", "Sonraki", nextLabel));
        prevLabel = Ui.text(this, "", 16, Ui.BOLD, Ui.TEXT);
        View prevCard = zapHintCard("▼", "Önceki", prevLabel);
        bottomBar.addView(prevCard);
        ((LinearLayout.LayoutParams) prevCard.getLayoutParams()).setMargins(px(12), 0, 0, 0);
        bottomBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        btnFav = Ui.button(this, R.drawable.ic_heart, null, 54, Ui.SCRIM);
        btnFav.setOnClickListener(v -> {
            boolean now = Prefs.toggleFavorite(this, cur());
            Toast.makeText(this, now ? "Favorilere eklendi" : "Favorilerden çıkarıldı", Toast.LENGTH_SHORT).show();
            updateOsd();
        });
        bottomBar.addView(btnFav);
        btnSleep = Ui.button(this, R.drawable.ic_moon, "Uyku", 54, Ui.SCRIM);
        btnSleep.setOnClickListener(v -> cycleSleep());
        addBarButton(btnSleep);
        LinearLayout btnRefresh = Ui.button(this, R.drawable.ic_refresh, "Yenile", 54, Ui.SCRIM);
        btnRefresh.setOnClickListener(v -> refresh());
        addBarButton(btnRefresh);
        btnList = Ui.button(this, R.drawable.ic_list, "Kanal listesi", 54, Ui.SCRIM);
        btnList.setOnClickListener(v -> showZap());
        addBarButton(btnList);

        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bLp.setMargins(px(40), 0, px(40), px(36));
        root.addView(bottomBar, bLp);
    }

    private void addBarButton(LinearLayout b) {
        bottomBar.addView(b);
        ((LinearLayout.LayoutParams) b.getLayoutParams()).setMargins(px(12), 0, 0, 0);
    }

    private View zapHintCard(String key, String caption, TextView label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(px(14), px(10), px(18), px(10));
        card.setBackground(Ui.rounded(this, Ui.SCRIM, 16));
        TextView k = Ui.text(this, key, 14, Ui.BOLD, Ui.TEXT);
        k.setGravity(Gravity.CENTER);
        k.setBackground(Ui.rounded(this, Ui.KEYCAP, 10));
        card.addView(k, Ui.margins(this, px(34), px(34), 0, 0, 12, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, caption, 12, Ui.MEDIUM, Ui.MUTED));
        col.addView(label, Ui.margins(this, -2, -2, 0, 3, 0, 0));
        card.addView(col);
        card.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return card;
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
        buttons.addView(errNext);
        ((LinearLayout.LayoutParams) errNext.getLayoutParams()).setMargins(px(12), 0, 0, 0);

        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void buildZap(FrameLayout root) {
        zapPanel = new FrameLayout(this);
        zapPanel.setBackgroundColor(0xF00B0D12);
        zapPanel.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(28), px(32), px(22), px(26));
        col.setClipChildren(false);
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
        zapScroll.setClipToPadding(false);
        zapList = new LinearLayout(this);
        zapList.setOrientation(LinearLayout.VERTICAL);
        zapList.setPadding(0, px(6), 0, px(6));
        zapScroll.addView(zapList);
        col.addView(zapScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        ((LinearLayout.LayoutParams) zapScroll.getLayoutParams()).setMargins(0, px(18), 0, px(14));

        col.addView(Ui.text(this, "▲▼ gez    ◀▶ kategori    OK izle    Geri kapat", 13, Ui.MEDIUM, Ui.MUTED));

        zapPanel.setVisibility(View.GONE);
        root.addView(zapPanel, new FrameLayout.LayoutParams(px(440), -1, Gravity.START));
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

    /** Kullanıcının kanal değiştirmesi. */
    private void changeChannel(int i) {
        retries = 0;
        playChannel(i);
    }

    /** Yenile tuşu: aynı kanalı baştan aç. */
    private void refresh() {
        Toast.makeText(this, cur().name + " yenileniyor", Toast.LENGTH_SHORT).show();
        changeChannel(index);
    }

    private void playChannel(int i) {
        index = wrap(i);
        Prefs.last(this, index);
        hideError();
        userPaused = false;
        setState(-1);
        showMsg(retries > 0 ? "Bağlantı yeniden kuruluyor… (" + retries + "/" + MAX_RETRIES + ")" : "Yükleniyor…");
        updateOsd();
        showOsd();
        stream.play(cur(), false);
    }

    private void setState(int s) {
        state = s;
        stateSince = System.currentTimeMillis();
    }

    private void onPlayerState(int s) {
        setState(s);
        if (s == 1) {
            userPaused = false;
            retries = 0;
            Health.report(cur(), true);
            hideMsg();
            osdLive.setText("● CANLI");
        } else if (s == 2) {
            osdLive.setText("❚❚ DURAKLATILDI");
        } else if (s == 3 && msg.getVisibility() != View.VISIBLE) {
            showMsg("Yükleniyor…");
        } else if (s == 0) {
            reconnect(); // yayın bitti/yenilendi: yeni yayını bul
        }
    }

    private void onPlayerError() {
        reconnect();
    }

    /** Otomatik yeniden bağlanma: birkaç kez dener, olmazsa Yenile ekranını gösterir. */
    private void reconnect() {
        if (stopped || sleeping) return;
        retries++;
        if (retries > MAX_RETRIES) {
            Health.report(cur(), false);
            showError(ERR_FAILED);
            return;
        }
        playChannel(index);
    }

    private void onTick() {
        long now = System.currentTimeMillis();
        updateClock();

        if (!stopped && !sleeping) {
            boolean errorShown = errorPanel.getVisibility() == View.VISIBLE;
            // Donma / kopma: belirli süre görüntü gelmezse yeniden bağlan
            if (!errorShown && !userPaused && state != 1 && state != 2 && now - stateSince > STALL_MS) {
                reconnect();
            }
            // Yayın yoksa veya bağlanamadıysa: arada bir tekrar dene, gelince otomatik aç
            if (errorShown && now - errorSince > RECHECK_MS) {
                errorSince = now;
                changeChannel(index); // arada bir kendiliğinden tekrar dene
            }
        }

        // Uyku zamanlayıcısı
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
        updateSleepButton();
    }

    // ------------------------------------------------------------------ mesajlar ve paneller

    private void showMsg(String s) {
        msg.setText(s);
        msg.setVisibility(View.VISIBLE);
    }

    private void hideMsg() {
        msg.setVisibility(View.GONE);
    }

    private void showError(int kind) {
        errorKind = kind;
        errorSince = System.currentTimeMillis();
        stream.stop();
        hideMsg();
        String text = "Bu kanalın yayını şu an açılamıyor.\n"
                + "Yenile'ye basabilir veya başka kanala geçebilirsin. Bir dakika içinde kendiliğinden tekrar denenecek.";
        errorText.setText(text);
        osdLive.setText("YAYIN YOK");
        errorPanel.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOsd);
        osdTop.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.GONE);
        errRefresh.requestFocus();
    }

    private void hideError() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            errorPanel.setVisibility(View.GONE);
            sink.requestFocus();
        }
    }

    private void updateOsd() {
        Channel c = cur();
        osdNum.setText(String.valueOf(c.number));
        osdNum.setBackground(Ui.rounded(this, c.color, 16));
        osdName.setText(c.name);
        osdLive.setText("● CANLI");
        osdSub.setText(c.category);
        Channel next = channels.get(wrap(index + 1));
        Channel prev = channels.get(wrap(index - 1));
        nextLabel.setText(next.number + " · " + next.name);
        prevLabel.setText(prev.number + " · " + prev.name);
        Ui.setButtonIcon(btnFav, Prefs.isFavorite(this, c) ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        updateClock();
    }

    private void updateClock() {
        clockLocal.setText(Actions.localTime());
        String tr = Actions.turkeyTime();
        clockTr.setText(tr == null ? "" : "Türkiye " + tr);
        clockTr.setVisibility(tr == null ? View.GONE : View.VISIBLE);
    }

    private void showOsd() {
        osdTop.setVisibility(View.VISIBLE);
        clockBox.setVisibility(View.VISIBLE);
        if (errorPanel.getVisibility() != View.VISIBLE) bottomBar.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOsd);
        ui.postDelayed(hideOsd, bottomBar.hasFocus() ? 8000 : 5000);
    }

    private void hideOsd() {
        ui.removeCallbacks(hideOsd);
        if (errorPanel.getVisibility() == View.VISIBLE) return;
        boolean hadFocus = bottomBar.hasFocus();
        osdTop.setVisibility(View.GONE);
        clockBox.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        if (hadFocus) sink.requestFocus();
    }

    // ------------------------------------------------------------------ hızlı kanal listesi

    private void showZap() {
        zapCats.clear();
        boolean anyFav = false;
        for (Channel c : channels) if (Prefs.isFavorite(this, c)) { anyFav = true; break; }
        if (anyFav) zapCats.add("Favoriler");
        zapCats.addAll(Channel.categories(channels));
        zapCat = Math.max(0, zapCats.indexOf(cur().category));
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
        View toFocus = null;
        for (Channel c : channels) {
            boolean inCat = "Favoriler".equals(cat) ? Prefs.isFavorite(this, c) : c.category.equals(cat);
            if (!inCat) continue;
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
        row.setPadding(px(14), 0, px(14), 0);
        final GradientDrawable bg = Ui.rounded(this, current ? Ui.SURFACE2 : 0x00000000, 14);
        row.setBackground(bg);

        final TextView num = Ui.text(this, String.valueOf(c.number), 17, Ui.MEDIUM, Ui.SOFT);
        row.addView(num, new LinearLayout.LayoutParams(px(38), -2));
        View sw = new View(this);
        sw.setBackground(Ui.rounded(this, c.color, 4));
        row.addView(sw, Ui.margins(this, px(10), px(26), 0, 0, 12, 0));
        final TextView name = Ui.text(this, c.name, 18, Ui.BOLD, Ui.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        String st = current ? "İZLENİYOR" : (Health.status(c) == Health.LIVE ? "Canlı" : (offline ? "Kapalı" : ""));
        final TextView badge = Ui.text(this, st, 12, Ui.BOLD, current ? Ui.WHITE : Ui.MUTED);
        badge.setPadding(px(9), px(4), px(9), px(4));
        if (current) badge.setBackground(Ui.rounded(this, Ui.ACCENT, 8));
        row.addView(badge);

        row.setAlpha(offline && !current ? 0.5f : 1f);
        row.setLayoutParams(Ui.margins(this, -1, px(54), 0, 0, 0, 6));
        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Ui.WHITE : (current ? Ui.SURFACE2 : 0x00000000));
            int fg = has ? Ui.BG : Ui.TEXT;
            name.setTextColor(fg);
            num.setTextColor(has ? Ui.BG : Ui.SOFT);
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
        int[] steps = {30, 60, 90};
        int curMin = Prefs.sleepAt > now ? (int) ((Prefs.sleepAt - now + 59999) / 60000) : 0;
        int nextMin = 0;
        for (int s : steps) {
            if (s > curMin) {
                nextMin = s;
                break;
            }
        }
        if (curMin == 0) nextMin = 30;
        Prefs.sleepAt = nextMin == 0 ? 0 : now + nextMin * 60000L;
        Toast.makeText(this, nextMin == 0 ? "Uyku zamanlayıcısı kapatıldı"
                : "Yayın " + nextMin + " dakika sonra kapanacak", Toast.LENGTH_SHORT).show();
        updateSleepButton();
        showOsd();
    }

    private void updateSleepButton() {
        long now = System.currentTimeMillis();
        if (Prefs.sleepAt > now) {
            int min = (int) ((Prefs.sleepAt - now + 59999) / 60000);
            Ui.setButtonLabel(btnSleep, "Uyku: " + min + " dk");
        } else {
            Ui.setButtonLabel(btnSleep, "Uyku");
        }
    }

    private void cancelSleep() {
        Prefs.sleepAt = 0;
        sleepPanel.setVisibility(View.GONE);
        Toast.makeText(this, "Uyku zamanlayıcısı iptal edildi", Toast.LENGTH_SHORT).show();
        updateSleepButton();
    }

    private void goSleep() {
        Prefs.sleepAt = 0;
        sleeping = true;
        stream.stop();
        hideZap();
        hideError();
        hideMsg();
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

        if (errorPanel.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(e); // butonlar arasında gezinme, Geri ile çıkış
        }

        boolean barFocused = bottomBar.getVisibility() == View.VISIBLE && bottomBar.hasFocus();
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
                    btnList.requestFocus();
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
        stream.stop();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        stream.release();
        super.onDestroy();
    }
}
