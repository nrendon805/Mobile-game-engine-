package com.droidmaker.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.EditText;

import com.droidmaker.core.lang.Builtins;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.runtime.Vars;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Code editor: monospace, line numbers, syntax colors, auto-indent and undo history. */
@SuppressLint("AppCompatCustomView")
public final class CodeEditText extends EditText {
    static final int C_KEYWORD = 0xFFFF79C6, C_FUNC = 0xFF61AFEF, C_VAR = 0xFFE5C07B, C_RES = 0xFF98C379,
            C_STR = 0xFFC3E88D, C_NUM = 0xFFF78C6C, C_COMMENT = 0xFF6A737D, C_SCRIPT = 0xFF56D4C8;

    private final Paint gutterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gutterBg = new Paint();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> resources = new HashSet<>(), scripts = new HashSet<>();
    private int gutter;
    private boolean selfEdit;
    private final List<String> undo = new ArrayList<>(), redo = new ArrayList<>();
    private String lastSnapshot = "";
    private int errorLine = -1;
    public Runnable onChange;

    public CodeEditText(Context c, Project p) {
        super(c);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(14);
        setTextColor(Ui.TEXT);
        setBackgroundColor(Ui.BG);
        setGravity(Gravity.TOP | Gravity.START);
        setHorizontallyScrolling(true);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setLineSpacing(0, 1.1f);
        gutterPaint.setTypeface(Typeface.MONOSPACE);
        gutterPaint.setColor(0xFF5C6370);
        gutterPaint.setTextAlign(Paint.Align.RIGHT);
        gutterBg.setColor(Ui.PANEL);
        if (p != null) {
            for (ResType t : ResType.values()) for (Resource r : p.live(t)) {
                if (t == ResType.SCRIPT) scripts.add(r.name); else resources.add(r.name);
            }
        }
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (selfEdit) return;
                if (count == 1 && before == 0 && start < s.length() && s.charAt(start) == '\n') autoIndent(start);
                if (count == 1 && before == 0 && start < s.length() && s.charAt(start) == '}') dedent(start);
            }
            public void afterTextChanged(Editable s) {
                if (selfEdit) return;
                errorLine = -1;
                handler.removeCallbacks(highlightTask);
                handler.postDelayed(highlightTask, 220);
                handler.removeCallbacks(snapshotTask);
                handler.postDelayed(snapshotTask, 700);
                if (onChange != null) onChange.run();
            }
        });
        updateGutter();
    }

    private final Runnable highlightTask = this::highlight;
    private final Runnable snapshotTask = this::snapshot;

    public void setCode(String code) {
        selfEdit = true;
        setText(code);
        selfEdit = false;
        lastSnapshot = code;
        undo.clear();
        redo.clear();
        highlight();
    }

    public String code() { return getText().toString(); }

    private void snapshot() {
        String now = code();
        if (now.equals(lastSnapshot)) return;
        undo.add(lastSnapshot);
        if (undo.size() > 100) undo.remove(0);
        redo.clear();
        lastSnapshot = now;
    }

    public void undo() {
        snapshot();
        if (undo.isEmpty()) return;
        redo.add(code());
        restore(undo.remove(undo.size() - 1));
    }

    public void redo() {
        if (redo.isEmpty()) return;
        undo.add(code());
        restore(redo.remove(redo.size() - 1));
    }

    private void restore(String s) {
        int sel = getSelectionStart();
        selfEdit = true;
        setText(s);
        selfEdit = false;
        lastSnapshot = s;
        setSelection(Math.min(Math.max(0, sel), s.length()));
        highlight();
    }

    private void autoIndent(int nlPos) {
        Editable e = getText();
        int ls = nlPos - 1;
        while (ls >= 0 && e.charAt(ls) != '\n') ls--;
        ls++;
        StringBuilder ind = new StringBuilder();
        for (int k = ls; k < nlPos && (e.charAt(k) == ' ' || e.charAt(k) == '\t'); k++) ind.append(e.charAt(k));
        int k = nlPos - 1;
        while (k >= ls && e.charAt(k) == ' ') k--;
        if (k >= ls && e.charAt(k) == '{') ind.append("    ");
        if (ind.length() > 0) {
            selfEdit = true;
            e.insert(nlPos + 1, ind);
            selfEdit = false;
        }
    }

    private void dedent(int bracePos) {
        Editable e = getText();
        int ls = bracePos - 1;
        while (ls >= 0 && e.charAt(ls) != '\n') ls--;
        ls++;
        for (int k = ls; k < bracePos; k++) if (e.charAt(k) != ' ') return;
        if (bracePos - ls >= 4) {
            selfEdit = true;
            e.delete(bracePos - 4, bracePos);
            selfEdit = false;
        }
    }

    public void insert(String s) {
        int a = Math.max(0, getSelectionStart()), b = Math.max(0, getSelectionEnd());
        getText().replace(Math.min(a, b), Math.max(a, b), s);
    }

    /** The identifier being typed just before the cursor. */
    public String currentWord() {
        int c = getSelectionStart();
        if (c < 0) return "";
        CharSequence t = getText();
        int s = c;
        while (s > 0 && (Character.isLetterOrDigit(t.charAt(s - 1)) || t.charAt(s - 1) == '_')) s--;
        return t.subSequence(s, c).toString();
    }

    public void replaceCurrentWord(String with) {
        int c = getSelectionStart();
        String w = currentWord();
        getText().replace(c - w.length(), c, with);
    }

    public void moveCursor(int delta) {
        int c = Math.max(0, Math.min(length(), getSelectionStart() + delta));
        setSelection(c);
    }

    public void goToLine(int line) {
        String s = code();
        int pos = 0;
        for (int l = 1; l < line && pos >= 0; l++) {
            pos = s.indexOf('\n', pos);
            if (pos < 0) { pos = s.length(); break; }
            pos++;
        }
        setSelection(Math.max(0, Math.min(s.length(), pos)));
        requestFocus();
    }

    public void markError(int line) {
        errorLine = line;
        highlight();
    }

    // ------------------------------------------------------------------ highlighting
    private void highlight() {
        Editable e = getText();
        for (ForegroundColorSpan s : e.getSpans(0, e.length(), ForegroundColorSpan.class)) e.removeSpan(s);
        for (BackgroundColorSpan s : e.getSpans(0, e.length(), BackgroundColorSpan.class)) e.removeSpan(s);
        String t = e.toString();
        int n = Math.min(t.length(), 60000);
        int i = 0;
        while (i < n) {
            char c = t.charAt(i);
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '/') {
                int j = t.indexOf('\n', i);
                if (j < 0) j = n;
                span(e, i, j, C_COMMENT);
                i = j;
            } else if (c == '/' && i + 1 < n && t.charAt(i + 1) == '*') {
                int j = t.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                span(e, i, j, C_COMMENT);
                i = j;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && t.charAt(j) != c && t.charAt(j) != '\n') { if (t.charAt(j) == '\\') j++; j++; }
                j = Math.min(n, j + 1);
                span(e, i, j, C_STR);
                i = j;
            } else if (Character.isDigit(c) || (c == '$' && i + 1 < n && Character.isLetterOrDigit(t.charAt(i + 1)))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(t.charAt(j)) || t.charAt(j) == '.')) j++;
                span(e, i, j, C_NUM);
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(t.charAt(j)) || t.charAt(j) == '_')) j++;
                String w = t.substring(i, j);
                int col = 0;
                if (Builtins.KEYWORDS.contains(w)) col = C_KEYWORD;
                else if (scripts.contains(w)) col = C_SCRIPT;
                else if (Builtins.function(w) != null) col = C_FUNC;
                else if (resources.contains(w)) col = C_RES;
                else if (Vars.instIndex(w) >= 0 || Vars.globalIndex(w) >= 0 || Builtins.constantValue(w) != null || w.matches("argument\\d+")) col = C_VAR;
                if (col != 0) span(e, i, j, col);
                i = j;
            } else i++;
        }
        if (errorLine > 0) {
            int st = 0;
            for (int l = 1; l < errorLine && st >= 0; l++) { st = t.indexOf('\n', st); if (st >= 0) st++; }
            if (st >= 0 && st <= t.length()) {
                int en = t.indexOf('\n', st);
                if (en < 0) en = t.length();
                if (en > st) e.setSpan(new BackgroundColorSpan(0x55FF5C5C), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        updateGutter();
        invalidate();
    }

    private static void span(Editable e, int a, int b, int color) {
        if (b > a) e.setSpan(new ForegroundColorSpan(color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ------------------------------------------------------------------ gutter
    private void updateGutter() {
        int lines = Math.max(1, getLineCount());
        gutterPaint.setTextSize(getTextSize() * 0.85f);
        int digits = String.valueOf(lines).length();
        int g = (int) (gutterPaint.measureText("0") * Math.max(2, digits) + Ui.dp(getContext(), 14));
        if (g != gutter) {
            gutter = g;
            setPadding(gutter + Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 160));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Layout layout = getLayout();
        if (layout != null) {
            int sx = getScrollX();
            canvas.drawRect(sx, getScrollY(), sx + gutter, getScrollY() + getHeight(), gutterBg);
            CharSequence t = getText();
            int line = 1;
            int first = layout.getLineForVertical(getScrollY()), last = layout.getLineForVertical(getScrollY() + getHeight());
            // count logical lines before the first visible visual line
            int startOff = layout.getLineStart(first);
            for (int k = 0; k < startOff && k < t.length(); k++) if (t.charAt(k) == '\n') line++;
            for (int l = first; l <= last && l < layout.getLineCount(); l++) {
                int st = layout.getLineStart(l);
                boolean logicalStart = st == 0 || t.charAt(st - 1) == '\n';
                if (logicalStart) {
                    gutterPaint.setColor(line == errorLine ? Ui.DANGER : 0xFF5C6370);
                    float y = layout.getLineBaseline(l) + getTotalPaddingTop();
                    canvas.drawText(String.valueOf(line), sx + gutter - Ui.dp(getContext(), 6), y, gutterPaint);
                    line++;
                }
            }
        }
    }
}
