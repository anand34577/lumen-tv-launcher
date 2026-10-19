package com.lumen.tvlauncher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** One launchable app. Drawables are loaded once on the worker thread and copied per view. */
final class AppEntry {
    final String pkg;
    final ComponentName component;
    final String label;
    final boolean system;
    final boolean leanback;
    final Drawable banner; // null when the app has no TV banner
    final Drawable icon;
    final int tint;        // card colour for icon-only apps
    final long installed;  // first install time, for the "Newest" sort

    AppEntry(String pkg, ComponentName component, String label, boolean system, boolean leanback,
             Drawable banner, Drawable icon, int tint, long installed) {
        this.pkg = pkg;
        this.component = component;
        this.label = label;
        this.system = system;
        this.leanback = leanback;
        this.banner = banner;
        this.icon = icon;
        this.tint = tint;
        this.installed = installed;
    }
}
