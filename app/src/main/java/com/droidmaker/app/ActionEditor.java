package com.droidmaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.droidmaker.core.project.Action;
import com.droidmaker.core.project.ActionDef;
import com.droidmaker.core.project.CodeGen;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Keys;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.ResType;

import java.util.ArrayList;
import java.util.List;

/** Dialog for editing an action's arguments, "applies to", relative and NOT. */
public final class ActionEditor {
    private ActionEditor() {}

    public interface Done { void done(boolean ok); }

    private interface Getter { String get(); }

    public static void show(Activity a, Project p, Action act, boolean isNew, Done done) {
        ActionDef d = act.def();
        if (d == null) { done.done(false); return; }
        LinearLayout form = Ui.col(a);
        form.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        List<Getter> getters = new ArrayList<>();
        int[] applies = {act.appliesTo};

        // ---- applies to
        if (d.canApply) {
            LinearLayout row = Ui.row(a);
            row.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
            TextView self = Ui.chip(a, "Self", false, null), other = Ui.chip(a, "Other", false, null);
            TextView obj = Ui.chip(a, "Object…", false, null);
            Runnable sync = () -> {
                Ui.setChip(a, self, applies[0] == Action.SELF);
                Ui.setChip(a, other, applies[0] == Action.OTHER);
                Ui.setChip(a, obj, applies[0] >= 0);
                GameObject o = p.object(applies[0]);
                obj.setText(applies[0] >= 0 && o != null ? o.name : "Object…");
            };
            self.setOnClickListener(v -> { applies[0] = Action.SELF; sync.run(); });
            other.setOnClickListener(v -> { applies[0] = Action.OTHER; sync.run(); });
            obj.setOnClickListener(v -> Dialogs.resource(a, p, ResType.OBJECT, false, r -> { if (r != null) { applies[0] = r.id; sync.run(); } }));
            row.addView(self);
            row.addView(other);
            row.addView(obj);
            sync.run();
            form.addView(Ui.stacked(a, "Applies to", Ui.hscroll(a, row)));
        }

        // ---- arguments
        for (int i = 0; i < d.args.length; i++) {
            ActionDef.Arg arg = d.args[i];
            String val = act.arg(i);
            switch (arg.type) {
                case EXPR: case STRING: case TEXT: case VARNAME: {
                    EditText e = Ui.field(a, val, arg.type == ActionDef.ArgType.EXPR ? "expression" : "", false);
                    if (arg.type != ActionDef.ArgType.STRING && arg.type != ActionDef.ArgType.TEXT) e.setTypeface(Typeface.MONOSPACE);
                    if (arg.type == ActionDef.ArgType.STRING && d.kind == ActionDef.Kind.COMMENT) Dialogs.multiline(e, 3);
                    String label = arg.label + (arg.type == ActionDef.ArgType.TEXT ? "  (start with \" for an expression)" : "");
                    form.addView(Ui.stacked(a, label, e));
                    getters.add(() -> e.getText().toString());
                    break;
                }
                case BOOL: {
                    Switch s = Ui.toggle(a, "1".equals(val.trim()), null);
                    form.addView(Ui.labeled(a, arg.label, s));
                    getters.add(() -> s.isChecked() ? "1" : "0");
                    break;
                }
                case MENU: {
                    int[] sel = {parseInt(val)};
                    TextView b = Ui.valueButton(a, opt(arg, sel[0]), null);
                    b.setOnClickListener(v -> Dialogs.choose(a, arg.label, arg.options, w -> { sel[0] = w; Ui.setValue(b, opt(arg, w)); }));
                    form.addView(Ui.labeled(a, arg.label, b));
                    getters.add(() -> String.valueOf(sel[0]));
                    break;
                }
                case SPRITE: case SOUND: case BACKGROUND: case OBJECT: case ROOM: case SCRIPT: {
                    ResType rt = arg.type == ActionDef.ArgType.SPRITE ? ResType.SPRITE : arg.type == ActionDef.ArgType.SOUND ? ResType.SOUND
                            : arg.type == ActionDef.ArgType.BACKGROUND ? ResType.BACKGROUND : arg.type == ActionDef.ArgType.OBJECT ? ResType.OBJECT
                            : arg.type == ActionDef.ArgType.ROOM ? ResType.ROOM : ResType.SCRIPT;
                    String[] cur = {val};
                    TextView b = Ui.valueButton(a, val.isEmpty() ? "<none>" : val, null);
                    b.setOnClickListener(v -> Dialogs.resource(a, p, rt, true, r -> { cur[0] = r == null ? "" : r.name; Ui.setValue(b, r == null ? "<none>" : r.name); }));
                    form.addView(Ui.labeled(a, arg.label, b));
                    getters.add(() -> cur[0]);
                    break;
                }
                case COLOR: {
                    int[] col = {parseInt(val) & 0xFFFFFF};
                    View sw = Ui.swatch(a, 0xFF000000 | col[0], null);
                    sw.setOnClickListener(v -> Dialogs.color(a, 0xFF000000 | col[0], false, c -> {
                        col[0] = c & 0xFFFFFF;
                        sw.setBackground(Ui.roundStroke(0xFF000000 | col[0], 0xFFFFFFFF, Ui.dp(a, 2), Ui.dp(a, 8)));
                    }));
                    form.addView(Ui.labeled(a, arg.label, sw));
                    getters.add(() -> String.valueOf(col[0]));
                    break;
                }
                case KEY: {
                    int[] k = {parseInt(val)};
                    TextView b = Ui.valueButton(a, Keys.name(k[0]), null);
                    b.setOnClickListener(v -> Dialogs.key(a, key -> { k[0] = key; Ui.setValue(b, Keys.name(key)); }));
                    form.addView(Ui.labeled(a, arg.label, b));
                    getters.add(() -> String.valueOf(k[0]));
                    break;
                }
                case DIRS: {
                    char[] mask = (val + "000000000").substring(0, 9).toCharArray();
                    GridLayout g = new GridLayout(a);
                    g.setColumnCount(3);
                    String[] arrows = {"↖", "↑", "↗", "←", "■", "→", "↙", "↓", "↘"};
                    for (int k = 0; k < 9; k++) {
                        int kk = k;
                        TextView t = Ui.text(a, arrows[k], 22, Ui.TEXT);
                        t.setGravity(Gravity.CENTER);
                        Runnable paint = () -> t.setBackground(Ui.round(mask[kk] == '1' ? Ui.ACCENT : Ui.PANEL3, Ui.dp(a, 8)));
                        paint.run();
                        t.setOnClickListener(v -> { mask[kk] = mask[kk] == '1' ? '0' : '1'; paint.run(); });
                        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
                        gp.width = Ui.dp(a, 54);
                        gp.height = Ui.dp(a, 54);
                        gp.setMargins(4, 4, 4, 4);
                        g.addView(t, gp);
                    }
                    LinearLayout wrap = Ui.col(a);
                    wrap.setGravity(Gravity.CENTER_HORIZONTAL);
                    wrap.addView(g);
                    TextView note = Ui.text(a, "Pick one or more; a random one is used each time. ■ = stop.", 12, Ui.DIM);
                    note.setGravity(Gravity.CENTER);
                    wrap.addView(note);
                    form.addView(Ui.stacked(a, arg.label, wrap));
                    getters.add(() -> new String(mask));
                    break;
                }
                case CODE: {
                    TextView t = Ui.text(a, "Code is edited in the full code editor.", 14, Ui.DIM);
                    form.addView(Ui.stacked(a, arg.label, t));
                    getters.add(() -> act.arg(0));
                    break;
                }
            }
        }

        CheckBox rel = null, not = null;
        if (d.canRelative) {
            rel = new CheckBox(a);
            rel.setText("Relative (add to current value / position)");
            rel.setTextColor(Ui.TEXT);
            rel.setChecked(act.relative);
            LinearLayout w = Ui.row(a);
            w.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
            w.addView(rel);
            form.addView(w);
        }
        if (d.isQuestion()) {
            not = new CheckBox(a);
            not.setText("NOT (invert the condition)");
            not.setTextColor(Ui.TEXT);
            not.setChecked(act.not);
            LinearLayout w = Ui.row(a);
            w.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
            w.addView(not);
            form.addView(w);
        }
        if (form.getChildCount() == 0) { done.done(true); return; }

        TextView help = Ui.text(a, d.category + " · " + CodeGen.describe(act, p), 12, Ui.DIM);
        help.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 8));
        form.addView(help, 0);

        CheckBox fRel = rel, fNot = not;
        AlertDialog dlg = Dialogs.builder(a).setTitle(d.name).setView(Ui.scroll(a, form))
                .setPositiveButton("OK", (di, w) -> {
                    for (int i = 0; i < getters.size() && i < d.args.length; i++) {
                        while (act.args.size() <= i) act.args.add("");
                        act.args.set(i, getters.get(i).get());
                    }
                    act.appliesTo = d.canApply ? applies[0] : Action.SELF;
                    if (fRel != null) act.relative = fRel.isChecked();
                    if (fNot != null) act.not = fNot.isChecked();
                    done.done(true);
                })
                .setNegativeButton(isNew ? "Remove" : "Cancel", (di, w) -> done.done(false))
                .setOnCancelListener(di -> done.done(false))
                .create();
        dlg.show();
    }

    private static String opt(ActionDef.Arg arg, int i) {
        return i >= 0 && i < arg.options.length ? arg.options[i] : String.valueOf(i);
    }

    private static int parseInt(String s) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }
}
