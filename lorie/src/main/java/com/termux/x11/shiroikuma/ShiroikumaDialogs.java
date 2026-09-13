package com.termux.x11.shiroikuma;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The house dialogs and their building blocks (ported from raikidoban's {@code ExportImportPanel}):
 * a transparent window whose content is ONE bordered rounded black box (2 dp yellow stroke, 16 dp
 * radius), yellow text, and pill buttons — black fill, 1.5 dp yellow stroke, 50 dp corner radius,
 * yellow ripple, no all-caps, {@code minWidth 0}.
 */
public final class ShiroikumaDialogs {

    public static final int YELLOW = 0xFFFFFF00;
    public static final int DIM_YELLOW = 0xFFC8C800;
    public static final int WARN = 0xFFFF5252;
    public static final int BLACK = 0xFF000000;

    private final Activity activity;
    private final float density;

    public ShiroikumaDialogs(Activity activity) {
        this.activity = activity;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    public int dp(float v) {
        return Math.round(v * density);
    }

    // ---- ready-made dialogs ---------------------------------------------------------------------

    /** Title, body, one OK pill right-aligned. {@code onOk} runs after the dialog is dismissed. */
    public AlertDialog info(String title, String body, boolean cancelable, Runnable onOk) {
        LinearLayout box = infoBox(title, body);
        final AlertDialog dialog = boxDialog(box, cancelable);
        LinearLayout buttons = buttonRow();
        buttons.addView(pill(activity.getString(android.R.string.ok), v -> {
            dialog.dismiss();
            if (onOk != null)
                onOk.run();
        }));
        box.addView(buttons);
        dialog.show();
        transparentWindow(dialog);
        return dialog;
    }

    /** Title, body, Cancel + one action pill right-aligned. */
    public AlertDialog confirm(String title, String body, String action, Runnable onAction) {
        LinearLayout box = infoBox(title, body);
        final AlertDialog dialog = boxDialog(box, true);
        LinearLayout buttons = buttonRow();
        Button cancel = pill(activity.getString(android.R.string.cancel), v -> dialog.dismiss());
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).rightMargin = dp(10);
        buttons.addView(cancel);
        buttons.addView(pill(action, v -> {
            dialog.dismiss();
            onAction.run();
        }));
        box.addView(buttons);
        dialog.show();
        transparentWindow(dialog);
        return dialog;
    }

    /** A title over tappable rows (16 sp yellow), and a Cancel pill. */
    public interface OnPick {
        void pick(int index);
    }

    public AlertDialog list(String title, List<CharSequence> labels, OnPick onPick) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(16));
        box.setBackground(panelBackground());
        box.addView(text(title, 19, YELLOW, true));
        final AlertDialog dialog = boxDialog(box, true);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            TextView row = text(labels.get(i), 16, YELLOW, false);
            row.setPadding(dp(4), dp(10), dp(4), dp(10));
            row.setBackground(selectable());
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.pick(index);
            });
            box.addView(row);
        }
        LinearLayout buttons = buttonRow();
        buttons.addView(pill(activity.getString(android.R.string.cancel), v -> dialog.dismiss()));
        box.addView(buttons);
        dialog.show();
        transparentWindow(dialog);
        return dialog;
    }

    // ---- building blocks ------------------------------------------------------------------------

    public LinearLayout infoBox(String title, String body) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(16));
        box.setBackground(panelBackground());
        box.addView(text(title, 19, YELLOW, true));
        TextView bodyView = text(body, 14, YELLOW, false);
        bodyView.setPadding(0, dp(10), 0, 0);
        box.addView(bodyView);
        return box;
    }

    /** A dialog whose only content is {@code content}, scrollable, on a transparent window (set after show()). */
    public AlertDialog boxDialog(View content, boolean cancelable) {
        ScrollView scroll = new ScrollView(activity);
        int m = dp(10);
        scroll.setPadding(m, m, m, m);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        return dialog;
    }

    public void transparentWindow(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null)
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    public LinearLayout buttonRow() {
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(16), 0, 0);
        return buttons;
    }

    /** The bordered rounded panel every surface of these windows is drawn on. */
    public GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BLACK);
        bg.setStroke(Math.max(1, dp(2)), YELLOW);
        bg.setCornerRadius(dp(16));
        return bg;
    }

    /** A smaller bordered box (the directory box): 2 dp stroke in the given colour, 10 dp radius. */
    public GradientDrawable boxBackground(int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BLACK);
        bg.setStroke(Math.max(1, dp(2)), stroke);
        bg.setCornerRadius(dp(10));
        return bg;
    }

    private RippleDrawable selectable() {
        return new RippleDrawable(ColorStateList.valueOf((YELLOW & 0x00FFFFFF) | 0x33000000),
                null, new ColorDrawable(Color.WHITE));
    }

    public TextView text(CharSequence s, int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(activity);
        tv.setText(s);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold)
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    public CheckBox checkbox(String label, boolean bold, int indent) {
        CheckBox cb = new CheckBox(activity);
        cb.setText(label);
        cb.setTextColor(YELLOW);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (bold)
            cb.setTypeface(cb.getTypeface(), Typeface.BOLD);
        cb.setButtonTintList(ColorStateList.valueOf(YELLOW));
        cb.setPadding(dp(8) + indent, dp(7), 0, dp(7));
        return cb;
    }

    public View divider(int topGap) {
        View v = new View(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(topGap);
        v.setLayoutParams(lp);
        v.setBackgroundColor(YELLOW);
        v.setAlpha(0.4f);
        return v;
    }

    /** The house pill: black fill, 1.5 dp yellow stroke, 50 dp radius, yellow ripple, no all-caps, minWidth 0. */
    public Button pill(String label, View.OnClickListener onClick) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(YELLOW);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BLACK);
        bg.setStroke(Math.max(1, Math.round(1.5f * density)), YELLOW);
        bg.setCornerRadius(dp(50));
        b.setBackground(new RippleDrawable(
                ColorStateList.valueOf((YELLOW & 0x00FFFFFF) | 0x33000000), bg, null));
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(20), dp(8), dp(20), dp(8));
        b.setOnClickListener(onClick);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }
}
