package com.turktv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Kanal arama: ekran klavyesiyle yaz ya da mikrofona söyle. */
public class SearchActivity extends Activity {

    static final String EXTRA_VOICE = "voice";
    private static final int REQ_VOICE = 7;

    private List<Channel> channels;
    private EditText input;
    private TextView resultTitle;
    private GridLayout grid;

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        channels = Channel.loadAll(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(Ui.edgeX(this)), 0);

        // Başlık
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.rounded(this, Ui.ACCENT, 8));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_tv);
        badge.addView(mark, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        header.addView(badge, new LinearLayout.LayoutParams(px(32), px(32)));
        header.addView(Ui.text(this, "Kanal ara", 22, Ui.BOLD, Ui.TEXT), Ui.margins(this, -2, -2, 12, 0, 0, 0));
        root.addView(header);

        // Arama kutusu + mikrofon
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(px(24), 0, px(24), 0);
        field.setBackground(Ui.rounded(this, 0xFF1A1F29, 32));
        field.addView(Ui.icon(this, R.drawable.ic_search, 22, Ui.MUTED));
        input = new EditText(this);
        input.setBackground(null);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(0xFF6B7586);
        input.setHint("Kanal adı veya numarası");
        input.setTypeface(Ui.BOLD);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.addView(input, Ui.margins(this, 0, -2, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) input.getLayoutParams()).weight = 1f;
        final android.graphics.drawable.GradientDrawable fieldBg = Ui.rounded(this, 0xFF1A1F29, 32);
        field.setBackground(fieldBg);
        input.setOnFocusChangeListener((v, has) -> fieldBg.setStroke(has ? px(3) : 0, Ui.WHITE));
        bar.addView(field, new LinearLayout.LayoutParams(px(660), px(64)));

        LinearLayout mic = Ui.button(this, R.drawable.ic_mic, null, 64, Ui.SURFACE2);
        mic.setOnClickListener(v -> startVoice());
        ((LinearLayout.LayoutParams) mic.getLayoutParams()).setMargins(px(16), 0, 0, 0);
        bar.addView(mic);
        TextView micHint = Ui.text(this, "Mikrofona bas ve kanal adını söyle", 14, Ui.REGULAR, Ui.MUTED);
        micHint.setMaxWidth(px(240));
        bar.addView(micHint, Ui.margins(this, -2, -2, 18, 0, 0, 0));
        root.addView(bar, Ui.margins(this, -1, -2, 0, 30, 0, 0));

        resultTitle = Ui.text(this, "", 20, Ui.MEDIUM, Ui.TEXT);
        root.addView(resultTitle, Ui.margins(this, -1, -2, 0, 30, 0, 14));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setClipChildren(false);
        grid.setPadding(px(4), px(8), px(4), px(60));
        sc.addView(grid);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });
        input.setOnEditorActionListener((v, actionId, ev) -> {
            hideKeyboard();
            if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
            return true;
        });

        setContentView(root);
        filter("");
        if (getIntent().getBooleanExtra(EXTRA_VOICE, false)) startVoice();
        else input.requestFocus();
    }

    private void filter(String q) {
        String k = Channel.key(q);
        grid.removeAllViews();
        List<Channel> res = new ArrayList<>();
        if (!q.trim().isEmpty()) {
            String digits = q.trim();
            for (Channel c : channels) {
                boolean hit = Channel.key(c.name).contains(k) && !k.isEmpty();
                if (!hit && digits.matches("\\d+")) hit = String.valueOf(c.number).equals(digits);
                if (hit) res.add(c);
            }
        }
        resultTitle.setText(q.trim().isEmpty() ? "Yazmaya başla"
                : res.isEmpty() ? "Sonuç yok" : res.size() + " kanal bulundu");
        int gap = px(18);
        int w = (px(1280 - 2 * Ui.edgeX(this) - 8) - 5 * gap) / 6;
        int col = 0;
        for (Channel c : res) {
            View card = Cards.make(this, c, 180, 104, true, null);
            card.setOnClickListener(v -> open(c));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = w;
            lp.height = px(104);
            lp.setMargins(0, 0, (col % 6 == 5) ? 0 : gap, gap);
            grid.addView(card, lp);
            col++;
        }
    }

    private void open(Channel c) {
        Prefs.last(this, c.index);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, c.index);
        startActivity(i);
        finish();
    }

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Kanal adını söyle");
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Bu TV'de sesli arama desteklenmiyor, yazarak arayabilirsin", Toast.LENGTH_LONG).show();
            input.requestFocus();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VOICE || res != RESULT_OK || data == null) return;
        ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r == null || r.isEmpty()) return;
        String said = r.get(0).replaceAll("(?i)\\s*(kanalı|kanalını|kanal|aç|izle)\\s*", " ").trim();
        input.setText(said);
        input.setSelection(input.getText().length());
        // Tek sonuç varsa doğrudan aç
        if (grid.getChildCount() == 1) {
            open(Cards.channel(grid.getChildAt(0)));
        } else if (grid.getChildCount() > 0) {
            grid.getChildAt(0).requestFocus();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
            startVoice();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
