package com.droidmaker.core.runtime;

import static com.droidmaker.core.runtime.Mesh.v;

/** Builders for the d3d primitive shapes (in room coordinates, z up). */
public final class Prims {
    private Prims() {}

    public static void block(Mesh m, double x1, double y1, double z1, double x2, double y2, double z2, double hr, double vr) {
        double ax = Math.min(x1, x2), bx = Math.max(x1, x2);
        double ay = Math.min(y1, y2), by = Math.max(y1, y2);
        double az = Math.min(z1, z2), bz = Math.max(z1, z2);
        // top
        m.quad(v(ax, ay, bz, 0, 0, 1, 0, 0), v(bx, ay, bz, 0, 0, 1, hr, 0), v(bx, by, bz, 0, 0, 1, hr, vr), v(ax, by, bz, 0, 0, 1, 0, vr));
        // bottom
        m.quad(v(ax, ay, az, 0, 0, -1, 0, 0), v(bx, ay, az, 0, 0, -1, hr, 0), v(bx, by, az, 0, 0, -1, hr, vr), v(ax, by, az, 0, 0, -1, 0, vr));
        // north (y = ay, normal -y)
        m.quad(v(ax, ay, bz, 0, -1, 0, 0, 0), v(bx, ay, bz, 0, -1, 0, hr, 0), v(bx, ay, az, 0, -1, 0, hr, vr), v(ax, ay, az, 0, -1, 0, 0, vr));
        // south
        m.quad(v(bx, by, bz, 0, 1, 0, 0, 0), v(ax, by, bz, 0, 1, 0, hr, 0), v(ax, by, az, 0, 1, 0, hr, vr), v(bx, by, az, 0, 1, 0, 0, vr));
        // west
        m.quad(v(ax, by, bz, -1, 0, 0, 0, 0), v(ax, ay, bz, -1, 0, 0, hr, 0), v(ax, ay, az, -1, 0, 0, hr, vr), v(ax, by, az, -1, 0, 0, 0, vr));
        // east
        m.quad(v(bx, ay, bz, 1, 0, 0, 0, 0), v(bx, by, bz, 1, 0, 0, hr, 0), v(bx, by, az, 1, 0, 0, hr, vr), v(bx, ay, az, 1, 0, 0, 0, vr));
    }

    /** Floor from (x1,y1,z1) to (x2,y2,z2); z slopes along y. Faces up. */
    public static void floor(Mesh m, double x1, double y1, double z1, double x2, double y2, double z2, double hr, double vr) {
        double[] n = normal(x1, y1, z1, x2, y1, z1, x1, y2, z2);
        if (n[2] < 0) { n[0] = -n[0]; n[1] = -n[1]; n[2] = -n[2]; }
        m.quad(v(x1, y1, z1, n[0], n[1], n[2], 0, 0), v(x2, y1, z1, n[0], n[1], n[2], hr, 0),
                v(x2, y2, z2, n[0], n[1], n[2], hr, vr), v(x1, y2, z2, n[0], n[1], n[2], 0, vr));
    }

    /** Vertical wall between (x1,y1) and (x2,y2), from z1 to z2. Faces left of the x1->x2 direction. */
    public static void wall(Mesh m, double x1, double y1, double z1, double x2, double y2, double z2, double hr, double vr) {
        double dx = x2 - x1, dy = y2 - y1;
        double l = Math.hypot(dx, dy);
        double nx = l == 0 ? 0 : dy / l, ny = l == 0 ? -1 : -dx / l;
        double top = Math.max(z1, z2), bot = Math.min(z1, z2);
        m.quad(v(x1, y1, top, nx, ny, 0, 0, 0), v(x2, y2, top, nx, ny, 0, hr, 0),
                v(x2, y2, bot, nx, ny, 0, hr, vr), v(x1, y1, bot, nx, ny, 0, 0, vr));
    }

    public static void cylinder(Mesh m, double x1, double y1, double z1, double x2, double y2, double z2,
                                double hr, double vr, boolean closed, int steps) {
        cone(m, x1, y1, z1, x2, y2, z2, hr, vr, closed, steps, 1);
    }

