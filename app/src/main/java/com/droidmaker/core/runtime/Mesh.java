package com.droidmaker.core.runtime;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Triangle list in room coordinates. Vertex layout: x, y, z, nx, ny, nz, u, v.
 * Triangles are wound so that the outside face is the GL front face after the view's y flip.
 */
public final class Mesh {
    public static final int STRIDE = 8;
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    public float[] data = new float[STRIDE * 36];
    public int count;
    public final int uid = NEXT.getAndIncrement();
    /** Changes whenever the mesh content changes (lets the GPU backend cache uploads). */
    public int version;
    /** Static meshes (models) may be cached on the GPU; dynamic ones are streamed each draw. */
    public boolean cacheable;

    public void clear() { count = 0; version++; }

    public void vertex(double x, double y, double z, double nx, double ny, double nz, double u, double v) {
        int need = (count + 1) * STRIDE;
        if (need > data.length) {
            float[] d = new float[Math.max(need, data.length * 2)];
            System.arraycopy(data, 0, d, 0, count * STRIDE);
            data = d;
        }
        int o = count * STRIDE;
        data[o] = (float) x; data[o + 1] = (float) y; data[o + 2] = (float) z;
        data[o + 3] = (float) nx; data[o + 4] = (float) ny; data[o + 5] = (float) nz;
        data[o + 6] = (float) u; data[o + 7] = (float) v;
        count++;
        version++;
    }

    /**
     * Adds a triangle with per-vertex normals, ordering vertices so the face points along the
     * average normal (cross product must oppose the normal in room space; see Mat4.lookAtRoom).
     */
    public void tri(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        double nx = a[3] + b[3] + c[3], ny = a[4] + b[4] + c[4], nz = a[5] + b[5] + c[5];
        boolean flip = cx * nx + cy * ny + cz * nz > 0;
        put(a);
        if (flip) { put(c); put(b); } else { put(b); put(c); }
    }

    private void put(double[] p) { vertex(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]); }

    /** Quad a-b-c-d (in order around the edge) as two triangles. */
    public void quad(double[] a, double[] b, double[] c, double[] d) {
        tri(a, b, c);
        tri(a, c, d);
    }

    public static double[] v(double x, double y, double z, double nx, double ny, double nz, double u, double v) {
        return new double[]{x, y, z, nx, ny, nz, u, v};
    }

    public void append(Mesh o, float[] world) {
        for (int i = 0; i < o.count; i++) {
            int k = i * STRIDE;
            double x = o.data[k], y = o.data[k + 1], z = o.data[k + 2];
            double nx = o.data[k + 3], ny = o.data[k + 4], nz = o.data[k + 5];
            if (world != null) {
                double[] p = Mat4.transform(world, x, y, z);
                x = p[0]; y = p[1]; z = p[2];
                double tx = world[0] * nx + world[4] * ny + world[8] * nz;
                double ty = world[1] * nx + world[5] * ny + world[9] * nz;
                double tz = world[2] * nx + world[6] * ny + world[10] * nz;
                double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (l > 1e-9) { nx = tx / l; ny = ty / l; nz = tz / l; }
            }
            vertex(x, y, z, nx, ny, nz, o.data[k + 6], o.data[k + 7]);
        }
    }
}
