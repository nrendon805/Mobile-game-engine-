package com.droidmaker.run;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.droidmaker.core.project.Project;
import com.droidmaker.core.runtime.Platform;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Android services for the running game: sound, dialogs, storage, vibration. */
final class AndroidPlatform implements Platform {
    private final Activity act;
    private final Project project;
    private final SoundSystem sound;
    private final SharedPreferences prefs;
    private final File dataDir;
    private volatile GLScreen screen;
    private volatile boolean closing;
    private final java.util.List<CountDownLatch> waiting = new java.util.concurrent.CopyOnWriteArrayList<>();
    Runnable onEnded;
    Runnable onIgnoreAll;

    AndroidPlatform(Activity act, Project project, SoundSystem sound) {
        this.act = act;
        this.project = project;
        this.sound = sound;
        String key = project.dir == null ? project.name : project.dir.getName();
        prefs = act.getSharedPreferences("game_" + key, Context.MODE_PRIVATE);
        dataDir = new File(act.getFilesDir(), "gamedata/" + key);
        dataDir.mkdirs();
    }

    void setScreen(GLScreen s) { screen = s; }

    /** Releases any game thread waiting on a dialog (activity is closing). */
    void close() {
        closing = true;
        for (CountDownLatch l : waiting) l.countDown();
    }

    @Override public long timeMillis() { return android.os.SystemClock.uptimeMillis(); }

    @Override public void soundPlay(int snd, boolean loop) { sound.play(snd, loop); }
    @Override public void soundStop(int snd) { sound.stop(snd); }
    @Override public void soundStopAll() { sound.stopAll(); }
    @Override public boolean soundIsPlaying(int snd) { return sound.isPlaying(snd); }
    @Override public void soundVolume(int snd, double v) { sound.volume(snd, v); }
    @Override public void soundPitch(int snd, double p) { sound.pitch(snd, p); }

    private interface DialogBuilder { void build(CountDownLatch done); }

    private void block(DialogBuilder b) {
        if (closing) return;
        CountDownLatch l = new CountDownLatch(1);
        waiting.add(l);
        act.runOnUiThread(() -> {
            if (act.isFinishing() || closing) { l.countDown(); return; }
            try { b.build(l); } catch (Exception e) { l.countDown(); }
        });
        try { l.await(); } catch (InterruptedException ignored) { /* resume */ }
        waiting.remove(l);
    }

    private AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert).setCancelable(false);
    }

    @Override public void showMessage(String msg) {
        block(done -> dlg().setMessage(msg).setPositiveButton("OK", (d, w) -> done.countDown()).show());
    }

    @Override public boolean showQuestion(String msg) {
        AtomicReference<Boolean> r = new AtomicReference<>(false);
        block(done -> dlg().setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> { r.set(true); done.countDown(); })
                .setNegativeButton("No", (d, w) -> done.countDown()).show());
        return r.get();
    }

    @Override public String getString(String msg, String def) {
        AtomicReference<String> r = new AtomicReference<>(def);
        block(done -> {
            EditText e = new EditText(act);
            e.setText(def);
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setSelectAllOnFocus(true);
            dlg().setMessage(msg).setView(e)
                    .setPositiveButton("OK", (d, w) -> { r.set(e.getText().toString()); done.countDown(); })
                    .setNegativeButton("Cancel", (d, w) -> done.countDown()).show();
        });
        return r.get();
    }

    @Override public void toast(String msg) {
        act.runOnUiThread(() -> Toast.makeText(act, msg, Toast.LENGTH_SHORT).show());
    }

    @Override public void vibrate(int ms) {
        Vibrator v = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(ms);
    }

    @Override public void openUrl(String url) {
        act.runOnUiThread(() -> {
            try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { /* no browser */ }
        });
    }

    @Override public String loadValue(String key) { return prefs.getString(key, null); }
    @Override public void saveValue(String key, String value) { prefs.edit().putString(key, value).apply(); }
    @Override public void deleteValue(String key) { prefs.edit().remove(key).apply(); }

    private File safeFile(String name) {
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        if (n.isEmpty() || n.equals("..")) n = "file";
        return new File(dataDir, n);
    }

    @Override public String readFile(String name) {
        File f = safeFile(name);
        if (!f.exists()) return null;
        try { return Project.readText(f); } catch (Exception e) { return null; }
    }

    @Override public void writeFile(String name, String text) {
        try (FileOutputStream o = new FileOutputStream(safeFile(name))) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log("writeFile failed: " + e);
        }
    }

    @Override public boolean fileExists(String name) { return safeFile(name).exists(); }
    @Override public void deleteFile(String name) { safeFile(name).delete(); }

    @Override public int[] spritePixels(int sprite, int frame) {
        GLScreen s = screen;
        return s == null ? null : s.tex.pixels(false, sprite, frame);
    }

    @Override public int[] backgroundPixels(int bg) {
        GLScreen s = screen;
        return s == null ? null : s.tex.pixels(true, bg, 0);
    }

    @Override public void gameEnded() {
        if (onEnded != null) act.runOnUiThread(onEnded);
    }

    @Override public boolean scriptError(String message) {
        AtomicReference<Integer> choice = new AtomicReference<>(0);
        block(done -> {
            TextView tv = new TextView(act);
            tv.setText(message);
            tv.setTextIsSelectable(true);
            tv.setTextSize(14);
            int p = (int) (16 * act.getResources().getDisplayMetrics().density);
            tv.setPadding(p, p, p, p);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            ScrollView sv = new ScrollView(act);
            sv.addView(tv);
            dlg().setTitle("Game error").setView(sv)
                    .setPositiveButton("Continue", (d, w) -> { choice.set(0); done.countDown(); })
                    .setNeutralButton("Ignore all", (d, w) -> { choice.set(1); done.countDown(); })
                    .setNegativeButton("Stop game", (d, w) -> { choice.set(2); done.countDown(); })
                    .show();
        });
        if (closing) return false;
        if (choice.get() == 1 && onIgnoreAll != null) onIgnoreAll.run();
        return choice.get() != 2;
    }

    @Override public void log(String msg) { Log.i("DroidMakerGame", msg); }
}
