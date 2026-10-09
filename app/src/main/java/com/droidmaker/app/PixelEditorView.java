package com.droidmaker.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;

/** Zoomable pixel canvas with drawing tools. One finger draws, two fingers pan and zoom. */
@SuppressLint("ViewConstructor")
public final class PixelEditorView extends View {
    public enum Tool { PENCIL, ERASER, LINE, RECT, RECT_FILL, ELLIPSE, ELLIPSE_FILL, FILL, PICKER, MOVE, ORIGIN }

    public interface Listener {
        /** Called with a copy of the image before an edit is applied (for undo). */
        void beforeEdit(Bitmap before);
        void edited();
        void picked(int color);
        void origin(int x, int y);
    }

    private Bitmap image, onion;
    private Tool tool = Tool.PENCIL;
    private int color = 0xFF000000;
    private int brush = 1;
    private boolean mirror, showGrid = true;
    private int originX = -1, originY = -1;
    private int[] bbox;
    private Listener listener;

    private float scale = 8, offX, offY;
    private boolean fitted;
    private final Paint bmpPaint = new Paint(), gridPaint = new Paint(), checkPaint = new Paint(), linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();

    // gesture state
    private boolean drawing, panning;
    private Bitmap strokeBase;
    private int startX, startY, lastX, lastY;
    private float lastMidX, lastMidY, lastDist;

