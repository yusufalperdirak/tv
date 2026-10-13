package com.turktv.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private YtPlayer yt;          // resmi YouTube canlı yayını olan kanallar için (ör. ATV)
    private boolean usingYt;      // şu an YouTube'dan mı oynuyor
    private boolean ytFailed;     // bu kanal için YouTube denendi ve olmadı → listedeki linke geç
    private int ytToken;
    private boolean ytFromMemory; // hatırlanan yayın kimliğiyle mi başlatıldı
    private int state = -1;
    private long stateSince;
    private boolean userPaused;
    private int retries;
    private boolean stopped;
    private long errorSince;
    private boolean sleeping;

    private View sink;
    private ProgressBar spinner;     // ortadaki dönen yükleme simgesi
    private Clock clock;
    private View clockBox;
    private boolean zapOpen;
    private FrameLayout osd;
    private FrameLayout logoSlot;
    private TextView osdNum, osdName, osdLoading;
    private LinearLayout epgRow;
    private TextView nowTitle, nowTime, nextTitle;
    private View progressBar, progressFill;
    private TextView hint;
    private LinearLayout btnFav, btnRefresh, btnSleep, btnList, btnLive;
    private TextView liveBadge;   // "● CANLI" ya da "1:20 geride"
    private long ytBehindSec;     // YouTube'da kendi sarmalarımızdan hesaplanan gerilik
    // geri sarma görselleri
    private FrameLayout seekBubble;
    private ImageView bubbleIcon;
    private TextView bubbleNum, bubbleLabel;
    private int seekAccum;        // art arda basışlarda toplam saniye (+ ileri, − geri)
    private LinearLayout tsRow;   // zaman çizgisi
    private TextView tsLeft, tsLive;
    private FrameLayout tsTrack;
    private View tsFill, tsKnob;
    private final Runnable hideBubble = () -> {
        seekAccum = 0;
        if (seekBubble != null) seekBubble.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(220)
                .setInterpolator(Ui.EASE).withEndAction(() -> seekBubble.setVisibility(View.GONE)).start();
    };
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
        YouTubeLive.init(this);
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

        yt = new YtPlayer(this);
        yt.web.setVisibility(View.GONE);
        root.addView(yt.web, new FrameLayout.LayoutParams(-1, -1));
        yt.setListener(new YtPlayer.Listener() {
            @Override
            public void onState(int s) {
                if (usingYt && s >= 0) onPlayerState(s);
            }

            @Override
            public void onError(int code) {
                if (usingYt) ytFallback();
            }
        });

        sink = new View(this);
        sink.setFocusable(true);
        root.addView(sink, new FrameLayout.LayoutParams(1, 1));

        spinner = spinner();
        root.addView(spinner, new FrameLayout.LayoutParams(px(56), px(56), Gravity.CENTER));

        buildClock(root);
        buildOsd(root);
        buildSeekBubble(root);
        buildError(root);
        buildZap(root);
        buildSleep(root);

        numberEntry = Ui.text(this, "", 52, Ui.BOLD, Ui.TEXT);
        numberEntry.setLetterSpacing(0.1f);
        numberEntry.setPadding(px(22), px(12), px(22), px(12));
        numberEntry.setBackground(Ui.rounded(this, 0xE60B0D12, 18));
        numberEntry.setVisibility(View.GONE);
        FrameLayout.LayoutParams nLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        nLp.setMargins(0, px(Ui.edgeY(this) + 60), px(Ui.edgeX(this)), 0);
        root.addView(numberEntry, nLp);

        setContentView(root);
        sink.requestFocus();
        changeChannel(index);
    }

    // ------------------------------------------------------------------ arayüz

    private void buildClock(FrameLayout root) {
        clock = new Clock(this);
        clockBox = clock.view;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, px(Ui.edgeY(this)), px(Ui.edgeX(this)), 0);
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
        sLp.setMargins(px(Ui.edgeX(this)), 0, px(Ui.edgeX(this)), px(Ui.edgeY(this) + 8));
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
        osdName.setMaxWidth(px(420));
        r1.addView(osdName);
        osdLoading = Ui.text(this, "", 13, Ui.BOLD, 0xFFE3E7ED);
        osdLoading.setPadding(px(11), px(5), px(11), px(5));
        osdLoading.setBackground(Ui.rounded(this, 0x24F2F4F7, 12));
        r1.addView(osdLoading, Ui.margins(this, -2, -2, 14, 0, 0, 0));
        liveBadge = Ui.text(this, "", 13, Ui.BOLD, Ui.TEXT);
        liveBadge.setPadding(px(11), px(5), px(11), px(5));
        liveBadge.setBackground(Ui.rounded(this, 0x24F2F4F7, 12));
        liveBadge.setVisibility(View.GONE);
        r1.addView(liveBadge, Ui.margins(this, -2, -2, 14, 0, 0, 0));
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

        tsRow = new LinearLayout(this);
        tsRow.setOrientation(LinearLayout.HORIZONTAL);
        tsRow.setGravity(Gravity.CENTER_VERTICAL);
        tsLeft = Ui.text(this, "", 15, Ui.BOLD, Ui.TEXT);
        tsLeft.setMinWidth(px(64));
        tsRow.addView(tsLeft);
        tsTrack = new FrameLayout(this);
        tsTrack.setClipChildren(false);
        View base = new View(this);
        base.setBackground(Ui.rounded(this, 0x33F2F4F7, 3));
        FrameLayout.LayoutParams baseLp = new FrameLayout.LayoutParams(-1, px(6), Gravity.CENTER_VERTICAL);
        tsTrack.addView(base, baseLp);
        tsFill = new View(this);
        tsFill.setBackground(Ui.rounded(this, 0xD9F2F4F7, 3));
        tsTrack.addView(tsFill, new FrameLayout.LayoutParams(0, px(6), Gravity.CENTER_VERTICAL));
        tsKnob = new View(this);
        android.graphics.drawable.GradientDrawable kd = new android.graphics.drawable.GradientDrawable();
        kd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        kd.setColor(Ui.WHITE);
        tsKnob.setBackground(kd);
        tsKnob.setElevation(px(4));
        tsTrack.addView(tsKnob, new FrameLayout.LayoutParams(px(16), px(16), Gravity.CENTER_VERTICAL));
        tsRow.addView(tsTrack, Ui.margins(this, 0, px(20), 12, 0, 12, 0));
        ((LinearLayout.LayoutParams) tsTrack.getLayoutParams()).weight = 1f;
        tsLive = Ui.text(this, "CANLI", 12, Ui.BOLD, Ui.MUTED);
        tsLive.setLetterSpacing(0.1f);
        tsLive.setPadding(px(10), px(4), px(10), px(4));
        tsLive.setBackground(Ui.rounded(this, 0x24F2F4F7, 10));
        tsRow.addView(tsLive);
        tsRow.setVisibility(View.GONE);
        mid.addView(tsRow, Ui.margins(this, px(620), -2, 0, 14, 0, 0));

        // Sağ: butonlar
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setClipChildren(false);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        btnLive = Ui.button(this, 0, "Canlıya dön", 48, 0x24F2F4F7);
        for (int i = 0; i < btnLive.getChildCount(); i++) {
            if (btnLive.getChildAt(i) instanceof TextView) {
                SpannableString ls = new SpannableString("●  Canlıya dön");
                ls.setSpan(new ForegroundColorSpan(Ui.LIVE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ((TextView) btnLive.getChildAt(i)).setText(ls);
            }
        }
        btnLive.setOnClickListener(v -> goLive());
        btnLive.setVisibility(View.GONE);
        btns.addView(btnLive);
        ((LinearLayout.LayoutParams) btnLive.getLayoutParams()).setMargins(0, 0, px(10), 0);
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

    /** Dönen yükleme simgesi (beyaz). */
    private ProgressBar spinner() {
        ProgressBar p = new ProgressBar(this);
        p.setIndeterminate(true);
        p.setIndeterminateTintList(ColorStateList.valueOf(Ui.WHITE));
        p.setVisibility(View.GONE);
        return p;
    }

    /** ◀ ▶ basınca beliren yuvarlak "⟲ 10" göstergesi. */
    private void buildSeekBubble(FrameLayout root) {
        seekBubble = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout circle = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable cd = new android.graphics.drawable.GradientDrawable();
        cd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cd.setColor(0x8C0B0D12);
        circle.setBackground(cd);
        bubbleIcon = new ImageView(this);
        bubbleIcon.setImageResource(R.drawable.ic_rewind);
        circle.addView(bubbleIcon, new FrameLayout.LayoutParams(px(76), px(76), Gravity.CENTER));
        bubbleNum = Ui.text(this, "10", 17, Ui.BOLD, Ui.WHITE);
        bubbleNum.setGravity(Gravity.CENTER);
        circle.addView(bubbleNum, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        col.addView(circle, new LinearLayout.LayoutParams(px(120), px(120)));
        bubbleLabel = Ui.text(this, "", 15, Ui.BOLD, Ui.TEXT);
        bubbleLabel.setShadowLayer(px(6), 0, 0, 0x99000000);
        col.addView(bubbleLabel, Ui.margins(this, -2, -2, 0, 12, 0, 0));
        seekBubble.addView(col);
        seekBubble.setVisibility(View.GONE);
        root.addView(seekBubble, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.START));
    }

    private void showBubble(int dir) {
        if ((dir < 0) != (seekAccum < 0)) seekAccum = 0;
        seekAccum += dir * 10;
        int abs = Math.abs(seekAccum);
        bubbleIcon.setImageResource(dir < 0 ? R.drawable.ic_rewind : R.drawable.ic_forward);
        bubbleNum.setText(String.valueOf(Math.min(abs, 99)));
        bubbleLabel.setText(abs >= 60 ? (abs / 60) + " dk " + (abs % 60 > 0 ? (abs % 60) + " sn " : "") + (dir < 0 ? "geri" : "ileri")
                : abs + " sn " + (dir < 0 ? "geri" : "ileri"));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) seekBubble.getLayoutParams();
        lp.gravity = Gravity.CENTER_VERTICAL | (dir < 0 ? Gravity.START : Gravity.END);
        lp.setMargins(dir < 0 ? px(230) : 0, 0, dir < 0 ? 0 : px(230), px(40));
        seekBubble.setLayoutParams(lp);
        ui.removeCallbacks(hideBubble);
        seekBubble.animate().cancel();
        if (seekBubble.getVisibility() != View.VISIBLE) {
            seekBubble.setAlpha(0f);
            seekBubble.setScaleX(0.85f);
            seekBubble.setScaleY(0.85f);
            seekBubble.setVisibility(View.VISIBLE);
        }
        seekBubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(Ui.EASE).withEndAction(null).start();
        // basışta küçük bir "nabız"
        bubbleIcon.setRotation(0);
        bubbleIcon.animate().rotation(dir < 0 ? -40 : 40).setDuration(180).withEndAction(
                () -> bubbleIcon.animate().rotation(0).setDuration(180).start()).start();
        ui.postDelayed(hideBubble, 900);
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
        col.setPadding(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(22), px(Ui.edgeY(this)));
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
        root.addView(zapPanel, new FrameLayout.LayoutParams(px(400 + Ui.edgeX(this)), -1, Gravity.START));
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
        ytBehindSec = 0;
        ytFailed = false;
        playChannel(i, false);
    }

    private void refresh() {
        Toast.makeText(this, cur().name + " yenileniyor", Toast.LENGTH_SHORT).show();
        retries = 0;
        ytFailed = false;
        playChannel(index, true);
    }

    private void playChannel(int i, boolean force) {
        index = wrap(i);
        Prefs.last(this, index);
        hideError();
        userPaused = false;
        setState(-1);
        final Channel c = cur();
        final int t = ++ytToken;
        if (!c.youtube.isEmpty() && !ytFailed) {
            // Önce kanalın resmi YouTube canlı yayını; yoksa listedeki link
            usingYt = true;
            stream.stop();
            yt.web.setVisibility(View.VISIBLE);
            final String mem = force ? null : YouTubeLive.remembered(c.youtube);
            ytFromMemory = mem != null;
            if (mem != null) {
                // hatırlanan yayınla hemen başla, arka planda güncel mi diye bak
                yt.play(mem, false);
                YouTubeLive.find(c.youtube, true, id -> {
                    if (t != ytToken || stopped || id == null || id.equals(mem) || state == 1) return;
                    ytFromMemory = false;
                    yt.play(id, false);
                });
            } else {
                YouTubeLive.find(c.youtube, force, id -> {
                    if (t != ytToken || stopped) return;
                    if (id == null) ytFallback();
                    else yt.play(id, false);
                });
            }
        } else {
            usingYt = false;
            yt.stop();
            yt.web.setVisibility(View.GONE);
            stream.play(c, false, force);
        }
        updateOsd();
        showOsd();
        updateLoading();
    }

    /** YouTube'da yayın yok / açılmadı: aynı kanalı listedeki linkten aç. */
    private void ytFallback() {
        if (ytFromMemory) {
            // hatırlanan yayın artık geçerli değil: bir kez de güncelini arayıp dene
            ytFromMemory = false;
            YouTubeLive.forget(cur().youtube);
            ytToken++;
            yt.stop();
            playChannel(index, true);
            return;
        }
        ytFailed = true;
        ytToken++;
        usingYt = false;
        yt.stop();
        yt.web.setVisibility(View.GONE);
        playChannel(index, true);
    }

    private void setState(int s) {
        state = s;
        stateSince = System.currentTimeMillis();
    }

    /** "Açılıyor" işareti; ilk açılışta (donuk kare yokken) ortada da yazı gösterilir. */
    private void updateLoading() {
        boolean loading = state != 1 && state != 2 && errorPanel.getVisibility() != View.VISIBLE;
        osdLoading.setVisibility(View.GONE);
        spinner.setVisibility(loading ? View.VISIBLE : View.GONE);
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
        if (usingYt) {
            ytFallback();
            return;
        }
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
        updateTimeshift();

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

    // ------------------------------------------------------------------ geri sarma

    /** dir: -1 geri, +1 ileri (10 saniye). */
    private void seek(int dir) {
        showOsd();
        showBubble(dir);
        if (usingYt) {
            if (dir < 0) {
                yt.seekBy(-10);
                ytBehindSec += 10;
            } else if (ytBehindSec <= 10) {
                goLive();
                return;
            } else {
                yt.seekBy(10);
                ytBehindSec -= 10;
            }
        } else if (!stream.seekBy(dir * 10000L)) {
            Toast.makeText(this, "Bu kanalda daha fazla geri sarılamıyor", Toast.LENGTH_SHORT).show();
        }
        updateTimeshift();
    }

    private void goLive() {
        if (usingYt) yt.goLive();
        else stream.goLive();
        ytBehindSec = 0;
        updateTimeshift();
        if (osd.hasFocus()) btnList.requestFocus();
    }

    /** Canlı mı, geride mi: etiket, zaman çizgisi ve "Canlıya dön" butonu. */
    private void updateTimeshift() {
        long behind = usingYt ? ytBehindSec * 1000 : stream.behindLiveMs();
        boolean playing = state == 1 || state == 2;
        if (behind < 0 || !playing) {
            liveBadge.setVisibility(View.GONE);
            tsRow.setVisibility(View.GONE);
            btnLive.setVisibility(View.GONE);
            return;
        }
        if (behind < 4000) { // canlı
            SpannableString s = new SpannableString("●  CANLI");
            s.setSpan(new ForegroundColorSpan(Ui.LIVE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            liveBadge.setText(s);
            liveBadge.setVisibility(View.VISIBLE);
            tsRow.setVisibility(View.GONE);
            if (btnLive.hasFocus()) btnList.requestFocus();
            btnLive.setVisibility(View.GONE);
            return;
        }
        liveBadge.setVisibility(View.GONE);
        btnLive.setVisibility(View.VISIBLE);
        long sec = behind / 1000;
        tsLeft.setText("−" + (sec / 60) + ":" + String.format(Actions.TR, "%02d", sec % 60));

        // oynatma noktasının geri sarılabilir pencere içindeki yeri
        float frac;
        long[] tl = usingYt ? null : stream.timeline();
        if (tl != null) {
            long liveEdge = Math.max(1, tl[1] - StreamPlayer.LIVE_OFFSET_MS);
            frac = Math.max(0f, Math.min(1f, tl[0] / (float) liveEdge));
        } else {
            long window = Math.max(sec + 60, 300);
            frac = 1f - sec / (float) window;
        }
        if (tsRow.getVisibility() != View.VISIBLE) Ui.fadeIn(tsRow, px(8));
        final float f = frac;
        tsTrack.post(() -> {
            int w = tsTrack.getWidth();
            if (w <= 0) return;
            int knob = px(16);
            tsFill.getLayoutParams().width = (int) (w * f);
            tsFill.requestLayout();
            tsKnob.animate().translationX(Math.max(0, w * f - knob / 2f)).setDuration(220).setInterpolator(Ui.EASE).start();
        });
    }

    private void updateHint() {
        String h = "◀▶ geri/ileri sar  ·  ▲▼ kanal  ·  OK menü";
        long now = System.currentTimeMillis();
        if (Prefs.sleepAt > now) h += "  ·  Uyku: " + ((Prefs.sleepAt - now + 59999) / 60000) + " dk";
        hint.setText(h);
    }

    private void updateClock() {
        clock.update();
    }

    private void showOsd() {
        Ui.fadeIn(osd, px(24));
        Ui.fadeIn(clockBox, -px(12));
        ui.removeCallbacks(hideOsd);
        ui.postDelayed(hideOsd, osd.hasFocus() ? 8000 : 5000);
    }

    private void hideOsd() {
        ui.removeCallbacks(hideOsd);
        if (errorPanel.getVisibility() == View.VISIBLE) return;
        boolean hadFocus = osd.hasFocus();
        if (hadFocus) sink.requestFocus();
        Ui.fadeOut(osd, px(24));
        Ui.fadeOut(clockBox, -px(12));
    }

    private void showError() {
        errorSince = System.currentTimeMillis();
        stream.stopKeepFrame();
        ytToken++;
        yt.stop();
        spinner.setVisibility(View.GONE);
        errorText.setText("Bu kanalın yayını şu an açılamıyor.\n"
                + "Yenile'ye basabilir veya başka kanala geçebilirsin. Bir dakika içinde kendiliğinden tekrar denenecek.");
        osdLoading.setText("Yayın yok");
        osdLoading.setVisibility(View.VISIBLE);
        errorPanel.setAlpha(0f);
        errorPanel.setVisibility(View.VISIBLE);
        errorPanel.animate().alpha(1f).setDuration(260).setInterpolator(Ui.EASE).start();
        Ui.fadeIn(osd, px(24));
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
        zapOpen = true;
        buildZapRows(true);
        Ui.slideIn(zapPanel, px(120));
    }

    private void hideZap() {
        if (!zapOpen) return;
        zapOpen = false;
        sink.requestFocus();
        Ui.slideOut(zapPanel, px(120));
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
        spinner.setVisibility(View.GONE);
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

        if (zapOpen) {
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
                // OK: menüyü aç (kanal listesi butonu seçili gelir)
                showOsd();
                btnList.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (barFocused) {
                    showOsd();
                    return super.dispatchKeyEvent(e);
                }
                seek(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(1);
                return true;
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
                if (usingYt) yt.toggle();
                else stream.toggle();
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
            if (zapOpen) hideZap();
            changeChannel(n - 1);
        } else {
            Toast.makeText(this, "Kanal " + n + " yok", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onStart() {
        super.onStart();
        clock.start();
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
        clock.stop();
        stopped = true;
        ui.removeCallbacks(tick);
        stream.detach(); // geri dönülürse ana ekran yayını kesmeden devralır
        ytToken++;
        yt.stop();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        stream.release();
        yt.destroy();
        super.onDestroy();
    }
}
