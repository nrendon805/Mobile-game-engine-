package com.droidmaker.run;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import com.droidmaker.core.project.Project;
import com.droidmaker.core.runtime.Game;

import java.io.File;

/** Full-screen activity that plays a project. */
public final class RunnerActivity extends Activity {
    public static final String EXTRA_DIR = "dir";
    /** Optional room id to start in (testing a room from the editor). */
    public static final String EXTRA_ROOM = "room";

    private GameView view;
    private SoundSystem sound;
    private AndroidPlatform platform;
    private Game game;

    public static void start(Context ctx, File projectDir) {
        Intent i = new Intent(ctx, RunnerActivity.class);
        i.putExtra(EXTRA_DIR, projectDir.getAbsolutePath());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String dir = getIntent().getStringExtra(EXTRA_DIR);
        Project project;
        try {
            project = Project.load(new File(dir));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot load project: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        int startRoom = getIntent().getIntExtra(EXTRA_ROOM, -1);
        if (startRoom >= 0 && project.room(startRoom) != null) {
            project.roomOrder.remove(Integer.valueOf(startRoom));
            project.roomOrder.add(0, startRoom);
        }
        switch (project.orientation) {
            case Project.ORIENT_PORTRAIT: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT); break;
            case Project.ORIENT_AUTO: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR); break;
            default: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        if (project.keepScreenOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sound = new SoundSystem(project);
        platform = new AndroidPlatform(this, project, sound);
        game = new Game(project, platform);
        platform.onEnded = () -> { if (!isFinishing()) finish(); };
        platform.onIgnoreAll = game::ignoreAllErrors;
        view = new GameView(this, game, project, sound, null);
        platform.setScreen(view.screen());
        setContentView(view);
        view.requestFocus();
        hideBars();
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view != null) {
            view.releaseInput();
            view.onPause();
        }
        if (sound != null) sound.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.onResume();
        if (sound != null) sound.resume();
        hideBars();
    }

    @Override
    protected void onDestroy() {
        if (platform != null) platform.close();
        if (game != null) game.running = false;
        if (sound != null) sound.release();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (game != null && game.running) game.input.postBack();
        else super.onBackPressed();
    }
}