    public PixelEditorView(Context c) {
        super(c);
        bmpPaint.setFilterBitmap(false);
        gridPaint.setColor(0x33FFFFFF);
        gridPaint.setStrokeWidth(1);
        Bitmap chk = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        chk.setPixel(0, 0, 0xFF9A9A9A);
        chk.setPixel(1, 1, 0xFF9A9A9A);
        chk.setPixel(1, 0, 0xFF707070);
        chk.setPixel(0, 1, 0xFF707070);
        BitmapShader sh = new BitmapShader(chk, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix sm = new Matrix();
        sm.setScale(Ui.dp(c, 6), Ui.dp(c, 6));
        sh.setLocalMatrix(sm);
        checkPaint.setShader(sh);
        setBackgroundColor(0xFF101216);
    }

    public void setListener(Listener l) { listener = l; }

    public void setImage(Bitmap b) {
        if (image == null || b == null || image.getWidth() != b.getWidth() || image.getHeight() != b.getHeight()) fitted = false;
        image = b;
        invalidate();
    }

    public Bitmap image() { return image; }
    public void setOnion(Bitmap b) { onion = b; invalidate(); }
    public void setTool(Tool t) { tool = t; invalidate(); }
    public Tool tool() { return tool; }
    public void setColor(int c) { color = c; }
    public int color() { return color; }
    public void setBrush(int b) { brush = Math.max(1, b); }
    public int brush() { return brush; }
    public void setMirror(boolean b) { mirror = b; invalidate(); }
    public boolean mirror() { return mirror; }
    public void setGrid(boolean g) { showGrid = g; invalidate(); }
    public boolean grid() { return showGrid; }
    public void setOrigin(int x, int y) { originX = x; originY = y; invalidate(); }
    public void setBBox(int[] b) { bbox = b; invalidate(); }

    public void fit() { fitted = false; invalidate(); }

    private void doFit() {
        if (image == null || getWidth() == 0) return;
        float sx = getWidth() * 0.92f / image.getWidth(), sy = getHeight() * 0.92f / image.getHeight();
        scale = Math.max(0.25f, Math.min(sx, sy));
        offX = (getWidth() - image.getWidth() * scale) / 2;
        offY = (getHeight() - image.getHeight() * scale) / 2;
        fitted = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { fitted = false; }

    @Override
    protected void onDraw(Canvas c) {
        if (image == null) return;
        if (!fitted) doFit();
        float w = image.getWidth() * scale, h = image.getHeight() * scale;
        c.save();
        c.clipRect(offX, offY, offX + w, offY + h);
        c.drawRect(offX, offY, offX + w, offY + h, checkPaint);
        c.restore();
        m.setScale(scale, scale);
        m.postTranslate(offX, offY);
        if (onion != null) {
            bmpPaint.setAlpha(70);
            c.drawBitmap(onion, m, bmpPaint);
            bmpPaint.setAlpha(255);
        }
        c.drawBitmap(image, m, bmpPaint);
        if (showGrid && scale >= 6) {
            float x0 = Math.max(offX, 0), x1 = Math.min(offX + w, getWidth());
            float y0 = Math.max(offY, 0), y1 = Math.min(offY + h, getHeight());
            int ix0 = (int) Math.max(0, (x0 - offX) / scale), ix1 = (int) Math.min(image.getWidth(), (x1 - offX) / scale + 1);
            int iy0 = (int) Math.max(0, (y0 - offY) / scale), iy1 = (int) Math.min(image.getHeight(), (y1 - offY) / scale + 1);
            for (int i = ix0; i <= ix1; i++) c.drawLine(offX + i * scale, y0, offX + i * scale, y1, gridPaint);
            for (int j = iy0; j <= iy1; j++) c.drawLine(x0, offY + j * scale, x1, offY + j * scale, gridPaint);
        }
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        linePaint.setColor(0xAAFFFFFF);
        c.drawRect(offX, offY, offX + w, offY + h, linePaint);
        if (bbox != null) {
            linePaint.setColor(0xCC4CD58A);
            c.drawRect(offX + bbox[0] * scale, offY + bbox[1] * scale, offX + (bbox[2] + 1) * scale, offY + (bbox[3] + 1) * scale, linePaint);
        }
        if (mirror) {
            linePaint.setColor(0x88FFB547);
            c.drawLine(offX + w / 2, offY, offX + w / 2, offY + h, linePaint);
        }
        if (originX >= 0) {
            float ox = offX + originX * scale, oy = offY + originY * scale;
            float r = Ui.dp(getContext(), 10);
            linePaint.setColor(0xFFFF5C5C);
            linePaint.setStrokeWidth(Ui.dp(getContext(), 2));
            c.drawLine(ox - r, oy, ox + r, oy, linePaint);
            c.drawLine(ox, oy - r, ox, oy + r, linePaint);
            c.drawCircle(ox, oy, r * 0.5f, linePaint);
        }
    }

    private int ix(float x) { return (int) Math.floor((x - offX) / scale); }
    private int iy(float y) { return (int) Math.floor((y - offY) / scale); }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (image == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                panning = false;
                int x = ix(e.getX()), y = iy(e.getY());
                startStroke(x, y);
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) {
                    cancelStroke();
                    panning = true;
                    lastMidX = (e.getX(0) + e.getX(1)) / 2;
                    lastMidY = (e.getY(0) + e.getY(1)) / 2;
                    lastDist = dist(e);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (panning && e.getPointerCount() >= 2) {
                    float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                    float d = dist(e);
                    float f = lastDist > 0 ? d / lastDist : 1;
                    float ns = Math.max(0.25f, Math.min(80, scale * f));
                    f = ns / scale;
                    offX = mx - (lastMidX - offX) * f;
                    offY = my - (lastMidY - offY) * f;
                    scale = ns;
                    lastMidX = mx; lastMidY = my; lastDist = d;
                    invalidate();
                } else if (drawing) moveStroke(ix(e.getX()), iy(e.getY()));
                break;
            case MotionEvent.ACTION_UP:
                if (drawing) endStroke(ix(e.getX()), iy(e.getY()));
                panning = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelStroke();
                panning = false;
                break;
        }
        return true;
    }

    private static float dist(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    // ------------------------------------------------------------------ strokes
    private void startStroke(int x, int y) {
        drawing = true;
        startX = lastX = x;
        startY = lastY = y;
        switch (tool) {
            case PICKER:
                if (inside(x, y) && listener != null) listener.picked(image.getPixel(x, y));
                drawing = false;
                return;
            case ORIGIN:
                if (listener != null) listener.origin(Math.max(0, Math.min(image.getWidth(), x)), Math.max(0, Math.min(image.getHeight(), y)));
                return;
            case FILL:
                if (!inside(x, y)) { drawing = false; return; }
                if (listener != null) listener.beforeEdit(image.copy(Bitmap.Config.ARGB_8888, false));
                flood(x, y, color);
                if (mirror) flood(image.getWidth() - 1 - x, y, color);
                drawing = false;
                edited();
                return;
            default:
                break;
        }
        strokeBase = image.copy(Bitmap.Config.ARGB_8888, false);
        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
            plotBrush(x, y);
            invalidate();
        }
    }

    private void moveStroke(int x, int y) {
        if (tool == Tool.ORIGIN) {
            if (listener != null) listener.origin(Math.max(0, Math.min(image.getWidth(), x)), Math.max(0, Math.min(image.getHeight(), y)));
            return;
        }
        if (strokeBase == null) return;
        switch (tool) {
            case PENCIL: case ERASER:
                line(lastX, lastY, x, y, true);
                break;
            case MOVE:
                restoreBase();
                shift(x - startX, y - startY);
                break;
            default:
                restoreBase();
                shape(startX, startY, x, y);
        }
        lastX = x;
        lastY = y;
        invalidate();
    }

    private void endStroke(int x, int y) {
        moveStroke(x, y);
        drawing = false;
        if (strokeBase != null && listener != null && tool != Tool.ORIGIN) listener.beforeEdit(strokeBase);
        strokeBase = null;
        if (tool != Tool.ORIGIN) edited();
    }

    private void cancelStroke() {
        if (drawing && strokeBase != null) {
            restoreBase();
            invalidate();
        }
        drawing = false;
        strokeBase = null;
    }

    private void edited() {
        invalidate();
        if (listener != null) listener.edited();
    }

    private void restoreBase() {
        int[] px = new int[image.getWidth() * image.getHeight()];
        strokeBase.getPixels(px, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        image.setPixels(px, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
    }

    private boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight(); }

    private void put(int x, int y, int c) {
        if (inside(x, y)) image.setPixel(x, y, c);
        if (mirror) {
            int mx = image.getWidth() - 1 - x;
            if (inside(mx, y)) image.setPixel(mx, y, c);
        }
    }

    private void plotBrush(int x, int y) {
        int c = tool == Tool.ERASER ? 0 : color;
        int r0 = -(brush - 1) / 2;
        for (int dy = 0; dy < brush; dy++)
            for (int dx = 0; dx < brush; dx++) put(x + r0 + dx, y + r0 + dy, c);
    }

    private void line(int x0, int y0, int x1, int y1, boolean brushed) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        int guard = 0;
        while (guard++ < 100000) {
            if (brushed) plotBrush(x0, y0); else put(x0, y0, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void shape(int x0, int y0, int x1, int y1) {
        int l = Math.min(x0, x1), r = Math.max(x0, x1), t = Math.min(y0, y1), b = Math.max(y0, y1);
        switch (tool) {
            case LINE: line(x0, y0, x1, y1, true); break;
            case RECT:
                line(l, t, r, t, true); line(r, t, r, b, true); line(r, b, l, b, true); line(l, b, l, t, true);
                break;
            case RECT_FILL:
                for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) put(x, y, color);
                break;
            case ELLIPSE: case ELLIPSE_FILL: {
                double cx = (l + r) / 2.0, cy = (t + b) / 2.0, rx = (r - l) / 2.0 + 0.5, ry = (b - t) / 2.0 + 0.5;
                for (int y = t; y <= b; y++)
                    for (int x = l; x <= r; x++) {
                        double nx = (x - cx) / rx, ny = (y - cy) / ry;
                        double d = nx * nx + ny * ny;
                        if (tool == Tool.ELLIPSE_FILL) { if (d <= 1.0) put(x, y, color); }
                        else {
                            double inner = Math.max(0, 1 - 2.0 * brush / Math.max(1, Math.min(rx, ry) * 2));
                            if (d <= 1.0 && d >= inner * inner) put(x, y, color);
                        }
                    }
                break;
            }
            default: break;
        }
    }

    private void shift(int dx, int dy) {
        int w = image.getWidth(), h = image.getHeight();
        int[] src = new int[w * h], dst = new int[w * h];
        strokeBase.getPixels(src, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = x - dx, sy = y - dy;
                if (sx >= 0 && sy >= 0 && sx < w && sy < h) dst[y * w + x] = src[sy * w + sx];
            }
        image.setPixels(dst, 0, w, 0, 0, w, h);
    }

    private void flood(int x, int y, int c) {
        int w = image.getWidth(), h = image.getHeight();
        int[] px = new int[w * h];
        image.getPixels(px, 0, w, 0, 0, w, h);
        int target = px[y * w + x];
        if (target == c) return;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(y * w + x);
        while (!q.isEmpty()) {
            int p = q.poll();
            if (px[p] != target) continue;
            int px0 = p % w, py0 = p / w;
            int lx = px0, rx = px0;
            while (lx > 0 && px[py0 * w + lx - 1] == target) lx--;
            while (rx < w - 1 && px[py0 * w + rx + 1] == target) rx++;
            for (int k = lx; k <= rx; k++) {
                px[py0 * w + k] = c;
                if (py0 > 0 && px[(py0 - 1) * w + k] == target) q.add((py0 - 1) * w + k);
                if (py0 < h - 1 && px[(py0 + 1) * w + k] == target) q.add((py0 + 1) * w + k);
            }
        }
        image.setPixels(px, 0, w, 0, 0, w, h);
    }

    /** Converts a view rect to show the image region (for external previews). */
    public RectF imageRect() {
        return new RectF(offX, offY, offX + (image == null ? 0 : image.getWidth() * scale), offY + (image == null ? 0 : image.getHeight() * scale));
    }
}
