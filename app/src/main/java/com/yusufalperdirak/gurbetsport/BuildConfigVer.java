package com.yusufalperdirak.gurbetsport;

import android.content.Context;

final class BuildConfigVer {
    private BuildConfigVer() {}

    @SuppressWarnings("deprecation")
    static String name(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
