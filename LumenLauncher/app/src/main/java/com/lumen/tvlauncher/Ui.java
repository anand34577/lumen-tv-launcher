package com.lumen.tvlauncher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

final class Ui {
    private Ui() {}

    static int dp(Context ctx, float value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable roundRect(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable ring(int color, int strokePx, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0);
        d.setStroke(strokePx, color);
        d.setCornerRadius(radius);
        return d;
    }

    /** A Drawable can only be attached to one view; give each view its own instance. */
    static Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable().mutate() : d;
    }
}
