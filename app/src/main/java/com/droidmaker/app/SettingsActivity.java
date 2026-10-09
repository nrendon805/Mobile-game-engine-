package com.droidmaker.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.droidmaker.core.project.Keys;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Room;

/** Game-wide settings: resolution, orientation, scaling, virtual gamepad and start code. */
public final class SettingsActivity extends BaseActivity {
    private EditText gw, gh, name;
    private Switch interp, fps, keepOn;
    private int orientation, scale, pad, buttons;
    private final int[] keys = new int[4];
    private SeekBar opacity;

    private static final String[] ORIENT = {"Landscape", "Portrait", "Auto-rotate"};
    private static final String[] SCALE = {"Fit (keep aspect, black bars)", "Stretch to fill", "Expand view to fill (no bars)"};
    private static final String[] PADS = {"None", "D-pad + buttons", "Joystick + buttons"};
    private static final String[][] RES = {{"640 × 360 (16:9)", "640", "360"}, {"960 × 540 (16:9)", "960", "540"},
            {"1280 × 720 (16:9)", "1280", "720"}, {"480 × 320 (3:2)", "480", "320"}, {"800 × 480", "800", "480"},
            {"320 × 180 (pixel art)", "320", "180"}, {"360 × 640 (portrait)", "360", "640"}, {"540 × 960 (portrait)", "540", "960"}};

    @Override
    protected void build(Bundle state) {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.toolbar(this, "Game settings", true));
        LinearLayout f = Ui.col(this);
        f.setPadding(0, 0, 0, Ui.dp(this, 40));

        f.addView(Ui.section(this, "Game"));
        name = Ui.field(this, p.name, "name", false);
        f.addView(Ui.stacked(this, "Game name", name));

        f.addView(Ui.section(this, "Screen"));
        gw = Dialogs.numberField(this, p.gameWidth);
        gh = Dialogs.numberField(this, p.gameHeight);
        f.addView(Ui.labeled(this, "Game width", gw));
        f.addView(Ui.labeled(this, "Game height", gh));
        LinearLayout presets = Ui.row(this);
        presets.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        presets.addView(Ui.chip(this, "Presets…", false, v -> {
            String[] n = new String[RES.length];
            for (int i = 0; i < n.length; i++) n[i] = RES[i][0];
            Dialogs.choose(this, "Resolution", n, k -> {
                gw.setText(RES[k][1]);
                gh.setText(RES[k][2]);
                if (Integer.parseInt(RES[k][1]) < Integer.parseInt(RES[k][2])) orientation = Project.ORIENT_PORTRAIT;
            });
        }));
        f.addView(presets);
        orientation = p.orientation;
        scale = p.scaleMode;
        TextView ob = Ui.valueButton(this, ORIENT[orientation], null);
        ob.setOnClickListener(v -> Dialogs.choose(this, "Orientation", ORIENT, k -> { orientation = k; Ui.setValue(ob, ORIENT[k]); }));
        f.addView(Ui.labeled(this, "Orientation", ob));
        TextView sb = Ui.valueButton(this, SCALE[scale], null);
        sb.setOnClickListener(v -> Dialogs.choose(this, "Scaling", SCALE, k -> { scale = k; Ui.setValue(sb, SCALE[k]); }));
        f.addView(Ui.labeled(this, "Scaling", sb));
        interp = Ui.toggle(this, p.interpolate, null);
        f.addView(Ui.labeled(this, "Smooth pixels (off = crisp pixel art)", interp));
        fps = Ui.toggle(this, p.showFps, null);
        f.addView(Ui.labeled(this, "Show FPS counter", fps));
        keepOn = Ui.toggle(this, p.keepScreenOn, null);
        f.addView(Ui.labeled(this, "Keep screen on while playing", keepOn));
        TextView note = Ui.text(this, "Views and 3D/raycast rooms use this resolution. 2D rooms without a view are scaled to fit the screen.", 12, Ui.DIM);
        note.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        f.addView(note);

