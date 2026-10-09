package com.droidmaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Keys;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.project.Sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Reusable dialogs: text input, confirmation, lists, colors, resources and keys. */
public final class Dialogs {
    private Dialogs() {}

    public static AlertDialog.Builder builder(Activity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    public static void input(Activity a, String title, String initial, String hint, boolean numeric, Consumer<String> ok) {
        EditText e = Ui.field(a, initial, hint, numeric);
        e.setSelectAllOnFocus(true);
        LinearLayout box = Ui.col(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        box.addView(e, Ui.lp(Ui.MATCH, Ui.WRAP));
        AlertDialog d = builder(a).setTitle(title).setView(box)
                .setPositiveButton("OK", (di, w) -> ok.accept(e.getText().toString().trim()))
                .setNegativeButton("Cancel", null).create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        e.requestFocus();
    }

    /** Asks for a valid, unused resource name. */
    public static void name(Activity a, Project p, String title, String initial, Resource self, Consumer<String> ok) {
        input(a, title, initial, "name", false, s -> {
            if (!Project.isValidName(s)) { toast(a, "Names may only use letters, digits and _ and must not start with a digit"); return; }
            Resource other = p.find(s);
            if (other != null && other != self) { toast(a, "'" + s + "' is already used"); return; }
            if (Project.isReserved(s)) { toast(a, "'" + s + "' is a built-in name"); return; }
            ok.accept(s);
        });
    }

    public static void toast(Activity a, String msg) {
        android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    public static void confirm(Activity a, String title, String msg, String okLabel, Runnable ok) {
        builder(a).setTitle(title).setMessage(msg).setPositiveButton(okLabel, (d, w) -> ok.run())
                .setNegativeButton("Cancel", null).show();
    }

    public static void message(Activity a, String title, String msg) {
        builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    public static void choose(Activity a, String title, String[] items, IntConsumer pick) {
        builder(a).setTitle(title).setItems(items, (d, w) -> pick.accept(w)).show();
    }

    // ------------------------------------------------------------------ colors
    private static final int[] PRESETS = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00,
            0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFF800000, 0xFF804000, 0xFF808000, 0xFF008000,
            0xFF008080, 0xFF000080, 0xFF800080, 0xFFFFC0CB, 0xFF8B4513, 0xFF2E8B57, 0xFF4FA3FF, 0xFFFFB547,
            0xFF1D2B53, 0xFF7E2553, 0xFF008751, 0xFFAB5236, 0xFF5F574F, 0xFFC2C3C7, 0xFFFFF1E8, 0xFFFF004D,
            0xFFFFA300, 0xFFFFEC27, 0xFF00E436, 0xFF29ADFF, 0xFF83769C, 0xFFFF77A8, 0xFFFFCCAA, 0x00000000
    };
    private static final List<Integer> recent = new ArrayList<>();

    public static void color(Activity a, int argb, boolean withAlpha, IntConsumer ok) {
        LinearLayout box = Ui.col(a);
        int pad = Ui.dp(a, 16);
        box.setPadding(pad, pad, pad, 0);
        ColorPickerView picker = new ColorPickerView(a, argb, withAlpha);
        View preview = new View(a);
        EditText hex = Ui.field(a, hexOf(argb, withAlpha), "#RRGGBB", false);
        boolean[] syncing = {false};
        Runnable updatePreview = () -> preview.setBackground(Ui.roundStroke(picker.color() | (withAlpha ? 0 : 0xFF000000), 0xFFFFFFFF, Ui.dp(a, 1), Ui.dp(a, 8)));
        picker.setListener(c -> {
            updatePreview.run();
            syncing[0] = true;
            hex.setText(hexOf(c, withAlpha));
            syncing[0] = false;
        });
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                Integer c = parseHex(s.toString(), withAlpha);
                if (c != null) { picker.setColor(c); updatePreview.run(); }
            }
        });
        LinearLayout top = Ui.row(a);
        top.addView(preview, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 44)));
        LinearLayout.LayoutParams hp = Ui.lpw(0, Ui.WRAP, 1);
        hp.leftMargin = Ui.dp(a, 12);
        top.addView(hex, hp);
        box.addView(top);
        LinearLayout.LayoutParams pp = Ui.lp(Ui.MATCH, Ui.WRAP);
        pp.topMargin = Ui.dp(a, 12);
        box.addView(picker, pp);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(8);
        List<Integer> sw = new ArrayList<>(recent);
        for (int c : PRESETS) if (withAlpha || (c >>> 24) != 0) sw.add(c);
        int s = Ui.dp(a, 32);
        for (int c : sw) {
            View v = new View(a);
            v.setBackground(Ui.roundStroke(c == 0 ? 0x22FFFFFF : c, 0x44FFFFFF, 1, Ui.dp(a, 6)));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = s;
            gp.height = s;
            gp.setMargins(3, 3, 3, 3);
            v.setOnClickListener(x -> { picker.setColor(c); updatePreview.run(); hex.setText(hexOf(c, withAlpha)); });
            grid.addView(v, gp);
        }
        LinearLayout.LayoutParams gl = Ui.lp(Ui.WRAP, Ui.WRAP);
        gl.topMargin = Ui.dp(a, 10);
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(grid, gl);
        updatePreview.run();
        builder(a).setTitle("Color").setView(Ui.scroll(a, box))
                .setPositiveButton("OK", (d, w) -> {
                    int c = picker.color();
                    if (!withAlpha) c |= 0xFF000000;
                    recent.remove(Integer.valueOf(c));
                    recent.add(0, c);
                    while (recent.size() > 8) recent.remove(recent.size() - 1);
                    ok.accept(c);
                })
                .setNegativeButton("Cancel", null).show();
    }

    static String hexOf(int c, boolean alpha) {
        return alpha ? String.format("#%08X", c) : String.format("#%06X", c & 0xFFFFFF);
    }

    static Integer parseHex(String s, boolean alpha) {
        s = s.trim().replace("#", "").replace("$", "");
        try {
            if (s.length() == 6) return (int) (0xFF000000L | Long.parseLong(s, 16));
            if (s.length() == 8 && alpha) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) { /* invalid */ }
        return null;
    }

    // ------------------------------------------------------------------ resources
    public static void resource(Activity a, Project p, ResType type, boolean allowNone, Consumer<Resource> pick) {
        LinearLayout list = Ui.col(a);
        AlertDialog[] dlg = new AlertDialog[1];
        if (allowNone) list.addView(resRow(a, p, null, "<none>", () -> { dlg[0].dismiss(); pick.accept(null); }));
        List<Resource> res = p.live(type);
        for (Resource r : res) list.addView(resRow(a, p, r, r.name + (r.folder.isEmpty() ? "" : "   (" + r.folder + ")"), () -> { dlg[0].dismiss(); pick.accept(r); }));
        if (res.isEmpty()) {
            TextView t = Ui.text(a, "No " + type.title.toLowerCase() + " yet.", 15, Ui.DIM);
            t.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20));
            list.addView(t);
        }
        ScrollView sv = Ui.scroll(a, list);
        dlg[0] = builder(a).setTitle("Choose " + type.title.toLowerCase().replaceAll("s$", "")).setView(sv)
                .setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    static View resRow(Activity a, Project p, Resource r, String label, Runnable click) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 16), Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 6));
        row.setMinimumHeight(Ui.dp(a, 52));
        row.setBackground(Ui.ripple(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), 0xFFFFFFFF));
        ImageView iv = new ImageView(a);
        Bitmap b = thumb(p, r);
        int s = Ui.dp(a, 36);
        if (b != null) {
            BitmapDrawable bd = new BitmapDrawable(a.getResources(), b);
            bd.setFilterBitmap(false);
            iv.setImageDrawable(bd);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackground(Ui.round(Ui.PANEL3, Ui.dp(a, 6)));
        row.addView(iv, Ui.lp(s, s));
        TextView t = Ui.text(a, label, 16, Ui.TEXT);
        t.setPadding(Ui.dp(a, 14), 0, 0, 0);
        row.addView(t, Ui.lpw(0, Ui.WRAP, 1));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    static Bitmap thumb(Project p, Resource r) {
        if (r instanceof Sprite) return ImageIO.spriteFrame(p, (Sprite) r, 0);
        if (r instanceof Background) return ImageIO.background(p, (Background) r);
        if (r instanceof GameObject) return ImageIO.objectImage(p, (GameObject) r);
        return null;
    }

    // ------------------------------------------------------------------ keys
    public static void key(Activity a, IntConsumer pick) {
        GridLayout g = new GridLayout(a);
        g.setColumnCount(4);
        g.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        AlertDialog[] d = new AlertDialog[1];
        for (int k : Keys.PICKER) {
            TextView b = Ui.button(a, Keys.name(k), v -> { d[0].dismiss(); pick.accept(k); });
            b.setTextSize(13);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = Ui.dp(a, 72);
            gp.setMargins(4, 4, 4, 4);
            g.addView(b, gp);
        }
        d[0] = builder(a).setTitle("Choose a key").setView(Ui.scroll(a, g)).setNegativeButton("Cancel", null).create();
        d[0].show();
    }

    // ------------------------------------------------------------------ numbers
    public static double num(EditText e, double def) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (Exception x) { return def; }
    }

    public static int inum(EditText e, int def) {
        try { return (int) Math.round(Double.parseDouble(e.getText().toString().trim())); } catch (Exception x) { return def; }
    }

    public static EditText numberField(Activity a, double v) {
        EditText e = Ui.field(a, fmt(v), "", true);
        e.setMinWidth(Ui.dp(a, 96));
        e.setGravity(Gravity.END);
        return e;
    }

    public static String fmt(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        return Double.toString(v);
    }

    public static void multiline(EditText e, int lines) {
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
    }
}
