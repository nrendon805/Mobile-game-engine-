package com.droidmaker.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.project.Room;
import com.droidmaker.run.RunnerActivity;

import java.util.ArrayList;
import java.util.List;

/** Room editor: paint objects and tiles onto a zoomable room, and edit room, view, raycast and 3D settings. */
public final class RoomEditorActivity extends BaseActivity implements RoomView.Listener {
    private Room room;
    private RoomView view;
    private LinearLayout bar, toolRow, palette;
    private final List<TextView> toolChips = new ArrayList<>(), modeChips = new ArrayList<>();
    private final List<Object[]> undo = new ArrayList<>(), redo = new ArrayList<>();

    @Override
    protected void build(Bundle state) {
        room = p.room(getIntent().getIntExtra("id", -1));
        if (room == null) { finish(); return; }
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        bar = Ui.toolbar(this, room.name, true);
        bar.findViewWithTag("title").setOnClickListener(v -> Dialogs.name(this, p, "Rename room", room.name, room, s -> {
            p.rename(room, s);
            Ui.setToolbarTitle(bar, s);
        }));
        TextView play = Ui.icon(this, "▶", v -> play());
        play.setTextColor(Ui.OK);
        bar.addView(play);
        bar.addView(Ui.icon(this, "↶", v -> undo()));
        bar.addView(Ui.icon(this, "↷", v -> redo()));
        bar.addView(Ui.icon(this, "⚙", v -> settings()));
        bar.addView(Ui.icon(this, "⋮", v -> menu()));
        root.addView(bar);

        toolRow = Ui.row(this);
        toolRow.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        HorizontalScrollView th = Ui.hscroll(this, toolRow);
        th.setBackgroundColor(Ui.PANEL);
        root.addView(th);

        view = new RoomView(this, p, room);
        view.setListener(this);
        root.addView(view, Ui.lpw(Ui.MATCH, 0, 1));

        palette = Ui.row(this);
        palette.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        HorizontalScrollView ph = Ui.hscroll(this, palette);
        ph.setBackgroundColor(Ui.PANEL2);
        root.addView(ph, Ui.lp(Ui.MATCH, Ui.dp(this, 84)));
        setContentView(root);

        for (Resource r : p.live(ResType.OBJECT)) { view.object = r.id; break; }
        buildTools();
        buildPalette();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) { buildPalette(); view.invalidate(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.save();
    }

    private void play() {
        if (!App.save()) return;
        Intent i = new Intent(this, RunnerActivity.class);
        i.putExtra(RunnerActivity.EXTRA_DIR, p.dir.getAbsolutePath());
        i.putExtra(RunnerActivity.EXTRA_ROOM, room.id);
        startActivity(i);
    }

    // ================================================================= tools
    private void buildTools() {
        toolRow.removeAllViews();
        toolChips.clear();
        modeChips.clear();
        TextView objs = Ui.chip(this, "Objects", view.mode == RoomView.Mode.OBJECTS, v -> setMode(RoomView.Mode.OBJECTS));
        TextView tiles = Ui.chip(this, "Tiles", view.mode == RoomView.Mode.TILES, v -> setMode(RoomView.Mode.TILES));
        modeChips.add(objs);
        modeChips.add(tiles);
        toolRow.addView(objs);
        toolRow.addView(tiles);
        View sep = new View(this);
        sep.setBackgroundColor(Ui.LINE);
        toolRow.addView(sep, Ui.lp(Ui.dp(this, 1), Ui.dp(this, 28)));
        addTool("✏ Paint", RoomView.Tool.PAINT);
        addTool("⬚ Select", RoomView.Tool.SELECT);
        addTool("⌫ Erase", RoomView.Tool.ERASE);
        addTool("✋ Pan", RoomView.Tool.PAN);
        TextView snap = Ui.chip(this, "Snap", view.snap, null);
        snap.setOnClickListener(v -> { view.snap = !view.snap; Ui.setChip(this, snap, view.snap); });
        toolRow.addView(snap);
        TextView grid = Ui.chip(this, "Grid", view.grid, null);
        grid.setOnClickListener(v -> { view.grid = !view.grid; Ui.setChip(this, grid, view.grid); view.invalidate(); });
        toolRow.addView(grid);
        toolRow.addView(Ui.chip(this, "Fit", false, v -> view.fit()));
    }

    private void addTool(String label, RoomView.Tool t) {
        TextView c = Ui.chip(this, label, view.tool == t, null);
        c.setTag(t);
        c.setOnClickListener(v -> {
            view.tool = t;
            for (TextView x : toolChips) Ui.setChip(this, x, x.getTag() == t);
        });
        toolChips.add(c);
        toolRow.addView(c);
    }

    private void setMode(RoomView.Mode m) {
        view.mode = m;
        Ui.setChip(this, modeChips.get(0), m == RoomView.Mode.OBJECTS);
        Ui.setChip(this, modeChips.get(1), m == RoomView.Mode.TILES);
        view.select(null);
        buildPalette();
        view.invalidate();
    }

    private void buildPalette() {
        palette.removeAllViews();
        if (view.mode == RoomView.Mode.OBJECTS) {
            List<Resource> objs = p.live(ResType.OBJECT);
            if (objs.isEmpty()) {
                TextView t = Ui.text(this, "No objects yet — create one in the project tree.", 14, Ui.DIM);
                t.setPadding(Ui.dp(this, 12), 0, 0, 0);
                t.setGravity(Gravity.CENTER_VERTICAL);
                palette.addView(t, Ui.lp(Ui.WRAP, Ui.MATCH));
                return;
            }
            for (Resource r : objs) palette.addView(objectTile((GameObject) r));
        } else {
            Background bg = p.background(view.tileBg);
            TextView pick = Ui.button(this, bg == null ? "Choose tileset" : bg.name, v -> Dialogs.resource(this, p, ResType.BACKGROUND, false, r -> {
                if (r == null) return;
                Background b = (Background) r;
                view.tileBg = b.id;
                view.tileW = b.tileset ? b.tileWidth : b.width;
                view.tileH = b.tileset ? b.tileHeight : b.height;
                view.tileSx = 0;
                view.tileSy = 0;
                buildPalette();
                view.invalidate();
            }));
            palette.addView(pick, Ui.lp(Ui.WRAP, Ui.MATCH));
            TextView depth = Ui.button(this, "Depth " + view.tileDepth, v -> Dialogs.input(this, "Tile depth", String.valueOf(view.tileDepth), "", true, s -> {
                try { view.tileDepth = Integer.parseInt(s.trim()); } catch (Exception ignored) { /* ignore */ }
                buildPalette();
            }));
            LinearLayout.LayoutParams dl = Ui.lp(Ui.WRAP, Ui.MATCH);
            dl.leftMargin = Ui.dp(this, 6);
            palette.addView(depth, dl);
            if (bg != null) palette.addView(tilePicker(bg));
        }
    }

    private View objectTile(GameObject o) {
        LinearLayout t = Ui.col(this);
        t.setGravity(Gravity.CENTER);
        boolean sel = view.object == o.id;
        t.setBackground(Ui.ripple(Ui.roundStroke(sel ? 0xFF2D4A6E : Ui.PANEL3, sel ? Ui.ACCENT : Ui.LINE, Ui.dp(this, sel ? 2 : 1), Ui.dp(this, 8)), 0));
        ImageView iv = new ImageView(this);
        Bitmap b = ImageIO.objectImage(p, o);
        if (b != null) {
            BitmapDrawable bd = new BitmapDrawable(getResources(), b);
            bd.setFilterBitmap(false);
            iv.setImageDrawable(bd);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(iv, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView n = Ui.text(this, o.name, 10, Ui.TEXT);
        n.setSingleLine(true);
        n.setGravity(Gravity.CENTER);
        t.addView(n, Ui.lp(Ui.dp(this, 72), Ui.WRAP));
        t.setOnClickListener(v -> {
            view.object = o.id;
            if (view.tool != RoomView.Tool.PAINT) {
                view.tool = RoomView.Tool.PAINT;
                for (TextView x : toolChips) Ui.setChip(this, x, x.getTag() == RoomView.Tool.PAINT);
            }
            buildPalette();
        });
        LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 76), Ui.MATCH);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        t.setLayoutParams(lp);
        return t;
    }

    /** Shows the tileset scaled to the palette height; tapping selects a tile. */
    private View tilePicker(Background bg) {
        Bitmap img = ImageIO.background(p, bg);
        if (img == null) return Ui.text(this, "  (empty background)", 13, Ui.DIM);
        int ph = Ui.dp(this, 76);
        float s = Math.max(1, Math.min(4, (float) ph / Math.max(1, img.getHeight()) * 2));
        int w = Math.round(img.getWidth() * s), h = Math.round(img.getHeight() * s);
        Bitmap shown = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(shown);
        Paint pt = new Paint();
        pt.setFilterBitmap(false);
        c.drawBitmap(img, new android.graphics.Rect(0, 0, img.getWidth(), img.getHeight()), new android.graphics.Rect(0, 0, w, h), pt);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(3);
        pt.setColor(0xFFFFB547);
        c.drawRect(view.tileSx * s, view.tileSy * s, (view.tileSx + view.tileW) * s, (view.tileSy + view.tileH) * s, pt);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(shown);
        iv.setScaleType(ImageView.ScaleType.FIT_START);
        float sc = s;
        iv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                // the image view is shown at height ph; map touch back to tileset pixels
                float k = (float) h / Math.max(1, v.getHeight());
                int tx = (int) (e.getX() * k / sc), ty = (int) (e.getY() * k / sc);
                view.tileSx = (tx / view.tileW) * view.tileW;
                view.tileSy = (ty / view.tileH) * view.tileH;
                view.tool = RoomView.Tool.PAINT;
                buildPalette();
            }
            return true;
        });
        float k = (float) ph / h;
        LinearLayout.LayoutParams lp = Ui.lp(Math.round(w * k), ph);
        lp.leftMargin = Ui.dp(this, 8);
        iv.setLayoutParams(lp);
        return iv;
    }

    // ================================================================= undo
    private Object[] snapshot() {
        List<Room.Inst> is = new ArrayList<>();
        for (Room.Inst i : room.instances) is.add(i.copy());
        List<Room.Tile> ts = new ArrayList<>();
        for (Room.Tile t : room.tiles) ts.add(t.copy());
        return new Object[]{is, ts};
    }

    @SuppressWarnings("unchecked")
    private void apply(Object[] s) {
        room.instances.clear();
        room.instances.addAll((List<Room.Inst>) s[0]);
        room.tiles.clear();
        room.tiles.addAll((List<Room.Tile>) s[1]);
        view.select(null);
        view.invalidate();
    }

    @Override public void beforeChange() {
        undo.add(snapshot());
        if (undo.size() > 60) undo.remove(0);
        redo.clear();
    }

    @Override public void changed() { App.save(); }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.add(snapshot());
        apply(undo.remove(undo.size() - 1));
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.add(snapshot());
        apply(redo.remove(redo.size() - 1));
    }

    @Override public void selected(Room.Inst inst) { }

    @Override public void instanceMenu(Room.Inst inst) { instanceProperties(inst); }

    // ================================================================= instance properties
    private void instanceProperties(Room.Inst inst) {
        GameObject o = p.object(inst.object);
        LinearLayout f = Ui.col(this);
        EditText x = Dialogs.numberField(this, inst.x), y = Dialogs.numberField(this, inst.y);
        EditText xs = Dialogs.numberField(this, inst.xscale), ys = Dialogs.numberField(this, inst.yscale), a = Dialogs.numberField(this, inst.angle);
        f.addView(Ui.labeled(this, "X", x));
        f.addView(Ui.labeled(this, "Y", y));
        f.addView(Ui.labeled(this, "X scale", xs));
        f.addView(Ui.labeled(this, "Y scale", ys));
        f.addView(Ui.labeled(this, "Rotation", a));
        LinearLayout btns = Ui.row(this);
        btns.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        btns.addView(Ui.button(this, "Creation code" + (inst.code == null || inst.code.isEmpty() ? "" : " ✓"), v -> {
            dlg[0].dismiss();
            App.codeTarget = new App.CodeTarget() {
                public String title() { return (o == null ? "instance" : o.name) + " creation code"; }
                public String get() { return inst.code; }
                public void set(String code) { inst.code = code; }
            };
            startActivity(new Intent(this, ScriptEditorActivity.class));
        }), Ui.lpw(0, Ui.WRAP, 1));
        f.addView(btns);
        LinearLayout btns2 = Ui.row(this);
        btns2.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        btns2.addView(Ui.button(this, "Duplicate", v -> {
            beforeChange();
            Room.Inst c = inst.copy();
            c.id = room.newInstanceId();
            c.x += room.gridX;
            room.instances.add(c);
            view.select(c);
            dlg[0].dismiss();
            App.save();
        }), Ui.lpw(0, Ui.WRAP, 1));
        View gap = new View(this);
        btns2.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        btns2.addView(Ui.button(this, "Delete", Ui.DANGER, v -> {
            beforeChange();
            room.instances.remove(inst);
            view.select(null);
            dlg[0].dismiss();
            App.save();
        }), Ui.lpw(0, Ui.WRAP, 1));
        f.addView(btns2);
        dlg[0] = Dialogs.builder(this).setTitle(o == null ? "Instance" : o.name).setView(Ui.scroll(this, f))
                .setPositiveButton("OK", (d, w) -> {
                    beforeChange();
                    inst.x = Dialogs.num(x, inst.x);
                    inst.y = Dialogs.num(y, inst.y);
                    inst.xscale = Dialogs.num(xs, 1);
                    inst.yscale = Dialogs.num(ys, 1);
                    inst.angle = Dialogs.num(a, 0);
                    view.invalidate();
                    App.save();
                })
                .setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    private void menu() {
        Dialogs.choose(this, room.name, new String[]{"Room settings", "Creation code", "Clear all instances", "Clear all tiles", "Shift everything…"}, w -> {
            switch (w) {
                case 0: settings(); break;
                case 1: creationCode(); break;
                case 2: Dialogs.confirm(this, "Clear instances?", "Removes all " + room.instances.size() + " instances (undo is available).", "Clear", () -> {
                    beforeChange(); room.instances.clear(); view.select(null); view.invalidate(); App.save();
                }); break;
                case 3: Dialogs.confirm(this, "Clear tiles?", "Removes all " + room.tiles.size() + " tiles.", "Clear", () -> {
                    beforeChange(); room.tiles.clear(); view.invalidate(); App.save();
                }); break;
                case 4: {
                    LinearLayout f = Ui.col(this);
                    EditText dx = Dialogs.numberField(this, room.gridX), dy = Dialogs.numberField(this, 0);
                    f.addView(Ui.labeled(this, "Move X by", dx));
                    f.addView(Ui.labeled(this, "Move Y by", dy));
                    Dialogs.builder(this).setTitle("Shift everything").setView(f).setPositiveButton("OK", (d, x) -> {
                        beforeChange();
                        double sx = Dialogs.num(dx, 0), sy = Dialogs.num(dy, 0);
                        for (Room.Inst i : room.instances) { i.x += sx; i.y += sy; }
                        for (Room.Tile t : room.tiles) { t.x += sx; t.y += sy; }
                        view.invalidate();
                        App.save();
                    }).setNegativeButton("Cancel", null).show();
                    break;
                }
            }
        });
    }

    private void creationCode() {
        App.codeTarget = new App.CodeTarget() {
            public String title() { return room.name + " creation code"; }
            public String get() { return room.creationCode; }
            public void set(String code) { room.creationCode = code; }
        };
        startActivity(new Intent(this, ScriptEditorActivity.class));
    }

    // ================================================================= room settings
    private void settings() {
        RoomSettings.show(this, p, room, () -> {
            view.fit();
            view.invalidate();
            App.save();
        }, this::creationCode);
    }
}
