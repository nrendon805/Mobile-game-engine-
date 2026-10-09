package com.droidmaker.app;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.project.Action;
import com.droidmaker.core.project.ActionDef;
import com.droidmaker.core.project.ActionLibrary;
import com.droidmaker.core.project.Checker;
import com.droidmaker.core.project.CodeGen;
import com.droidmaker.core.project.Ev;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Keys;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.project.Sprite;

import java.util.ArrayList;
import java.util.List;

/** Object editor: events, their drag-and-drop action lists, the action palette and object properties. */
public final class ObjectEditorActivity extends BaseActivity implements ActionListView.Listener {
    private GameObject obj;
    private int eventKey = -1;
    private LinearLayout bar, eventRow, actionBar, tiles, catRow;
    private ActionListView list;
    private ScrollView listScroll;
    private ImageView spriteIcon;
    private TextView spriteLabel, trash;
    private String category = "Move";
    private boolean paletteOpen = true;
    private View palette;
    private static final List<Action> clipboard = new ArrayList<>();
    private static List<Action> eventClipboard;

    @Override
    protected void build(Bundle state) {
        obj = p.object(getIntent().getIntExtra("id", -1));
        if (obj == null) { finish(); return; }
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        bar = Ui.toolbar(this, obj.name, true);
        bar.findViewWithTag("title").setOnClickListener(v -> rename());
        bar.addView(Ui.icon(this, "⚙", v -> properties()));
        bar.addView(Ui.icon(this, "⋮", v -> menu()));
        root.addView(bar);

        // sprite summary row
        LinearLayout sum = Ui.row(this);
        sum.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        sum.setBackgroundColor(Ui.PANEL);
        spriteIcon = new ImageView(this);
        spriteIcon.setBackground(Ui.round(Ui.PANEL3, Ui.dp(this, 6)));
        spriteIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sum.addView(spriteIcon, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        spriteLabel = Ui.text(this, "", 14, Ui.TEXT);
        spriteLabel.setPadding(Ui.dp(this, 10), 0, 0, 0);
        sum.addView(spriteLabel, Ui.lpw(0, Ui.WRAP, 1));
        sum.addView(Ui.button(this, "Sprite", v -> Dialogs.resource(this, p, ResType.SPRITE, true, r -> {
            obj.sprite = r == null ? -1 : r.id;
            refreshSummary();
        })));
        root.addView(sum);

        // events
        eventRow = Ui.row(this);
        eventRow.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        HorizontalScrollView es = Ui.hscroll(this, eventRow);
        es.setBackgroundColor(Ui.PANEL);
        root.addView(es, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(Ui.divider(this));

        // action list
        list = new ActionListView(this, p);
        list.setListener(this);
        listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.addView(list, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(listScroll, Ui.lpw(Ui.MATCH, 0, 1));

        // action toolbar (for the selected action)
        actionBar = Ui.row(this);
        actionBar.setBackgroundColor(Ui.PANEL2);
        actionBar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        root.addView(actionBar, Ui.lp(Ui.MATCH, Ui.dp(this, 48)));

        // palette
        LinearLayout pal = Ui.col(this);
        pal.setBackgroundColor(Ui.PANEL);
        catRow = Ui.row(this);
        catRow.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        LinearLayout catBar = Ui.row(this);
        TextView toggle = Ui.icon(this, "▾", null);
        toggle.setOnClickListener(v -> {
            paletteOpen = !paletteOpen;
            palette.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
            toggle.setText(paletteOpen ? "▾" : "▴");
        });
        catBar.addView(Ui.hscroll(this, catRow), Ui.lpw(0, Ui.WRAP, 1));
        catBar.addView(toggle);
        catBar.setBackgroundColor(Ui.PANEL);
        root.addView(catBar);
        tiles = Ui.row(this);
        tiles.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 6));
        HorizontalScrollView ts = Ui.hscroll(this, tiles);
        pal.addView(ts);
        palette = pal;
        root.addView(pal);
        setContentView(root);

        trash = Ui.text(this, "🗑  Drop here to delete", 15, 0xFFFFFFFF);
        trash.setGravity(Gravity.CENTER);
        trash.setBackgroundColor(0xFF8A2A2A);
        trash.setVisibility(View.GONE);
        trash.setOnDragListener(this::trashDrag);
        actionBar.setOnDragListener(this::trashDrag);

        if (!obj.events.isEmpty()) eventKey = sortedKeys().get(0);
        refreshSummary();
        refreshEvents();
        refreshCategories();
        refreshActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (obj != null) { list.recompute(); refreshSummary(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.save();
    }

    // ================================================================= summary & properties
    private void refreshSummary() {
        Sprite s = p.sprite(obj.sprite);
        Bitmap b = ImageIO.objectImage(p, obj);
        if (b != null) {
            BitmapDrawable bd = new BitmapDrawable(getResources(), b);
            bd.setFilterBitmap(false);
            spriteIcon.setImageDrawable(bd);
        } else spriteIcon.setImageDrawable(null);
        StringBuilder t = new StringBuilder(s == null ? "No sprite" : s.name);
        if (obj.solid) t.append(" · solid");
        if (!obj.visible) t.append(" · invisible");
        if (obj.persistent) t.append(" · persistent");
        if (obj.depth != 0) t.append(" · depth ").append(obj.depth);
        GameObject par = p.object(obj.parent);
        if (par != null) t.append(" · parent ").append(par.name);
        spriteLabel.setText(t);
    }

    private void rename() {
        Dialogs.name(this, p, "Rename object", obj.name, obj, s -> {
            p.rename(obj, s);
            Ui.setToolbarTitle(bar, s);
            App.save();
        });
    }

    private void properties() {
        LinearLayout f = Ui.col(this);
        Switch vis = Ui.toggle(this, obj.visible, null), sol = Ui.toggle(this, obj.solid, null), per = Ui.toggle(this, obj.persistent, null);
        EditText depth = Dialogs.numberField(this, obj.depth);
        int[] parent = {obj.parent}, mask = {obj.mask}, shape = {obj.shape3d};
        TextView parentB = Ui.valueButton(this, nameOf(ResType.OBJECT, obj.parent), null);
        parentB.setOnClickListener(v -> Dialogs.resource(this, p, ResType.OBJECT, true, r -> {
            if (r != null && (r == obj || isDescendant((GameObject) r))) { Dialogs.toast(this, "That would make a loop of parents"); return; }
            parent[0] = r == null ? -1 : r.id;
            Ui.setValue(parentB, nameOf(ResType.OBJECT, parent[0]));
        }));
        TextView maskB = Ui.valueButton(this, mask[0] < 0 ? "<same as sprite>" : nameOf(ResType.SPRITE, mask[0]), null);
        maskB.setOnClickListener(v -> Dialogs.resource(this, p, ResType.SPRITE, true, r -> {
            mask[0] = r == null ? -1 : r.id;
            Ui.setValue(maskB, mask[0] < 0 ? "<same as sprite>" : r.name);
        }));
        TextView shapeB = Ui.valueButton(this, GameObject.SHAPE_NAMES[shape[0]], null);
        shapeB.setOnClickListener(v -> Dialogs.choose(this, "Look in 3D rooms", GameObject.SHAPE_NAMES, w -> { shape[0] = w; Ui.setValue(shapeB, GameObject.SHAPE_NAMES[w]); }));
        EditText h3 = Dialogs.numberField(this, obj.height3d);
        Switch rcw = Ui.toggle(this, obj.rcWall, null);
        f.addView(Ui.section(this, "Object"));
        f.addView(Ui.labeled(this, "Visible", vis));
        f.addView(Ui.labeled(this, "Solid", sol));
        f.addView(Ui.labeled(this, "Persistent (survives room changes)", per));
        f.addView(Ui.labeled(this, "Depth (higher = behind)", depth));
        f.addView(Ui.labeled(this, "Parent", parentB));
        f.addView(Ui.labeled(this, "Collision mask", maskB));
        f.addView(Ui.section(this, "3D and raycast"));
        f.addView(Ui.labeled(this, "Shape in 3D rooms", shapeB));
        f.addView(Ui.labeled(this, "3D block height (0 = grid)", h3));
        f.addView(Ui.labeled(this, "Raycast wall", rcw));
        TextView note = Ui.text(this, "Raycast walls fill their grid cell in 2.5D rooms using the sprite as wall texture. "
                + "Other instances with sprites appear as billboards.", 12, Ui.DIM);
        note.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        f.addView(note);
        Dialogs.builder(this).setTitle(obj.name + " properties").setView(Ui.scroll(this, f))
                .setPositiveButton("OK", (d, w) -> {
                    obj.visible = vis.isChecked();
                    obj.solid = sol.isChecked();
                    obj.persistent = per.isChecked();
                    obj.depth = Dialogs.inum(depth, obj.depth);
                    obj.parent = parent[0];
                    obj.mask = mask[0];
                    obj.shape3d = shape[0];
                    obj.height3d = Dialogs.num(h3, 0);
                    obj.rcWall = rcw.isChecked();
                    refreshSummary();
                    App.save();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private boolean isDescendant(GameObject o) {
        int guard = 0;
        GameObject cur = o;
        while (cur != null && guard++ < 64) {
            if (cur.parent == obj.id) return true;
            cur = p.object(cur.parent);
        }
        return false;
    }

    private String nameOf(ResType t, int id) {
        Resource r = p.resource(t, id);
        return r == null ? "<none>" : r.name;
    }

    // ================================================================= events
    private List<Integer> sortedKeys() {
        List<Integer> keys = new ArrayList<>(obj.events.keySet());
        keys.sort((a, b) -> Integer.compare(Ev.sortKey(a), Ev.sortKey(b)));
        return keys;
    }

    private void refreshEvents() {
        eventRow.removeAllViews();
        for (int k : sortedKeys()) {
            String name = Ev.name(Ev.typeOf(k), Ev.numOf(k), p);
            int n = obj.events.get(k).size();
            TextView c = Ui.chip(this, name + (n > 0 ? "  " + n : ""), k == eventKey, v -> { eventKey = k; refreshEvents(); refreshActions(); });
            c.setOnLongClickListener(v -> { eventMenu(k); return true; });
            eventRow.addView(c);
        }
        TextView add = Ui.chip(this, "+ Event", false, v -> EventPicker.show(this, p, (t, num) -> addEvent(Ev.key(t, num))));
        add.setTextColor(Ui.ACCENT);
        eventRow.addView(add);
    }

    private void addEvent(int key) {
        if (!obj.events.containsKey(key)) obj.addEvent(Ev.typeOf(key), Ev.numOf(key));
        eventKey = key;
        refreshEvents();
        refreshActions();
        App.save();
    }

    private void eventMenu(int k) {
        String name = Ev.name(Ev.typeOf(k), Ev.numOf(k), p);
        Dialogs.choose(this, name, new String[]{"Change event type", "Copy event", "Paste actions into this event", "Show generated code", "Check for errors", "Delete event"}, w -> {
            switch (w) {
                case 0:
                    EventPicker.show(this, p, (t, num) -> {
                        int nk = Ev.key(t, num);
                        if (obj.events.containsKey(nk)) { Dialogs.toast(this, "That event already exists"); return; }
                        obj.events.put(nk, obj.events.remove(k));
                        eventKey = nk;
                        refreshEvents();
                        refreshActions();
                    });
                    break;
                case 1:
                    eventClipboard = new ArrayList<>();
                    for (Action a : obj.events.get(k)) eventClipboard.add(a.copy());
                    Dialogs.toast(this, "Event copied");
                    break;
                case 2:
                    if (eventClipboard == null) { Dialogs.toast(this, "Copy an event first"); return; }
                    for (Action a : eventClipboard) obj.events.get(k).add(a.copy());
                    refreshEvents();
                    refreshActions();
                    break;
                case 3: showCode(k); break;
                case 4: checkEvent(k, true); break;
                case 5:
                    Dialogs.confirm(this, "Delete event?", name + " and its " + obj.events.get(k).size() + " actions will be removed.", "Delete", () -> {
                        obj.events.remove(k);
                        List<Integer> keys = sortedKeys();
                        eventKey = keys.isEmpty() ? -1 : keys.get(0);
                        refreshEvents();
                        refreshActions();
                    });
                    break;
            }
        });
    }

    private void showCode(int k) {
        String code = CodeGen.generate(obj.events.get(k), p);
        TextView t = Ui.text(this, code.isEmpty() ? "// (empty)" : code, 13, Ui.TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.addView(t);
        Dialogs.builder(this).setTitle("Code for " + Ev.name(Ev.typeOf(k), Ev.numOf(k), p)).setView(Ui.scroll(this, h))
                .setPositiveButton("OK", null).show();
    }

    private boolean checkEvent(int k, boolean showOk) {
        ScriptError e = Checker.checkEvent(obj.events.get(k), p);
        if (e == null) {
            if (showOk) Dialogs.toast(this, "No errors");
            return true;
        }
        Dialogs.message(this, "Error in " + Ev.name(Ev.typeOf(k), Ev.numOf(k), p),
                e.getMessage() + "\n\n(line " + e.line + " of the generated code — use “Show generated code” to see it)");
        return false;
    }

    private void menu() {
        Dialogs.choose(this, obj.name, new String[]{"Properties", "Rename", "Check all events", "Paste copied actions", "Function reference"}, w -> {
            switch (w) {
                case 0: properties(); break;
                case 1: rename(); break;
                case 2: {
                    boolean ok = true;
                    for (int k : sortedKeys()) if (!checkEvent(k, false)) { ok = false; eventKey = k; refreshEvents(); refreshActions(); break; }
                    if (ok) Dialogs.toast(this, "All events are OK");
                    break;
                }
                case 3: paste(); break;
                case 4: startActivity(new Intent(this, ReferenceActivity.class)); break;
            }
        });
    }

    // ================================================================= actions
    private List<Action> current() { return eventKey < 0 ? null : obj.events.get(eventKey); }

    private void refreshActions() {
        List<Action> l = current();
        list.setActions(l);
        list.setEmptyHint(l == null ? "Tap “+ Event” to add an event (e.g. Create or Step)" : "Drag an action here or tap one below");
        list.select(-1);
        updateActionBar();
    }

    private void updateActionBar() {
        actionBar.removeAllViews();
        List<Action> l = current();
        int sel = list.selected();
        if (l == null) return;
        if (sel < 0) {
            TextView t = Ui.text(this, l.size() + " action" + (l.size() == 1 ? "" : "s") + " · tap to select, hold to drag", 13, Ui.DIM);
            t.setPadding(Ui.dp(this, 10), 0, 0, 0);
            actionBar.addView(t, Ui.lpw(0, Ui.WRAP, 1));
            actionBar.addView(Ui.icon(this, "📋", v -> paste()));
            actionBar.addView(Ui.icon(this, "</>", v -> showCode(eventKey)));
            return;
        }
        actionBar.addView(small("Edit", v -> editActionAt(list.selected())));
        actionBar.addView(Ui.icon(this, "▲", v -> move(-1)));
        actionBar.addView(Ui.icon(this, "▼", v -> move(1)));
        actionBar.addView(Ui.icon(this, "⧉", v -> duplicate()));
        actionBar.addView(Ui.icon(this, "✂", v -> { copy(); delete(); }));
        actionBar.addView(Ui.icon(this, "📋", v -> paste()));
        View sp = new View(this);
        actionBar.addView(sp, Ui.lpw(0, 1, 1));
        TextView del = Ui.icon(this, "🗑", v -> delete());
        actionBar.addView(del);
    }

    private TextView small(String s, View.OnClickListener l) {
        TextView b = Ui.button(this, s, Ui.ACCENT, l);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.dp(this, 38));
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        b.setLayoutParams(lp);
        b.setMinHeight(Ui.dp(this, 38));
        return b;
    }

    private void move(int dir) {
        List<Action> l = current();
        int i = list.selected(), j = i + dir;
        if (l == null || i < 0 || j < 0 || j >= l.size()) return;
        java.util.Collections.swap(l, i, j);
        list.recompute();
        list.select(j);
    }

    private void duplicate() {
        List<Action> l = current();
        int i = list.selected();
        if (l == null || i < 0) return;
        l.add(i + 1, l.get(i).copy());
        list.recompute();
        list.select(i + 1);
        refreshEvents();
    }

    private void copy() {
        List<Action> l = current();
        int i = list.selected();
        if (l == null || i < 0) return;
        clipboard.clear();
        clipboard.add(l.get(i).copy());
    }

    private void paste() {
        List<Action> l = current();
        if (l == null) { Dialogs.toast(this, "Add an event first"); return; }
        List<Action> src = !clipboard.isEmpty() ? clipboard : eventClipboard;
        if (src == null || src.isEmpty()) { Dialogs.toast(this, "Nothing copied"); return; }
        int at = list.selected() < 0 ? l.size() : list.selected() + 1;
        for (Action a : src) l.add(at++, a.copy());
        list.recompute();
        list.select(at - 1);
        refreshEvents();
    }

    private void delete() {
        List<Action> l = current();
        int i = list.selected();
        if (l == null || i < 0 || i >= l.size()) return;
        l.remove(i);
        list.recompute();
        list.select(Math.min(i, l.size() - 1));
        refreshEvents();
    }

    private void editActionAt(int i) {
        List<Action> l = current();
        if (l == null || i < 0 || i >= l.size()) return;
        Action a = l.get(i);
        if ("code".equals(a.id)) { editCode(a); return; }
        ActionEditor.show(this, p, a, false, ok -> { list.invalidate(); });
    }

    private void editCode(Action a) {
        App.codeTarget = new App.CodeTarget() {
            public String title() { return obj.name + ": " + Ev.name(Ev.typeOf(eventKey), Ev.numOf(eventKey), p); }
            public String get() { return a.arg(0); }
            public void set(String code) {
                while (a.args.isEmpty()) a.args.add("");
                a.args.set(0, code);
            }
        };
        startActivity(new Intent(this, ScriptEditorActivity.class));
    }

    private void insertNew(ActionDef d, int at) {
        List<Action> l = current();
        if (l == null) {
            Dialogs.toast(this, "Add an event first (+ Event)");
            return;
        }
        Action a = new Action(d.id);
        at = Math.max(0, Math.min(l.size(), at));
        l.add(at, a);
        list.recompute();
        list.select(at);
        refreshEvents();
        int idx = at;
        if ("code".equals(d.id)) { editCode(a); return; }
        if (d.args.length > 0 || d.canApply || d.canRelative || d.isQuestion()) {
            boolean needsDialog = d.args.length > 0;
            if (!needsDialog) return;
            ActionEditor.show(this, p, a, true, ok -> {
                if (!ok) {
                    l.remove(a);
                    list.recompute();
                    list.select(Math.min(idx, l.size() - 1));
                    refreshEvents();
                } else list.invalidate();
            });
        }
    }

    // ---- ActionListView.Listener
    @Override public void selected(int index) { updateActionBar(); }
    @Override public void edit(int index) { editActionAt(index); }
    @Override public void dropNew(ActionDef def, int index) { insertNew(def, index); }
    @Override public void moved() { refreshEvents(); }

    // ================================================================= palette
    private void refreshCategories() {
        catRow.removeAllViews();
        for (String c : ActionLibrary.CATEGORIES) {
            TextView chip = Ui.chip(this, c, c.equals(category), v -> { category = c; refreshCategories(); });
            if (!c.equals(category)) chip.setTextColor(Ui.categoryColor(c) | 0xFF000000);
            catRow.addView(chip);
        }
        refreshTiles();
    }

    private void refreshTiles() {
        tiles.removeAllViews();
        List<ActionDef> defs = ActionLibrary.category(category);
        // two rows of tiles, scrolling sideways
        GridLayout g = new GridLayout(this);
        g.setRowCount(2);
        g.setOrientation(GridLayout.VERTICAL);
        int w = Ui.dp(this, 84), h = Ui.dp(this, 70);
        for (ActionDef d : defs) {
            LinearLayout t = Ui.col(this);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.ripple(Ui.round(Ui.PANEL2, Ui.dp(this, 10)), 0));
            TextView ic = Ui.text(this, d.icon, 18, 0xFFFFFFFF);
            ic.setGravity(Gravity.CENTER);
            ic.setTypeface(Typeface.DEFAULT_BOLD);
            ic.setBackground(Ui.round(Ui.categoryColor(d.category), Ui.dp(this, 7)));
            t.addView(ic, Ui.lp(Ui.dp(this, 34), Ui.dp(this, 30)));
            TextView name = Ui.text(this, d.name, 10.5f, Ui.TEXT);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), 0);
            t.addView(name);
            t.setOnClickListener(v -> {
                List<Action> l = current();
                int at = l == null ? 0 : (list.selected() < 0 ? l.size() : list.selected() + 1);
                insertNew(d, at);
            });
            t.setOnLongClickListener(v -> {
                ClipData data = ClipData.newPlainText("action", d.id);
                v.startDragAndDrop(data, new View.DragShadowBuilder(v), new ActionListView.Drag(d, -1), 0);
                return true;
            });
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = w;
            gp.height = h;
            gp.setMargins(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
            g.addView(t, gp);
        }
        tiles.addView(g);
    }

    /** The action bar becomes a delete zone while an action from the list is dragged. */
    private boolean trashDrag(View v, DragEvent e) {
        Object st = e.getLocalState();
        if (!(st instanceof ActionListView.Drag)) return false;
        ActionListView.Drag d = (ActionListView.Drag) st;
        if (d.def != null) return false;
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                actionBar.setBackgroundColor(0xFF6A2424);
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                actionBar.setBackgroundColor(0xFFAA3333);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                actionBar.setBackgroundColor(0xFF6A2424);
                return true;
            case DragEvent.ACTION_DROP: {
                List<Action> l = current();
                if (l != null && d.fromIndex >= 0 && d.fromIndex < l.size()) {
                    l.remove(d.fromIndex);
                    list.recompute();
                    list.select(-1);
                    refreshEvents();
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                actionBar.setBackgroundColor(Ui.PANEL2);
                return true;
        }
        return true;
    }
}
