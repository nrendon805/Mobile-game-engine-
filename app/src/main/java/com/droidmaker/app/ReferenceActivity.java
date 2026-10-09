package com.droidmaker.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.droidmaker.core.lang.Builtins;
import com.droidmaker.core.runtime.Vars;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Searchable list of every built-in function, variable and constant. Tap to copy. */
public final class ReferenceActivity extends BaseActivity {
    private LinearLayout list;
    private EditText search;

    @Override protected boolean needsProject() { return false; }

    @Override
    protected void build(Bundle state) {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.toolbar(this, "Function reference", true));
        search = Ui.field(this, "", "Search functions, variables, constants…", false);
        LinearLayout sw = Ui.col(this);
        sw.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        sw.addView(search);
        root.addView(sw);
        list = Ui.col(this);
        root.addView(Ui.scroll(this, list), Ui.lpw(Ui.MATCH, 0, 1));
        setContentView(root);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { refresh(); }
        });
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase();
        Map<String, List<Builtins.Info>> byCat = new LinkedHashMap<>();
        for (String n : Builtins.functionNames()) {
            Builtins.Info f = Builtins.function(n);
            if (!q.isEmpty() && !n.contains(q) && !f.help.toLowerCase().contains(q)) continue;
            byCat.computeIfAbsent(f.category, k -> new ArrayList<>()).add(f);
        }
        int shown = 0;
        for (Map.Entry<String, List<Builtins.Info>> e : byCat.entrySet()) {
            list.addView(Ui.section(this, e.getKey() + " (" + e.getValue().size() + ")"));
            for (Builtins.Info f : e.getValue()) {
                if (shown++ > 400) break;
                list.addView(row(f.signature, f.help, f.name + "("));
            }
        }
        List<String> vars = new ArrayList<>();
        for (String v : Vars.NAMES) if (q.isEmpty() || v.contains(q)) vars.add(v);
        if (!vars.isEmpty()) {
            list.addView(Ui.section(this, "Instance variables"));
            list.addView(row(android.text.TextUtils.join(", ", vars), "Every instance has these (x, speed, alarm[0..11] …)", null));
        }
        List<String> gv = new ArrayList<>();
        for (String v : Vars.GNAMES) if (q.isEmpty() || v.contains(q)) gv.add(v);
        if (!gv.isEmpty()) {
            list.addView(Ui.section(this, "Global variables"));
            list.addView(row(android.text.TextUtils.join(", ", gv), "Shared by the whole game (score, room, mouse_x, view_xview[0] …)", null));
        }
        List<String> cs = new ArrayList<>();
        for (String c : Builtins.constantNames()) if (q.isEmpty() || c.contains(q)) cs.add(c);
        if (!cs.isEmpty()) {
            list.addView(Ui.section(this, "Constants"));
            list.addView(row(android.text.TextUtils.join(", ", cs), "Colors are 0xRRGGBB, e.g. c_red = 0xFF0000", null));
        }
        if (q.isEmpty()) {
            list.addView(Ui.section(this, "Language"));
            list.addView(row("var a, b = 1;  if (x) {...} else {...}  while (c) {...}  do {...} until (c)", "Statements", null));
            list.addView(row("for (i = 0; i < n; i += 1) {...}  repeat (n) {...}  with (obj) {...}", "Loops; with() runs code as each instance", null));
            list.addView(row("switch (v) { case 1: ... break; default: ... }  exit  return value", "", null));
            list.addView(row("a[i] = 1;  a[i, j] = 2;  arr = [1, 2, 3];  other.x  global.score  obj_player.hp", "Arrays and other instances", null));
            list.addView(row("+ - * / div mod  == != < > <= >=  && || ^^ !  & | ^ << >>  ?:", "Operators (= also compares inside conditions)", null));
            list.addView(row("argument0 … argument15, argument[n], argument_count", "Script arguments", null));
        }
    }

    private LinearLayout row(String title, String help, String copy) {
        LinearLayout r = Ui.col(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        TextView t = Ui.text(this, title, 14, CodeEditText.C_FUNC);
        t.setTypeface(Typeface.MONOSPACE);
        r.addView(t);
        if (help != null && !help.isEmpty()) r.addView(Ui.text(this, help, 13, Ui.DIM));
        if (copy != null) {
            r.setBackground(Ui.ripple(new android.graphics.drawable.ColorDrawable(0), 0xFFFFFFFF));
            r.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", copy));
                Dialogs.toast(this, "Copied " + copy);
            });
        }
        return r;
    }
}
