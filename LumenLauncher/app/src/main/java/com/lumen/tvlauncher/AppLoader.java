package com.lumen.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Queries launchable apps. Call off the main thread. */
final class AppLoader {
    private static final int FALLBACK_TINT = 0xFF1E2433;
    /** Settings apps are reachable from the gear button in the top bar; listing them again is just a duplicate. */
    private static final java.util.Set<String> EXCLUDED = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.tv.settings", // Android settings
            "com.apps.tvsettings"));   // vendor TV settings (picture menu is under the gear too)

    private AppLoader() {}

    static List<AppEntry> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> byPkg = new LinkedHashMap<>();
        // TV apps first so their leanback activity and banner win over a phone-style entry.
        collect(ctx, pm, byPkg, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        collect(ctx, pm, byPkg, Intent.CATEGORY_LAUNCHER, false);

        List<AppEntry> list = new ArrayList<>(byPkg.values());
        Collator collator = Collator.getInstance();
        list.sort((a, b) -> collator.compare(a.label, b.label));
        return list;
    }

    private static void collect(Context ctx, PackageManager pm, Map<String, AppEntry> out,
                                String category, boolean leanback) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName.equals(ctx.getPackageName()) || out.containsKey(ai.packageName)
                    || EXCLUDED.contains(ai.packageName)) {
                continue;
            }
            ApplicationInfo app = ai.applicationInfo;
            String label = String.valueOf(app.loadLabel(pm));
            boolean system = (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            Drawable banner = leanback ? ai.loadBanner(pm) : null;
            Drawable icon = ai.loadIcon(pm);
            int tint = banner == null ? tintOf(icon) : FALLBACK_TINT;
            long installed = 0;
            try {
                installed = pm.getPackageInfo(ai.packageName, 0).firstInstallTime;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            out.put(ai.packageName, new AppEntry(ai.packageName, new ComponentName(ai.packageName, ai.name),
                    label, system, leanback, banner, icon, tint, installed));
        }
    }

    /** Dark, muted version of the icon's dominant vivid colour. */
    private static int tintOf(Drawable d) {
        if (d == null) return FALLBACK_TINT;
        final int size = 16;
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Rect old = new Rect(d.getBounds());
        d.setBounds(0, 0, size, size);
        d.draw(new Canvas(bm));
        d.setBounds(old);
        int[] px = new int[size * size];
        bm.getPixels(px, 0, size, 0, 0, size, size);
        bm.recycle();

        float[] hsv = new float[3];
        double r = 0, g = 0, b = 0, sum = 0;
        for (int p : px) {
            if (Color.alpha(p) < 160) continue;
            Color.colorToHSV(p, hsv);
            double w = 0.05 + hsv[1] * hsv[2]; // favour saturated, bright pixels
            r += Color.red(p) * w;
            g += Color.green(p) * w;
            b += Color.blue(p) * w;
            sum += w;
        }
        if (sum == 0) return FALLBACK_TINT;
        Color.colorToHSV(Color.rgb((int) (r / sum), (int) (g / sum), (int) (b / sum)), hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = 0.30f;
        return Color.HSVToColor(hsv);
    }
}
