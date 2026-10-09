package com.droidmaker.core.runtime;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keyboard, touch and virtual gamepad state. The UI thread posts events; the game thread applies
 * them once per step so "pressed"/"released" flags last exactly one step.
 */
public final class InputState {
    public static final int MAX_TOUCH = 10;

    // ---- event queue (UI thread -> game thread)
    private static final int E_KEY_DOWN = 1, E_KEY_UP = 2, E_TOUCH_DOWN = 3, E_TOUCH_MOVE = 4, E_TOUCH_UP = 5,
            E_CHAR = 6, E_JOY = 7, E_VBTN_DOWN = 8, E_VBTN_UP = 9, E_BACK = 10, E_RELEASE = 11;

    private static final class Ev {
        final int type, a; final float x, y;
        Ev(int type, int a, float x, float y) { this.type = type; this.a = a; this.x = x; this.y = y; }
    }

    private final ConcurrentLinkedQueue<Ev> queue = new ConcurrentLinkedQueue<>();

    public void postKeyDown(int key) { queue.add(new Ev(E_KEY_DOWN, key, 0, 0)); }
    public void postKeyUp(int key) { queue.add(new Ev(E_KEY_UP, key, 0, 0)); }
    public void postChar(int ch) { queue.add(new Ev(E_CHAR, ch, 0, 0)); }
    /** Touch coordinates in screen pixels. */
    public void postTouchDown(int pointer, float sx, float sy) { queue.add(new Ev(E_TOUCH_DOWN, pointer, sx, sy)); }
    public void postTouchMove(int pointer, float sx, float sy) { queue.add(new Ev(E_TOUCH_MOVE, pointer, sx, sy)); }
    public void postTouchUp(int pointer, float sx, float sy) { queue.add(new Ev(E_TOUCH_UP, pointer, sx, sy)); }
    public void postJoystick(float x, float y) { queue.add(new Ev(E_JOY, 0, x, y)); }
    public void postVButtonDown(int b) { queue.add(new Ev(E_VBTN_DOWN, b, 0, 0)); }
    public void postVButtonUp(int b) { queue.add(new Ev(E_VBTN_UP, b, 0, 0)); }
    public void postBack() { queue.add(new Ev(E_BACK, 0, 0, 0)); }
    /** Thread-safe: releases every key, touch and button at the next step (e.g. when the app pauses). */
    public void postReleaseAll() { queue.add(new Ev(E_RELEASE, 0, 0, 0)); }

    // ---- keyboard
    public final boolean[] keyDown = new boolean[256];
    public final boolean[] keyPressed = new boolean[256];
    public final boolean[] keyReleased = new boolean[256];
    private final int[] keyRefs = new int[256]; // several sources (vpad + keyboard) may hold a key
    public int lastKey;
    public String lastChar = "";
    public final StringBuilder keyboardString = new StringBuilder();
    public boolean backPressed;

    // ---- touches (slot order = finger order)
    public static final class Touch {
        public int pointer = -1;
        public boolean down, pressed, released;
        public double sx, sy;          // screen px
        public double dx, dy;          // display (GUI) coords
        public double rx, ry;          // room coords
        public double startSx, startSy;
        public long startTime;
        public boolean moved;
        public long consumedStep = -1;
    }

    public final Touch[] touches = new Touch[MAX_TOUCH];
    public double mouseX, mouseY;
    public int lastButton;

    // ---- gestures this step
    public boolean swipeLeft, swipeRight, swipeUp, swipeDown, doubleTap, longPress;
    private long lastTapTime;
    private double lastTapX, lastTapY;
    private boolean longFired;

    // ---- virtual pad
    public double joyX, joyY;
    public final boolean[] vbDown = new boolean[4], vbPressed = new boolean[4], vbReleased = new boolean[4];
    public int[] vbKeys = {32, 'Z', 'X', 13};
    public int dpadMode; // set by game from project

