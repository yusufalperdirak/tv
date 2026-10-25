package com.turktv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ana ekran (launcher). Üstte seçili kanalın bilgisi, altta iki satır:
 * "Son izlenenler + Kanallar" ve cihazdaki "Uygulamalar".
 * Kanal seçiliyken arkada o kanalın canlı önizlemesi, uygulama seçiliyken kayan manzara oynar.
 */
public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Channel> channels;
    private Channel selected;
    private boolean resumed;
    private int builtEdge;
    private String builtLineup = "";

    // arka plan
    private FrameLayout channelBg;
    private StreamPlayer preview;
    private YtPlayer ytPreview;
    private int ytToken;
    private AerialView aerial;
    private TextView previewNote;

    // üst bilgiler
    private ClockPill clock;
    private ImageView weatherIcon;
    private TextView weatherText, ratesText, prayerText, dateText;
    private View sepRates, sepPrayer;

    // kanal bilgisi
    private LinearLayout hero;
    private TextView heroMeta, heroName, heroNow, heroTime;
    private View heroLive;
    private FrameLayout heroBar;
    private View heroFill;

    // satırlar
    private LinearLayout rowsCol;
    private View statusBar;
    private FrameLayout rowsClip;
    // tasarım px (1280 genişlik): kartlar ekranın alt kısmında, önizleme geniş görünür;
    // uygulamaların ilk sırası alttan yalnızca biraz görünür
    private static final int ROWS_CLIP_TOP = 96, ROWS_TOP = 462, HERO_TOP = 292;
    // kanal paneli: kenar boşluğundan PANEL_IN dışarı taşar, içinde tam CARDS_VISIBLE kart sığar
    // PANEL_OUT: panelin üstünde/altında boş alan; büyüyen kart ve gölgesi hiçbir yerden kesilmez
    private static final int PANEL_OUT = 24;
    private static final int PANEL_IN = 24, PANEL_PAD_Y = 28, CARD_GAP = 26, CARDS_VISIBLE = 5;
    private int cardW = 200, cardH = 113;
    private static final int SYS_RED = 0xFFE30A17; // sistem uygulama kartları
    private static final int APP_W = 255, APP_H = 143, APP_GAP = 28;
    private FrameLayout rowA;
    private HScroll chScroll, appScroll;
    private LinearLayout chContent, appCards;
    private final Map<String, List<View>> cardsByName = new HashMap<>();
    private View lastChannelCard;
    private boolean appsMode;
    private String builtApps = "";

    private boolean checkedUpdate;

    private final Runnable previewRunnable = this::startPreview;
    // 5 sn aynı kanalda durunca tam ekran izlemeye geç
    private static final long AUTO_WATCH_MS = 5000;
    private boolean autoArmed;      // kullanıcı kumandayla gezindiyse
    private boolean autoPending;    // süre doldu, önizlemenin başlaması bekleniyor
    private boolean previewPlaying;
    private boolean leaving;
    private FrameLayout uiLayer;
    private final Runnable autoWatch = () -> {
        if (!resumed || appsMode || !autoArmed || leaving || !hasWindowFocus() || clock.isFocused()) return;
        if (previewPlaying) fadeAndWatch();
        else autoPending = true;
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.update();
            updateHero();
            updateStatus();
            ui.postDelayed(this, 20000);
        }
    };

    /** Otomatik kaydırması kapalı yatay kaydırıcı: kaydırmayı odak değişiminde kendimiz yapıyoruz. */
    static final class HScroll extends HorizontalScrollView {
        HScroll(Context c) { super(c); }
        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
    }

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Health.init(this);
        channels = Lineup.get(this);
        Epg.ensureLoaded(this);
        YouTubeLive.init(this);
        for (Channel c : channels) if (!c.youtube.isEmpty()) YouTubeLive.find(c.youtube, false, id -> { });
        selected = channels.get(Lineup.lastIndex(this));

        builtEdge = Prefs.edge(this);
        buildUi();
        buildChannelRow();
        loadApps();
        if (Prefs.autostart(this)) WakeService.start(this);
        ScanJob.schedule(this);

        if (savedInstanceState == null) {
            RemoteList.update(this, (changed, err) -> {
                if (changed && !isFinishing()) {
                    Toast.makeText(this, "Kanal listesi güncellendi", Toast.LENGTH_SHORT).show();
                    Lineup.invalidate();
                    recreate();
                }
            });
            Intent in = getIntent();
            boolean asHome = in != null && in.hasCategory(Intent.CATEGORY_HOME);
            if (Prefs.openLast(this) && !asHome && in != null && in.getBooleanExtra("fromSplash", true)
                    && !in.getBooleanExtra("home", false)) {
                openChannel(selected);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Ana ekran tuşu: kanal satırının başına dön
        if (appsMode || lastChannelCard == null) focusSelectedChannel();
    }

    // ------------------------------------------------------------------ arayüz

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        channelBg = new FrameLayout(this);
        root.addView(channelBg, new FrameLayout.LayoutParams(-1, -1));
        preview = new StreamPlayer(this);
        channelBg.addView(preview.view, new FrameLayout.LayoutParams(-1, -1));
        ytPreview = new YtPlayer(this);
        ytPreview.web.setVisibility(View.GONE);
        channelBg.addView(ytPreview.web, new FrameLayout.LayoutParams(-1, -1));
        ytPreview.setListener(new YtPlayer.Listener() {
            @Override
            public void onState(int state) {
            }

            @Override
            public void onError(int code) {
                ytToken++;
                ytPreview.stop();
                ytPreview.web.setVisibility(View.GONE);
                if (resumed && !appsMode) preview.play(selected.withUrl(Sources.best(MainActivity.this, selected)), !Prefs.previewSound(MainActivity.this));
            }
        });
        preview.setListener(new StreamPlayer.Listener() {
            @Override
            public void onState(int state) {
                if (state == 1) {
                    previewPlaying = true;
                    if (autoPending && resumed && !appsMode) fadeAndWatch();
                    previewNote.setVisibility(View.GONE);
                    Health.report(selected, true);
                    refreshCards(selected);
                }
            }

            @Override
            public void onError() {
                preview.stop();
                previewNote.setText("Bu kanalın yayını şu an açılamıyor");
                previewNote.setVisibility(View.VISIBLE);
                Health.report(selected, false);
                refreshCards(selected);
            }
        });

        aerial = new AerialView(this);
        aerial.setAlpha(0f);
        aerial.setVisibility(View.GONE);
        root.addView(aerial, new FrameLayout.LayoutParams(-1, -1));

        // Önizleme dışındaki her şey bu katmanda: tam ekrana geçerken birlikte solar
        uiLayer = new FrameLayout(this);
        uiLayer.setClipChildren(false);
        root.addView(uiLayer, new FrameLayout.LayoutParams(-1, -1));

        View scrimLeft = new View(this);
        scrimLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x8C0B0D12, 0x400B0D12, 0x000B0D12}));
        uiLayer.addView(scrimLeft, new FrameLayout.LayoutParams(px(760), -1, Gravity.START));
        View scrimBottom = new View(this);
        scrimBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB30B0D12, 0x660B0D12, 0x1A0B0D12, 0x000B0D12}));
        uiLayer.addView(scrimBottom, new FrameLayout.LayoutParams(-1, px(360), Gravity.BOTTOM));
        View scrimTop = new View(this);
        scrimTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x800B0D12, 0x000B0D12}));
        uiLayer.addView(scrimTop, new FrameLayout.LayoutParams(-1, px(140), Gravity.TOP));

        previewNote = Ui.text(this, "", 14, Ui.MEDIUM, Ui.SOFT);
        previewNote.setPadding(px(14), px(8), px(14), px(8));
        previewNote.setBackground(Ui.rounded(this, 0xCC0B0D12, 16));
        previewNote.setVisibility(View.GONE);
        FrameLayout.LayoutParams pnLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pnLp.setMargins(0, px(Ui.edgeY(this) + 66), px(Ui.edgeX(this)), 0);
        uiLayer.addView(previewNote, pnLp);

        buildStatus(uiLayer);
        buildHero(uiLayer);
        hero.setVisibility(View.GONE); // kartların üstünde kanal bilgisi yazmaz: önizleme tek başına

        rowsCol = new LinearLayout(this);
        rowsCol.setOrientation(LinearLayout.VERTICAL);
        rowsCol.setClipChildren(false);
        rowsCol.setClipToPadding(false);
        // Satırlar saatin altından başlayan bir alanda kayar: yukarı kayınca sağ üstteki saatin üstüne binmez
        rowsClip = new FrameLayout(this);
        // üstteki sınır (saatin altı) uiLayer'ın kırpmasıyla gelir; satırlar kendi sınırlarına göre kırpılmaz
        rowsClip.setClipChildren(false);
        rowsClip.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, or, ob) ->
                v.setClipBounds(new Rect(0, 0, r - l, btm - t)));
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-1, -1);
        cLp.topMargin = px(ROWS_CLIP_TOP);
        uiLayer.addView(rowsClip, cLp);
        FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(-1, px(3000), Gravity.TOP); // uzun uygulama ızgarası sığsın
        rLp.topMargin = px(ROWS_TOP - ROWS_CLIP_TOP);
        rowsClip.addView(rowsCol, rLp);

        // Satır A: son izlenenler + kanallar (tek kaydırıcıda)
        chScroll = new HScroll(this);
        styleScroll(chScroll);
        chContent = new LinearLayout(this);
        chContent.setOrientation(LinearLayout.HORIZONTAL);
        chContent.setClipChildren(false);
        chContent.setClipToPadding(false);
        // kartlar panele tam sığsın: (ekran − 2·kenar − aralıklar) / 5
        int usable = Math.round(1280 - 2 * Ui.edgeX(this));
        cardW = (usable - (CARDS_VISIBLE - 1) * CARD_GAP) / CARDS_VISIBLE;
        cardH = Math.round(cardW * 113f / 200f);
        chContent.setPadding(px(PANEL_IN), px(PANEL_OUT + PANEL_PAD_Y), px(PANEL_IN), px(PANEL_OUT + PANEL_PAD_Y));
        chScroll.setFadingEdgeLength(px(PANEL_IN));
        chScroll.addView(chContent);
        // yarı saydam, yuvarlak köşeli panel; kaydırılan kartlar köşelerinden taşmaz
        // panel zemini ayrı bir katman: kaydırıcı ondan daha yüksek, böylece kartlar yalnızca sağ-sol kenarda kırpılır
        rowA = new FrameLayout(this);
        View panelBg = new View(this);
        android.graphics.drawable.GradientDrawable panel = Ui.rounded(this, 0x700E1016, 34);
        panel.setStroke(Math.max(1, px(1)), 0x14FFFFFF);
        panelBg.setBackground(panel);
        FrameLayout.LayoutParams bgLp = new FrameLayout.LayoutParams(-1, -1);
        bgLp.setMargins(0, px(PANEL_OUT), 0, px(PANEL_OUT));
        rowA.addView(panelBg, bgLp);
        rowA.addView(chScroll, new FrameLayout.LayoutParams(-1, -2));
        // yükseklik sabit: zemin görünümü (match_parent) panelin boyunu ekran kadar uzatmasın
        int rowH = px(cardH) + 2 * px(PANEL_OUT + PANEL_PAD_Y);
        LinearLayout.LayoutParams aLp = Ui.margins(this, -1, rowH, Ui.edgeX(this) - PANEL_IN, 0, Ui.edgeX(this) - PANEL_IN, 0);
        aLp.topMargin = -px(PANEL_OUT);
        rowsCol.addView(rowA, aLp);

        // Satır B: uygulamalar
        LinearLayout rowB = new LinearLayout(this);
        rowB.setOrientation(LinearLayout.VERTICAL);
        rowB.setClipChildren(false);
        // Uygulamalar: kanal kartı boyutunda, alt alta ızgara (Apple TV gibi)
        appCards = new LinearLayout(this);
        appCards.setOrientation(LinearLayout.VERTICAL);
        appCards.setClipChildren(false);
        appCards.setClipToPadding(false);
        appCards.setPadding(px(Ui.edgeX(this)), px(10), px(Ui.edgeX(this)), px(260));
        rowB.addView(appCards);
        rowsCol.addView(rowB, Ui.margins(this, -1, -2, 0, 18 - PANEL_OUT, 0, 0));

        setContentView(root);
    }

    private void styleScroll(HScroll hs) {
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setHorizontalFadingEdgeEnabled(true);
        hs.setFadingEdgeLength(px(Ui.edgeX(this)));
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
    }

    private void buildStatus(FrameLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        weatherIcon = new ImageView(this);
        weatherIcon.setVisibility(View.GONE);
        bar.addView(weatherIcon, new LinearLayout.LayoutParams(px(22), px(22)));
        weatherText = Ui.text(this, "", 16, Ui.MEDIUM, 0xFFE6E9EE);
        bar.addView(weatherText, Ui.margins(this, -2, -2, 6, 0, 0, 0));

        sepRates = sep();
        bar.addView(sepRates);
        ratesText = Ui.text(this, "", 15, Ui.MEDIUM, 0xFFC9CED6);
        bar.addView(ratesText);

        sepPrayer = sep();
        bar.addView(sepPrayer);
        prayerText = Ui.text(this, "", 15, Ui.MEDIUM, 0xFFC9CED6);
        bar.addView(prayerText);

        bar.addView(sep());
        dateText = Ui.text(this, "", 15, Ui.MEDIUM, 0xFFC9CED6);
        bar.addView(dateText, Ui.margins(this, -2, -2, 0, 0, 12, 0));

        clock = new ClockPill(this);
        clock.setOnClickListener(v -> go(new Intent(this, TodayActivity.class)));
        clock.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusSelectedChannel();
                return true;
            }
            return false;
        });
        bar.addView(clock);

        // büyüyen hap kesilmesin: çevresinde boşluk bırak, kenar boşluğundan düş
        bar.setPadding(px(10), px(10), px(10), px(10));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, px(Ui.edgeY(this) - 10), px(Ui.edgeX(this) - 10), 0);
        root.addView(bar, lp);
        statusBar = bar;
        // sağ üstte yalnızca saat
        weatherIcon.setVisibility(View.GONE);
        weatherText.setVisibility(View.GONE);
        sepRates.setVisibility(View.GONE);
        ratesText.setVisibility(View.GONE);
        sepPrayer.setVisibility(View.GONE);
        prayerText.setVisibility(View.GONE);
        for (int i = 0; i < bar.getChildCount(); i++) {
            View v = bar.getChildAt(i);
            if (v != clock) v.setVisibility(View.GONE);
        }
    }

    private View sep() {
        View v = new View(this);
        v.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, px(1)), px(16));
        lp.setMargins(px(12), 0, px(12), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void buildHero(FrameLayout root) {
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = Ui.text(this, "●  CANLI", 12, Ui.BOLD, 0xFFFF7A7E);
        live.setLetterSpacing(0.1f);
        live.setPadding(px(10), px(5), px(10), px(5));
        live.setBackground(Ui.rounded(this, 0x3DE30A17, 12));
        heroLive = live;
        meta.addView(live, Ui.margins(this, -2, -2, 0, 0, 12, 0));
        heroMeta = Ui.text(this, "", 14, Ui.MEDIUM, 0xFFAEB5C0);
        meta.addView(heroMeta);
        hero.addView(meta);

        heroName = Ui.text(this, "", 66, Ui.BOLD, Ui.TEXT);
        heroName.setLetterSpacing(-0.02f);
        heroName.setSingleLine(true);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroName, Ui.margins(this, -1, -2, 0, 12, 0, 0));

        heroNow = Ui.text(this, "", 20, Ui.BOLD, Ui.TEXT);
        heroNow.setSingleLine(true);
        heroNow.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroNow, Ui.margins(this, -1, -2, 0, 12, 0, 0));

        LinearLayout prog = new LinearLayout(this);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        heroBar = new FrameLayout(this);
        heroBar.setBackground(Ui.rounded(this, 0x38FFFFFF, 2));
        heroFill = new View(this);
        heroFill.setBackground(Ui.rounded(this, Ui.TEXT, 2));
        heroBar.addView(heroFill, new FrameLayout.LayoutParams(0, -1));
        prog.addView(heroBar, new LinearLayout.LayoutParams(px(214), px(4)));
        heroTime = Ui.text(this, "", 14, Ui.REGULAR, 0xFFAEB5C0);
        prog.addView(heroTime, Ui.margins(this, -2, -2, 12, 0, 0, 0));
        hero.addView(prog, Ui.margins(this, -1, -2, 0, 12, 0, 0));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(640), -2, Gravity.TOP | Gravity.START);
        lp.setMargins(px(Ui.edgeX(this)), px(HERO_TOP), 0, 0);
        root.addView(hero, lp);
    }

    // ------------------------------------------------------------------ satırlar

    private String lineupKey() {
        StringBuilder sb = new StringBuilder();
        for (Channel c : channels) sb.append(c.name).append('|');
        return sb.toString();
    }

    private String recentKey() {
        StringBuilder sb = new StringBuilder();
        for (Channel c : Lineup.recent(this, 2)) sb.append(c.name).append('|');
        return sb.toString();
    }

    private View recentGroup, recentDivider;
    private String builtRecent = "";

    /** Sadece "Son izlenenler" kısmını yeniler (tüm kanal satırını baştan kurmadan). */
    private void rebuildRecent() {
        builtRecent = recentKey();
        if (recentGroup != null) {
            for (int i = 0; i < ((ViewGroup) recentGroup).getChildCount(); i++) {
                View cardsRow = ((ViewGroup) recentGroup).getChildAt(i);
                if (cardsRow instanceof LinearLayout) {
                    LinearLayout cr = (LinearLayout) cardsRow;
                    for (int j = 0; j < cr.getChildCount(); j++) {
                        Channel ch = Cards.channel(cr.getChildAt(j));
                        if (ch != null) {
                            List<View> l = cardsByName.get(ch.name);
                            if (l != null) l.remove(cr.getChildAt(j));
                        }
                    }
                }
            }
            chContent.removeView(recentGroup);
            chContent.removeView(recentDivider);
            recentGroup = null;
            recentDivider = null;
        }
        List<Channel> recent = new ArrayList<>(); // "Son izlenenler" kaldırıldı
        if (recent.isEmpty()) return;
        recentGroup = group("Son izlenenler", null, recent);
        View div = new View(this);
        div.setBackgroundColor(0x2EFFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Math.max(1, px(1.5f)), px(113));
        dlp.setMargins(px(6), px(28), px(22), 0);
        div.setLayoutParams(dlp);
        recentDivider = div;
        chContent.addView(recentGroup, 0);
        chContent.addView(div, 1);
    }

    private void buildChannelRow() {
        builtLineup = lineupKey();
        chContent.removeAllViews();
        cardsByName.clear();
        lastChannelCard = null;
        recentGroup = null;
        recentDivider = null;
        chContent.addView(group("Kanallar", String.valueOf(channels.size()), channels));
        rebuildRecent();
    }

    private LinearLayout group(String title, String count, List<Channel> list) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setClipChildren(false);
        // başlık yok: kartlar doğrudan panelin içinde
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        g.addView(cards);
        for (int i = 0; i < list.size(); i++) {
            final Channel c = list.get(i);
            final boolean last = i == list.size() - 1;
            final View card = Cards.make(this, c, cardW, cardH, true, (ch, v, has) -> {
                if (!has) return;
                lastChannelCard = v;
                // sayfa sayfa: görünen 5 kartın içinde kayma olmaz; 6. karta geçince sonraki 5'e kayar
                ViewGroup row = (ViewGroup) v.getParent();
                int idx = row.indexOfChild(v);
                scrollTo(chScroll, chContent, row.getChildAt((idx / CARDS_VISIBLE) * CARDS_VISIBLE));
                setAppsMode(false);
                select(ch);
            });
            card.setOnClickListener(v -> openChannel(c));
            card.setOnLongClickListener(v -> {
                channelMenu(c);
                return true;
            });
            cards.addView(card, Ui.margins(this, px(cardW), px(cardH), 0, 0, last ? 0 : CARD_GAP, 0));
            List<View> l = cardsByName.get(c.name);
            if (l == null) {
                l = new ArrayList<>();
                cardsByName.put(c.name, l);
            }
            l.add(card);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        g.setLayoutParams(lp);
        return g;
    }

    /** Kartı kaydırıcının sol kenarına getir. */
    private void scrollTo(HorizontalScrollView hs, ViewGroup content, View v) {
        Rect r = new Rect();
        v.getDrawingRect(r);
        content.offsetDescendantRectToMyCoords(v, r);
        // kart panelin sol iç kenarına gelir; sona doğru kaydırma durur, böylece panelde hep tam 5 kart görünür
        hs.smoothScrollTo(Math.max(0, r.left - px(PANEL_IN)), 0);
    }

    private void refreshCards(Channel c) {
        List<View> l = cardsByName.get(c.name);
        if (l != null) for (View v : l) Cards.refresh(v);
    }

    /** Seçili kanala odaklan (son izlenenlerde varsa oradaki, en solda). */
    private void focusSelectedChannel() {
        List<View> l = cardsByName.get(selected.name);
        View target = l != null && !l.isEmpty() ? l.get(l.size() - 1) : firstChannelCard();
        if (target != null) target.requestFocus();
    }

    private View firstChannelCard() {
        for (List<View> l : cardsByName.values()) if (!l.isEmpty()) return l.get(0);
        return null;
    }

    private void channelMenu(final Channel c) {
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new String[]{"Bu kanalı gizle", "Kanalları düzenle (sırala / gizle)", "Vazgeç"}, (d, w) -> {
                    if (w == 0) {
                        List<String> h = Prefs.hidden(this);
                        if (!h.contains(c.name)) h.add(c.name);
                        Prefs.hidden(this, h);
                        Lineup.invalidate();
                        Toast.makeText(this, c.name + " gizlendi. Ayarlar › Kanalları düzenle'den geri açabilirsin.",
                                Toast.LENGTH_LONG).show();
                        reloadLineup();
                    } else if (w == 1) {
                        go(new Intent(this, EditChannelsActivity.class));
                    }
                })
                .show();
    }

    private void reloadLineup() {
        channels = Lineup.get(this);
        selected = channels.get(Lineup.lastIndex(this));
        buildChannelRow();
        ui.post(this::focusSelectedChannel);
    }

    // ------------------------------------------------------------------ uygulamalar

    private void loadApps() {
        Apps.load(this, list -> {
            if (isFinishing()) return;
            StringBuilder key = new StringBuilder();
            for (Apps.Entry e : list) key.append(e.pkg).append('|');
            if (key.toString().equals(builtApps) && appCards.getChildCount() > 0) return;
            builtApps = key.toString();
            boolean hadFocus = appCards.hasFocus();
            appCards.removeAllViews();
            int cols = 4; // Apple TV gibi: sırada 4 uygulama
            LinearLayout rowView = null;
            List<Apps.Entry> all = new ArrayList<>();
            all.add(Apps.builtin("Ara", R.drawable.ic_search, SYS_RED, () -> go(new Intent(this, SearchActivity.class))));
            all.add(Apps.builtin("Tarayıcı", R.drawable.ic_globe, SYS_RED, () -> go(new Intent(this, BrowserActivity.class))));
            all.add(Apps.builtin("Ayarlar", R.drawable.ic_gear, SYS_RED, () -> go(new Intent(this, SettingsActivity.class))));
            all.add(Apps.builtin("TV ayarları", R.drawable.ic_sliders, SYS_RED, () -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    Toast.makeText(this, "TV ayarları açılamadı", Toast.LENGTH_SHORT).show();
                }
            }));
            all.addAll(list);
            for (int i = 0; i < all.size(); i++) {
                final Apps.Entry e = all.get(i);
                if (i % cols == 0) {
                    rowView = new LinearLayout(this);
                    rowView.setOrientation(LinearLayout.HORIZONTAL);
                    rowView.setClipChildren(false);
                    appCards.addView(rowView, Ui.margins(this, -1, -2, 0, 0, 0, APP_GAP));
                }
                View card = Apps.card(this, e, APP_W, APP_H, (en, v, has) -> {
                    if (!has) return;
                    setAppsMode(true);
                    scrollGridTo(v);
                });
                card.setOnClickListener(v -> launch(e));
                rowView.addView(card, Ui.margins(this, px(APP_W), px(APP_H), 0, 0, i % cols == cols - 1 ? 0 : APP_GAP, 0));
            }
            if (hadFocus && appCards.getChildCount() > 0) appCards.getChildAt(0).requestFocus();
        });
    }

    private void launch(Apps.Entry e) {
        if (e.action != null) {
            e.action.run();
            return;
        }
        try {
            startActivity(e.launch);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Intent alt = getPackageManager().getLeanbackLaunchIntentForPackage(e.pkg);
            if (alt == null) alt = getPackageManager().getLaunchIntentForPackage(e.pkg);
            if (alt != null) startActivity(alt);
            else Toast.makeText(this, e.label + " açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    /** Seçili uygulamanın sırası ekranın ortasına gelsin; üstteki kanallar yukarı kayar. */
    private void scrollGridTo(View card) {
        Rect r = new Rect();
        card.getDrawingRect(r);
        rowsCol.offsetDescendantRectToMyCoords(card, r);
        int top = px(ROWS_TOP);
        float ty = Math.min(0, px(330) - (top + r.top));
        rowsCol.animate().translationY(ty).setDuration(380).setInterpolator(Ui.EASE).start();
    }

    /** Uygulamalara geçiş: arka plan siyah-yeşile döner, kanal önizlemesi durur. */
    private void setAppsMode(boolean on) {
        if (appsMode == on) return;
        appsMode = on;
        if (!on) rowsCol.animate().translationY(0).setDuration(380).setInterpolator(Ui.EASE).start();
        rowA.animate().alpha(on ? 0.6f : 1f).setDuration(380).start();
        statusBar.animate().alpha(on ? 0f : 1f).setDuration(400).setInterpolator(Ui.EASE).start();
        clock.setFocusable(!on);
        hero.animate().alpha(on ? 0f : 1f).translationY(on ? -px(20) : 0).setDuration(320).setInterpolator(Ui.EASE).start();
        previewNote.setVisibility(View.GONE);
        if (on) {
            ui.removeCallbacks(previewRunnable);
            aerial.setVisibility(View.VISIBLE);
            aerial.start();
            aerial.animate().alpha(1f).setDuration(600).withEndAction(() -> {
                if (!appsMode) return;
                ytToken++;
                preview.stop();
                ytPreview.stop();
                ytPreview.web.setVisibility(View.GONE);
            }).start();
        } else {
            aerial.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                if (appsMode) return;
                aerial.stop();
                aerial.setVisibility(View.GONE);
            }).start();
            ui.removeCallbacks(previewRunnable);
            if (resumed) ui.postDelayed(previewRunnable, 350);
        }
    }

    // ------------------------------------------------------------------ bilgi

    private void select(Channel c) {
        boolean changed = c != selected;
        selected = c;
        ui.removeCallbacks(autoWatch);
        autoPending = false;
        if (autoArmed) ui.postDelayed(autoWatch, AUTO_WATCH_MS);
        updateHero();
        if (changed) {
            hero.animate().cancel();
            hero.setAlpha(0.3f);
            hero.setTranslationX(-px(8));
            hero.animate().alpha(1f).translationX(0).translationY(0).setDuration(240).setInterpolator(Ui.EASE).start();
        }
        if (!changed && !preview.isFrozen() && previewNote.getVisibility() == View.GONE && resumed) return;
        previewNote.setVisibility(View.GONE);
        ui.removeCallbacks(previewRunnable);
        // hızlı gezinirken her kartta yayın açılmasın: kısa süre durunca başlar
        if (resumed) ui.postDelayed(previewRunnable, 450);
    }

    private void startPreview() {
        if (!resumed || appsMode) return;
        previewPlaying = false;
        final Channel c = selected;
        final boolean muted = !Prefs.previewSound(this);
        final int t = ++ytToken;
        if (!c.youtube.isEmpty()) {
            preview.stop();
            ytPreview.web.setVisibility(View.VISIBLE);
            String mem = YouTubeLive.remembered(c.youtube);
            if (mem != null) {
                ytPreview.play(mem, muted);
                return;
            }
            YouTubeLive.find(c.youtube, false, id -> {
                if (t != ytToken || !resumed || selected != c || appsMode) return;
                if (id != null) {
                    ytPreview.play(id, muted);
                } else {
                    ytPreview.stop();
                    ytPreview.web.setVisibility(View.GONE);
                    preview.play(c.withUrl(Sources.best(this, c)), muted);
                }
            });
        } else {
            ytPreview.stop();
            ytPreview.web.setVisibility(View.GONE);
            preview.play(c.withUrl(Sources.best(this, c)), muted);
        }
    }

    private void updateHero() {
        Channel c = selected;
        if (c == null) return;
        boolean off = Health.status(c) == Health.OFFLINE;
        heroLive.setVisibility(off ? View.GONE : View.VISIBLE);
        heroMeta.setText("Kanal " + c.number + "  ·  " + c.category + (off ? "  ·  şu an yayın yok" : ""));
        heroName.setText(c.name);
        Epg.Prog now = Epg.now(c);
        Epg.Prog next = Epg.next(c);
        if (now == null && next == null) {
            heroNow.setVisibility(View.GONE);
            ((View) heroBar.getParent()).setVisibility(View.GONE);
            return;
        }
        heroNow.setVisibility(View.VISIBLE);
        ((View) heroBar.getParent()).setVisibility(View.VISIBLE);
        if (now != null) {
            heroNow.setText(now.title);
            heroBar.setVisibility(View.VISIBLE);
            heroFill.getLayoutParams().width = (int) (px(214) * now.progress(System.currentTimeMillis()));
            heroFill.requestLayout();
            heroTime.setText(Epg.hm(now.start) + " – " + Epg.hm(now.end) + (next != null ? "  ·  Sonra: " + next.title : ""));
        } else {
            heroNow.setText(next.title);
            heroBar.setVisibility(View.GONE);
            heroTime.setText("Başlangıç " + Epg.hm(next.start));
        }
    }

    private void updateStatus() {
        if (statusBar != null) return; // sağ üstte yalnızca saat var

        dateText.setText(Actions.dateText());
        Info.Weather w = Info.weather(this);
        if (w != null) {
            weatherIcon.setImageResource(Info.icon(w));
            weatherIcon.setVisibility(View.VISIBLE);
            weatherText.setText(Math.round(w.temp) + "°");
        } else {
            weatherIcon.setVisibility(View.GONE);
            weatherText.setText("");
        }
        double[] r = Prefs.showRates(this) ? Info.rates(this) : null;
        if (r != null) {
            ratesText.setText(String.format(Actions.TR, "€ %.2f ₺   $ %.2f ₺", r[0], r[1]));
            ratesText.setVisibility(View.VISIBLE);
            sepRates.setVisibility(w != null ? View.VISIBLE : View.GONE);
        } else {
            ratesText.setVisibility(View.GONE);
            sepRates.setVisibility(View.GONE);
        }
        String[] p = Prefs.showPrayer(this) ? Info.nextPrayer(this) : null;
        if (p != null) {
            prayerText.setText(p[0] + " " + p[1]);
            prayerText.setVisibility(View.VISIBLE);
            sepPrayer.setVisibility(View.VISIBLE);
        } else {
            prayerText.setVisibility(View.GONE);
            sepPrayer.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ kumanda

    /** Diğer öğeler solarak kaybolur, önizlenen kanal kesintisiz tam ekrana geçer. */
    private void fadeAndWatch() {
        fadeTo(selected, 650);
    }

    /** Ekrandaki öğeler solar, video hiç kararmadan tam ekran izlemeye geçilir. */
    private void fadeTo(final Channel c, long ms) {
        if (leaving) return;
        leaving = true;
        autoPending = false;
        ui.removeCallbacks(autoWatch);
        uiLayer.animate().alpha(0f).setDuration(ms).setInterpolator(Ui.EASE).withEndAction(() -> {
            if (!resumed) {
                leaving = false;
                uiLayer.setAlpha(1f);
                return;
            }
            Prefs.watched(this, c);
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_INDEX, c.index);
            startActivity(i);
            overridePendingTransition(0, 0); // aynı görüntü devam eder: geçiş efekti yok
        }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            autoArmed = true;
            ui.removeCallbacks(autoWatch);
            autoPending = false;
            if (!appsMode && !leaving) ui.postDelayed(autoWatch, AUTO_WATCH_MS);
            if (k == KeyEvent.KEYCODE_SEARCH) {
                Intent i = new Intent(this, SearchActivity.class);
                i.putExtra(SearchActivity.EXTRA_VOICE, true);
                go(i);
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK) {
                // ana ekrandan çıkılmaz; uygulamalardaysa kanallara döner
                if (appsMode) focusSelectedChannel();
                else if (lastChannelCard != null && chScroll.getScrollX() > 0 && Cards.channel(lastChannelCard) != selected) focusSelectedChannel();
                return true;
            }
            if (Prefs.isControlKey(this, k)) {
                // Uygulamalar / Menü tuşu: uygulamalar satırına geç (tekrar basınca kanallara dön)
                if (appsMode) focusSelectedChannel();
                else if (appCards.getChildCount() > 0) appCards.getChildAt(0).requestFocus();
                return true;
            }
        } else if (k == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------------ gezinme

    private void go(Intent i) {
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openChannel(Channel c) {
        if (resumed) {
            fadeTo(c, 300);
            return;
        }
        Prefs.watched(this, c);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, c.index);
        go(i);
    }

    private void maybeAskSetup() {
        if (HomeWatch.hasUsage(this)) WakeService.start(this);
        if (Prefs.autostart(this) && !Prefs.askedOverlay(this) && !Actions.hasOverlay(this)) {
            Prefs.askedOverlay(this, true);
            new AlertDialog.Builder(this)
                    .setTitle("TV açılınca otomatik başlasın mı?")
                    .setMessage("Televizyon açıldığında bu uygulamanın kendiliğinden açılabilmesi için "
                            + "\"Diğer uygulamaların üzerinde göster\" iznini bir kez vermen gerekiyor.")
                    .setPositiveButton("İzin ver", (d, w) -> Actions.requestOverlay(this))
                    .setNegativeButton("Sonra", null)
                    .show();
        }
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onResume() {
        super.onResume();
        if (builtEdge != Prefs.edge(this)) {
            recreate();
            return;
        }
        resumed = true;
        autoArmed = false;
        autoPending = false;
        leaving = false;
        uiLayer.animate().cancel();
        uiLayer.setAlpha(1f);
        channels = Lineup.get(this);
        selected = channels.get(Lineup.lastIndex(this));
        if (!lineupKey().equals(builtLineup)) {
            buildChannelRow();
        } else {
            if (!recentKey().equals(builtRecent)) rebuildRecent();
            for (List<View> l : cardsByName.values()) for (View v : l) Cards.refresh(v);
        }
        if (appsMode && appCards.hasFocus()) {
            aerial.start(); // bir uygulamadan dönüldü: uygulamalar satırında kal
        } else {
            setAppsMode(false);
            focusSelectedChannel();
        }
        updateHero();
        updateStatus();
        loadApps();
        ui.removeCallbacks(previewRunnable);
        if (!appsMode) startPreview(); // izlemeden dönüşte görüntü hiç kesilmeden devam etsin

        Info.refresh(this, this::updateStatus);
        ui.postDelayed(() -> {
            if (resumed) Health.checkAll(channels, this::refreshCards);
        }, 8000);
        ui.postDelayed(() -> {
            if (resumed) Epg.refreshIfNeeded(this, channels, changed -> {
                if (changed) updateHero();
            });
        }, 20000);
        if (!checkedUpdate) {
            checkedUpdate = true;
            ui.postDelayed(() -> {
                if (resumed) Updater.check(this, false);
            }, 6000);
        }
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 20000);
        clock.start();
        maybeAskSetup();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(previewRunnable);
        ui.removeCallbacks(autoWatch);
        ui.removeCallbacks(tick);
        clock.stop();
        aerial.stop();
        preview.detach(); // tam ekrana geçilirse yayın kesilmeden devam eder
        ytToken++;
        ytPreview.stop();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        preview.release();
        ytPreview.destroy();
        super.onDestroy();
    }
}
