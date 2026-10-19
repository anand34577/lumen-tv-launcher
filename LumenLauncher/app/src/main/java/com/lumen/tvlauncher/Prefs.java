package com.lumen.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All launcher state: layout, appearance, custom names and launch history. */
final class Prefs {
    static final int SORT_AZ = 0, SORT_MOST_USED = 1, SORT_NEWEST = 2;
    static final int CLOCK_SYSTEM = 0, CLOCK_12H = 1, CLOCK_24H = 2;

    private static final String FAVOURITES = "favourites";
    private static final String HIDDEN = "hidden";
    private static final String SEEDED = "seeded";
    private static final String THEME = "theme";
    private static final String COLUMNS = "columns";
    private static final String LABELS_ALWAYS = "labels_always";
    private static final String SORT = "sort";
    private static final String RECENTS = "recents";
    private static final String CLOCK = "clock";

    private final SharedPreferences sp;
    private final SharedPreferences names;
    private final SharedPreferences usage;

    Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        names = ctx.getSharedPreferences("names", Context.MODE_PRIVATE);
        usage = ctx.getSharedPreferences("usage", Context.MODE_PRIVATE);
    }

    // ---- layout

    /** Ordered list of favourite package names. */
    List<String> favourites() {
        String raw = sp.getString(FAVOURITES, "");
        return raw.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    void setFavourites(List<String> pkgs) {
        sp.edit().putString(FAVOURITES, TextUtils.join(",", pkgs)).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet(HIDDEN, new HashSet<>()));
    }

    void setHidden(Set<String> pkgs) {
        sp.edit().putStringSet(HIDDEN, new HashSet<>(pkgs)).apply();
    }

    boolean seeded() {
        return sp.getBoolean(SEEDED, false);
    }

    void markSeeded() {
        sp.edit().putBoolean(SEEDED, true).apply();
    }

    // ---- appearance

    int theme() {
        return sp.getInt(THEME, 0);
    }

    void setTheme(int index) {
        sp.edit().putInt(THEME, index).apply();
    }

    int columns() {
        return sp.getInt(COLUMNS, 5);
    }

    void setColumns(int n) {
        sp.edit().putInt(COLUMNS, n).apply();
    }

    boolean labelsAlways() {
        return sp.getBoolean(LABELS_ALWAYS, true);
    }

    void setLabelsAlways(boolean always) {
        sp.edit().putBoolean(LABELS_ALWAYS, always).apply();
    }

    int sort() {
        return sp.getInt(SORT, SORT_AZ);
    }

    void setSort(int sort) {
        sp.edit().putInt(SORT, sort).apply();
    }

    boolean showRecents() {
        return sp.getBoolean(RECENTS, true);
    }

    void setShowRecents(boolean show) {
        sp.edit().putBoolean(RECENTS, show).apply();
    }

    int clock() {
        return sp.getInt(CLOCK, CLOCK_SYSTEM);
    }

    void setClock(int mode) {
        sp.edit().putInt(CLOCK, mode).apply();
    }

    // ---- custom names

    /** User-chosen name, or null when the app's own label is used. */
    String name(String pkg) {
        return names.getString(pkg, null);
    }

    void setName(String pkg, String name) {
        if (TextUtils.isEmpty(name)) names.edit().remove(pkg).apply();
        else names.edit().putString(pkg, name).apply();
    }

    // ---- launch history ("count,lastMillis")

    void recordLaunch(String pkg) {
        usage.edit().putString(pkg, (launchCount(pkg) + 1) + "," + System.currentTimeMillis()).apply();
    }

    int launchCount(String pkg) {
        return (int) usageField(pkg, 0);
    }

    long lastLaunch(String pkg) {
        return usageField(pkg, 1);
    }

    void forgetLaunches(String pkg) {
        usage.edit().remove(pkg).apply();
    }

    private long usageField(String pkg, int index) {
        String raw = usage.getString(pkg, null);
        if (raw == null) return 0;
        String[] parts = raw.split(",");
        try {
            return parts.length > index ? Long.parseLong(parts[index]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Back to first-run state: default favourites, no hidden apps, default look. */
    void reset() {
        sp.edit().clear().apply();
        names.edit().clear().apply();
        usage.edit().clear().apply();
    }
}