    private final Game game;
    private final boolean[] joyKeys = new boolean[4]; // left right up down held by the joystick

    InputState(Game game) {
        this.game = game;
        for (int i = 0; i < MAX_TOUCH; i++) touches[i] = new Touch();
    }

    public boolean touchDown(int slot) { return slot >= 0 && slot < MAX_TOUCH && touches[slot].down; }

    public int touchCount() {
        int n = 0;
        for (Touch t : touches) if (t.down) n++;
        return n;
    }

    public double guiX(int slot) { return slot >= 0 && slot < MAX_TOUCH ? touches[slot].dx : 0; }
    public double guiY(int slot) { return slot >= 0 && slot < MAX_TOUCH ? touches[slot].dy : 0; }

    public int currentKey() {
        for (int k = 255; k >= 2; k--) if (keyDown[k]) return k;
        return 0;
    }

    public void forceKey(int k) {
        if (k >= 2 && k < 256) { keyDown[k] = true; keyPressed[k] = true; keyRefs[k]++; }
    }

    private void press(int k) {
        if (k < 0 || k >= 256) return;
        if (keyRefs[k]++ == 0) {
            keyDown[k] = true;
            keyPressed[k] = true;
            lastKey = k;
        }
    }

    private void release(int k) {
        if (k < 0 || k >= 256 || keyRefs[k] == 0) return;
        if (--keyRefs[k] == 0) {
            keyDown[k] = false;
            keyReleased[k] = true;
        }
    }

    public void releaseAll() {
        for (int k = 0; k < 256; k++) { if (keyDown[k]) keyReleased[k] = true; keyDown[k] = false; keyRefs[k] = 0; }
        for (Touch t : touches) { if (t.down) t.released = true; t.down = false; t.pointer = -1; }
        for (int b = 0; b < 4; b++) { if (vbDown[b]) vbReleased[b] = true; vbDown[b] = false; }
        joyX = joyY = 0;
        for (int i = 0; i < 4; i++) joyKeys[i] = false;
    }

    /** Called at the start of each step on the game thread. */
    void update(long now) {
        for (int k = 0; k < 256; k++) { keyPressed[k] = false; keyReleased[k] = false; }
        for (int b = 0; b < 4; b++) { vbPressed[b] = false; vbReleased[b] = false; }
        for (Touch t : touches) {
            t.pressed = false;
            if (t.released) { t.released = false; t.pointer = -1; }
        }
        swipeLeft = swipeRight = swipeUp = swipeDown = doubleTap = longPress = false;
        backPressed = false;

        Ev e;
        while ((e = queue.poll()) != null) {
            switch (e.type) {
                case E_KEY_DOWN: press(e.a); break;
                case E_KEY_UP: release(e.a); break;
                case E_CHAR:
                    lastChar = String.valueOf((char) e.a);
                    if (e.a == 8) { if (keyboardString.length() > 0) keyboardString.setLength(keyboardString.length() - 1); }
                    else if (e.a >= 32) { keyboardString.append((char) e.a); if (keyboardString.length() > 1024) keyboardString.delete(0, 512); }
                    break;
                case E_BACK: backPressed = true; break;
                case E_RELEASE: releaseAll(); break;
                case E_TOUCH_DOWN: {
                    Touch t = slotFor(e.a, true);
                    if (t == null) break;
                    t.down = true; t.pressed = true; t.released = false;
                    t.sx = e.x; t.sy = e.y; t.startSx = e.x; t.startSy = e.y; t.startTime = now; t.moved = false;
                    lastButton = 1;
                    if (t == touches[0] || firstDown() == t) longFired = false;
                    break;
                }
                case E_TOUCH_MOVE: {
                    Touch t = slotFor(e.a, false);
                    if (t == null || !t.down) break;
                    t.sx = e.x; t.sy = e.y;
                    double tol = game.screenH * 0.03 + 8;
                    if (Math.abs(t.sx - t.startSx) > tol || Math.abs(t.sy - t.startSy) > tol) t.moved = true;
                    break;
                }
                case E_TOUCH_UP: {
                    Touch t = slotFor(e.a, false);
                    if (t == null || !t.down) break;
                    t.sx = e.x; t.sy = e.y;
                    t.down = false; t.released = true;
                    gestureOnRelease(t, now);
                    break;
                }
                case E_JOY: setJoystick(e.x, e.y); break;
                case E_VBTN_DOWN:
                    if (e.a >= 0 && e.a < 4 && !vbDown[e.a]) { vbDown[e.a] = true; vbPressed[e.a] = true; press(vbKeys[e.a]); }
                    break;
                case E_VBTN_UP:
                    if (e.a >= 0 && e.a < 4 && vbDown[e.a]) { vbDown[e.a] = false; vbReleased[e.a] = true; release(vbKeys[e.a]); }
                    break;
            }
        }
        // long press
        Touch p = firstDown();
        if (p != null && !p.moved && !longFired && now - p.startTime > 550) { longPress = true; longFired = true; }
        for (Touch t : touches) if (t.down || t.released) mapTouch(t);
        Touch m = firstDown();
        if (m == null) for (Touch t : touches) if (t.released) { m = t; break; }
        if (m != null) { mouseX = m.rx; mouseY = m.ry; }
    }

