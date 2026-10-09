package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Sprite;

import java.util.HashMap;
import java.util.Map;

/** A live object instance in the running game. */
public final class Instance {
    public final int id;
    public ObjectRt obj;
    public double x, y, xprevious, yprevious, xstart, ystart;
    public double hspeed, vspeed, speed, direction;
    public double friction, gravity, gravityDirection = 270;
    public double z, zspeed, pitch;
    public int spriteIndex = -1, maskIndex = -1;
    public double imageIndex, imageSpeed = 1, imageXscale = 1, imageYscale = 1, imageAngle, imageAlpha = 1;
    public int imageBlend = 0xFFFFFF;
    public double depth;
    public boolean visible = true, solid, persistent;
    public final double[] alarm = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    public boolean destroyed, dying;
    /** Order of creation; used for stable sorting and instance_find. */
    public long order;
    /** Touch tracking for mouse enter/leave events. */
    public boolean touchInside;
    /** Last frame in which draw_self() was called from a Draw event in a raycast room. */
    public long rcDrawFrame = -10;

    private Map<String, Object> vars;

    // cached bounding box (room coords; right/bottom exclusive)
    public double bl, bt, br, bb;
    public boolean hasBox;
    private double cx = Double.NaN, cy, cxs, cys, cang;
    private int cspr = -2;
    private int crev = -1;

    public Instance(int id, ObjectRt obj) {
        this.id = id;
        this.obj = obj;
    }

    public Object getVar(String name) {
        if (vars == null) return V.ZERO;
        Object o = vars.get(name);
        return o == null ? V.ZERO : o;
    }

    public void setVar(String name, Object v) {
        if (vars == null) vars = new HashMap<>();
        vars.put(name, v);
    }

    public boolean hasVar(String name) { return vars != null && vars.containsKey(name); }

    public Map<String, Object> varsMap() {
        if (vars == null) vars = new HashMap<>();
        return vars;
    }

    // ---------------------------------------------------------------- motion (kept in sync like GM)
    public void setHspeed(double h) {
        hspeed = h;
        syncPolar();
    }

    public void setVspeed(double v) {
        vspeed = v;
        syncPolar();
    }

    private void syncPolar() {
        speed = Math.sqrt(hspeed * hspeed + vspeed * vspeed);
        if (speed > 1e-9) direction = norm(Math.toDegrees(Math.atan2(-vspeed, hspeed)));
        if (Math.abs(hspeed) < 1e-12) hspeed = 0;
        if (Math.abs(vspeed) < 1e-12) vspeed = 0;
    }

    public void setSpeed(double s) {
        speed = s;
        syncCartesian();
    }

    public void setDirection(double d) {
        direction = norm(d);
        syncCartesian();
    }

    private void syncCartesian() {
        double r = Math.toRadians(direction);
        hspeed = speed * Math.cos(r);
        vspeed = -speed * Math.sin(r);
        if (Math.abs(hspeed) < 1e-9) hspeed = 0;
        if (Math.abs(vspeed) < 1e-9) vspeed = 0;
    }

    public void motionSet(double dir, double spd) {
        direction = norm(dir);
        speed = spd;
        syncCartesian();
    }

    public void motionAdd(double dir, double spd) {
        double r = Math.toRadians(dir);
        hspeed += spd * Math.cos(r);
        vspeed -= spd * Math.sin(r);
        syncPolar();
    }

    public static double norm(double d) {
        d = d % 360;
        if (d < 0) d += 360;
        return d;
    }

    // ---------------------------------------------------------------- bounding box
    /** Recomputes the cached collision box if something changed. */
    public void updateBBox(Game g) {
        int m = maskIndex >= 0 ? maskIndex : spriteIndex;
        Sprite s = g.project.sprite(m);
        int rev = s == null ? -1 : s.revision;
        if (x == cx && y == cy && imageXscale == cxs && imageYscale == cys && imageAngle == cang && m == cspr && rev == crev) return;
        cx = x; cy = y; cxs = imageXscale; cys = imageYscale; cang = imageAngle; cspr = m; crev = rev;
        if (s == null) {
            hasBox = false;
            bl = br = x;
            bt = bb = y;
            return;
        }
        hasBox = true;
        int left = s.maskMode == Sprite.MASK_FULL ? 0 : s.bboxLeft;
        int top = s.maskMode == Sprite.MASK_FULL ? 0 : s.bboxTop;
        int right = s.maskMode == Sprite.MASK_FULL ? s.width - 1 : s.bboxRight;
        int bottom = s.maskMode == Sprite.MASK_FULL ? s.height - 1 : s.bboxBottom;
        double x1 = (left - s.originX) * imageXscale, x2 = (right + 1 - s.originX) * imageXscale;
        double y1 = (top - s.originY) * imageYscale, y2 = (bottom + 1 - s.originY) * imageYscale;
        if (imageAngle % 360 == 0) {
            bl = x + Math.min(x1, x2);
            br = x + Math.max(x1, x2);
            bt = y + Math.min(y1, y2);
            bb = y + Math.max(y1, y2);
        } else {
            double a = Math.toRadians(-imageAngle);
            double c = Math.cos(a), sn = Math.sin(a);
            double[] xs = {x1, x2, x2, x1}, ys = {y1, y1, y2, y2};
            bl = bt = Double.MAX_VALUE;
            br = bb = -Double.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                double px = x + xs[k] * c - ys[k] * sn;
                double py = y + xs[k] * sn + ys[k] * c;
                bl = Math.min(bl, px); br = Math.max(br, px);
                bt = Math.min(bt, py); bb = Math.max(bb, py);
            }
        }
    }

    public boolean circleMask(Game g) {
        Sprite s = g.project.sprite(maskIndex >= 0 ? maskIndex : spriteIndex);
        return s != null && s.circleMask;
    }

    @Override public String toString() { return obj.name + "#" + id; }
}
