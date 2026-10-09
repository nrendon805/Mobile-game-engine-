package com.droidmaker.core.runtime;

/** Minimal column-major 4x4 float matrix math (OpenGL layout). */
public final class Mat4 {
    private Mat4() {}

    public static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1;
        return m;
    }

    public static void setIdentity(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
    }

    /** out = a * b (out may alias neither). */
    public static void mul(float[] out, float[] a, float[] b) {
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                out[c * 4 + r] = s;
            }
    }

    public static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        mul(o, a, b);
        return o;
    }

    public static float[] perspective(double fovyDeg, double aspect, double near, double far) {
        float[] m = new float[16];
        double f = 1.0 / Math.tan(Math.toRadians(fovyDeg) / 2);
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = (float) ((far + near) / (near - far));
        m[11] = -1;
        m[14] = (float) (2 * far * near / (near - far));
        return m;
    }

    public static float[] ortho(double l, double r, double b, double t, double n, double f) {
        float[] m = identity();
        m[0] = (float) (2 / (r - l));
        m[5] = (float) (2 / (t - b));
        m[10] = (float) (-2 / (f - n));
        m[12] = (float) (-(r + l) / (r - l));
        m[13] = (float) (-(t + b) / (t - b));
        m[14] = (float) (-(f + n) / (f - n));
        return m;
    }

    /**
     * View matrix for room coordinates (x right, y down, z up). The y axis is flipped so the
     * left-handed room space becomes right-handed before the usual look-at.
     */
    public static float[] lookAtRoom(double ex, double ey, double ez, double tx, double ty, double tz,
                                     double ux, double uy, double uz) {
        float[] look = lookAt(ex, -ey, ez, tx, -ty, tz, ux, -uy, uz);
        float[] flip = identity();
        flip[5] = -1;
        return mul(look, flip);
    }

    public static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz,
                                 double ux, double uy, double uz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-9) { fx = 1; fy = 0; fz = 0; fl = 1; }
        fx /= fl; fy /= fl; fz /= fl;
        // s = f x up
        double sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-9) { // up parallel to forward: pick another up
            double ax = 0, ay = 1, az = 0;
            if (Math.abs(fy) > 0.9) { ax = 1; ay = 0; }
            sx = fy * az - fz * ay; sy = fz * ax - fx * az; sz = fx * ay - fy * ax;
            sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        }
        sx /= sl; sy /= sl; sz /= sl;
        // u = s x f
        double vx = sy * fz - sz * fy, vy = sz * fx - sx * fz, vz = sx * fy - sy * fx;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) vx; m[5] = (float) vy; m[9] = (float) vz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * ex + sy * ey + sz * ez);
        m[13] = (float) -(vx * ex + vy * ey + vz * ez);
        m[14] = (float) (fx * ex + fy * ey + fz * ez);
        m[15] = 1;
        return m;
    }

    public static float[] translation(double x, double y, double z) {
        float[] m = identity();
        m[12] = (float) x; m[13] = (float) y; m[14] = (float) z;
        return m;
    }

    public static float[] scaling(double x, double y, double z) {
        float[] m = identity();
        m[0] = (float) x; m[5] = (float) y; m[10] = (float) z;
        return m;
    }

    /** Rotation about an axis by degrees (right-hand rule in the given coordinates). */
    public static float[] rotation(double deg, double ax, double ay, double az) {
        double l = Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-9) return identity();
        ax /= l; ay /= l; az /= l;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        float[] m = identity();
        m[0] = (float) (t * ax * ax + c);
        m[1] = (float) (t * ax * ay + s * az);
        m[2] = (float) (t * ax * az - s * ay);
        m[4] = (float) (t * ax * ay - s * az);
        m[5] = (float) (t * ay * ay + c);
        m[6] = (float) (t * ay * az + s * ax);
        m[8] = (float) (t * ax * az + s * ay);
        m[9] = (float) (t * ay * az - s * ax);
        m[10] = (float) (t * az * az + c);
        return m;
    }

    /** Transforms a point; returns {x, y, z, w}. */
    public static double[] transform(float[] m, double x, double y, double z) {
        return new double[]{
                m[0] * x + m[4] * y + m[8] * z + m[12],
                m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14],
                m[3] * x + m[7] * y + m[11] * z + m[15]};
    }
}
