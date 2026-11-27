package com.yusufalperdirak.gurbetsport;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Erisilebilirlik servisi: fiziksel tuslari sistemden ONCE gorur.
 * Kullanicinin ogrettigi tuslari yakalar (unitenin kendi islevi, or. uygulama acmasi, calismaz)
 * ve GurbetMap Sport'taki karsiligini calistirir. Ogretilmemis tuslara dokunmaz.
 */
public class KeyService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Hub.init(this);
        Hub.safe(() -> {
            AccessibilityServiceInfo i = getServiceInfo();
            if (i != null) {
                i.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(i);
            }
        });
        Keys.publish(this);
        Perms.refresh(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        try {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_UNKNOWN) return false;
            if (Keys.learning != null) {
                // Ogrenirken tusun hicbir yan etkisi olmasin; birakildiginda kaydet
                if (e.getAction() == KeyEvent.ACTION_UP) Hub.runMain(() -> Keys.assign(this, code));
                return true;
            }
            String a = Keys.actionFor(this, code);
            if (a == null) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                // Ses tuslari basili tutulunca tekrar etsin, digerleri bir kez
                boolean repeatable = "volup".equals(a) || "voldown".equals(a);
                if (e.getRepeatCount() == 0 || repeatable) Hub.runMain(() -> Keys.run(this, a));
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        Hub.safe(() -> Keys.publish(this));
        super.onDestroy();
    }
}
