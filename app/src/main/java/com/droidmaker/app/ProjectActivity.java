package com.droidmaker.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.droidmaker.core.json.Json;
import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Script;
import com.droidmaker.core.project.Sound;
import com.droidmaker.core.project.Sprite;
import com.droidmaker.run.RunnerActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The project tree: resources grouped by type and folders. Tapping opens the matching editor. */
public final class ProjectActivity extends BaseActivity {
    private LinearLayout tree;
    private LinearLayout bar;
    private ScrollView scroller;
    private static final Set<String> expanded = new HashSet<>();

    @Override
    protected void build(Bundle state) {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        bar = Ui.toolbar(this, p.name, true);
        TextView run = Ui.icon(this, "▶", v -> run(-1));
        run.setTextColor(Ui.OK);
        bar.addView(run);
        bar.addView(Ui.icon(this, "⚙", v -> startActivity(new Intent(this, SettingsActivity.class))));
        bar.addView(Ui.icon(this, "⋮", v -> projectMenu()));
        root.addView(bar);
        tree = Ui.col(this);
        tree.setPadding(0, 0, 0, Ui.dp(this, 32));
        scroller = Ui.scroll(this, tree);
        root.addView(scroller, Ui.lpw(Ui.MATCH, 0, 1));
        setContentView(root);
        if (expanded.isEmpty()) for (ResType t : ResType.values()) expanded.add(t.name());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (App.project() != p) { finish(); return; }
        Ui.setToolbarTitle(bar, p.name);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.save();
    }

    private void run(int room) {
        if (!App.save()) return;
        Intent i = new Intent(this, RunnerActivity.class);
        i.putExtra(RunnerActivity.EXTRA_DIR, p.dir.getAbsolutePath());
        if (room >= 0) i.putExtra(RunnerActivity.EXTRA_ROOM, room);
        startActivity(i);
    }

    private void projectMenu() {
        Dialogs.choose(this, p.name, new String[]{"Game settings", "Game start code", "Function reference", "Rename project", "Save now", "Clean unused images"}, w -> {
            switch (w) {
                case 0: startActivity(new Intent(this, SettingsActivity.class)); break;
                case 1: editGlobalCode(); break;
                case 2: startActivity(new Intent(this, ReferenceActivity.class)); break;
                case 3:
                    Dialogs.input(this, "Project name", p.name, "name", false, s -> {
                        if (!s.isEmpty()) { p.name = s; Ui.setToolbarTitle(bar, s); App.save(); }
                    });
                    break;
                case 4: if (App.save()) Dialogs.toast(this, "Saved"); break;
                case 5: ImageIO.cleanUnused(p); Dialogs.toast(this, "Done"); break;
            }
        });
    }

    private void editGlobalCode() {
        App.codeTarget = new App.CodeTarget() {
            public String title() { return "Game start code"; }
            public String get() { return p.globalCode; }
            public void set(String code) { p.globalCode = code; }
        };
        startActivity(new Intent(this, ScriptEditorActivity.class));
    }

    // ================================================================= tree
    private void refresh() {
        int y = scroller.getScrollY();
        tree.removeAllViews();
        for (ResType t : ResType.values()) {
            tree.addView(groupHeader(t));
            if (expanded.contains(t.name())) addFolder(t, "", 1);
        }
        tree.addView(Ui.section(this, "Game"));
        tree.addView(simpleRow("⚙", "Game settings", 0, v -> startActivity(new Intent(this, SettingsActivity.class))));
        tree.addView(simpleRow("{ }", "Game start code", 0, v -> editGlobalCode()));
        tree.addView(simpleRow("?", "Function reference", 0, v -> startActivity(new Intent(this, ReferenceActivity.class))));
        scroller.post(() -> scroller.scrollTo(0, y));
    }

    private View groupHeader(ResType t) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        r.setMinimumHeight(Ui.dp(this, 52));
        r.setBackground(Ui.ripple(new ColorDrawable(Ui.PANEL), 0xFFFFFFFF));
        boolean open = expanded.contains(t.name());
        TextView arrow = Ui.text(this, open ? "▾" : "▸", 16, Ui.DIM);
        arrow.setGravity(Gravity.CENTER);
        r.addView(arrow, Ui.lp(Ui.dp(this, 28), Ui.WRAP));
        TextView title = Ui.text(this, t.title, 16, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(title, Ui.lpw(0, Ui.WRAP, 1));
        TextView count = Ui.text(this, String.valueOf(p.count(t)), 13, Ui.DIM);
        count.setPadding(0, 0, Ui.dp(this, 8), 0);
        r.addView(count);
        TextView add = Ui.icon(this, "+", v -> create(t, ""));
        add.setTextColor(Ui.ACCENT);
        r.addView(add);
        r.setOnClickListener(v -> {
            if (!expanded.remove(t.name())) expanded.add(t.name());
            refresh();
        });
        r.setOnLongClickListener(v -> { newFolder(t, ""); return true; });
        LinearLayout wrap = Ui.col(this);
        wrap.addView(Ui.divider(this));
        wrap.addView(r);
        return wrap;
    }

