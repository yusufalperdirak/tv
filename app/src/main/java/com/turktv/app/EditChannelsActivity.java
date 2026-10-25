package com.turktv.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kanalları gizle ve sırala. OK: seç / bırak, ▲▼ taşı, ▶ gizle-göster, Geri: kaydet. */
public class EditChannelsActivity extends Activity {

    private final List<Channel> order = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();
    private LinearLayout list;
    private ScrollView scroll;
    private TextView countText;
    private int moving = -1;
    private boolean dirty;

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        order.addAll(Lineup.ordered(this, Channel.loadAll(this)));
        hidden.addAll(Prefs.hidden(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(Ui.edgeX(this)), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, "AYARLAR · KANALLAR", 12, Ui.BOLD, Ui.MUTED));
        left.addView(Ui.text(this, "Kanalları düzenle", 38, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 0, 6, 0, 0));
        TextView desc = Ui.text(this, "İzlemediğin kanalları gizle, kalanları istediğin sıraya koy. "
                + "Kanal numaraları yeni sıraya göre verilir.", 16, Ui.REGULAR, Ui.SOFT);
        desc.setLineSpacing(px(6), 1f);
        left.addView(desc, Ui.margins(this, -1, -2, 0, 18, 0, 0));
        left.addView(key("OK", "Kanalı seç, ▲ ▼ ile taşı, OK ile bırak"), Ui.margins(this, -1, -2, 0, 28, 0, 0));
        left.addView(key("▶", "Gizle ya da göster"), Ui.margins(this, -1, -2, 0, 12, 0, 0));
        left.addView(key("Geri", "Kaydet ve çık"), Ui.margins(this, -1, -2, 0, 12, 0, 0));
        countText = Ui.text(this, "", 15, Ui.MEDIUM, Ui.MUTED);
        left.addView(countText, Ui.margins(this, -1, -2, 0, 30, 0, 0));
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(px(400), -1);
        lLp.setMargins(0, 0, px(48), 0);
        root.addView(left, lLp);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(6), px(6), px(6), px(200));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);
        rebuild(0);
    }

    private LinearLayout key(String k, String text) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView kv = Ui.text(this, k, 13, Ui.BOLD, Ui.TEXT);
        kv.setGravity(Gravity.CENTER);
        kv.setMinWidth(px(52));
        kv.setPadding(px(10), px(6), px(10), px(6));
        kv.setBackground(Ui.rounded(this, Ui.SURFACE2, 8));
        r.addView(kv);
        r.addView(Ui.text(this, text, 15, Ui.REGULAR, Ui.SOFT), Ui.margins(this, -2, -2, 12, 0, 0, 0));
        return r;
    }

    private void rebuild(int focus) {
        list.removeAllViews();
        int n = 0;
        for (int i = 0; i < order.size(); i++) {
            Channel c = order.get(i);
            boolean hid = hidden.contains(c.name);
            String num = hid ? "—" : String.valueOf(++n);
            list.addView(row(i, c, num, hid));
        }
        countText.setText(n + " görünür kanal  ·  " + hidden.size() + " gizli");
        final View f = list.getChildAt(Math.max(0, Math.min(focus, list.getChildCount() - 1)));
        if (f != null) f.post(f::requestFocus);
    }

    private View row(final int i, Channel c, String num, boolean hid) {
        final LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(px(16), 0, px(18), 0);
        final boolean mv = i == moving;
        final GradientDrawable bg = Ui.rounded(this, mv ? Ui.WHITE : 0x0FFFFFFF, 14);
        if (mv) bg.setStroke(px(2), Ui.WHITE, px(8), px(5));
        r.setBackground(bg);
        final TextView n = Ui.text(this, num, 15, Ui.BOLD, mv ? Ui.BG : Ui.MUTED);
        r.addView(n, new LinearLayout.LayoutParams(px(40), -2));
        r.addView(Cards.make(this, c, 76, 44, false, null), Ui.margins(this, px(76), px(44), 0, 0, 16, 0));
        final TextView name = Ui.text(this, c.name, 17, Ui.BOLD, mv ? Ui.BG : Ui.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView st = Ui.text(this, mv ? "▲ ▼ taşınıyor" : (hid ? "Gizli" : "Görünür"), 14, Ui.BOLD,
                mv ? 0xFF4B5260 : (hid ? Ui.MUTED : 0xFF7FD3A6));
        r.addView(st);
        r.setAlpha(hid && !mv ? 0.5f : 1f);
        r.setLayoutParams(Ui.margins(this, -1, px(60), 0, 0, 0, 8));
        r.setTag(i);
        r.setOnFocusChangeListener((v, has) -> {
            if (moving >= 0) return;
            bg.setColor(has ? Ui.WHITE : 0x0FFFFFFF);
            n.setTextColor(has ? Ui.BG : Ui.MUTED);
            name.setTextColor(has ? Ui.BG : Ui.TEXT);
            st.setTextColor(has ? 0xFF4B5260 : (hid ? Ui.MUTED : 0xFF7FD3A6));
            v.setAlpha(has || !hid ? 1f : 0.5f);
            if (has) {
                int y = v.getTop() - scroll.getHeight() / 2 + v.getHeight() / 2;
                scroll.smoothScrollTo(0, Math.max(0, y));
            }
        });
        r.setOnClickListener(v -> {
            moving = moving == i ? -1 : i;
            rebuild(i);
        });
        return r;
    }

    private int focusedIndex() {
        View f = getCurrentFocus();
        return f != null && f.getTag() instanceof Integer ? (Integer) f.getTag() : -1;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (moving >= 0 && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
            int j = moving + (k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            if (j >= 0 && j < order.size()) {
                Channel c = order.remove(moving);
                order.add(j, c);
                moving = j;
                dirty = true;
                rebuild(j);
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT && moving < 0) {
            int i = focusedIndex();
            if (i >= 0) {
                String name = order.get(i).name;
                if (!hidden.remove(name)) hidden.add(name);
                dirty = true;
                rebuild(i);
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK && moving >= 0) {
            int i = moving;
            moving = -1;
            rebuild(i);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void finish() {
        if (dirty) {
            List<String> names = new ArrayList<>();
            for (Channel c : order) names.add(c.name);
            Prefs.order(this, names);
            Prefs.hidden(this, new ArrayList<>(hidden));
            Lineup.invalidate();
            Toast.makeText(this, "Kanal düzeni kaydedildi", Toast.LENGTH_SHORT).show();
        }
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
