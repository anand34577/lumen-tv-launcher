package com.lumen.tvlauncher;

import android.app.Dialog;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Supplier;

/**
 * Small pop-up menu anchored to the view that opened it: beside an app tile,
 * or dropping down under a top-bar button. Long lists scroll inside it.
 */
final class TvMenu extends Dialog {
    private final LinearLayout root;
    private final LinearLayout rows;
    private final ScrollView scroll;
    private final TextView hint;
    private int widthDp = 260;

    TvMenu(Context ctx, CharSequence title) {
        super(ctx, R.style.Lumen_Popup);
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.popup_bg);
        int p = Ui.dp(ctx, 6);
        root.setPadding(p, p, p, p);

        if (title != null) {
            TextView t = new TextView(ctx);
            t.setText(title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setLetterSpacing(0.06f);
            t.setTextColor(ctx.getColor(R.color.text_secondary));
            t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 6));
            root.addView(t);
        }

        hint = new TextView(ctx);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setTextColor(ctx.getColor(R.color.text_secondary));
        hint.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        hint.setVisibility(View.GONE);
        root.addView(hint);

        rows = new LinearLayout(ctx);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    TvMenu hint(CharSequence text) {
        if (!TextUtils.isEmpty(text)) {
            hint.setText(text);
            hint.setVisibility(View.VISIBLE);
        }
        return this;
    }

    TvMenu width(int dp) {
        widthDp = dp;
        return this;
    }

    TvMenu add(CharSequence text, Runnable action) {
        TextView row = row(text);
        row.setOnClickListener(v -> {
            dismiss();
            action.run();
        });
        return this;
    }

    TvMenu add(int textRes, Runnable action) {
        return add(getContext().getText(textRes), action);
    }

    /** Two-line row: title with smaller detail text underneath. */
    TvMenu add(CharSequence title, CharSequence detail, Runnable action) {
        if (TextUtils.isEmpty(detail)) return add(title, action);
        add(twoLine(title, detail), action);
        makeTwoLine((TextView) rows.getChildAt(rows.getChildCount() - 1));
        return this;
    }

    /** A setting that stays open when selected and shows its new value, so changes preview live. */
    TvMenu addSetting(CharSequence title, Supplier<CharSequence> value, Runnable action) {
        TextView row = row(twoLine(title, value.get()));
        makeTwoLine(row);
        row.setOnClickListener(v -> {
            action.run();
            row.setText(twoLine(title, value.get()));
        });
        return this;
    }

    private TextView row(CharSequence text) {
        TextView row = new TextView(getContext(), null, 0, R.style.Lumen_MenuRow);
        row.setText(text);
        rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static CharSequence twoLine(CharSequence title, CharSequence detail) {
        SpannableStringBuilder sb = new SpannableStringBuilder(title).append('\n');
        int start = sb.length();
        sb.append(detail);
        sb.setSpan(new RelativeSizeSpan(0.8f), start, sb.length(), 0);
        return sb;
    }

    private void makeTwoLine(TextView row) {
        row.setSingleLine(false);
        row.setMaxLines(3);
        int v = Ui.dp(getContext(), 8);
        row.setPadding(row.getPaddingStart(), v, row.getPaddingEnd(), v);
    }

    /** Beside an app tile; under (or above) any other anchor. Always kept on screen. */
    void show(View anchor) {
        Context c = getContext();
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = Ui.dp(c, widthDp), margin = Ui.dp(c, 20), gap = Ui.dp(c, 12);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels - 2 * margin, View.MeasureSpec.AT_MOST));
        int h = root.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int ax = loc[0], ay = loc[1], aw = anchor.getWidth(), ah = anchor.getHeight();
        int x, y;
        if (anchor instanceof TileView) {
            x = ax + aw + gap;
            if (x + w > dm.widthPixels - margin) x = ax - w - gap;
            y = ay;
        } else {
            boolean rightHalf = ax + aw / 2 > dm.widthPixels / 2;
            x = rightHalf ? ax + aw - w : ax;
            y = ay + ah + gap;
            if (y + h > dm.heightPixels - margin) {
                if (ay - gap - h >= margin) {
                    y = ay - gap - h; // fits above
                } else {
                    // Too tall for either: open beside the button so it never covers it.
                    x = rightHalf ? ax - w - gap : ax + aw + gap;
                    y = ay;
                }
            }
        }
        x = Math.max(margin, Math.min(x, dm.widthPixels - w - margin));
        y = Math.max(margin, Math.min(y, dm.heightPixels - h - margin));

        Window win = getWindow();
        if (win != null) {
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = x;
            lp.y = y;
            lp.width = w;
            lp.height = h;
            win.setAttributes(lp);
        }
        super.show();
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }
}
