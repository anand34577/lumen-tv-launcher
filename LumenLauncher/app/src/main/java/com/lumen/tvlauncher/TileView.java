package com.lumen.tvlauncher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A focusable app card: 16:9 banner (or tinted icon tile) with a label underneath.
 * With a null app it becomes the "+ Add" tile at the end of Favourites.
 */
final class TileView extends LinearLayout {
    static final char FAVOURITES = 'f', RECENT = 'r', APPS = 'a';

    private static final float FOCUS_SCALE = 1.08f;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    final AppEntry app;
    final char section;
    private final String title;
    private final FrameLayout frame;
    private final TextView label;
    private final Drawable focusRing;
    private final Drawable moveRing;

    TileView(Context ctx, AppEntry app, String title, int width, int height, char section, boolean labelAlways) {
        super(ctx);
        this.app = app;
        this.title = title;
        this.section = section;
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        setContentDescription(title);

        float radius = ctx.getResources().getDimension(R.dimen.card_radius);
        // The ring sits a few dp outside the card so it stays visible on white banners.
        int inset = Ui.dp(ctx, 5);
        StateListDrawable ring = new StateListDrawable();
        ring.addState(new int[]{android.R.attr.state_focused},
                Ui.ring(ctx.getColor(R.color.text_primary), Ui.dp(ctx, 2.5f), radius + inset));
        focusRing = ring;
        moveRing = Ui.ring(ctx.getColor(R.color.accent), Ui.dp(ctx, 3), radius + inset);

        frame = new FrameLayout(ctx);
        frame.setDuplicateParentStateEnabled(true);
        frame.setPadding(inset, inset, inset, inset);
        frame.setForeground(focusRing);

        FrameLayout card = new FrameLayout(ctx);
        int cardColor = app == null ? ctx.getColor(R.color.chip)
                : app.banner != null ? ctx.getColor(R.color.surface) : app.tint;
        card.setBackground(Ui.roundRect(cardColor, radius));
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);

        ImageView art = new ImageView(ctx);
        if (app != null && app.banner != null) {
            art.setImageDrawable(Ui.copy(app.banner));
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            art.setImageDrawable(app != null ? Ui.copy(app.icon) : ctx.getDrawable(R.drawable.ic_add));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = Math.round(height * (app != null ? 0.52f : 0.34f));
            card.addView(art, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        frame.addView(card, new FrameLayout.LayoutParams(width, height));
        LayoutParams flp = new LayoutParams(width + 2 * inset, height + 2 * inset);
        flp.setMargins(-inset, -inset, -inset, -inset);
        addView(frame, flp);

        label = new TextView(ctx);
        label.setText(title);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, section == FAVOURITES ? 14 : 13);
        label.setTextColor(ctx.getColorStateList(R.color.tile_label));
        label.setDuplicateParentStateEnabled(true);
        label.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 2), 0);
        label.setAlpha(labelAlways ? 1f : 0f);
        addView(label, new LayoutParams(width, LayoutParams.WRAP_CONTENT));

        setOnFocusChangeListener((v, focused) -> {
            animate()
                    .scaleX(focused ? FOCUS_SCALE : 1f)
                    .scaleY(focused ? FOCUS_SCALE : 1f)
                    .translationZ(focused ? 1f : 0f) // draw above neighbours while enlarged
                    .setDuration(160)
                    .setInterpolator(EASE)
                    .start();
            if (!labelAlways) label.animate().alpha(focused ? 1f : 0f).setDuration(160).start();
        });
    }

    /** Stable id used to restore focus after the screen is rebuilt. */
    String key() {
        return section + ":" + (app == null ? "+" : app.pkg);
    }

    void setMoving(boolean moving) {
        frame.setForeground(moving ? moveRing : focusRing);
        label.setText(moving ? getContext().getString(R.string.move_hint) : title);
        if (moving) label.setAlpha(1f);
    }
}
