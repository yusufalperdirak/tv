package com.turktv.app;

import android.service.dreams.DreamService;

/** TV'nin kendi ekran koruyucusu olarak seçilebilen GurbeTV ekran koruyucusu. */
public class SaverDream extends DreamService {
    private SaverView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Ui.initFonts(this);
        view = new SaverView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
