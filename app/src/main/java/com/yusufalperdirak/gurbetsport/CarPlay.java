package com.yusufalperdirak.gurbetsport;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Unitedeki CarPlay uygulamasini (CP-Link vb.) bulur ve acar. */
final class CarPlay {
    private CarPlay() {}

    private static String pkg;

    static void find(Context c) {
        String best = null, bestLabel = "";
        int bestScore = 0;
        try {
            PackageManager pm = c.getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            for (ResolveInfo r : apps) {
                String p = r.activityInfo.packageName;
                if (p.equals(c.getPackageName())) continue;
                String pl = p.toLowerCase(Locale.ROOT);
                String l = String.valueOf(r.loadLabel(pm));
                String ll = l.toLowerCase(Locale.ROOT);
                int s = 0;
                if (ll.contains("cp-link") || ll.contains("cplink") || ll.contains("cp link") || pl.contains("cplink")) s = 5;
                else if (ll.contains("carplay") || pl.contains("carplay")) s = 4;
                else if (ll.contains("zlink") || pl.contains("zlink") || ll.contains("carlink") || pl.contains("carlink")
                        || ll.contains("autokit") || pl.contains("autokit")) s = 2;
                if (s > bestScore) { bestScore = s; best = p; bestLabel = l; }
            }
        } catch (Exception ignored) {}
        pkg = best;
        try {
            JSONObject o = new JSONObject();
            o.put("found", best != null);
            o.put("name", bestLabel);
            Hub.put("cp", o);
        } catch (Exception ignored) {}
    }

    static void launch(Context c) {
        if (pkg == null) find(c);
        if (pkg == null) {
            Toast.makeText(c, "CarPlay uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "CarPlay açılamadı", Toast.LENGTH_SHORT).show();
        }
    }
}
