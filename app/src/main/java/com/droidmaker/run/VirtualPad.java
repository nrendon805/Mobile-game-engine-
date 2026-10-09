package com.droidmaker.run;

import android.util.SparseIntArray;

import com.droidmaker.core.project.Project;
import com.droidmaker.core.runtime.InputState;

/** On-screen joystick / d-pad and A B X Y buttons. Touches it owns never reach the game as taps. */
final class VirtualPad {
    private static final int STICK = 100;
    private final Project project;
    private final InputState input;
    private final SparseIntArray owner = new SparseIntArray(); // pointer id -> control
    private int w = 1, h = 1;
    private float cx, cy, radius, btnR;
    private final float[] bx = new float[4], by = new float[4];
    private volatile float knobX, knobY;
    private final boolean[] down = new boolean[4];
    private static final String[] LABELS = {"A", "B", "X", "Y"};
    private static final int[] COLORS = {0x4CD58A, 0xFF5C5C, 0x4FA3FF, 0xFFB547};

    VirtualPad(Project project, InputState input) {
        this.project = project;
        this.input = input;
    }

    boolean enabled() { return project.vpad != Project.PAD_NONE; }

    void layout(int width, int height) {
        w = width;
        h = height;
        float s = Math.min(w, h);
        radius = s * 0.15f;
        cx = s * 0.06f + radius * 1.15f;
        cy = h - s * 0.06f - radius * 1.15f;
        btnR = s * 0.075f;
        float bxBase = w - s * 0.06f - btnR * 1.2f, byBase = h - s * 0.06f - btnR * 1.2f;
        int n = Math.max(0, Math.min(4, project.vpadButtons));
        // A bottom-right, B left of A, X above A, Y above B
        bx[0] = bxBase; by[0] = byBase;
        bx[1] = bxBase - btnR * 2.6f; by[1] = byBase + btnR * 0.3f;
        bx[2] = bxBase - btnR * 0.3f; by[2] = byBase - btnR * 2.6f;
        bx[3] = bxBase - btnR * 2.9f; by[3] = byBase - btnR * 2.3f;
        if (n == 1) { bx[0] = bxBase - btnR * 0.6f; }
    }

    /** Returns true if the pad takes this pointer. */
    boolean down(int pointer, float x, float y) {
        if (!enabled()) return false;
        float dx = x - cx, dy = y - cy;
        if (dx * dx + dy * dy <= (radius * 1.6f) * (radius * 1.6f)) {
            owner.put(pointer, STICK);
            stick(x, y);
            return true;
        }
        int n = Math.max(0, Math.min(4, project.vpadButtons));
        for (int b = 0; b < n; b++) {
            float ex = x - bx[b], ey = y - by[b];
            if (ex * ex + ey * ey <= (btnR * 1.35f) * (btnR * 1.35f)) {
                owner.put(pointer, b);
                if (!down[b]) { down[b] = true; input.postVButtonDown(b); }
                return true;
            }
        }
        return false;
    }

    boolean move(int pointer, float x, float y) {
        int c = owner.get(pointer, -1);
        if (c < 0) return false;
        if (c == STICK) stick(x, y);
        return true;
    }

    boolean up(int pointer) {
        int c = owner.get(pointer, -1);
        if (c < 0) return false;
        owner.delete(pointer);
        if (c == STICK) {
            knobX = knobY = 0;
            input.postJoystick(0, 0);
        } else if (c >= 0 && c < 4) {
            boolean still = false;
            for (int i = 0; i < owner.size(); i++) if (owner.valueAt(i) == c) still = true;
            if (!still && down[c]) { down[c] = false; input.postVButtonUp(c); }
        }
        return true;
    }

    void releaseAll() {
        for (int i = 0; i < owner.size(); i++) {
            int c = owner.valueAt(i);
            if (c >= 0 && c < 4 && down[c]) { down[c] = false; input.postVButtonUp(c); }
        }
        owner.clear();
        knobX = knobY = 0;
        input.postJoystick(0, 0);
    }

    private void stick(float x, float y) {
        float dx = (x - cx) / radius, dy = (y - cy) / radius;
        float l = (float) Math.hypot(dx, dy);
        if (l > 1) { dx /= l; dy /= l; }
        if (project.vpad == Project.PAD_DPAD) {
            // 8-way digital
            if (l < 0.3f) { dx = 0; dy = 0; }
            else {
                double a = Math.atan2(dy, dx);
                int sector = (int) Math.round(a / (Math.PI / 4));
                double qa = sector * Math.PI / 4;
                dx = (float) Math.round(Math.cos(qa));
                dy = (float) Math.round(Math.sin(qa));
            }
        }
        knobX = dx;
        knobY = dy;
        input.postJoystick(dx, dy);
    }

    void draw(GLScreen s) {
        if (!enabled()) return;
        float a = (float) project.vpadOpacity;
        if (project.vpad == Project.PAD_STICK) {
            s.circlePx(cx, cy, radius, 0x000000, a * 0.35f, false);
            s.circlePx(cx, cy, radius, 0xFFFFFF, a * 0.8f, true);
            s.circlePx(cx + knobX * radius * 0.6f, cy + knobY * radius * 0.6f, radius * 0.42f, 0xFFFFFF, a * 0.7f, false);
        } else {
            float arm = radius * 0.36f;
            s.fillRectPx(cx - arm, cy - radius, cx + arm, cy + radius, 0x000000, a * 0.45f);
            s.fillRectPx(cx - radius, cy - arm, cx + radius, cy + arm, 0x000000, a * 0.45f);
            int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int[] d : dirs) {
                boolean on = (d[0] != 0 && Math.signum(knobX) == d[0]) || (d[1] != 0 && Math.signum(knobY) == d[1]);
                float px = cx + d[0] * radius * 0.66f, py = cy + d[1] * radius * 0.66f;
                float t = arm * 0.55f;
                // arrow triangle pointing outwards
                float ax = d[0], ay = d[1];
                s.triangle(px + ax * t, py + ay * t, px - ay * t - ax * t * 0.5f, py + ax * t - ay * t * 0.5f,
                        px + ay * t - ax * t * 0.5f, py - ax * t - ay * t * 0.5f,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, on ? a : a * 0.6f, false);
            }
        }
        int n = Math.max(0, Math.min(4, project.vpadButtons));
        for (int b = 0; b < n; b++) {
            s.circlePx(bx[b], by[b], btnR, COLORS[b], (down[b] ? 0.85f : 0.45f) * a + 0.05f, false);
            s.circlePx(bx[b], by[b], btnR, 0xFFFFFF, a * 0.8f, true);
            s.textPx(bx[b], by[b], LABELS[b], btnR * 0.9f, 0xFFFFFF, Math.min(1, a + 0.3f));
        }
    }
}
