package com.turktv.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sağ üstteki saat: yerel saat ve Türkiye saati yan yana, eşit büyüklükte, altlarında küçük
 * etiketlerle; arkadaki görüntüden ayrışsın diye yarı saydam yumuşak bir zemin üzerinde.
 */
final class Clock {

    final LinearLayout view;
    private final TextView localTime, trTime, localLabel;
    private final View divider, trCol;

    Clock(Context c) {
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(Ui.px(c, 20), Ui.px(c, 10), Ui.px(c, 20), Ui.px(c, 10));
        view.setBackground(Ui.rounded(c, 0x990B0D12, 18));

        LinearLayout lc = column(c);
        localTime = Ui.text(c, "", 30, Ui.MEDIUM, Ui.TEXT);
        lc.addView(localTime);
        localLabel = Ui.text(c, "YEREL", 10, Ui.BOLD, Ui.MUTED);
        localLabel.setLetterSpacing(0.14f);
        lc.addView(localLabel, Ui.margins(c, -2, -2, 0, 3, 0, 0));
        view.addView(lc);

        divider = new View(c);
        divider.setBackgroundColor(0x33F2F4F7);
        view.addView(divider, Ui.margins(c, Math.max(1, Ui.px(c, 1)), Ui.px(c, 34), 18, 0, 18, 0));

        LinearLayout tc = column(c);
        trTime = Ui.text(c, "", 30, Ui.MEDIUM, Ui.TEXT);
        tc.addView(trTime);
        TextView trLabel = Ui.text(c, "TÜRKİYE", 10, Ui.BOLD, Ui.MUTED);
        trLabel.setLetterSpacing(0.14f);
        tc.addView(trLabel, Ui.margins(c, -2, -2, 0, 3, 0, 0));
        view.addView(tc);
        trCol = tc;
        update();
    }

    private static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        return l;
    }

    void update() {
        localTime.setText(Actions.localTime());
        String tr = Actions.turkeyTime();
        boolean same = tr == null;
        trCol.setVisibility(same ? View.GONE : View.VISIBLE);
        divider.setVisibility(same ? View.GONE : View.VISIBLE);
        localLabel.setText(same ? "SAAT" : "YEREL");
        if (!same) trTime.setText(tr);
    }
}