    private Touch firstDown() {
        for (Touch t : touches) if (t.down) return t;
        return null;
    }

    private Touch slotFor(int pointer, boolean create) {
        for (Touch t : touches) if (t.pointer == pointer && (t.down || t.released)) return t;
        if (!create) return null;
        for (Touch t : touches) if (!t.down && !t.released) { t.pointer = pointer; return t; }
        for (Touch t : touches) if (!t.down) { t.pointer = pointer; return t; }
        return null;
    }

    private void gestureOnRelease(Touch t, long now) {
        double dx = t.sx - t.startSx, dy = t.sy - t.startSy;
        double dist = Math.hypot(dx, dy);
        long dt = now - t.startTime;
        double minDist = Math.min(game.screenW, game.screenH) * 0.12;
        if (dist > minDist && dt < 600) {
            if (Math.abs(dx) > Math.abs(dy)) { if (dx < 0) swipeLeft = true; else swipeRight = true; }
            else { if (dy < 0) swipeUp = true; else swipeDown = true; }
        } else if (!t.moved && dt < 300) {
            double tol = Math.min(game.screenW, game.screenH) * 0.06;
            if (now - lastTapTime < 350 && Math.hypot(t.sx - lastTapX, t.sy - lastTapY) < tol) {
                doubleTap = true;
                lastTapTime = 0;
            } else {
                lastTapTime = now;
                lastTapX = t.sx;
                lastTapY = t.sy;
            }
        }
    }

    private void setJoystick(double x, double y) {
        joyX = x;
        joyY = y;
        boolean[] want = {x < -0.35, x > 0.35, y < -0.35, y > 0.35};
        int[] keys = {37, 39, 38, 40};
        for (int i = 0; i < 4; i++) {
            if (want[i] && !joyKeys[i]) { joyKeys[i] = true; press(keys[i]); }
            else if (!want[i] && joyKeys[i]) { joyKeys[i] = false; release(keys[i]); }
        }
    }

    /** Converts a touch's screen position into display and room coordinates. */
    void mapTouch(Touch t) {
        double[] d = game.screenToDisplay(t.sx, t.sy);
        t.dx = d[0];
        t.dy = d[1];
        double[] r = game.displayToRoom(t.dx, t.dy);
        t.rx = r[0];
        t.ry = r[1];
    }

    /** Re-maps held touches after the view moved (so mouse_x follows the camera). */
    void remap() {
        for (Touch t : touches) if (t.down) mapTouch(t);
        Touch m = firstDown();
        if (m != null) { mouseX = m.rx; mouseY = m.ry; }
    }
}
