package com.droidmaker.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Programmatic UI toolkit: colors, sizes and widget factories tuned for phone touch screens. */
public final class Ui {
    private Ui() {}

    // ---- palette
    public static final int BG = 0xFF15171C, PANEL = 0xFF1F232B, PANEL2 = 0xFF282D37, PANEL3 = 0xFF323845,
            LINE = 0xFF363C49, TEXT = 0xFFE8ECF2, DIM = 0xFF9AA3B2, ACCENT = 0xFF4FA3FF, WARM = 0xFFFFB547,
            OK = 0xFF4CD58A, DANGER = 0xFFFF5C5C, SELECT = 0x334FA3FF;

    public static int categoryColor(String cat) {
        switch (cat) {
            case "Move": return 0xFF3D7BE0;
            case "Main": return 0xFF36A46A;
            case "Game": return 0xFF8A5CD6;
            case "Control": return 0xFFE08A2E;
            case "Variables": return 0xFF2BA3A3;
            case "Score": return 0xFFC9A227;
            case "Draw": return 0xFFD45A9A;
            case "Touch": return 0xFF27A9D6;
            case "3D": return 0xFFD9534F;
            case "Raycast": return 0xFF9C6B45;
        }
        return 0xFF666C7A;
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable roundStroke(int color, int stroke, int strokeW, float radiusPx) {
        GradientDrawable g = round(color, radiusPx);
        g.setStroke(strokeW, stroke);
        return g;
    }

    public static Drawable ripple(Drawable content, int mask) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask == 0 ? null : new ColorDrawable(mask));
    }

    // ---- layouts
    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpw(int w, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.weight = weight;
        return p;
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(l, t, r, b);
        return p;
    }

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.addView(child);
        return s;
    }

    public static HorizontalScrollView hscroll(Context c, View child) {
        HorizontalScrollView s = new HorizontalScrollView(c);
        s.setHorizontalScrollBarEnabled(false);
        s.addView(child);
        return s;
    }

    // ---- text
    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, DIM);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 6));
        return t;
    }

    // ---- buttons
    /** Filled pill button. */
    public static TextView button(Context c, String label, int bg, View.OnClickListener l) {
        TextView b = text(c, label, 15, bg == ACCENT || bg == DANGER || bg == OK ? 0xFFFFFFFF : TEXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 44));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setBackground(ripple(round(bg, dp(c, 10)), 0));
        b.setOnClickListener(l);
        b.setClickable(true);
        return b;
    }

    public static TextView button(Context c, String label, View.OnClickListener l) { return button(c, label, PANEL3, l); }

    /** Square icon button using a text glyph. */
    public static TextView icon(Context c, String glyph, View.OnClickListener l) {
        TextView b = text(c, glyph, 20, TEXT);
        b.setGravity(Gravity.CENTER);
        int s = dp(c, 44);
        b.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        b.setMinWidth(s);
        b.setMinHeight(s);
        b.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT), 0xFFFFFFFF));
        b.setOnClickListener(l);
        return b;
    }

    /** Small rounded chip; selected chips are highlighted. */
    public static TextView chip(Context c, String label, boolean selected, View.OnClickListener l) {
        TextView b = text(c, label, 14, selected ? 0xFFFFFFFF : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinHeight(dp(c, 38));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        b.setBackground(ripple(round(selected ? ACCENT : PANEL3, dp(c, 19)), 0));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, dp(c, 38));
        p.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        b.setLayoutParams(p);
        return b;
    }

    public static void setChip(Context c, TextView b, boolean selected) {
        b.setTextColor(selected ? 0xFFFFFFFF : TEXT);
        b.setBackground(ripple(round(selected ? ACCENT : PANEL3, dp(c, 19)), 0));
    }

    /** Top app bar: back glyph, title and right-side actions. */
    public static LinearLayout toolbar(Activity a, String title, boolean back) {
        LinearLayout bar = row(a);
        bar.setBackgroundColor(PANEL);
        bar.setPadding(dp(a, 4), 0, dp(a, 4), 0);
        bar.setMinimumHeight(dp(a, 56));
        if (back) bar.addView(icon(a, "←", v -> a.onBackPressed()));
        else {
            View pad = new View(a);
            bar.addView(pad, new LinearLayout.LayoutParams(dp(a, 12), 1));
        }
        TextView t = title(a, title);
        t.setTag("title");
        bar.addView(t, lpw(0, WRAP, 1));
        return bar;
    }

    public static void setToolbarTitle(LinearLayout bar, String s) {
        View v = bar.findViewWithTag("title");
        if (v instanceof TextView) ((TextView) v).setText(s);
    }

    // ---- form fields
    public static EditText field(Context c, String value, String hint, boolean numeric) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(roundStroke(PANEL2, LINE, dp(c, 1), dp(c, 8)));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    /** A labelled row: label on the left, control on the right. */
    public static LinearLayout labeled(Context c, String label, View control) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        r.setMinimumHeight(dp(c, 52));
        TextView t = text(c, label, 15, TEXT);
        r.addView(t, lpw(0, WRAP, 1));
        r.addView(control);
        return r;
    }

    /** A labelled field stacked vertically (for wide inputs). */
    public static LinearLayout stacked(Context c, String label, View control) {
        LinearLayout r = col(c);
        r.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        TextView t = text(c, label, 13, DIM);
        t.setPadding(dp(c, 2), 0, 0, dp(c, 4));
        r.addView(t);
        r.addView(control, lp(MATCH, WRAP));
        return r;
    }

    public static Switch toggle(Context c, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    /** A button showing a value that opens a chooser when tapped. */
    public static TextView valueButton(Context c, String value, View.OnClickListener l) {
        TextView b = text(c, value + "  ▾", 15, TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMaxWidth(dp(c, 220));
        b.setMinHeight(dp(c, 40));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        b.setBackground(ripple(roundStroke(PANEL2, LINE, dp(c, 1), dp(c, 8)), 0));
        b.setOnClickListener(l);
        return b;
    }

    public static void setValue(TextView b, String value) { b.setText(value + "  ▾"); }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1) / 2)));
        return v;
    }

    /** Card container with rounded panel background. */
    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(round(PANEL, dp(c, 14)));
        LinearLayout.LayoutParams p = lp(MATCH, WRAP);
        p.setMargins(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        l.setLayoutParams(p);
        return l;
    }

    public static String colorHex(int rgb) { return String.format("#%06X", rgb & 0xFFFFFF); }

    /** Small square color swatch. */
    public static View swatch(Context c, int argb, View.OnClickListener l) {
        View v = new View(c);
        GradientDrawable g = roundStroke(argb, 0xFFFFFFFF, dp(c, 2), dp(c, 8));
        v.setBackground(g);
        int s = dp(c, 40);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        v.setOnClickListener(l);
        return v;
    }
}
