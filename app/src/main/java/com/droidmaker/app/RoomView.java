package com.droidmaker.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Sprite;

import java.util.ArrayList;
import java.util.List;

/** Interactive room canvas: place, select, move and erase instances and tiles. Two fingers pan and zoom. */
@SuppressLint("ViewConstructor")
public final class RoomView extends View {
    public enum Mode { OBJECTS, TILES }
    public enum Tool { PAINT, SELECT, ERASE, PAN }

    public interface Listener {
        void beforeChange();
        void changed();
        void selected(Room.Inst inst);
        void instanceMenu(Room.Inst inst);
    }

    private final Project p;
    private final Room room;
    private Listener listener;
    public Mode mode = Mode.OBJECTS;
    public Tool tool = Tool.PAINT;
    public boolean snap = true, grid = true;
    public int object = -1;
    public int tileBg = -1, tileSx, tileSy, tileW = 16, tileH = 16, tileDepth = 1000000;
    private Room.Inst selected;

    private float scale = 1, offX, offY;
    private boolean fitted;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(), gridP = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final GestureDetector gd;

    // gesture state
    private boolean twoFinger, stroke, dragging;
    private float lastMidX, lastMidY, lastDist, lastX, lastY;
    private double dragDx, dragDy;
    private long lastPlaceKey = Long.MIN_VALUE;
    private String status = "";