    /** topScale = 1 for a cylinder, 0 for a cone. */
    public static void cone(Mesh m, double x1, double y1, double z1, double x2, double y2, double z2,
                            double hr, double vr, boolean closed, int steps, double topScale) {
        steps = Math.max(3, Math.min(128, steps));
        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
        double rx = Math.abs(x2 - x1) / 2, ry = Math.abs(y2 - y1) / 2;
        double bot = Math.min(z1, z2), top = Math.max(z1, z2);
        for (int i = 0; i < steps; i++) {
            double a0 = 2 * Math.PI * i / steps, a1 = 2 * Math.PI * (i + 1) / steps;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double u0 = hr * i / steps, u1 = hr * (i + 1) / steps;
            double[] b0 = v(cx + c0 * rx, cy + s0 * ry, bot, c0, s0, 0, u0, vr);
            double[] b1 = v(cx + c1 * rx, cy + s1 * ry, bot, c1, s1, 0, u1, vr);
            double[] t0 = v(cx + c0 * rx * topScale, cy + s0 * ry * topScale, top, c0, s0, 0, u0, 0);
            double[] t1 = v(cx + c1 * rx * topScale, cy + s1 * ry * topScale, top, c1, s1, 0, u1, 0);
            if (topScale > 0) m.quad(t0, t1, b1, b0);
            else m.tri(t0, b1, b0);
            if (closed) {
                m.tri(v(cx, cy, bot, 0, 0, -1, 0.5, 0.5), v(b0[0], b0[1], bot, 0, 0, -1, 0.5 + c0 / 2, 0.5 + s0 / 2),
                        v(b1[0], b1[1], bot, 0, 0, -1, 0.5 + c1 / 2, 0.5 + s1 / 2));
                if (topScale > 0)
                    m.tri(v(cx, cy, top, 0, 0, 1, 0.5, 0.5), v(t0[0], t0[1], top, 0, 0, 1, 0.5 + c0 / 2, 0.5 + s0 / 2),
                            v(t1[0], t1[1], top, 0, 0, 1, 0.5 + c1 / 2, 0.5 + s1 / 2));
            }
        }
    }

    public static void ellipsoid(Mesh m, double x1, double y1, double z1, double x2, double y2, double z2,
                                 double hr, double vr, int steps) {
        steps = Math.max(4, Math.min(64, steps));
        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, cz = (z1 + z2) / 2;
        double rx = Math.abs(x2 - x1) / 2, ry = Math.abs(y2 - y1) / 2, rz = Math.abs(z2 - z1) / 2;
        int rings = Math.max(3, steps / 2);
        for (int r = 0; r < rings; r++) {
            double p0 = Math.PI * r / rings - Math.PI / 2, p1 = Math.PI * (r + 1) / rings - Math.PI / 2;
            for (int i = 0; i < steps; i++) {
                double a0 = 2 * Math.PI * i / steps, a1 = 2 * Math.PI * (i + 1) / steps;
                double[] q00 = sp(cx, cy, cz, rx, ry, rz, a0, p0, hr * i / steps, vr * (1 - (double) r / rings));
                double[] q10 = sp(cx, cy, cz, rx, ry, rz, a1, p0, hr * (i + 1) / steps, vr * (1 - (double) r / rings));
                double[] q11 = sp(cx, cy, cz, rx, ry, rz, a1, p1, hr * (i + 1) / steps, vr * (1 - (double) (r + 1) / rings));
                double[] q01 = sp(cx, cy, cz, rx, ry, rz, a0, p1, hr * i / steps, vr * (1 - (double) (r + 1) / rings));
                if (r > 0) m.tri(q00, q10, q11);
                if (r < rings - 1) m.tri(q00, q11, q01);
            }
        }
    }

    private static double[] sp(double cx, double cy, double cz, double rx, double ry, double rz, double a, double p, double u, double vv) {
        double nx = Math.cos(p) * Math.cos(a), ny = Math.cos(p) * Math.sin(a), nz = Math.sin(p);
        return v(cx + nx * rx, cy + ny * ry, cz + nz * rz, nx, ny, nz, u, vv);
    }

    /** Camera-facing sprite quad standing on z (its bottom edge), centered horizontally on the origin. */
    public static void billboard(Mesh m, double x, double y, double z, double w, double h, double ox,
                                 double camX, double camY) {
        double dx = x - camX, dy = y - camY;
        double l = Math.hypot(dx, dy);
        if (l < 1e-6) { dx = 1; dy = 0; l = 1; }
        // right vector as seen by the camera (perpendicular to view direction)
        double rx = -dy / l, ry = dx / l;
        double nx = -dx / l, ny = -dy / l;
        double left = -ox, right = w - ox;
        double topZ = z + h, botZ = z;
        m.quad(v(x + rx * left, y + ry * left, topZ, nx, ny, 0, 0, 0),
                v(x + rx * right, y + ry * right, topZ, nx, ny, 0, 1, 0),
                v(x + rx * right, y + ry * right, botZ, nx, ny, 0, 1, 1),
                v(x + rx * left, y + ry * left, botZ, nx, ny, 0, 0, 1));
    }

    private static double[] normal(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
        double ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9) return new double[]{0, 0, 1};
        return new double[]{nx / l, ny / l, nz / l};
    }
}
