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
        channels = Lineup.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(Ui.edgeX(this)), px(Ui.edgeY(this)), px(Ui.edgeX(this)), 0);

        // Başlık
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, "Kanal ara", 40, Ui.BOLD, Ui.TEXT));
        root.addView(header);

        // Arama kutusu + mikrofon
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(px(28), 0, px(28), 0);
        field.addView(Ui.icon(this, R.drawable.ic_search, 30, Ui.SOFT));
        input = new EditText(this);
        input.setBackground(null);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(0xFF8B95A7);
        input.setHint("Kanal adını yaz");
        input.setTypeface(Ui.REGULAR);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(30));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.addView(input, Ui.margins(this, 0, -2, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) input.getLayoutParams()).weight = 1f;
        final android.graphics.drawable.GradientDrawable fieldBg = Ui.rounded(this, 0xFF1C212B, 40);
        field.setBackground(fieldBg);
        input.setOnFocusChangeListener((v, has) -> fieldBg.setStroke(has ? px(3) : 0, Ui.WHITE));
        bar.addView(field, new LinearLayout.LayoutParams(0, px(80), 1f));

        // sesle arama: büyük, yazılı düğme
        LinearLayout mic = Ui.button(this, R.drawable.ic_mic, "Sesle ara", 80, Ui.SURFACE2);
        mic.setOnClickListener(v -> startVoice());
        ((LinearLayout.LayoutParams) mic.getLayoutParams()).setMargins(px(18), 0, 0, 0);
        bar.addView(mic);
        root.addView(bar, Ui.margins(this, -1, -2, 0, 28, 0, 0));

        resultTitle = Ui.text(this, "", 22, Ui.MEDIUM, Ui.SOFT);
        root.addView(resultTitle, Ui.margins(this, -1, -2, 4, 30, 0, 16));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setClipChildren(false);
        grid.setPadding(px(4), px(14), px(4), px(60));
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
        if (q.trim().isEmpty()) res.addAll(channels); // boşken bütün kanallar: yazdıkça daralır
        resultTitle.setText(q.trim().isEmpty() ? "Bütün kanallar · yazdıkça daralır"
                : res.isEmpty() ? "\"" + q.trim() + "\" bulunamadı" : res.size() + " kanal bulundu");
        int gap = px(24);
        int w = (px(1280 - 2 * Ui.edgeX(this) - 8) - 4 * gap) / 5;
        int h = Math.round(w * 113f / 200f);
        int col = 0;
        for (Channel c : res) {
            View card = Cards.make(this, c, 200, 113, true, null);
            card.setOnClickListener(v -> open(c));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = w;
            lp.height = h;
            lp.setMargins(0, 0, (col % 5 == 4) ? 0 : gap, gap);
            grid.addView(card, lp);
            col++;
        }
    }

    private void open(Channel c) {
        Prefs.watched(this, c);
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
