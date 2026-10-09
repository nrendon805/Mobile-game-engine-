package com.droidmaker.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

import com.droidmaker.core.project.Action;
import com.droidmaker.core.project.ActionDef;
import com.droidmaker.core.project.CodeGen;
import com.droidmaker.core.project.Project;

import java.util.ArrayList;
import java.util.List;

/** Draws an event's action list with block indentation; supports tap, edit and drag-and-drop reordering. */
@SuppressLint("ViewConstructor")
public final class ActionListView extends View {
    public interface Listener {
        void selected(int index);
        void edit(int index);
        void dropNew(ActionDef def, int index);
        void moved();
    }

    /** Drag payload for actions dragged from the palette or within the list. */
    public static final class Drag {
        public final ActionDef def;
        public final int fromIndex;
        public Drag(ActionDef def, int fromIndex) { this.def = def; this.fromIndex = fromIndex; }
    }

    private final Project project;
    private List<Action> actions = new ArrayList<>();
    private int[] depth = new int[0];
    private int selected = -1;
    private int insertAt = -1;
    private Listener listener;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG), glyph = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int rowH, indent, iconSize, pad;
    private final GestureDetector gestures;
    private String emptyHint = "";

    public ActionListView(Context c, Project p) {
        super(c);
        project = p;
        rowH = Ui.dp(c, 54);
        indent = Ui.dp(c, 22);
        iconSize = Ui.dp(c, 38);
        pad = Ui.dp(c, 10);
        text.setTextSize(Ui.dp(c, 14.5f));
        text.setColor(Ui.TEXT);
        glyph.setTextSize(Ui.dp(c, 17));
        glyph.setColor(0xFFFFFFFF);
        glyph.setTextAlign(Paint.Align.CENTER);
        glyph.setTypeface(Typeface.DEFAULT_BOLD);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                int i = (int) (e.getY() / rowH);
                if (i < 0 || i >= actions.size()) { select(-1); return true; }
                if (i == selected && listener != null) listener.edit(i);
                else select(i);
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                int i = (int) (e.getY() / rowH);
                if (i < 0 || i >= actions.size()) return;
                select(i);
                startDrag(i);
            }
        });
        setOnDragListener(this::onDragEvent);
    }

    public void setListener(Listener l) { listener = l; }

    public void setEmptyHint(String s) { emptyHint = s; invalidate(); }

    public void setActions(List<Action> list) {
        actions = list == null ? new ArrayList<>() : list;
        if (selected >= actions.size()) selected = -1;
        recompute();
    }

    public List<Action> actions() { return actions; }

    public int selected() { return selected; }

    public void select(int i) {
        selected = i;
        invalidate();
        if (listener != null) listener.selected(i);
    }

    /** Recomputes indentation and size after the list changed. */
    public void recompute() {
        int n = actions.size();
        depth = new int[n];
        int pos = 0;
        while (pos < n) pos = assign(pos, 0);
        requestLayout();
        invalidate();
    }

    private ActionDef.Kind kind(int i) {
        ActionDef d = actions.get(i).def();
        return d == null ? ActionDef.Kind.NORMAL : d.kind;
    }

    private int assign(int i, int d) {
        int n = actions.size();
        if (i >= n) return i;
        depth[i] = d;
        switch (kind(i)) {
            case BEGIN: {
                i++;
                while (i < n && kind(i) != ActionDef.Kind.END) i = assign(i, d + 1);
                if (i < n) { depth[i] = d; i++; }
                return i;
            }
            case QUESTION: {
                i = body(i + 1, d);
                if (i < n && kind(i) == ActionDef.Kind.ELSE) {
                    depth[i] = d;
                    i = body(i + 1, d);
                }
                return i;
            }
            case REPEAT:
                return body(i + 1, d);
            default:
                return i + 1;
        }
    }

    private int body(int i, int d) {
        if (i >= actions.size()) return i;
        return kind(i) == ActionDef.Kind.BEGIN ? assign(i, d) : assign(i, d + 1);
    }

    @Override
    protected void onMeasure(int wm, int hm) {
        int w = MeasureSpec.getSize(wm);
        int h = Math.max(rowH * (actions.size() + 2), getSuggestedMinimumHeight());
        if (getParent() instanceof View) h = Math.max(h, ((View) getParent()).getHeight());
        setMeasuredDimension(w, h);
    }

    private final RectF r = new RectF();

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        float round = Ui.dp(getContext(), 10);
        if (actions.isEmpty()) {
            text.setColor(Ui.DIM);
            String s = emptyHint;
            float tw = text.measureText(s);
            c.drawText(s, Math.max(pad, (w - tw) / 2), rowH * 0.7f, text);
            text.setColor(Ui.TEXT);
        }
        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            ActionDef d = a.def();
            float top = i * rowH;
            float left = pad + depth[i] * indent;
            r.set(left, top + 3, w - pad, top + rowH - 3);
            fill.setColor(i == selected ? 0xFF2D4A6E : Ui.PANEL);
            c.drawRoundRect(r, round, round, fill);
            if (i == selected) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(Ui.dp(getContext(), 2));
                fill.setColor(Ui.ACCENT);
                c.drawRoundRect(r, round, round, fill);
                fill.setStyle(Paint.Style.FILL);
            }
            // icon tile
            int col = d == null ? 0xFF666666 : Ui.categoryColor(d.category);
            if (d != null && (d.kind == ActionDef.Kind.BEGIN || d.kind == ActionDef.Kind.END || d.kind == ActionDef.Kind.ELSE)) col = 0xFF555B68;
            float ix = left + Ui.dp(getContext(), 6), iy = top + (rowH - iconSize) / 2f;
            r.set(ix, iy, ix + iconSize, iy + iconSize);
            fill.setColor(col);
            c.drawRoundRect(r, round * 0.7f, round * 0.7f, fill);
            if (d != null && d.isQuestion()) {
                // question actions get an octagon-ish marker like GM's blue question icons
                fill.setColor(0x40FFFFFF);
                c.drawCircle(ix + iconSize - 6, iy + 6, 5, fill);
            }
            String g = d == null ? "?" : d.icon;
            c.drawText(g, ix + iconSize / 2f, iy + iconSize / 2f - (glyph.descent() + glyph.ascent()) / 2, glyph);
            // description
            String s = CodeGen.describe(a, project);
            float tx = ix + iconSize + Ui.dp(getContext(), 10);
            float avail = w - pad - tx - Ui.dp(getContext(), 8);
            CharSequence el = TextUtils.ellipsize(s, text, Math.max(10, avail), TextUtils.TruncateAt.END);
            text.setColor(d != null && d.kind == ActionDef.Kind.COMMENT ? 0xFF8FBF8F : Ui.TEXT);
            c.drawText(el, 0, el.length(), tx, top + rowH / 2f - (text.descent() + text.ascent()) / 2, text);
        }
        if (insertAt >= 0) {
            float y = insertAt * rowH;
            fill.setColor(Ui.WARM);
            c.drawRect(pad, y - 3, w - pad, y + 3, fill);
            c.drawCircle(pad + 6, y, 8, fill);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e);
    }

    private void startDrag(int i) {
        ClipData data = ClipData.newPlainText("action", String.valueOf(i));
        View.DragShadowBuilder shadow = new RowShadow(this, i);
        startDragAndDrop(data, shadow, new Drag(null, i), 0);
    }

    /** Shadow image of a single row while dragging. */
    private final class RowShadow extends View.DragShadowBuilder {
        private final int index;
        RowShadow(View v, int index) { super(v); this.index = index; }

        @Override public void onProvideShadowMetrics(android.graphics.Point size, android.graphics.Point touch) {
            size.set(getWidth(), rowH);
            touch.set(getWidth() / 4, rowH / 2);
        }

        @Override public void onDrawShadow(Canvas c) {
            c.translate(0, -index * rowH);
            c.clipRect(0, index * rowH, getWidth(), (index + 1) * rowH);
            fill.setAlpha(255);
            draw(c);
        }
    }

    private boolean onDragEvent(View v, DragEvent e) {
        Object st = e.getLocalState();
        if (!(st instanceof Drag)) return false;
        Drag d = (Drag) st;
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                insertAt = Math.max(0, Math.min(actions.size(), Math.round(e.getY() / rowH)));
                autoScroll(e.getY());
                invalidate();
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                insertAt = -1;
                invalidate();
                return true;
            case DragEvent.ACTION_DROP: {
                int at = Math.max(0, Math.min(actions.size(), Math.round(e.getY() / rowH)));
                insertAt = -1;
                if (d.def != null) {
                    if (listener != null) listener.dropNew(d.def, at);
                } else if (d.fromIndex >= 0 && d.fromIndex < actions.size()) {
                    Action a = actions.remove(d.fromIndex);
                    if (at > d.fromIndex) at--;
                    actions.add(at, a);
                    selected = at;
                    recompute();
                    if (listener != null) { listener.moved(); listener.selected(at); }
                }
                invalidate();
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                insertAt = -1;
                invalidate();
                return true;
        }
        return true;
    }

    private void autoScroll(float y) {
        if (!(getParent() instanceof ScrollView)) return;
        ScrollView sv = (ScrollView) getParent();
        float rel = y - sv.getScrollY();
        int edge = rowH;
        if (rel < edge) sv.smoothScrollBy(0, -rowH / 3);
        else if (rel > sv.getHeight() - edge) sv.smoothScrollBy(0, rowH / 3);
    }
}
