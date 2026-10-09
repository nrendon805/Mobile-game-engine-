package com.droidmaker.run;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.runtime.Game;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLSurfaceView that runs the game loop on the GL thread and forwards input. */
@SuppressLint("ViewConstructor")
public final class GameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final Game game;
    private final GLScreen screen;
    private final VirtualPad pad;
    private final SoundSystem sound;
    private volatile boolean started;
    private volatile boolean crashed;
    private final Runnable onCrash;

    public GameView(Context ctx, Game game, Project project, SoundSystem sound, Runnable onCrash) {
        super(ctx);
        this.game = game;
        this.sound = sound;
        this.onCrash = onCrash;
        this.screen = new GLScreen(game, project);
        this.pad = new VirtualPad(project, game.input);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    GLScreen screen() { return screen; }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        screen.init();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screen.size(width, height);
        pad.layout(width, height);
        game.setScreenSize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (crashed) {
            screen.beginFrame();
            screen.endFrame();
            return;
        }
        try {
            if (!started) {
                started = true;
                sound.awaitLoaded(3000);
                game.setScreenSize(screen.width(), screen.height());
                game.start();
            }
            game.update(System.nanoTime());
            screen.beginFrame();
            if (game.running) game.draw(screen);
            screen.beginOverlay();
            pad.draw(screen);
            screen.endFrame();
        } catch (ScriptError e) {
            game.error(e);
        } catch (Throwable t) {
            crashed = true;
            android.util.Log.e("DroidMaker", "Game crashed", t);
            if (onCrash != null) post(onCrash);
            game.platform.scriptError("The game stopped because of an internal error:\n" + t);
            game.stop();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = e.getPointerId(idx);
                float x = e.getX(idx), y = e.getY(idx);
                if (!pad.down(id, x, y)) game.input.postTouchDown(id, x, y);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    float x = e.getX(i), y = e.getY(i);
                    if (!pad.move(id, x, y)) game.input.postTouchMove(id, x, y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = e.getPointerId(idx);
                if (!pad.up(id)) game.input.postTouchUp(id, e.getX(idx), e.getY(idx));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                pad.releaseAll();
                for (int i = 0; i < e.getPointerCount(); i++) game.input.postTouchUp(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
        }
        return true;
    }

    /** Maps Android key codes to Game Maker virtual key codes. */
    static int vk(int code) {
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) return 'A' + (code - KeyEvent.KEYCODE_A);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return '0' + (code - KeyEvent.KEYCODE_0);
        if (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12) return 112 + (code - KeyEvent.KEYCODE_F1);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return 37;
            case KeyEvent.KEYCODE_DPAD_UP: return 38;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 39;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 40;
            case KeyEvent.KEYCODE_SPACE: return 32;
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER: return 13;
            case KeyEvent.KEYCODE_ESCAPE: return 27;
            case KeyEvent.KEYCODE_DEL: return 8;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 46;
            case KeyEvent.KEYCODE_TAB: return 9;
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT: return 16;
            case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_CTRL_RIGHT: return 17;
            case KeyEvent.KEYCODE_ALT_LEFT: case KeyEvent.KEYCODE_ALT_RIGHT: return 18;
            case KeyEvent.KEYCODE_PAGE_UP: return 33;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 34;
            case KeyEvent.KEYCODE_MOVE_HOME: return 36;
            case KeyEvent.KEYCODE_MOVE_END: return 35;
            case KeyEvent.KEYCODE_INSERT: return 45;
        }
        return -1;
    }

    static int gamepadButton(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return 0;
            case KeyEvent.KEYCODE_BUTTON_B: return 1;
            case KeyEvent.KEYCODE_BUTTON_X: return 2;
            case KeyEvent.KEYCODE_BUTTON_Y: return 3;
        }
        return -1;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { game.input.postBack(); return true; }
        int b = gamepadButton(keyCode);
        if (b >= 0) { if (event.getRepeatCount() == 0) game.input.postVButtonDown(b); return true; }
        int k = vk(keyCode);
        if (k >= 0 && event.getRepeatCount() == 0) game.input.postKeyDown(k);
        int ch = event.getUnicodeChar();
        if (keyCode == KeyEvent.KEYCODE_DEL) ch = 8;
        if (ch > 0) game.input.postChar(ch);
        return k >= 0 || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        int b = gamepadButton(keyCode);
        if (b >= 0) { game.input.postVButtonUp(b); return true; }
        int k = vk(keyCode);
        if (k >= 0) game.input.postKeyUp(k);
        return k >= 0 || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        // physical gamepad sticks feed the virtual joystick
        if ((e.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK
                && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f) { x = hx; y = hy; }
            if (Math.abs(x) < 0.15f) x = 0;
            if (Math.abs(y) < 0.15f) y = 0;
            game.input.postJoystick(x, y);
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    void releaseInput() {
        pad.releaseAll();
        game.input.postReleaseAll();
    }
}
