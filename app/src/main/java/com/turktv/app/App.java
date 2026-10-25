package com.turktv.app;

import android.app.Application;
import android.graphics.Typeface;

import java.lang.reflect.Field;

/** Uygulama açılırken yazı tipini (Inter) yükler; sistem pencereleri de mümkünse aynı yazı tipini kullanır. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Ui.initFonts(this);
        override("DEFAULT", Ui.REGULAR);
        override("SANS_SERIF", Ui.REGULAR);
        override("DEFAULT_BOLD", Ui.BOLD);
    }

    /** Uygulama içindeki varsayılan yazı tipini değiştirir (desteklemeyen sürümlerde sessizce atlanır). */
    private static void override(String field, Typeface tf) {
        try {
            Field f = Typeface.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, tf);
        } catch (Throwable ignored) {
        }
    }
}