    public RoomView(Context c, Project p, Room room) {
        super(c);
        this.p = p;
        this.room = room;
        bmp.setFilterBitmap(false);
        gridP.setColor(0x30FFFFFF);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(Ui.dp(c, 12));
        text.setShadowLayer(3, 0, 0, 0xFF000000);
        setBackgroundColor(0xFF0E1014);
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public void onLongPress(MotionEvent e) {
                if (twoFinger || mode != Mode.OBJECTS) return;
                Room.Inst i = hit(rx(e.getX()), ry(e.getY()));
                if (i != null && listener != null && (tool == Tool.SELECT || tool == Tool.PAN)) {
                    dragging = false;
                    stroke = false;
                    select(i);
                    listener.instanceMenu(i);
                }
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (tool == Tool.SELECT) {
                    Room.Inst i = hit(rx(e.getX()), ry(e.getY()));
                    if (i != null && listener != null) { select(i); listener.instanceMenu(i); return true; }
                }
                return false;
            }
        });
    }

    public void setListener(Listener l) { listener = l; }

    public Room.Inst selected() { return selected; }

    public void select(Room.Inst i) {
        selected = i;
        if (listener != null) listener.selected(i);
        invalidate();
    }

    public void fit() { fitted = false; invalidate(); }

    private void doFit() {
        if (getWidth() == 0) return;
        float sx = getWidth() * 0.94f / Math.max(1, room.width), sy = getHeight() * 0.94f / Math.max(1, room.height);
        scale = Math.max(0.05f, Math.min(sx, sy));
        offX = (getWidth() - room.width * scale) / 2;
        offY = (getHeight() - room.height * scale) / 2;
        fitted = true;
    }

    private double rx(float x) { return (x - offX) / scale; }
    private double ry(float y) { return (y - offY) / scale; }

    // ================================================================= drawing
    @Override
    protected void onDraw(Canvas c) {
        if (!fitted) doFit();
        float rw = room.width * scale, rh = room.height * scale;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(room.drawBackgroundColor ? 0xFF000000 | room.backgroundColor : 0xFF303030);
        c.drawRect(offX, offY, offX + rw, offY + rh, paint);
        c.save();
        c.clipRect(offX, offY, offX + rw, offY + rh);
        c.translate(offX, offY);
        c.scale(scale, scale);
        // background layers
        for (Room.BgLayer b : room.backgrounds) if (b.visible && !b.foreground) drawLayer(c, b);
        // tiles & instances by depth
        List<Object> items = new ArrayList<>();
        items.addAll(room.tiles);
        items.addAll(room.instances);
        items.sort((a, b) -> Double.compare(depthOf(b), depthOf(a)));
        for (Object o : items) {
            if (o instanceof Room.Tile) drawTile(c, (Room.Tile) o);
            else drawInst(c, (Room.Inst) o);
        }
        for (Room.BgLayer b : room.backgrounds) if (b.visible && b.foreground) drawLayer(c, b);
        c.restore();
        // grid
        if (grid) {
            int gx = mode == Mode.TILES && tileBg >= 0 ? tileW : room.gridX, gy = mode == Mode.TILES && tileBg >= 0 ? tileH : room.gridY;
            if (gx * scale >= 6 && gy * scale >= 6) {
                for (int x = 0; x <= room.width; x += gx) c.drawLine(offX + x * scale, offY, offX + x * scale, offY + rh, gridP);
                for (int y = 0; y <= room.height; y += gy) c.drawLine(offX, offY + y * scale, offX + rw, offY + y * scale, gridP);
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        paint.setColor(0x99FFFFFF);
        c.drawRect(offX, offY, offX + rw, offY + rh, paint);
        if (room.viewsEnabled) {
            Room.View v = room.views[0];
            paint.setColor(0xCCFFB547);
            paint.setPathEffect(new DashPathEffect(new float[]{12, 8}, 0));
            c.drawRect(offX + v.xview * scale, offY + v.yview * scale, offX + (v.xview + v.wview) * scale, offY + (v.yview + v.hview) * scale, paint);
            paint.setPathEffect(null);
        }
        if (selected != null) {
            RectF r = box(selected);
            paint.setColor(Ui.ACCENT);
            paint.setStrokeWidth(Ui.dp(getContext(), 2.5f));
            c.drawRect(offX + (float) r.left * scale, offY + (float) r.top * scale, offX + (float) r.right * scale, offY + (float) r.bottom * scale, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        String info = room.width + "×" + room.height + " · " + room.instances.size() + " instances" + (status.isEmpty() ? "" : " · " + status);
        c.drawText(info, Ui.dp(getContext(), 8), Ui.dp(getContext(), 18), text);
    }

    private double depthOf(Object o) {
        if (o instanceof Room.Tile) return ((Room.Tile) o).depth;
        GameObject go = p.object(((Room.Inst) o).object);
        return go == null ? 0 : go.depth;
    }

    private void drawLayer(Canvas c, Room.BgLayer b) {
        Background bg = p.background(b.bg);
        Bitmap img = ImageIO.background(p, bg);
        if (img == null) return;
        if (b.stretch) {
            c.drawBitmap(img, new Rect(0, 0, img.getWidth(), img.getHeight()), new RectF(0, 0, room.width, room.height), bmp);
            return;
        }
        float w = img.getWidth(), h = img.getHeight();
        float x0 = (float) b.x, y0 = (float) b.y;
        if (b.htiled) x0 = x0 % w - (x0 % w > 0 ? w : 0);
        if (b.vtiled) y0 = y0 % h - (y0 % h > 0 ? h : 0);
        float x1 = b.htiled ? room.width : x0 + 1, y1 = b.vtiled ? room.height : y0 + 1;
        int guard = 0;
        for (float y = y0; y < y1 && guard < 5000; y += h)
            for (float x = x0; x < x1 && guard++ < 5000; x += w) c.drawBitmap(img, x, y, bmp);
    }

    private void drawTile(Canvas c, Room.Tile t) {
        Bitmap img = ImageIO.background(p, p.background(t.bg));
        if (img == null) return;
        c.drawBitmap(img, new Rect(t.sx, t.sy, t.sx + t.w, t.sy + t.h), new RectF((float) t.x, (float) t.y, (float) t.x + t.w, (float) t.y + t.h), bmp);
    }

    private void drawInst(Canvas c, Room.Inst i) {
        GameObject o = p.object(i.object);
        if (o == null) return;
        Sprite s = p.sprite(o.sprite);
        Bitmap img = ImageIO.spriteFrame(p, s, 0);
        if (img != null) {
            m.reset();
            m.postTranslate(-s.originX, -s.originY);
            m.postScale((float) i.xscale, (float) i.yscale);
            m.postRotate((float) -i.angle);
            m.postTranslate((float) i.x, (float) i.y);
            if (!o.visible) bmp.setAlpha(110);
            c.drawBitmap(img, m, bmp);
            bmp.setAlpha(255);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xAA6A4FD6);
            c.drawRect((float) i.x, (float) i.y, (float) i.x + 16, (float) i.y + 16, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(11);
            c.drawText(o.name.length() > 4 ? o.name.substring(4, Math.min(o.name.length(), 6)) : o.name, (float) i.x + 2, (float) i.y + 12, paint);
        }
    }

    /** Bounding box of an instance in room coords. */
    RectF box(Room.Inst i) {
        GameObject o = p.object(i.object);
        Sprite s = o == null ? null : p.sprite(o.sprite);
        if (s == null || s.frames.isEmpty()) return new RectF((float) i.x, (float) i.y, (float) i.x + 16, (float) i.y + 16);
        float x1 = (float) ((0 - s.originX) * i.xscale), x2 = (float) ((s.width - s.originX) * i.xscale);
        float y1 = (float) ((0 - s.originY) * i.yscale), y2 = (float) ((s.height - s.originY) * i.yscale);
        if (i.angle != 0) {
            double a = Math.toRadians(-i.angle), cs = Math.cos(a), sn = Math.sin(a);
            float[] xs = {x1, x2, x2, x1}, ys = {y1, y1, y2, y2};
            float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                float px = (float) (xs[k] * cs - ys[k] * sn), py = (float) (xs[k] * sn + ys[k] * cs);
                l = Math.min(l, px); r = Math.max(r, px); t = Math.min(t, py); b = Math.max(b, py);
            }
            return new RectF((float) i.x + l, (float) i.y + t, (float) i.x + r, (float) i.y + b);
        }
        return new RectF((float) i.x + Math.min(x1, x2), (float) i.y + Math.min(y1, y2), (float) i.x + Math.max(x1, x2), (float) i.y + Math.max(y1, y2));
    }

    /** Top-most instance at a room point. */
    Room.Inst hit(double x, double y) {
        Room.Inst best = null;
        double bestDepth = Double.MAX_VALUE;
        for (Room.Inst i : room.instances) {
            RectF r = box(i);
            if (x >= r.left && x < r.right && y >= r.top && y < r.bottom) {
                double d = depthOf(i);
                if (d <= bestDepth) { bestDepth = d; best = i; }
            }
        }
        return best;
    }

    private Room.Tile hitTile(double x, double y) {
        Room.Tile best = null;
        for (Room.Tile t : room.tiles)
            if (x >= t.x && x < t.x + t.w && y >= t.y && y < t.y + t.h && (best == null || t.depth <= best.depth)) best = t;
        return best;
    }

    private double snapX(double x, int g) { return snap ? Math.floor(x / g) * g : Math.round(x); }

    // ================================================================= touch
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                twoFinger = false;
                lastX = x;
                lastY = y;
                stroke = true;
                lastPlaceKey = Long.MIN_VALUE;
                begin(x, y);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) {
                    twoFinger = true;
                    stroke = false;
                    dragging = false;
                    lastMidX = (e.getX(0) + e.getX(1)) / 2;
                    lastMidY = (e.getY(0) + e.getY(1)) / 2;
                    lastDist = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (twoFinger && e.getPointerCount() >= 2) {
                    float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                    float d = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
                    float f = lastDist > 0 ? d / lastDist : 1;
                    float ns = Math.max(0.05f, Math.min(20, scale * f));
                    f = ns / scale;
                    offX = mx - (lastMidX - offX) * f;
                    offY = my - (lastMidY - offY) * f;
                    scale = ns;
                    lastMidX = mx; lastMidY = my; lastDist = d;
                    invalidate();
                } else if (stroke) {
                    move(x, y);
                }
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (stroke && listener != null && changedInStroke) listener.changed();
                stroke = false;
                dragging = false;
                changedInStroke = false;
                status = "";
                invalidate();
                break;
        }
        return true;
    }

    private boolean changedInStroke;

    private void markChange() {
        if (!changedInStroke && listener != null) listener.beforeChange();
        changedInStroke = true;
    }

    private void begin(float x, float y) {
        double rxp = rx(x), ryp = ry(y);
        if (tool == Tool.PAN) return;
        if (mode == Mode.OBJECTS) {
            switch (tool) {
                case PAINT: paint(rxp, ryp); break;
                case ERASE: erase(rxp, ryp); break;
                case SELECT: {
                    Room.Inst i = hit(rxp, ryp);
                    select(i);
                    if (i != null) {
                        dragging = true;
                        dragDx = i.x - rxp;
                        dragDy = i.y - ryp;
                    }
                    break;
                }
                default: break;
            }
        } else {
            if (tool == Tool.PAINT) paintTile(rxp, ryp);
            else if (tool == Tool.ERASE) eraseTile(rxp, ryp);
        }
    }

    private void move(float x, float y) {
        if (tool == Tool.PAN) {
            offX += x - lastX;
            offY += y - lastY;
            invalidate();
            return;
        }
        double rxp = rx(x), ryp = ry(y);
        if (mode == Mode.OBJECTS) {
            switch (tool) {
                case PAINT: paint(rxp, ryp); break;
                case ERASE: erase(rxp, ryp); break;
                case SELECT:
                    if (dragging && selected != null) {
                        double nx = rxp + dragDx, ny = ryp + dragDy;
                        if (snap) { nx = Math.round(nx / room.gridX) * room.gridX; ny = Math.round(ny / room.gridY) * room.gridY; }
                        else { nx = Math.round(nx); ny = Math.round(ny); }
                        if (nx != selected.x || ny != selected.y) {
                            markChange();
                            selected.x = nx;
                            selected.y = ny;
                            status = "x " + (int) nx + "  y " + (int) ny;
                            invalidate();
                        }
                    }
                    break;
                default: break;
            }
        } else {
            if (tool == Tool.PAINT) paintTile(rxp, ryp);
            else if (tool == Tool.ERASE) eraseTile(rxp, ryp);
        }
    }

    private void paint(double x, double y) {
        if (object < 0 || p.object(object) == null) return;
        if (x < -room.gridX || y < -room.gridY || x > room.width + room.gridX || y > room.height + room.gridY) return;
        double px = snap ? Math.floor(x / room.gridX) * room.gridX : Math.round(x);
        double py = snap ? Math.floor(y / room.gridY) * room.gridY : Math.round(y);
        GameObject o = p.object(object);
        Sprite s = p.sprite(o.sprite);
        if (snap && s != null) { px += s.originX; py += s.originY; } // place the sprite's top-left on the grid
        long key = ((long) Math.round(px) << 32) ^ Math.round(py);
        if (key == lastPlaceKey) return;
        lastPlaceKey = key;
        for (Room.Inst i : room.instances) if (i.object == object && Math.abs(i.x - px) < 0.5 && Math.abs(i.y - py) < 0.5) return;
        if (!snap && lastPlaceKey != Long.MIN_VALUE && stroke && room.instances.size() > 0) {
            // free placement: one instance per touch unless moved far enough
            Room.Inst last = room.instances.get(room.instances.size() - 1);
            if (last.object == object && Math.hypot(last.x - px, last.y - py) < Math.max(8, s == null ? 16 : Math.min(s.width, s.height))) return;
        }
        markChange();
        Room.Inst i = new Room.Inst();
        i.id = room.newInstanceId();
        i.object = object;
        i.x = px;
        i.y = py;
        room.instances.add(i);
        status = "x " + (int) px + "  y " + (int) py;
        invalidate();
    }

    private void erase(double x, double y) {
        Room.Inst i = hit(x, y);
        if (i == null) return;
        markChange();
        room.instances.remove(i);
        if (i == selected) select(null);
        invalidate();
    }

    private void paintTile(double x, double y) {
        Background b = p.background(tileBg);
        if (b == null || b.file == null) return;
        int gx = Math.max(1, tileW), gy = Math.max(1, tileH);
        double px = snap ? Math.floor(x / gx) * gx : Math.round(x), py = snap ? Math.floor(y / gy) * gy : Math.round(y);
        if (px < 0 || py < 0 || px >= room.width || py >= room.height) return;
        for (Room.Tile t : room.tiles)
            if (t.x == px && t.y == py && t.depth == tileDepth) {
                if (t.bg == tileBg && t.sx == tileSx && t.sy == tileSy) return;
                markChange();
                t.bg = tileBg; t.sx = tileSx; t.sy = tileSy; t.w = tileW; t.h = tileH;
                invalidate();
                return;
            }
        markChange();
        Room.Tile t = new Room.Tile();
        t.bg = tileBg; t.sx = tileSx; t.sy = tileSy; t.w = tileW; t.h = tileH; t.x = px; t.y = py; t.depth = tileDepth;
        room.tiles.add(t);
        invalidate();
    }

    private void eraseTile(double x, double y) {
        Room.Tile t = hitTile(x, y);
        if (t == null) return;
        markChange();
        room.tiles.remove(t);
        invalidate();
    }
}
