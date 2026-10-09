package com.droidmaker.app;

import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.droidmaker.core.project.Sound;
import com.droidmaker.core.sfx.SfxSynth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Sound editor: import audio files or generate retro effects with the built-in synthesizer. */
public final class SoundEditorActivity extends BaseActivity {
    private static final int REQ_AUDIO = 5;
    private Sound sound;
    private LinearLayout bar;
    private TextView status;
    private MediaPlayer player;
    private final SfxSynth synth = new SfxSynth(System.nanoTime());
    private final SeekBar[] sliders = new SeekBar[SfxSynth.NAMES.length];
    private TextView waveBtn, kindBtn;
    private boolean syncing;

    @Override
    protected void build(Bundle state) {
        sound = p.sound(getIntent().getIntExtra("id", -1));
        if (sound == null) { finish(); return; }
        if (sound.synth != null) synth.fromMap(sound.synth);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        bar = Ui.toolbar(this, sound.name, true);
        bar.findViewWithTag("title").setOnClickListener(v -> Dialogs.name(this, p, "Rename sound", sound.name, sound, s -> {
            p.rename(sound, s);
            Ui.setToolbarTitle(bar, s);
        }));
        TextView play = Ui.icon(this, "▶", v -> play());
        play.setTextColor(Ui.OK);
        bar.addView(play);
        bar.addView(Ui.icon(this, "■", v -> stop()));
        root.addView(bar);

        LinearLayout content = Ui.col(this);
        content.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 40));

        LinearLayout card = Ui.card(this);
        status = Ui.text(this, "", 14, Ui.DIM);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 6));
        card.addView(status);
        LinearLayout btns = Ui.row(this);
        btns.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 10));
        btns.addView(Ui.button(this, "Import audio file", Ui.ACCENT, v -> importFile()), Ui.lpw(0, Ui.WRAP, 1));
        card.addView(btns);
        kindBtn = Ui.valueButton(this, sound.kind == Sound.KIND_MUSIC ? "Music (streamed)" : "Effect (fast)", null);
        kindBtn.setOnClickListener(v -> Dialogs.choose(this, "Kind", new String[]{"Effect (fast, short sounds)", "Music (streamed, long tracks)"}, w -> {
            sound.kind = w;
            Ui.setValue(kindBtn, w == 1 ? "Music (streamed)" : "Effect (fast)");
        }));
        card.addView(Ui.labeled(this, "Kind", kindBtn));
        SeekBar vol = new SeekBar(this);
        vol.setMax(100);
        vol.setProgress((int) Math.round(sound.volume * 100));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int pr, boolean u) { sound.volume = pr / 100.0; }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(Ui.stacked(this, "Volume", vol));
        content.addView(card);

        content.addView(Ui.section(this, "Effect generator"));
        LinearLayout gen = Ui.card(this);
        GridLayout presets = new GridLayout(this);
        presets.setColumnCount(4);
        presets.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        for (int i = 0; i < SfxSynth.PRESETS.length; i++) {
            int k = i;
            TextView b = Ui.button(this, SfxSynth.PRESETS[i], v -> { synth.preset(k); syncSliders(); generate(); });
            b.setTextSize(13);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            gp.width = 0;
            gp.setMargins(4, 4, 4, 4);
            presets.addView(b, gp);
        }
        gen.addView(presets);
        LinearLayout tools = Ui.row(this);
        tools.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        tools.addView(Ui.chip(this, "Mutate", false, v -> { mutate(); generate(); }));
        tools.addView(Ui.chip(this, "Generate ▶", false, v -> generate()));
        gen.addView(tools);
        waveBtn = Ui.valueButton(this, SfxSynth.WAVES[synth.wave], null);
        waveBtn.setOnClickListener(v -> Dialogs.choose(this, "Wave", SfxSynth.WAVES, w -> { synth.wave = w; Ui.setValue(waveBtn, SfxSynth.WAVES[w]); generate(); }));
        gen.addView(Ui.labeled(this, "Wave", waveBtn));
        for (int i = 0; i < SfxSynth.NAMES.length; i++) {
            int k = i;
            SeekBar sb = new SeekBar(this);
            sb.setMax(1000);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int pr, boolean user) {
                    if (syncing || !user) return;
                    double[] v = synth.values();
                    v[k] = SfxSynth.SIGNED[k] ? pr / 500.0 - 1 : pr / 1000.0;
                    synth.setValues(v);
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) { generate(); }
            });
            sliders[i] = sb;
            gen.addView(Ui.stacked(this, SfxSynth.LABELS[i], sb));
        }
        content.addView(gen);
        root.addView(Ui.scroll(this, content), Ui.lpw(Ui.MATCH, 0, 1));
        setContentView(root);
        syncSliders();
        updateStatus();
    }

    private void syncSliders() {
        syncing = true;
        double[] v = synth.values();
        for (int i = 0; i < sliders.length; i++)
            sliders[i].setProgress((int) Math.round(SfxSynth.SIGNED[i] ? (v[i] + 1) * 500 : v[i] * 1000));
        if (waveBtn != null) Ui.setValue(waveBtn, SfxSynth.WAVES[synth.wave]);
        syncing = false;
    }

    private void mutate() {
        double[] v = synth.values();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < v.length - 1; i++) {
            if (r.nextInt(2) == 0) continue;
            v[i] += (r.nextDouble() * 2 - 1) * 0.05;
            v[i] = Math.max(SfxSynth.SIGNED[i] ? -1 : 0, Math.min(1, v[i]));
        }
        synth.setValues(v);
        syncSliders();
    }

    private void updateStatus() {
        if (sound.file == null) status.setText("No audio yet. Import a file (wav, ogg, mp3) or use a generator preset below.");
        else {
            File f = p.file(sound.file);
            status.setText((sound.synth != null ? "Generated effect" : "Imported file") + " · " + (f.length() / 1024) + " KB");
        }
    }

    private void generate() {
        try {
            byte[] wav = synth.wav();
            String rel = "snd/snd" + sound.id + "_" + UUID.randomUUID().toString().substring(0, 8) + ".wav";
            File f = p.file(rel);
            f.getParentFile().mkdirs();
            try (OutputStream o = new FileOutputStream(f)) { o.write(wav); }
            String old = sound.file;
            sound.file = rel;
            sound.synth = synth.toMap();
            sound.kind = Sound.KIND_EFFECT;
            Ui.setValue(kindBtn, "Effect (fast)");
            deleteIfUnused(old);
            App.save();
            updateStatus();
            play();
        } catch (Exception e) {
            Dialogs.toast(this, "Generation failed: " + e.getMessage());
        }
    }

    private void deleteIfUnused(String rel) {
        if (rel == null) return;
        for (Sound s : p.sounds) if (s != null && rel.equals(s.file)) return;
        p.file(rel).delete();
    }

    private void importFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Choose audio"), REQ_AUDIO);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_AUDIO || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String ext = "ogg";
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) { /* name stays unknown */ }
        if (name != null && name.contains(".")) ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        else {
            String mime = getContentResolver().getType(uri);
            if (mime != null) {
                if (mime.contains("mpeg") || mime.contains("mp3")) ext = "mp3";
                else if (mime.contains("wav")) ext = "wav";
                else if (mime.contains("mp4") || mime.contains("aac")) ext = "m4a";
            }
        }
        String rel = "snd/snd" + sound.id + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext.replaceAll("[^a-z0-9]", "");
        File f = p.file(rel);
        f.getParentFile().mkdirs();
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[32768];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
            String old = sound.file;
            sound.file = rel;
            sound.synth = null;
            if (total > 400 * 1024) {
                sound.kind = Sound.KIND_MUSIC;
                Ui.setValue(kindBtn, "Music (streamed)");
            }
            deleteIfUnused(old);
            App.save();
            updateStatus();
            if (name != null && sound.name.startsWith("snd_sound")) {
                String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                String clean = "snd_" + base.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                if (com.droidmaker.core.project.Project.isValidName(clean) && p.find(clean) == null) {
                    p.rename(sound, clean);
                    Ui.setToolbarTitle(bar, clean);
                }
            }
        } catch (Exception e) {
            Dialogs.toast(this, "Import failed: " + e.getMessage());
        }
    }

    private void play() {
        stop();
        if (sound.file == null) return;
        try {
            player = new MediaPlayer();
            player.setDataSource(p.file(sound.file).getAbsolutePath());
            player.prepare();
            player.setVolume((float) sound.volume, (float) sound.volume);
            player.start();
        } catch (Exception e) {
            Dialogs.toast(this, "Cannot play this file");
        }
    }

    private void stop() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { /* ignore */ }
            player.release();
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stop();
        App.save();
    }
}
