package com.turktv.app;

import android.app.Activity;
import android.os.Bundle;

/** Bugün: hava durumu, döviz kuru ve ezan vakitleri. Geri ile kapanır. */
public class TodayActivity extends Activity {
    private TodayPage page;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        page = new TodayPage(this);
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        page.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        page.stop();
    }
}
