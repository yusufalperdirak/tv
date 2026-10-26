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
        registerActivityLifecycleCallbacks(new Lifecycle());
    }

    /**
     * Uygulama arka plandan (başka uygulama, TV ana ekranı) yeniden açılınca açılış animasyonu
     * tekrar oynar. Kısa süreli çıkışlarda (30 sn altı) oynamaz.
     */
    private static final class Lifecycle implements ActivityLifecycleCallbacks {
        private int started;
        private long backgroundAt;

        @Override public void onActivityStarted(android.app.Activity a) {
            if (started == 0 && backgroundAt > 0 && a instanceof MainActivity
                    && System.currentTimeMillis() - backgroundAt > 30_000) {
                a.startActivity(new android.content.Intent(a, SplashActivity.class).putExtra(SplashActivity.EXTRA_REPLAY, true));
                a.overridePendingTransition(0, 0);
            }
            started++;
        }

        @Override public void onActivityStopped(android.app.Activity a) {
            started = Math.max(0, started - 1);
            if (started == 0 && !a.isChangingConfigurations()) backgroundAt = System.currentTimeMillis();
        }

        @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
        @Override public void onActivityResumed(android.app.Activity a) {}
        @Override public void onActivityPaused(android.app.Activity a) {}
        @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
        @Override public void onActivityDestroyed(android.app.Activity a) {}
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
