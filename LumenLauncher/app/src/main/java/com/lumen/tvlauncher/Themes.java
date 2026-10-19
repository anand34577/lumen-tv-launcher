package com.lumen.tvlauncher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/** Background themes: a dark vertical gradient plus two soft radial glows. Drawn, not bitmaps. */
final class Themes {
    static final String[] NAMES = {"Midnight", "Ocean", "Aurora", "Ember", "Graphite", "Black"};

    // {top, bottom, glow top-left, glow bottom-right}
    private static final int[][] COLORS = {
            {0xFF0D111C, 0xFF06080C, 0x3A3D5BD6, 0x2214B8A6},
            {0xFF08161F, 0xFF04090D, 0x3A1E88B8, 0x2222C3EE},
            {0xFF0B1412, 0xFF050807, 0x3310B981, 0x268B5CF6},
            {0xFF160E0C, 0xFF080505, 0x33E76F51, 0x22F4A261},
            {0xFF141518, 0xFF0A0A0C, 0x14FFFFFF, 0x0AFFFFFF},
            {0xFF000000, 0xFF000000, 0x00000000, 0x00000000},
    };

    private Themes() {}

    static Drawable background(Context ctx, int index) {
        int[] c = COLORS[Math.floorMod(index, COLORS.length)];
        float w = ctx.getResources().getDisplayMetrics().widthPixels;
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c[0], c[1]});
        return new LayerDrawable(new Drawable[]{
                base,
                glow(c[2], 0.12f, 0f, w * 0.75f),
                glow(c[3], 0.95f, 1f, w * 0.6f),
        });
    }

    private static GradientDrawable glow(int color, float cx, float cy, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(radius);
        return g;
    }
}
