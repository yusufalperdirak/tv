package com.turktv.app;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ana ekran: seçili kanalın canlı yayını tüm arka planda oynar; altta kategori satırları.
 * Sağa-sola kanal, yukarı-aşağı kategori. OK izle, OK basılı tut favori / sırala.
 */
public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Channel> channels;
    private Channel selected;
    private boolean resumed;
    private boolean builtHideOffline;
    private int builtEdge;
    private String builtFavs = "";

    private StreamPlayer preview;
    private YtPlayer ytPreview;   // resmi YouTube yayını olan kanalların önizlemesi
    private int ytToken;
    private TextView previewNote;
    private TextView clockLocal, clockTr;
    private TextView infoCat, infoName, infoHint;
    private LinearLayout epgBox;
    private TextView nowTitle, nowTime, nextTitle, nextTime;
    private View progressFill;
    private VScroll rowsScroll;
    private LinearLayout rows;
    private LinearLayout favCards; // favoriler satırının kart dizisi (yoksa null)
    private final Map<Channel, List<View>> cardMap = new HashMap<>();

    // favori taşıma modu
    private View moving;
    private List<String> movingSaved;

    private final Runnable previewRunnable = this::startPreview;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateInfo();
            ui.postDelayed(this, 20000);
        }
    };

    /** Otomatik kaydırmayı kapatılmış kaydırıcılar: kaydırmayı odak değişiminde kendimiz yapıyoruz. */
    static final class VScroll extends ScrollView {
        VScroll(Context c) { super(c); }
        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
    }

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
        channels = Channel.loadAll(this);
        Epg.ensureLoaded(this);
        YouTubeLive.init(this);
        // YouTube'dan açılan kanalların yayınını önceden hazırla (anında açılsın)
        for (Channel c : channels) if (!c.youtube.isEmpty()) YouTubeLive.find(c.youtube, false, id -> { });
        selected = channels.get(clamp(Prefs.last(this)));

        builtEdge = Prefs.edge(this);
        buildUi();
        rebuildRows(selected);
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

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        preview = new StreamPlayer(this);
        root.addView(preview.view, new FrameLayout.LayoutParams(-1, -1));
        ytPreview = new YtPlayer(this);
        ytPreview.web.setVisibility(View.GONE);
        root.addView(ytPreview.web, new FrameLayout.LayoutParams(-1, -1));
        ytPreview.setListener(new YtPlayer.Listener() {
            @Override
            public void onState(int state) {
            }

            @Override
            public void onError(int code) { // YouTube oynatmadı: listedeki link
                ytToken++;
                ytPreview.stop();
                ytPreview.web.setVisibility(View.GONE);
                if (resumed) preview.play(selected, !Prefs.previewSound(MainActivity.this));
            }
        });
        preview.setListener(new StreamPlayer.Listener() {
            @Override
            public void onState(int state) {
                if (state == 1) {
                    previewNote.setVisibility(View.GONE);
                    Health.report(selected, true);
                    onStatus(selected);
                }
            }

            @Override
            public void onError() {
                preview.stop();
                previewNote.setText("Bu kanalın yayını şu an açılamıyor");
                previewNote.setVisibility(View.VISIBLE);
                Health.report(selected, false);
                onStatus(selected);
            }
        });

        // Okunabilirlik için soldan ve alttan karartma
        View scrimLeft = new View(this);
        scrimLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF50B0D12, 0xC00B0D12, 0x300B0D12, 0x000B0D12}));
        root.addView(scrimLeft, new FrameLayout.LayoutParams(-1, -1));
        View scrimBottom = new View(this);
        scrimBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF0B0D12, 0xF20B0D12, 0x000B0D12}));
        root.addView(scrimBottom, new FrameLayout.LayoutParams(-1, px(400), Gravity.BOTTOM));

        previewNote = Ui.text(this, "", 14, Ui.MEDIUM, Ui.SOFT);
        previewNote.setPadding(px(14), px(8), px(14), px(8));
        previewNote.setBackground(Ui.rounded(this, 0xCC0B0D12, 16));
        previewNote.setVisibility(View.GONE);
        FrameLayout.LayoutParams pnLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pnLp.setMargins(0, px(Ui.edgeY(this) + 70), px(Ui.edgeX(this)), 0);
        root.addView(previewNote, pnLp);

        // --- Üst çubuk
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.rounded(this, Ui.ACCENT, 8));
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_tv);
        badge.addView(mark, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        header.addView(badge, new LinearLayout.LayoutParams(px(32), px(32)));
        header.addView(Ui.text(this, "TV", 22, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 10, 0, 26, 0));
        header.addView(navPill("Ana sayfa", true, v -> {
            View first = firstCard();
            if (first != null) first.requestFocus();
        }));
        header.addView(navPill("Ara", false, v -> startActivity(new Intent(this, SearchActivity.class))));
        header.addView(navPill("Ayarlar", false, v -> startActivity(new Intent(this, SettingsActivity.class))));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        clockLocal = Ui.text(this, "", 20, Ui.MEDIUM, Ui.TEXT);
        header.addView(clockLocal);
        clockTr = Ui.text(this, "", 14, Ui.MEDIUM, Ui.MUTED);
        header.addView(clockTr, Ui.margins(this, -2, -2, 10, 0, 0, 0));
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(-1, px(48), Gravity.TOP);
        hLp.setMargins(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(Ui.edgeX(this)), 0);
        root.addView(header, hLp);

        // --- Kanal bilgisi
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        infoCat = Ui.text(this, "", 13, Ui.BOLD, 0xFFA8B1BF);
        infoCat.setLetterSpacing(0.12f);
        info.addView(infoCat);
        infoName = Ui.text(this, "", 58, Ui.BOLD, Ui.TEXT);
        infoName.setSingleLine(true);
        infoName.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(infoName, Ui.margins(this, -1, -2, 0, 10, 0, 0));

        epgBox = new LinearLayout(this);
        epgBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nowRow = epgRow("ŞİMDİ", 0xFFFF6B70);
        nowTitle = (TextView) nowRow.getChildAt(1);
        nowTime = (TextView) nowRow.getChildAt(2);
        nowTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(19));
        epgBox.addView(nowRow);
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.rounded(this, 0x2EF2F4F7, 2));
        progressFill = new View(this);
        progressFill.setBackground(Ui.rounded(this, Ui.TEXT, 2));
        bar.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        epgBox.addView(bar, Ui.margins(this, px(360), px(4), 0, 10, 0, 10));
        LinearLayout nextRow = epgRow("SONRA", Ui.MUTED);
        nextTitle = (TextView) nextRow.getChildAt(1);
        nextTime = (TextView) nextRow.getChildAt(2);
        epgBox.addView(nextRow);
        info.addView(epgBox, Ui.margins(this, -1, -2, 0, 18, 0, 0));

        infoHint = Ui.text(this, "", 13, Ui.MEDIUM, Ui.MUTED);
        info.addView(infoHint, Ui.margins(this, -1, -2, 0, 16, 0, 0));
        FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams(px(660), -2, Gravity.TOP | Gravity.START);
        iLp.setMargins(px(Ui.edgeX(this)), px(Ui.edgeY(this) + 98), 0, 0);
        root.addView(info, iLp);

        // --- Kategori satırları (kendi alanında kayar, üstteki bilgilerin üzerine taşmaz)
        rowsScroll = new VScroll(this);
        rowsScroll.setVerticalScrollBarEnabled(false);
        rowsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, 0, 0, px(260)); // son satır da en üste kaydırılabilsin
        rowsScroll.addView(rows, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(-1, -1);
        rLp.topMargin = px(404);
        root.addView(rowsScroll, rLp);

        setContentView(root);
    }

    private LinearLayout epgRow(String label, int labelColor) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.BOTTOM);
        TextView l = Ui.text(this, label, 12, Ui.BOLD, labelColor);
        l.setLetterSpacing(0.06f);
        r.addView(l, Ui.margins(this, -2, -2, 0, 0, 12, 2));
        TextView t = Ui.text(this, "", 16, Ui.BOLD, Ui.TEXT);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 0f;
        ((LinearLayout.LayoutParams) t.getLayoutParams()).width = -2;
        t.setMaxWidth(px(440));
        TextView tm = Ui.text(this, "", 14, Ui.REGULAR, 0xFFA8B1BF);
        r.addView(tm, Ui.margins(this, -2, -2, 12, 0, 0, 1));
        return r;
    }

    private TextView navPill(String label, boolean active, View.OnClickListener click) {
        TextView t = Ui.text(this, label, 15, Ui.BOLD, active ? Ui.TEXT : 0xFFA8B1BF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(16), 0, px(16), 0);
        t.setFocusable(true);
        t.setClickable(true);
        final int restBg = active ? 0x24F2F4F7 : 0x00000000;
        final int restFg = active ? Ui.TEXT : 0xFFA8B1BF;
        t.setBackground(Ui.rounded(this, restBg, 18));
        t.setLayoutParams(Ui.margins(this, -2, px(36), 0, 0, 6, 0));
        t.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.rounded(this, has ? Ui.WHITE : restBg, 18));
            ((TextView) v).setTextColor(has ? Ui.BG : restFg);
            if (has) rowsScroll.smoothScrollTo(0, 0);
        });
        t.setOnClickListener(click);
        return t;
    }

    // ------------------------------------------------------------------ satırlar

    private void rebuildRows(Channel focus) {
        View had = getCurrentFocus();
        boolean focusInRows = had == null || Cards.channel(had) != null;
        rows.removeAllViews();
        cardMap.clear();
        favCards = null;
        builtHideOffline = Prefs.hideOffline(this);
        List<String> favNames = Prefs.favorites(this);
        builtFavs = TextUtils.join("|", favNames);

        List<Channel> favs = new ArrayList<>();
        for (String n : favNames) {
            for (Channel c : channels) {
                if (c.name.equals(n)) {
                    favs.add(c);
                    break;
                }
            }
        }
        if (!favs.isEmpty()) favCards = addRow("Favoriler", "OK basılı tut: sırayı değiştir", favs, false);
        for (String cat : Channel.categories(channels)) {
            List<Channel> l = new ArrayList<>();
            for (Channel c : channels) if (c.category.equals(cat)) l.add(c);
            addRow(cat, null, l, builtHideOffline);
        }
        if (focus != null && focusInRows) {
            final View target = cardFor(focus);
            if (target != null) ui.post(target::requestFocus);
        }
    }

    private LinearLayout addRow(String title, String hint, List<Channel> list, boolean hideOffline) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(px(Ui.edgeX(this)), 0, px(Ui.edgeX(this)), 0);
        head.addView(Ui.text(this, title, 20, Ui.MEDIUM, Ui.TEXT));
        if (hint != null) head.addView(Ui.text(this, hint, 13, Ui.REGULAR, Ui.MUTED), Ui.margins(this, -2, -2, 14, 0, 0, 2));
        row.addView(head);

        HScroll hs = new HScroll(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setPadding(px(Ui.edgeX(this)), px(16), px(400), px(18));
        hs.addView(cards);
        row.addView(hs);

        for (final Channel c : list) {
            if (hideOffline && Health.status(c) == Health.OFFLINE) continue;
            final View card = Cards.make(this, c, 156, 92, true, (ch, v, has) -> {
                if (!has) return;
                hs.smoothScrollTo(Math.max(0, v.getLeft() - px(Ui.edgeX(this))), 0);
                rowsScroll.smoothScrollTo(0, row.getTop());
                select(ch);
            });
            card.setOnClickListener(v -> {
                if (moving == null) openChannel(c);
            });
            card.setOnLongClickListener(v -> {
                onLongPress(v, c);
                return true;
            });
            cards.addView(card, Ui.margins(this, px(156), px(92), 0, 0, 16, 0));
            List<View> l = cardMap.get(c);
            if (l == null) {
                l = new ArrayList<>();
                cardMap.put(c, l);
            }
            l.add(card);
        }
        if (cards.getChildCount() == 0) return cards;
        rows.addView(row, Ui.margins(this, -1, -2, 0, 0, 0, 10));
        return cards;
    }

    private View firstCard() {
        if (rows.getChildCount() == 0) return null;
        LinearLayout row = (LinearLayout) rows.getChildAt(0);
        LinearLayout cards = (LinearLayout) ((ViewGroup) row.getChildAt(1)).getChildAt(0);
        return cards.getChildCount() > 0 ? cards.getChildAt(0) : null;
    }

    /** Kanalın kartı: favorilerde varsa oradaki, yoksa kategorisindeki. */
    private View cardFor(Channel c) {
        List<View> l = cardMap.get(c);
        if (l == null || l.isEmpty()) return firstCard();
        return l.get(0);
    }

    private void onStatus(Channel c) {
        if (isFinishing()) return;
        List<View> l = cardMap.get(c);
        if (l != null) for (View v : l) Cards.refresh(v);
        if (c == selected) updateInfo();
    }

    // ------------------------------------------------------------------ favoriler

    private void onLongPress(View card, Channel c) {
        if (moving != null) return;
        boolean inFavRow = favCards != null && card.getParent() == favCards;
        if (inFavRow) {
            moving = card;
            movingSaved = Prefs.favorites(this);
            Cards.setMoving(this, card, true);
            updateInfo();
            return;
        }
        boolean now = Prefs.toggleFavorite(this, c);
        Toast.makeText(this, c.name + (now ? " favorilere eklendi" : " favorilerden çıkarıldı"), Toast.LENGTH_SHORT).show();
        rebuildRowsKeepCategory(c);
    }

    /** Favoriler değişince satırları yenile, odak kanalın kategori satırında kalsın. */
    private void rebuildRowsKeepCategory(Channel c) {
        rebuildRows(null);
        List<View> l = cardMap.get(c);
        if (l != null && !l.isEmpty()) {
            final View target = l.get(l.size() - 1);
            ui.post(target::requestFocus);
        }
    }

    private void moveFav(int d) {
        LinearLayout cards = (LinearLayout) moving.getParent();
        int i = cards.indexOfChild(moving);
        int j = i + d;
        if (j < 0 || j >= cards.getChildCount()) return;
        View card = moving;
        cards.removeView(card);
        cards.addView(card, j);
        List<String> order = new ArrayList<>();
        for (int k = 0; k < cards.getChildCount(); k++) order.add(Cards.channel(cards.getChildAt(k)).name);
        Prefs.setFavorites(this, order);
        builtFavs = TextUtils.join("|", order);
        card.requestFocus();
        Cards.setMoving(this, card, true);
    }

    private void endMove(boolean commit) {
        View card = moving;
        moving = null;
        Cards.setMoving(this, card, false);
        if (!commit) {
            Prefs.setFavorites(this, movingSaved);
            rebuildRows(Cards.channel(card));
        }
        updateInfo();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (moving != null && k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN
                && k != KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        moveFav(-1);
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        moveFav(1);
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        if (e.getRepeatCount() == 0) endMove(true);
                        break;
                    case KeyEvent.KEYCODE_BACK:
                        endMove(false);
                        break;
                }
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && k == KeyEvent.KEYCODE_SEARCH) {
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra(SearchActivity.EXTRA_VOICE, true);
            startActivity(i);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------------ bilgi alanı

    private void select(Channel c) {
        if (c == selected && !preview.isFrozen() && previewNote.getVisibility() == View.GONE && resumed) {
            updateInfo();
            return;
        }
        selected = c;
        updateInfo();
        previewNote.setVisibility(View.GONE);
        ui.removeCallbacks(previewRunnable);
        if (resumed) ui.postDelayed(previewRunnable, 500);
    }

    private void startPreview() {
        if (!resumed) return;
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
                if (t != ytToken || !resumed || selected != c) return;
                if (id != null) {
                    ytPreview.play(id, muted);
                } else { // YouTube'da yayın yok: listedeki link
                    ytPreview.stop();
                    ytPreview.web.setVisibility(View.GONE);
                    preview.play(c, muted);
                }
            });
        } else {
            ytPreview.stop();
            ytPreview.web.setVisibility(View.GONE);
            preview.play(c, muted);
        }
    }

    private void updateInfo() {
        Channel c = selected;
        if (c == null) return;
        if (moving != null) {
            Channel m = Cards.channel(moving);
            infoCat.setText("FAVORİLER · SIRALAMA");
            infoName.setText(m.name + "'i taşıyorsun");
            epgBox.setVisibility(View.GONE);
            infoHint.setText("◀ ▶ yerini değiştir     OK bitir     Geri vazgeç");
            infoHint.setTextColor(Ui.TEXT);
            return;
        }
        String off = Health.status(c) == Health.OFFLINE ? "  ·  YAYIN KAPALI" : "";
        infoCat.setText(c.category.toUpperCase(Actions.TR) + "  ·  " + c.number + off);
        infoName.setText(c.name);
        infoHint.setTextColor(Ui.MUTED);
        infoHint.setText(Prefs.isFavorite(this, c)
                ? "OK izle  ·  OK basılı tut: favorilerden çıkar"
                : "OK izle  ·  OK basılı tut: favorilere ekle");

        Epg.Prog now = Epg.now(c);
        Epg.Prog next = Epg.next(c);
        if (now == null && next == null) {
            epgBox.setVisibility(View.GONE);
            return;
        }
        epgBox.setVisibility(View.VISIBLE);
        long t = System.currentTimeMillis();
        if (now != null) {
            nowTitle.setText(now.title);
            nowTime.setText(Epg.hm(now.start) + " – " + Epg.hm(now.end));
            progressFill.getLayoutParams().width = (int) (px(360) * now.progress(t));
        } else {
            nowTitle.setText("Program bilgisi yok");
            nowTime.setText("");
            progressFill.getLayoutParams().width = 0;
        }
        progressFill.requestLayout();
        View nextRow = (View) nextTitle.getParent();
        if (next != null) {
            nextRow.setVisibility(View.VISIBLE);
            nextTitle.setText(next.title);
            nextTime.setText(Epg.hm(next.start));
        } else {
            nextRow.setVisibility(View.GONE);
        }
    }

    private void updateClock() {
        clockLocal.setText(Actions.localTime());
        String tr = Actions.turkeyTime();
        clockTr.setText(tr == null ? "" : "TR " + tr);
        clockTr.setVisibility(tr == null ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ gezinme

    private void openChannel(Channel c) {
        Prefs.last(this, c.index);
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
        if (Channel.loadAll(this) != channels || builtEdge != Prefs.edge(this)) {
            recreate();
            return;
        }
        resumed = true;
        selected = channels.get(clamp(Prefs.last(this)));
        String favs = TextUtils.join("|", Prefs.favorites(this));
        if (builtHideOffline != Prefs.hideOffline(this) || !favs.equals(builtFavs)) {
            rebuildRows(selected);
        } else {
            View target = cardFor(selected);
            if (target != null) target.requestFocus();
        }
        for (Map.Entry<Channel, List<View>> en : cardMap.entrySet()) for (View v : en.getValue()) Cards.refresh(v);
        updateInfo();
        ui.removeCallbacks(previewRunnable);
        ui.postDelayed(previewRunnable, 300);

        // Yayın açılırken bağlantıyı yormamak için arka plan işleri biraz sonra
        ui.postDelayed(() -> {
            if (resumed) Health.checkAll(channels, this::onStatus);
        }, 8000);
        ui.postDelayed(() -> {
            if (resumed) Epg.refreshIfNeeded(this, channels, changed -> {
                if (changed) updateInfo();
            });
        }, 25000);
        ui.removeCallbacks(tick);
        tick.run();
        maybeAskOverlay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (moving != null) endMove(true);
        ui.removeCallbacks(previewRunnable);
        ui.removeCallbacks(tick);
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
