package com.droidmaker.app;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.project.Room;

import java.util.ArrayList;
import java.util.List;

/** The room settings form: size, speed, mode, colors, background layers, views, raycast and 3D options. */
final class RoomSettings {
    private RoomSettings() {}

    interface Field { void apply(); }

    static void show(Activity a, Project p, Room r, Runnable done, Runnable editCode) {
        LinearLayout f = Ui.col(a);
        List<Field> fields = new ArrayList<>();

        f.addView(Ui.section(a, "General"));
        EditText w = num(a, f, "Width", r.width), h = num(a, f, "Height", r.height);
        EditText speed = num(a, f, "Speed (steps per second)", r.speed);
        int[] mode = {r.mode};
        TextView modeB = Ui.valueButton(a, Room.MODE_NAMES[r.mode], null);
        modeB.setOnClickListener(v -> Dialogs.choose(a, "Room mode", new String[]{"2D", "2.5D Raycast (Wolfenstein style)", "3D"}, k -> {
            mode[0] = k;
            Ui.setValue(modeB, Room.MODE_NAMES[k]);
        }));
        f.addView(Ui.labeled(a, "Mode", modeB));
        EditText gx = num(a, f, "Grid width", r.gridX), gy = num(a, f, "Grid height", r.gridY);
        EditText cap = Ui.field(a, r.caption, "shown nowhere on phones; for your notes", false);
        f.addView(Ui.stacked(a, "Caption", cap));
        Switch pers = Ui.toggle(a, r.persistent, null);
        f.addView(Ui.labeled(a, "Persistent (remembers state)", pers));
        int[] bgc = {r.backgroundColor};
        View sw = Ui.swatch(a, 0xFF000000 | bgc[0], null);
        sw.setOnClickListener(v -> Dialogs.color(a, 0xFF000000 | bgc[0], false, c -> {
            bgc[0] = c & 0xFFFFFF;
            sw.setBackground(Ui.roundStroke(0xFF000000 | bgc[0], 0xFFFFFFFF, Ui.dp(a, 2), Ui.dp(a, 8)));
        }));
        f.addView(Ui.labeled(a, "Background color", sw));
        Switch drawBg = Ui.toggle(a, r.drawBackgroundColor, null);
        f.addView(Ui.labeled(a, "Fill with background color", drawBg));
        LinearLayout cc = Ui.row(a);
        cc.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 4));
        android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        cc.addView(Ui.button(a, "Edit creation code" + (r.creationCode.trim().isEmpty() ? "" : " ✓"), v -> { dlg[0].dismiss(); editCode.run(); }), Ui.lpw(0, Ui.WRAP, 1));
        f.addView(cc);
        fields.add(() -> {
            r.width = Math.max(16, Math.min(100000, Dialogs.inum(w, r.width)));
            r.height = Math.max(16, Math.min(100000, Dialogs.inum(h, r.height)));
            r.speed = Math.max(1, Math.min(240, Dialogs.inum(speed, r.speed)));
            r.mode = mode[0];
            r.gridX = Math.max(1, Dialogs.inum(gx, r.gridX));
            r.gridY = Math.max(1, Dialogs.inum(gy, r.gridY));
            r.caption = cap.getText().toString();
            r.persistent = pers.isChecked();
            r.backgroundColor = bgc[0];
            r.drawBackgroundColor = drawBg.isChecked();
        });

        // ---- backgrounds
        f.addView(Ui.section(a, "Background layers"));
        for (int k = 0; k < 8; k++) {
            Room.BgLayer b = r.backgrounds[k];
            if (k > 1 && !b.visible && b.bg < 0) continue; // show layers 0-1 plus any in use
            int kk = k;
            int[] bgId = {b.bg};
            TextView bb = Ui.valueButton(a, name(p, ResType.BACKGROUND, b.bg), null);
            bb.setOnClickListener(v -> Dialogs.resource(a, p, ResType.BACKGROUND, true, res -> {
                bgId[0] = res == null ? -1 : res.id;
                Ui.setValue(bb, res == null ? "<none>" : res.name);
            }));
            f.addView(Ui.labeled(a, "Layer " + kk + " image", bb));
            Switch vis = Ui.toggle(a, b.visible, null), fg = Ui.toggle(a, b.foreground, null);
            Switch ht = Ui.toggle(a, b.htiled, null), vt = Ui.toggle(a, b.vtiled, null), st = Ui.toggle(a, b.stretch, null);
            f.addView(Ui.labeled(a, "  visible", vis));
            f.addView(Ui.labeled(a, "  foreground (in front)", fg));
            f.addView(Ui.labeled(a, "  tile horizontally", ht));
            f.addView(Ui.labeled(a, "  tile vertically", vt));
            f.addView(Ui.labeled(a, "  stretch to room", st));
            EditText hs = num(a, f, "  scroll speed X", b.hspeed), vs = num(a, f, "  scroll speed Y", b.vspeed);
            fields.add(() -> {
                b.bg = bgId[0];
                b.visible = vis.isChecked() && bgId[0] >= 0;
                b.foreground = fg.isChecked();
                b.htiled = ht.isChecked();
                b.vtiled = vt.isChecked();
                b.stretch = st.isChecked();
                b.hspeed = Dialogs.num(hs, 0);
                b.vspeed = Dialogs.num(vs, 0);
            });
        }

        // ---- views
        f.addView(Ui.section(a, "View (camera)"));
        Switch views = Ui.toggle(a, r.viewsEnabled, null);
        f.addView(Ui.labeled(a, "Enable view", views));
        Room.View v0 = r.views[0];
        EditText vx = num(a, f, "View X", v0.xview), vy = num(a, f, "View Y", v0.yview);
        EditText vw = num(a, f, "View width", v0.wview), vh = num(a, f, "View height", v0.hview);
        int[] follow = {v0.follow};
        TextView fb = Ui.valueButton(a, name(p, ResType.OBJECT, v0.follow), null);
        fb.setOnClickListener(v -> Dialogs.resource(a, p, ResType.OBJECT, true, res -> {
            follow[0] = res == null ? -1 : res.id;
            Ui.setValue(fb, res == null ? "<none>" : res.name);
        }));
        f.addView(Ui.labeled(a, "Follow object (also the 3D/raycast camera)", fb));
        EditText hb = num(a, f, "Horizontal border", v0.hborder), vb = num(a, f, "Vertical border", v0.vborder);
        EditText hsp = num(a, f, "Max scroll speed X (-1 = instant)", v0.hspeed), vsp = num(a, f, "Max scroll speed Y (-1 = instant)", v0.vspeed);
        TextView vnote = Ui.text(a, "The view is shown on the whole screen at the game resolution (Game settings). "
                + "Without a view, 2D rooms are scaled to fit the screen.", 12, Ui.DIM);
        vnote.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 6));
        f.addView(vnote);
        fields.add(() -> {
            r.viewsEnabled = views.isChecked();
            v0.visible = true;
            v0.xview = Dialogs.inum(vx, 0);
            v0.yview = Dialogs.inum(vy, 0);
            v0.wview = Math.max(1, Dialogs.inum(vw, p.gameWidth));
            v0.hview = Math.max(1, Dialogs.inum(vh, p.gameHeight));
            v0.xport = 0;
            v0.yport = 0;
            v0.wport = p.gameWidth;
            v0.hport = p.gameHeight;
            v0.follow = follow[0];
            v0.hborder = Dialogs.inum(hb, 200);
            v0.vborder = Dialogs.inum(vb, 150);
            v0.hspeed = Dialogs.inum(hsp, -1);
            v0.vspeed = Dialogs.inum(vsp, -1);
        });

        // ---- raycast
        f.addView(Ui.section(a, "2.5D raycast settings"));
        EditText fov = num(a, f, "Field of view (degrees)", r.rcFov);
        int[] floor = {r.rcFloorColor}, ceil = {r.rcCeilColor}, fogc = {r.rcFogColor};
        f.addView(Ui.labeled(a, "Floor color", colorSwatch(a, floor)));
        f.addView(Ui.labeled(a, "Ceiling color", colorSwatch(a, ceil)));
        int[] ft = {r.rcFloorTex}, ct = {r.rcCeilTex};
        f.addView(Ui.labeled(a, "Floor texture", resButton(a, p, ft)));
        f.addView(Ui.labeled(a, "Ceiling texture", resButton(a, p, ct)));
        EditText fog = num(a, f, "Fog distance (0 = off)", r.rcFogDistance);
        f.addView(Ui.labeled(a, "Fog color", colorSwatch(a, fogc)));
        EditText res = num(a, f, "Render width (pixels)", r.rcResolution);
        TextView rnote = Ui.text(a, "Instances of objects marked “Raycast wall” become walls on the grid (cell = grid width). "
                + "The camera follows the view's follow object (or call rc_camera_follow).", 12, Ui.DIM);
        rnote.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 6));
        f.addView(rnote);
        fields.add(() -> {
            r.rcFov = Dialogs.num(fov, 66);
            r.rcFloorColor = floor[0];
            r.rcCeilColor = ceil[0];
            r.rcFloorTex = ft[0];
            r.rcCeilTex = ct[0];
            r.rcFogDistance = Dialogs.num(fog, 0);
            r.rcFogColor = fogc[0];
            r.rcResolution = Math.max(80, Math.min(1280, Dialogs.inum(res, 320)));
        });

        // ---- 3D
        f.addView(Ui.section(a, "3D settings"));
        EditText d3fov = num(a, f, "Field of view (degrees)", r.d3Fov);
        EditText eye = num(a, f, "Eye height", r.d3EyeHeight);
        Switch dfloor = Ui.toggle(a, r.d3DrawFloor, null);
        f.addView(Ui.labeled(a, "Draw ground", dfloor));
        int[] floorc = {r.d3FloorColor}, ambient = {r.d3Ambient}, fog3c = {r.d3FogColor};
        f.addView(Ui.labeled(a, "Ground color", colorSwatch(a, floorc)));
        int[] gtex = {r.d3FloorTex};
        f.addView(Ui.labeled(a, "Ground texture", resButton(a, p, gtex)));
        Switch light = Ui.toggle(a, r.d3Lighting, null);
        f.addView(Ui.labeled(a, "Lighting", light));
        f.addView(Ui.labeled(a, "Ambient light", colorSwatch(a, ambient)));
        Switch fog3 = Ui.toggle(a, r.d3Fog, null);
        f.addView(Ui.labeled(a, "Fog", fog3));
        f.addView(Ui.labeled(a, "Fog color", colorSwatch(a, fog3c)));
        EditText fs = num(a, f, "Fog start", r.d3FogStart), fe = num(a, f, "Fog end", r.d3FogEnd);
        fields.add(() -> {
            r.d3Fov = Dialogs.num(d3fov, 60);
            r.d3EyeHeight = Dialogs.num(eye, 16);
            r.d3DrawFloor = dfloor.isChecked();
            r.d3FloorColor = floorc[0];
            r.d3FloorTex = gtex[0];
            r.d3Lighting = light.isChecked();
            r.d3Ambient = ambient[0];
            r.d3Fog = fog3.isChecked();
            r.d3FogColor = fog3c[0];
            r.d3FogStart = Dialogs.num(fs, 200);
            r.d3FogEnd = Dialogs.num(fe, 800);
        });

        dlg[0] = Dialogs.builder(a).setTitle(r.name + " settings").setView(Ui.scroll(a, f))
                .setPositiveButton("OK", (d, x) -> {
                    for (Field fl : fields) fl.apply();
                    done.run();
                })
                .setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    private static EditText num(Activity a, LinearLayout f, String label, double v) {
        EditText e = Dialogs.numberField(a, v);
        f.addView(Ui.labeled(a, label, e));
        return e;
    }

    private static String name(Project p, ResType t, int id) {
        Resource r = p.resource(t, id);
        return r == null ? "<none>" : r.name;
    }

    private static View colorSwatch(Activity a, int[] holder) {
        View sw = Ui.swatch(a, 0xFF000000 | holder[0], null);
        sw.setOnClickListener(v -> Dialogs.color(a, 0xFF000000 | holder[0], false, c -> {
            holder[0] = c & 0xFFFFFF;
            sw.setBackground(Ui.roundStroke(0xFF000000 | holder[0], 0xFFFFFFFF, Ui.dp(a, 2), Ui.dp(a, 8)));
        }));
        return sw;
    }

    private static TextView resButton(Activity a, Project p, int[] holder) {
        TextView b = Ui.valueButton(a, name(p, ResType.BACKGROUND, holder[0]), null);
        b.setOnClickListener(v -> Dialogs.resource(a, p, ResType.BACKGROUND, true, res -> {
            holder[0] = res == null ? -1 : res.id;
            Ui.setValue(b, res == null ? "<none>" : res.name);
        }));
        return b;
    }
}
