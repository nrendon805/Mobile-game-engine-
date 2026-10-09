package com.droidmaker.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.droidmaker.core.lang.Builtins;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.project.Checker;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.runtime.Vars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Code editor screen with a symbol bar, autocomplete and syntax check. Edits App.codeTarget. */
public final class ScriptEditorActivity extends BaseActivity {
    private CodeEditText editor;
    private TextView banner;
    private LinearLayout suggestions;
    private App.CodeTarget target;
    private final List<String> names = new ArrayList<>();

    private static final String[] SYMBOLS = {"⇥", ";", "{", "}", "(", ")", "=", "\"", "+", "-", "*", "/", "<", ">", "!", "&&", "||",
            "[", "]", ",", ".", "_", "◀", "▶"};

    @Override
    protected void build(Bundle state) {
        target = App.codeTarget;
        if (target == null) { finish(); return; }
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout bar = Ui.toolbar(this, target.title(), true);
        bar.addView(Ui.icon(this, "↶", v -> editor.undo()));
        bar.addView(Ui.icon(this, "↷", v -> editor.redo()));
        TextView check = Ui.icon(this, "✓", v -> check(true));
        check.setTextColor(Ui.OK);
        bar.addView(check);
        bar.addView(Ui.icon(this, "⋮", v -> menu()));
        root.addView(bar);

        editor = new CodeEditText(this, p);
        editor.setCode(target.get() == null ? "" : target.get());
        editor.onChange = this::updateSuggestions;
        root.addView(editor, Ui.lpw(Ui.MATCH, 0, 1));

        banner = Ui.text(this, "", 13, Ui.TEXT);
        banner.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        banner.setVisibility(View.GONE);
        banner.setTypeface(Typeface.MONOSPACE);
        root.addView(banner);

        suggestions = Ui.row(this);
        suggestions.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        HorizontalScrollView sh = Ui.hscroll(this, suggestions);
        sh.setBackgroundColor(Ui.PANEL);
        root.addView(sh, Ui.lp(Ui.MATCH, Ui.dp(this, 46)));

        LinearLayout sym = Ui.row(this);
        for (String s : SYMBOLS) {
            TextView b = Ui.text(this, s, 17, Ui.TEXT);
            b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            b.setGravity(android.view.Gravity.CENTER);
            b.setMinWidth(Ui.dp(this, 42));
            b.setBackground(Ui.ripple(Ui.round(Ui.PANEL3, Ui.dp(this, 6)), 0));
            LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.dp(this, 40));
            lp.setMargins(Ui.dp(this, 2), Ui.dp(this, 3), Ui.dp(this, 2), Ui.dp(this, 3));
            b.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
            b.setOnClickListener(v -> symbol(s));
            sym.addView(b, lp);
        }
        HorizontalScrollView hs = Ui.hscroll(this, sym);
        hs.setBackgroundColor(Ui.PANEL2);
        root.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP));
        setContentView(root);

        // completion vocabulary
        names.addAll(Builtins.functionNames());
        names.addAll(Builtins.constantNames());
        Collections.addAll(names, Vars.NAMES);
        Collections.addAll(names, Vars.GNAMES);
        names.addAll(Builtins.KEYWORDS);
        for (ResType t : ResType.values()) for (Resource r : p.live(t)) names.add(r.name);
        Collections.sort(names);
        editor.requestFocus();
        updateSuggestions();
    }

    private void symbol(String s) {
        switch (s) {
            case "⇥": editor.insert("    "); break;
            case "◀": editor.moveCursor(-1); break;
            case "▶": editor.moveCursor(1); break;
            case "(": editor.insert("("); break;
            case "\"": editor.insert("\""); break;
            default: editor.insert(s);
        }
    }

    private void updateSuggestions() {
        suggestions.removeAllViews();
        String w = editor.currentWord();
        if (w.length() < 2 || Character.isDigit(w.charAt(0))) {
            hint("Tip: type 2+ letters for suggestions · ✓ checks your code");
            return;
        }
        List<String> starts = new ArrayList<>(), contains = new ArrayList<>();
        String lw = w.toLowerCase();
        for (String n : names) {
            if (n.equals(w)) continue;
            String ln = n.toLowerCase();
            if (ln.startsWith(lw)) starts.add(n);
            else if (lw.length() >= 3 && ln.contains(lw)) contains.add(n);
            if (starts.size() > 30) break;
        }
        starts.sort((a, b) -> a.length() - b.length());
        List<String> all = new ArrayList<>(starts);
        all.addAll(contains);
        int k = 0;
        for (String n : all) {
            if (k++ >= 20) break;
            TextView c = Ui.chip(this, n, false, v -> complete(n));
            c.setTypeface(Typeface.MONOSPACE);
            suggestions.addView(c);
        }
        Builtins.Info f = Builtins.function(w);
        if (f != null) hint(f.signature + (f.help.isEmpty() ? "" : "  — " + f.help));
    }

    private void hint(String s) {
        if (suggestions.getChildCount() == 0) {
            TextView t = Ui.text(this, s, 13, Ui.DIM);
            t.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setSingleLine(true);
            suggestions.addView(t, Ui.lp(Ui.WRAP, Ui.MATCH));
        }
    }

    private void complete(String n) {
        Builtins.Info f = Builtins.function(n);
        boolean script = p.find(ResType.SCRIPT, n) != null;
        if (f != null || script) {
            editor.replaceCurrentWord(n + (f != null && f.max == 0 ? "()" : "("));
            showBanner(f != null ? f.signature + (f.help.isEmpty() ? "" : "\n" + f.help) : n + "(argument0, argument1, ...)", false);
        } else editor.replaceCurrentWord(n);
    }

    private void showBanner(String s, boolean error) {
        banner.setText(s);
        banner.setTextColor(error ? 0xFFFFFFFF : Ui.TEXT);
        banner.setBackgroundColor(error ? 0xFF8A2A2A : Ui.PANEL2);
        banner.setVisibility(View.VISIBLE);
    }

    private boolean check(boolean showOk) {
        ScriptError e = Checker.check(editor.code(), p);
        if (e == null) {
            editor.markError(-1);
            if (showOk) showBanner("✓ No errors found", false);
            return true;
        }
        editor.markError(e.line);
        showBanner("Line " + e.line + ": " + e.getMessage(), true);
        banner.setOnClickListener(v -> editor.goToLine(e.line));
        if (e.line > 0) editor.goToLine(e.line);
        return false;
    }

    private void menu() {
        Dialogs.choose(this, "Code", new String[]{"Function reference", "Go to line…", "Insert snippet…", "Hide keyboard"}, w -> {
            switch (w) {
                case 0: startActivity(new Intent(this, ReferenceActivity.class)); break;
                case 1:
                    Dialogs.input(this, "Go to line", "", "line number", true, s -> {
                        try { editor.goToLine(Integer.parseInt(s.trim())); } catch (Exception ignored) { /* ignore */ }
                    });
                    break;
                case 2: snippets(); break;
                case 3:
                    InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (im != null) im.hideSoftInputFromWindow(editor.getWindowToken(), 0);
                    break;
            }
        });
    }

    private static final String[][] SNIPPETS = {
            {"if", "if (condition) {\n    \n}"},
            {"if / else", "if (condition) {\n    \n} else {\n    \n}"},
            {"for loop", "var i;\nfor (i = 0; i < 10; i += 1) {\n    \n}"},
            {"while loop", "while (condition) {\n    \n}"},
            {"repeat", "repeat (3) {\n    \n}"},
            {"with", "with (obj_name) {\n    \n}"},
            {"switch", "switch (value) {\n    case 0:\n        break;\n    default:\n        break;\n}"},
            {"move with arrows / joystick", "var spd = 4;\nx += (keyboard_check(vk_right) - keyboard_check(vk_left)) * spd;\ny += (keyboard_check(vk_down) - keyboard_check(vk_up)) * spd;"},
            {"tap to shoot", "if (mouse_check_button_pressed(mb_left)) {\n    var b = instance_create(x, y, obj_bullet);\n    b.direction = point_direction(x, y, mouse_x, mouse_y);\n    b.speed = 10;\n}"},
            {"GUI button", "draw_set_color(c_navy);\nif (gui_button(16, 16, 176, 72, \"Pause\")) {\n    show_message(\"Paused\");\n}"},
            {"save highscore", "if (score > save_get(\"best\", 0)) save_set(\"best\", score);"},
    };

    private void snippets() {
        String[] names = new String[SNIPPETS.length];
        for (int i = 0; i < names.length; i++) names[i] = SNIPPETS[i][0];
        Dialogs.choose(this, "Insert snippet", names, w -> editor.insert(SNIPPETS[w][1]));
    }

    private void saveBack() {
        target.set(editor.code());
        App.save();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        saveBack();
        if (!check(false)) {
            Dialogs.builder(this).setTitle("The code has an error")
                    .setMessage(banner.getText())
                    .setPositiveButton("Fix it", null)
                    .setNegativeButton("Leave anyway", (d, x) -> finish())
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (target != null && editor != null) saveBack();
    }
}
