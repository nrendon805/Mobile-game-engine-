package com.droidmaker.run;

import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.SystemClock;

import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Sound;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Plays project sounds: short effects through SoundPool, music through MediaPlayer. */
public final class SoundSystem {
    private final Project project;
    private SoundPool pool;
    private final Map<Integer, Integer> poolIds = new HashMap<>();
    private final Map<Integer, Long> durations = new HashMap<>();
    private final Map<Integer, int[]> streams = new HashMap<>();      // sound -> last stream id
    private final Map<Integer, Long> streamEnds = new HashMap<>();    // sound -> uptime when it ends (-1 looping)
    private final Map<Integer, MediaPlayer> players = new HashMap<>();
    private final Map<Integer, Float> volumes = new HashMap<>();
    private final Map<Integer, Float> pitches = new HashMap<>();
    private boolean released;

    public SoundSystem(Project project) {
        this.project = project;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((sp, id, status) -> {
            synchronized (loadLock) { pending--; loadLock.notifyAll(); }
        });
        for (Sound s : project.sounds) {
            if (s == null || s.file == null || s.kind != Sound.KIND_EFFECT) continue;
            File f = project.file(s.file);
            if (!f.exists()) continue;
            try {
                synchronized (loadLock) { pending++; }
                poolIds.put(s.id, pool.load(f.getAbsolutePath(), 1));
                durations.put(s.id, duration(f));
            } catch (Exception ignored) {
                // unreadable file: the sound simply stays silent
            }
        }
    }

    private final Object loadLock = new Object();
    private int pending;

    /** Waits (off the main thread) until effects are decoded, so the first sound_play works. */
    public void awaitLoaded(long maxMs) {
        long end = SystemClock.uptimeMillis() + maxMs;
        synchronized (loadLock) {
            while (pending > 0) {
                long left = end - SystemClock.uptimeMillis();
                if (left <= 0) break;
                try { loadLock.wait(left); } catch (InterruptedException e) { return; }
            }
        }
    }

    private static long duration(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 1000 : Long.parseLong(d);
        } catch (Exception e) {
            return 1000;
        } finally {
            try { r.release(); } catch (Exception ignored) { /* ignore */ }
        }
    }

    private float vol(int snd) {
        Sound s = project.sound(snd);
        Float v = volumes.get(snd);
        return (float) Math.max(0, Math.min(1, (s == null ? 1 : s.volume) * (v == null ? 1 : v)));
    }

    public synchronized void play(int snd, boolean loop) {
        if (released) return;
        Sound s = project.sound(snd);
        if (s == null || s.file == null) return;
        if (s.kind == Sound.KIND_MUSIC || !poolIds.containsKey(snd)) {
            MediaPlayer mp = players.get(snd);
            try {
                if (mp == null) {
                    File f = project.file(s.file);
                    if (!f.exists()) return;
                    mp = new MediaPlayer();
                    mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                    mp.setDataSource(f.getAbsolutePath());
                    mp.prepare();
                    players.put(snd, mp);
                } else if (mp.isPlaying()) {
                    mp.seekTo(0);
                }
                mp.setLooping(loop);
                float v = vol(snd);
                mp.setVolume(v, v);
                mp.start();
            } catch (Exception e) {
                players.remove(snd);
            }
            return;
        }
        Integer id = poolIds.get(snd);
        float v = vol(snd);
        Float p = pitches.get(snd);
        int stream = pool.play(id, v, v, 1, loop ? -1 : 0, p == null ? 1f : p);
        if (stream == 0) return;
        int[] prev = streams.get(snd);
        if (loop && prev != null) pool.stop(prev[0]);
        streams.put(snd, new int[]{stream});
        Long d = durations.get(snd);
        streamEnds.put(snd, loop ? -1L : SystemClock.uptimeMillis() + (d == null ? 1000 : (long) (d / (p == null ? 1f : p))));
    }

    public synchronized void stop(int snd) {
        MediaPlayer mp = players.get(snd);
        if (mp != null) {
            try { if (mp.isPlaying()) mp.pause(); mp.seekTo(0); } catch (Exception ignored) { /* ignore */ }
        }
        int[] st = streams.remove(snd);
        if (st != null) pool.stop(st[0]);
        streamEnds.remove(snd);
    }

    public synchronized void stopAll() {
        for (MediaPlayer mp : players.values()) {
            try { if (mp.isPlaying()) mp.pause(); mp.seekTo(0); } catch (Exception ignored) { /* ignore */ }
        }
        if (pool != null) for (int[] st : streams.values()) pool.stop(st[0]);
        streams.clear();
        streamEnds.clear();
    }

    public synchronized boolean isPlaying(int snd) {
        MediaPlayer mp = players.get(snd);
        if (mp != null) {
            try { if (mp.isPlaying()) return true; } catch (Exception ignored) { /* ignore */ }
        }
        Long end = streamEnds.get(snd);
        return end != null && (end < 0 || SystemClock.uptimeMillis() < end);
    }

    public synchronized void volume(int snd, double v) {
        volumes.put(snd, (float) v);
        float vv = vol(snd);
        MediaPlayer mp = players.get(snd);
        if (mp != null) mp.setVolume(vv, vv);
        int[] st = streams.get(snd);
        if (st != null) pool.setVolume(st[0], vv, vv);
    }

    public synchronized void pitch(int snd, double p) {
        float pp = (float) Math.max(0.5, Math.min(2.0, p));
        pitches.put(snd, pp);
        int[] st = streams.get(snd);
        if (st != null) pool.setRate(st[0], pp);
    }

    private final Map<Integer, Boolean> pausedMusic = new HashMap<>();

    public synchronized void pause() {
        pausedMusic.clear();
        for (Map.Entry<Integer, MediaPlayer> e : players.entrySet()) {
            try {
                if (e.getValue().isPlaying()) { e.getValue().pause(); pausedMusic.put(e.getKey(), true); }
            } catch (Exception ignored) { /* ignore */ }
        }
        if (pool != null) pool.autoPause();
    }

    public synchronized void resume() {
        for (Integer k : pausedMusic.keySet()) {
            MediaPlayer mp = players.get(k);
            try { if (mp != null) mp.start(); } catch (Exception ignored) { /* ignore */ }
        }
        pausedMusic.clear();
        if (pool != null) pool.autoResume();
    }

    public synchronized void release() {
        released = true;
        for (MediaPlayer mp : players.values()) {
            try { mp.release(); } catch (Exception ignored) { /* ignore */ }
        }
        players.clear();
        if (pool != null) { pool.release(); pool = null; }
    }
}
