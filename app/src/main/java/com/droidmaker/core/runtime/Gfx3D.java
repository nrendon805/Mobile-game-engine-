package com.droidmaker.core.runtime;

/** 3D drawing backend used in 3D rooms. */
public interface Gfx3D {
    /** View and projection matrices for following draws. */
    void matrices(float[] view, float[] proj);

    void fog(boolean on, int color, double start, double end);

    void lighting(boolean on, int ambient, D3State.Light[] lights);

    void culling(boolean on);

    /**
     * Draw a mesh with a world transform (null = identity). tex: -1 none, otherwise a texture id
     * from {@link D3State#spriteTex} / {@link D3State#bgTex}.
     */
    void mesh(Mesh m, float[] world, int tex, int color, double alpha);

    /** World matrix applied to 2D drawing calls made while in 3D mode (null = identity). */
    void world2D(float[] world);
}
