package com.droidmaker.core.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** State of the 3D mode: camera, fog, lights, transform stack and models. */
public final class D3State {
    public static final int CAM_AUTO = 0, CAM_FPS = 1, CAM_THIRD = 2, CAM_EXPLICIT = 3;

    public int camMode = CAM_AUTO;
    public int camInstance = -4;
    public double eyeHeight = 16, camDist = 96, camHeight = 48;
    public double fov = 60, znear = 1, zfar = 32000;
    public double[] explicit = new double[9];
    public boolean explicitPerspective = true;
    public double orthoX, orthoY, orthoW, orthoH, orthoAngle;
    /** Camera position of the current frame (for billboards). */
    public double camX, camY, camZ;

    public boolean fog;
    public int fogColor = 0x8090a0;
    public double fogStart = 200, fogEnd = 800;
    public boolean lighting = true;
    public int ambient = 0x505050;
    public boolean culling = false;
    /** Tint for d3d primitives (separate from the 2D draw color, which defaults to black). */
    public int color = 0xFFFFFF;
    public double alpha = 1;
    public final Light[] lights = new Light[8];

    public float[] world = Mat4.identity();
    private final ArrayDeque<float[]> stack = new ArrayDeque<>();

    public final List<Mesh> models = new ArrayList<>();
    public final Mesh scratch = new Mesh();

    public static final class Light {
        public boolean enabled, point;
        public double x, y, z, range = 200;
        public int color = 0xFFFFFF;
    }

    public D3State() {
        for (int i = 0; i < 8; i++) lights[i] = new Light();
        lights[0].enabled = true;
        lights[0].x = 0.4; lights[0].y = 0.3; lights[0].z = -1; // sun from above
    }

    public void push() { stack.push(world.clone()); }

    public void pop() { world = stack.isEmpty() ? Mat4.identity() : stack.pop(); }

    /** Applies m after the current world transform (GM's d3d_transform_add_*). */
    public void add(float[] m) { world = Mat4.mul(m, world); }

    public boolean worldIsIdentity() {
        float[] w = world;
        for (int i = 0; i < 16; i++) if (w[i] != ((i % 5 == 0) ? 1f : 0f)) return false;
        return true;
    }

    // texture ids: sprites 1 + spr*1024 + frame, backgrounds -(bg+2), none -1
    public static int spriteTex(int spr, int frame) { return spr < 0 ? -1 : 1 + spr * 1024 + Math.max(0, frame); }
    public static int bgTex(int bg) { return bg < 0 ? -1 : -(bg + 2); }
}