        f.addView(Ui.section(this, "Touch controls"));
        pad = p.vpad;
        buttons = p.vpadButtons;
        System.arraycopy(p.vpadKeys, 0, keys, 0, 4);
        TextView pb = Ui.valueButton(this, PADS[pad], null);
        pb.setOnClickListener(v -> Dialogs.choose(this, "On-screen gamepad", PADS, k -> { pad = k; Ui.setValue(pb, PADS[k]); }));
        f.addView(Ui.labeled(this, "On-screen gamepad", pb));
        TextView bb = Ui.valueButton(this, String.valueOf(buttons), null);
        bb.setOnClickListener(v -> Dialogs.choose(this, "Buttons", new String[]{"0", "1", "2", "3", "4"}, k -> { buttons = k; Ui.setValue(bb, String.valueOf(k)); }));
        f.addView(Ui.labeled(this, "Number of buttons", bb));
        String[] labels = {"A", "B", "X", "Y"};
        for (int i = 0; i < 4; i++) {
            int k = i;
            TextView kb = Ui.valueButton(this, Keys.name(keys[i]), null);
            kb.setOnClickListener(v -> Dialogs.key(this, key -> { keys[k] = key; Ui.setValue(kb, Keys.name(key)); }));
            f.addView(Ui.labeled(this, "Button " + labels[i] + " acts as key", kb));
        }
        opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress((int) Math.round(p.vpadOpacity * 100));
        f.addView(Ui.stacked(this, "Gamepad opacity", opacity));
        TextView pn = Ui.text(this, "The d-pad/joystick presses the arrow keys, so Keyboard events and keyboard_check(vk_left) work on phones. "
                + "Use vjoy_x() / vjoy_y() for analog values and vbutton_check(n) for buttons.", 12, Ui.DIM);
        pn.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        f.addView(pn);

        f.addView(Ui.section(this, "Code"));
        LinearLayout cb = Ui.row(this);
        cb.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        cb.addView(Ui.button(this, "Edit game start code", v -> {
            apply();
            App.codeTarget = new App.CodeTarget() {
                public String title() { return "Game start code"; }
                public String get() { return p.globalCode; }
                public void set(String code) { p.globalCode = code; }
            };
            startActivity(new Intent(this, ScriptEditorActivity.class));
        }), Ui.lpw(0, Ui.WRAP, 1));
        f.addView(cb);

        root.addView(Ui.scroll(this, f), Ui.lpw(Ui.MATCH, 0, 1));
        setContentView(root);
    }

    private void apply() {
        String n = name.getText().toString().trim();
        if (!n.isEmpty()) p.name = n;
        int nw = Math.max(64, Math.min(4096, Dialogs.inum(gw, p.gameWidth)));
        int nh = Math.max(64, Math.min(4096, Dialogs.inum(gh, p.gameHeight)));
        if (nw != p.gameWidth || nh != p.gameHeight) {
            // keep full-screen views matching the new resolution
            for (Room r : p.rooms) {
                if (r == null) continue;
                for (Room.View v : r.views) {
                    if (v.wport == p.gameWidth && v.hport == p.gameHeight && v.xport == 0 && v.yport == 0) {
                        v.wport = nw;
                        v.hport = nh;
                        if (v.wview == p.gameWidth && v.hview == p.gameHeight) { v.wview = nw; v.hview = nh; }
                    }
                }
            }
            p.gameWidth = nw;
            p.gameHeight = nh;
        }
        p.orientation = orientation;
        p.scaleMode = scale;
        p.interpolate = interp.isChecked();
        p.showFps = fps.isChecked();
        p.keepScreenOn = keepOn.isChecked();
        p.vpad = pad;
        p.vpadButtons = buttons;
        System.arraycopy(keys, 0, p.vpadKeys, 0, 4);
        p.vpadOpacity = opacity.getProgress() / 100.0;
        App.save();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (name != null) apply();
    }
}