    private Set<String> childFolders(ResType t, String parent) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> all = new ArrayList<>();
        for (Resource r : p.live(t)) if (!r.folder.isEmpty()) all.add(r.folder);
        for (String f : p.folders) if (f.startsWith(t.name() + ":")) all.add(f.substring(t.name().length() + 1));
        for (String f : all) {
            String rest;
            if (parent.isEmpty()) rest = f;
            else if (f.startsWith(parent + "/")) rest = f.substring(parent.length() + 1);
            else continue;
            if (rest.isEmpty()) continue;
            int slash = rest.indexOf('/');
            out.add(slash < 0 ? rest : rest.substring(0, slash));
        }
        return out;
    }

    private void addFolder(ResType t, String path, int depth) {
        for (String name : childFolders(t, path)) {
            String full = path.isEmpty() ? name : path + "/" + name;
            String key = t.name() + ":" + full;
            boolean open = expanded.contains(key);
            tree.addView(folderRow(t, full, name, depth, open));
            if (open) addFolder(t, full, depth + 1);
        }
        List<Resource> items = new ArrayList<>();
        for (Resource r : p.live(t)) if (r.folder.equals(path)) items.add(r);
        if (t == ResType.ROOM) {
            items.sort((a, b) -> Integer.compare(p.roomOrder.indexOf(a.id), p.roomOrder.indexOf(b.id)));
        } else items.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        for (Resource r : items) tree.addView(resourceRow(r, depth));
        if (depth == 1 && items.isEmpty() && childFolders(t, "").isEmpty()) {
            TextView hint = Ui.text(this, "Tap + to add " + article(t) + ".", 14, Ui.DIM);
            hint.setPadding(Ui.dp(this, 44), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
            tree.addView(hint);
        }
    }

    private static String article(ResType t) {
        switch (t) {
            case SPRITE: return "a sprite";
            case SOUND: return "a sound";
            case BACKGROUND: return "a background";
            case SCRIPT: return "a script";
            case OBJECT: return "an object";
            default: return "a room";
        }
    }

    private View folderRow(ResType t, String full, String name, int depth, boolean open) {
        View row = simpleRow(open ? "📂" : "📁", name, depth, v -> {
            String key = t.name() + ":" + full;
            if (!expanded.remove(key)) expanded.add(key);
            refresh();
        });
        row.setOnLongClickListener(v -> {
            Dialogs.choose(this, name, new String[]{"New " + t.title.toLowerCase().replaceAll("s$", "") + " here", "New subfolder", "Rename folder", "Delete folder (keeps contents)"}, w -> {
                switch (w) {
                    case 0: create(t, full); break;
                    case 1: newFolder(t, full); break;
                    case 2: renameFolder(t, full); break;
                    case 3: deleteFolder(t, full); break;
                }
            });
            return true;
        });
        return row;
    }

    private View simpleRow(String glyph, String label, int depth, View.OnClickListener l) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12 + depth * 18), 0, Ui.dp(this, 12), 0);
        r.setMinimumHeight(Ui.dp(this, 48));
        r.setBackground(Ui.ripple(new ColorDrawable(0), 0xFFFFFFFF));
        TextView g = Ui.text(this, glyph, 16, Ui.DIM);
        g.setGravity(Gravity.CENTER);
        r.addView(g, Ui.lp(Ui.dp(this, 36), Ui.WRAP));
        TextView t = Ui.text(this, label, 15, Ui.TEXT);
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        r.addView(t, Ui.lpw(0, Ui.WRAP, 1));
        r.setOnClickListener(l);
        return r;
    }

    private View resourceRow(Resource res, int depth) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12 + depth * 18), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        r.setMinimumHeight(Ui.dp(this, 52));
        r.setBackground(Ui.ripple(new ColorDrawable(0), 0xFFFFFFFF));
        int s = Ui.dp(this, 36);
        Bitmap b = Dialogs.thumb(p, res);
        if (b != null) {
            ImageView iv = new ImageView(this);
            BitmapDrawable bd = new BitmapDrawable(getResources(), b);
            bd.setFilterBitmap(false);
            iv.setImageDrawable(bd);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackground(Ui.round(Ui.PANEL2, Ui.dp(this, 6)));
            r.addView(iv, Ui.lp(s, s));
        } else {
            TextView g = Ui.text(this, glyph(res), 17, Ui.categoryColor(cat(res)));
            g.setGravity(Gravity.CENTER);
            g.setBackground(Ui.round(Ui.PANEL2, Ui.dp(this, 6)));
            r.addView(g, Ui.lp(s, s));
        }
        LinearLayout texts = Ui.col(this);
        texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
        texts.addView(Ui.text(this, res.name, 15, Ui.TEXT));
        String sub = subtitle(res);
        if (!sub.isEmpty()) texts.addView(Ui.text(this, sub, 12, Ui.DIM));
        r.addView(texts, Ui.lpw(0, Ui.WRAP, 1));
        r.setOnClickListener(v -> openEditor(res));
        r.setOnLongClickListener(v -> { resourceMenu(res); return true; });
        return r;
    }

    private static String glyph(Resource r) {
        switch (r.type()) {
            case SOUND: return "♪";
            case SCRIPT: return "ƒ";
            case ROOM: return "▦";
            case OBJECT: return "◆";
            case BACKGROUND: return "▤";
            default: return "▣";
        }
    }

    private static String cat(Resource r) {
        switch (r.type()) {
            case SOUND: return "Main";
            case SCRIPT: return "Control";
            case ROOM: return "Game";
            case OBJECT: return "Move";
            default: return "Draw";
        }
    }

    private String subtitle(Resource r) {
        if (r instanceof Sprite) { Sprite s = (Sprite) r; return s.width + "×" + s.height + " · " + s.frames.size() + " frame" + (s.frames.size() == 1 ? "" : "s"); }
        if (r instanceof Background) { Background b = (Background) r; return b.file == null ? "empty" : b.width + "×" + b.height + (b.tileset ? " · tileset" : ""); }
        if (r instanceof Sound) { Sound s = (Sound) r; return (s.kind == Sound.KIND_MUSIC ? "music" : "effect") + (s.file == null ? " · empty" : ""); }
        if (r instanceof Script) { String c = ((Script) r).code; int lines = c.isEmpty() ? 0 : c.split("\n").length; return lines + " lines"; }
        if (r instanceof GameObject) {
            GameObject o = (GameObject) r;
            StringBuilder b = new StringBuilder(o.events.size() + " events");
            if (o.solid) b.append(" · solid");
            if (o.parent >= 0 && p.object(o.parent) != null) b.append(" · parent ").append(p.object(o.parent).name);
            return b.toString();
        }
        if (r instanceof Room) {
            Room rm = (Room) r;
            String s = Room.MODE_NAMES[rm.mode] + " · " + rm.width + "×" + rm.height + " · " + rm.instances.size() + " instances";
            if (p.startRoom() == rm.id) s = "START · " + s;
            return s;
        }
        return "";
    }

    private void openEditor(Resource r) {
        Class<?> c;
        switch (r.type()) {
            case SPRITE: case BACKGROUND: c = SpriteEditorActivity.class; break;
            case SOUND: c = SoundEditorActivity.class; break;
            case SCRIPT:
                Script sc = (Script) r;
                App.codeTarget = new App.CodeTarget() {
                    public String title() { return sc.name; }
                    public String get() { return sc.code; }
                    public void set(String code) { sc.code = code; }
                };
                c = ScriptEditorActivity.class;
                break;
            case OBJECT: c = ObjectEditorActivity.class; break;
            default: c = RoomEditorActivity.class;
        }
        Intent i = new Intent(this, c);
        i.putExtra("id", r.id);
        i.putExtra("type", r.type().name());
        startActivity(i);
    }

    private void create(ResType t, String folder) {
        Resource r = p.create(t);
        r.folder = folder;
        if (t == ResType.ROOM && p.count(ResType.ROOM) == 1) ((Room) r).name = "rm_start";
        App.save();
        refresh();
        openEditor(r);
    }

    private void resourceMenu(Resource r) {
        List<String> items = new ArrayList<>();
        Collections.addAll(items, "Open", "Rename", "Duplicate", "Move to folder", "Delete");
        if (r instanceof Room) Collections.addAll(items, "Play from this room", "Make start room", "Move up in room order", "Move down in room order");
        Dialogs.choose(this, r.name, items.toArray(new String[0]), w -> {
            switch (items.get(w)) {
                case "Open": openEditor(r); break;
                case "Rename": Dialogs.name(this, p, "Rename", r.name, r, s -> { p.rename(r, s); App.save(); refresh(); }); break;
                case "Duplicate": duplicate(r); break;
                case "Move to folder": moveToFolder(r); break;
                case "Delete":
                    Dialogs.confirm(this, "Delete " + r.name + "?", deleteWarning(r), "Delete", () -> { p.delete(r); App.save(); refresh(); });
                    break;
                case "Play from this room": run(r.id); break;
                case "Make start room":
                    p.roomOrder.remove(Integer.valueOf(r.id));
                    p.roomOrder.add(0, r.id);
                    App.save(); refresh();
                    break;
                case "Move up in room order": moveRoom(r.id, -1); break;
                case "Move down in room order": moveRoom(r.id, 1); break;
            }
        });
    }

    private String deleteWarning(Resource r) {
        if (r instanceof GameObject) return "All its instances in rooms and collision events with it will also be removed.";
        if (r instanceof Sprite) return "Objects using it will have no sprite.";
        return "This cannot be undone.";
    }

    private void moveRoom(int id, int dir) {
        int k = p.roomOrder.indexOf(id), j = k + dir;
        if (k < 0 || j < 0 || j >= p.roomOrder.size()) return;
        Collections.swap(p.roomOrder, k, j);
        App.save();
        refresh();
    }

    private void duplicate(Resource r) {
        Resource copy;
        switch (r.type()) {
            case SPRITE: copy = new Sprite(); break;
            case SOUND: copy = new Sound(); break;
            case BACKGROUND: copy = new Background(); break;
            case SCRIPT: copy = new Script(); break;
            case OBJECT: copy = new GameObject(); break;
            default: copy = new Room();
        }
        copy.fromJson(Json.obj(Json.parse(Json.writeCompact(r.toJson()))));
        copy.name = p.uniqueName(r.name + "_copy");
        p.add(copy);
        App.save();
        refresh();
    }

    private void moveToFolder(Resource r) {
        ResType t = r.type();
        List<String> folders = new ArrayList<>();
        collectFolders(t, "", folders);
        List<String> items = new ArrayList<>();
        items.add("(top level)");
        items.addAll(folders);
        items.add("+ New folder…");
        Dialogs.choose(this, "Move " + r.name + " to", items.toArray(new String[0]), w -> {
            if (w == 0) r.folder = "";
            else if (w == items.size() - 1) {
                Dialogs.input(this, "New folder", "", "folder name", false, s -> {
                    s = s.replace("/", "_").trim();
                    if (s.isEmpty()) return;
                    r.folder = s;
                    expanded.add(t.name() + ":" + s);
                    App.save();
                    refresh();
                });
                return;
            } else r.folder = items.get(w);
            App.save();
            refresh();
        });
    }

    private void collectFolders(ResType t, String path, List<String> out) {
        for (String n : childFolders(t, path)) {
            String full = path.isEmpty() ? n : path + "/" + n;
            out.add(full);
            collectFolders(t, full, out);
        }
    }

    private void newFolder(ResType t, String parent) {
        Dialogs.input(this, "New folder", "", "folder name", false, s -> {
            s = s.replace("/", "_").trim();
            if (s.isEmpty()) return;
            String full = parent.isEmpty() ? s : parent + "/" + s;
            String key = t.name() + ":" + full;
            if (!p.folders.contains(key)) p.folders.add(key);
            expanded.add(t.name());
            expanded.add(key);
            App.save();
            refresh();
        });
    }

    private void renameFolder(ResType t, String full) {
        String name = full.substring(full.lastIndexOf('/') + 1);
        String parent = full.contains("/") ? full.substring(0, full.lastIndexOf('/')) : "";
        Dialogs.input(this, "Rename folder", name, "folder name", false, s -> {
            s = s.replace("/", "_").trim();
            if (s.isEmpty()) return;
            String nf = parent.isEmpty() ? s : parent + "/" + s;
            for (Resource r : p.live(t)) {
                if (r.folder.equals(full)) r.folder = nf;
                else if (r.folder.startsWith(full + "/")) r.folder = nf + r.folder.substring(full.length());
            }
            String pre = t.name() + ":";
            for (int i = 0; i < p.folders.size(); i++) {
                String f = p.folders.get(i);
                if (!f.startsWith(pre)) continue;
                String path = f.substring(pre.length());
                if (path.equals(full)) p.folders.set(i, pre + nf);
                else if (path.startsWith(full + "/")) p.folders.set(i, pre + nf + path.substring(full.length()));
            }
            expanded.add(pre + nf);
            App.save();
            refresh();
        });
    }

    private void deleteFolder(ResType t, String full) {
        String parent = full.contains("/") ? full.substring(0, full.lastIndexOf('/')) : "";
        for (Resource r : p.live(t)) {
            if (r.folder.equals(full)) r.folder = parent;
            else if (r.folder.startsWith(full + "/")) r.folder = parent + (parent.isEmpty() ? "" : "/") + r.folder.substring(full.length() + 1);
        }
        String pre = t.name() + ":";
        p.folders.removeIf(f -> f.equals(pre + full) || f.startsWith(pre + full + "/"));
        App.save();
        refresh();
    }
}
