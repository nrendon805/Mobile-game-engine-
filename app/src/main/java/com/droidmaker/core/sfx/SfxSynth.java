package com.droidmaker.core.sfx;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Retro sound effect synthesizer (a port of the classic sfxr algorithm). Produces 16-bit mono
 * 44.1 kHz WAV data from a small set of parameters, with presets for common game sounds.
 */
public final class SfxSynth {
    public int wave = 0; // 0 square, 1 saw, 2 sine, 3 noise
    public double baseFreq = 0.3, freqLimit = 0, freqRamp = 0, freqDramp = 0;
    public double duty = 0, dutyRamp = 0;
    public double vibStrength = 0, vibSpeed = 0;
    public double attack = 0, sustain = 0.3, punch = 0, decay = 0.4;
    public double lpfFreq = 1, lpfRamp = 0, lpfResonance = 0;
    public double hpfFreq = 0, hpfRamp = 0;
    public double phaserOffset = 0, phaserRamp = 0;
    public double repeatSpeed = 0;
    public double arpSpeed = 0, arpMod = 0;
    public double volume = 0.5;

    public static final String[] PRESETS = {"Coin", "Laser", "Explosion", "Power-up", "Hit", "Jump", "Blip", "Random"};
    public static final String[] WAVES = {"Square", "Saw", "Sine", "Noise"};

    private final Random rnd;

    public SfxSynth(long seed) { rnd = new Random(seed); }

    private double frnd(double r) { return rnd.nextDouble() * r; }
    private int rnd(int n) { return rnd.nextInt(n + 1); }

    public void reset() {
        wave = 0; baseFreq = 0.3; freqLimit = 0; freqRamp = 0; freqDramp = 0; duty = 0; dutyRamp = 0;
        vibStrength = 0; vibSpeed = 0; attack = 0; sustain = 0.3; punch = 0; decay = 0.4;
        lpfFreq = 1; lpfRamp = 0; lpfResonance = 0; hpfFreq = 0; hpfRamp = 0; phaserOffset = 0; phaserRamp = 0;
        repeatSpeed = 0; arpSpeed = 0; arpMod = 0;
    }

    public void preset(int k) {
        reset();
        switch (k) {
            case 0: // coin
                baseFreq = 0.4 + frnd(0.5);
                attack = 0; sustain = frnd(0.1); decay = 0.1 + frnd(0.4); punch = 0.3 + frnd(0.3);
                if (rnd(1) == 1) { arpSpeed = 0.5 + frnd(0.2); arpMod = 0.2 + frnd(0.4); }
                break;
            case 1: // laser
                wave = rnd(2);
                if (wave == 2 && rnd(1) == 1) wave = rnd(1);
                baseFreq = 0.5 + frnd(0.5); freqLimit = baseFreq - 0.2 - frnd(0.6); if (freqLimit < 0.2) freqLimit = 0.2;
                freqRamp = -0.15 - frnd(0.2);
                if (rnd(2) == 0) { baseFreq = 0.3 + frnd(0.6); freqLimit = frnd(0.1); freqRamp = -0.35 - frnd(0.3); }
                if (rnd(1) == 1) { duty = frnd(0.5); dutyRamp = frnd(0.2); } else { duty = 0.4 + frnd(0.5); dutyRamp = -frnd(0.7); }
                attack = 0; sustain = 0.1 + frnd(0.2); decay = frnd(0.4);
                if (rnd(1) == 1) punch = frnd(0.3);
                if (rnd(2) == 0) { phaserOffset = frnd(0.2); phaserRamp = -frnd(0.2); }
                if (rnd(1) == 1) hpfFreq = frnd(0.3);
                break;
            case 2: // explosion
                wave = 3;
                if (rnd(1) == 1) { baseFreq = 0.1 + frnd(0.4); freqRamp = -0.1 + frnd(0.4); }
                else { baseFreq = 0.2 + frnd(0.7); freqRamp = -0.2 - frnd(0.2); }
                baseFreq *= baseFreq;
                if (rnd(4) == 0) freqRamp = 0;
                if (rnd(2) == 0) repeatSpeed = 0.3 + frnd(0.5);
                attack = 0; sustain = 0.1 + frnd(0.3); decay = frnd(0.5);
                if (rnd(1) == 0) { phaserOffset = -0.3 + frnd(0.9); phaserRamp = -frnd(0.3); }
                punch = 0.2 + frnd(0.6);
                if (rnd(1) == 1) { vibStrength = frnd(0.7); vibSpeed = frnd(0.6); }
                if (rnd(2) == 0) { arpSpeed = 0.6 + frnd(0.3); arpMod = 0.8 - frnd(1.6); }
                break;
            case 3: // power-up
                if (rnd(1) == 1) wave = 1; else duty = frnd(0.6);
                if (rnd(1) == 1) { baseFreq = 0.2 + frnd(0.3); freqRamp = 0.1 + frnd(0.4); repeatSpeed = 0.4 + frnd(0.4); }
                else { baseFreq = 0.2 + frnd(0.3); freqRamp = 0.05 + frnd(0.2); if (rnd(1) == 1) { vibStrength = frnd(0.7); vibSpeed = frnd(0.6); } }
                attack = 0; sustain = frnd(0.4); decay = 0.1 + frnd(0.4);
                break;
            case 4: // hit
                wave = rnd(2);
                if (wave == 2) wave = 3;
                if (wave == 0) duty = frnd(0.6);
                baseFreq = 0.2 + frnd(0.6); freqRamp = -0.3 - frnd(0.4);
                attack = 0; sustain = frnd(0.1); decay = 0.1 + frnd(0.2);
                if (rnd(1) == 1) hpfFreq = frnd(0.3);
                break;
            case 5: // jump
                wave = 0; duty = frnd(0.6);
                baseFreq = 0.3 + frnd(0.3); freqRamp = 0.1 + frnd(0.2);
                attack = 0; sustain = 0.1 + frnd(0.3); decay = 0.1 + frnd(0.2);
                if (rnd(1) == 1) hpfFreq = frnd(0.3);
                if (rnd(1) == 1) lpfFreq = 1 - frnd(0.6);
                break;
            case 6: // blip
                wave = rnd(1);
                if (wave == 0) duty = frnd(0.6);
                baseFreq = 0.2 + frnd(0.4);
                attack = 0; sustain = 0.1 + frnd(0.1); decay = frnd(0.2); hpfFreq = 0.1;
                break;
            default: // random
                wave = rnd(3);
                baseFreq = Math.pow(frnd(2) - 1, 2);
                if (rnd(1) == 1) baseFreq = Math.pow(frnd(2) - 1, 3) + 0.5;
                freqLimit = 0;
                freqRamp = Math.pow(frnd(2) - 1, 5);
                if (baseFreq > 0.7 && freqRamp > 0.2) freqRamp = -freqRamp;
                if (baseFreq < 0.2 && freqRamp < -0.05) freqRamp = -freqRamp;
                freqDramp = Math.pow(frnd(2) - 1, 3);
                duty = frnd(2) - 1; dutyRamp = Math.pow(frnd(2) - 1, 3);
                vibStrength = Math.pow(frnd(2) - 1, 3); vibSpeed = frnd(2) - 1;
                attack = Math.pow(frnd(2) - 1, 3); sustain = Math.pow(frnd(2) - 1, 2); decay = frnd(2) - 1;
                punch = Math.pow(frnd(0.8), 2);
                if (attack + sustain + decay < 0.2) { sustain += 0.2 + frnd(0.3); decay += 0.2 + frnd(0.3); }
                lpfResonance = frnd(2) - 1; lpfFreq = 1 - Math.pow(frnd(1), 3); lpfRamp = Math.pow(frnd(2) - 1, 3);
                if (lpfFreq < 0.1 && lpfRamp < -0.05) lpfRamp = -lpfRamp;
                hpfFreq = Math.pow(frnd(1), 5); hpfRamp = Math.pow(frnd(2) - 1, 5);
                phaserOffset = Math.pow(frnd(2) - 1, 3); phaserRamp = Math.pow(frnd(2) - 1, 3);
                repeatSpeed = frnd(2) - 1; arpSpeed = frnd(2) - 1; arpMod = frnd(2) - 1;
                attack = Math.abs(attack); sustain = Math.abs(sustain); decay = Math.abs(decay);
        }
    }

    // ------------------------------------------------------------------ synthesis
    private double fperiod, fmaxperiod, fslide, fdslide, squareDuty, squareSlide, arpMul;
    private int period, arpTime, arpLimit;

    private void resetSample(boolean restart) {
        if (!restart) phase = 0;
        fperiod = 100.0 / (baseFreq * baseFreq + 0.001);
        period = (int) fperiod;
        fmaxperiod = 100.0 / (freqLimit * freqLimit + 0.001);
        fslide = 1.0 - Math.pow(freqRamp, 3) * 0.01;
        fdslide = -Math.pow(freqDramp, 3) * 0.000001;
        squareDuty = 0.5 - duty * 0.5;
        squareSlide = -dutyRamp * 0.00005;
        if (arpMod >= 0) arpMul = 1.0 - Math.pow(arpMod, 2) * 0.9; else arpMul = 1.0 + Math.pow(arpMod, 2) * 10.0;
        arpTime = 0;
        arpLimit = (int) (Math.pow(1.0 - arpSpeed, 2) * 20000 + 32);
        if (arpSpeed == 1.0) arpLimit = 0;
        if (!restart) {
            fltp = 0; fltdp = 0;
            fltw = Math.pow(lpfFreq, 3) * 0.1;
            fltwd = 1.0 + lpfRamp * 0.0001;
            fltdmp = 5.0 / (1.0 + Math.pow(lpfResonance, 2) * 20.0) * (0.01 + fltw);
            if (fltdmp > 0.8) fltdmp = 0.8;
            fltphp = 0;
            flthp = Math.pow(hpfFreq, 2) * 0.1;
            flthpd = 1.0 + hpfRamp * 0.0003;
            vibPhase = 0;
            vibSpd = Math.pow(vibSpeed, 2) * 0.01;
            vibAmp = vibStrength * 0.5;
            envVol = 0; envStage = 0; envTime = 0;
            envLength[0] = (int) (attack * attack * 100000.0);
            envLength[1] = (int) (sustain * sustain * 100000.0);
            envLength[2] = (int) (decay * decay * 100000.0);
            fphase = Math.pow(phaserOffset, 2) * 1020.0;
            if (phaserOffset < 0) fphase = -fphase;
            fdphase = Math.pow(phaserRamp, 2) * 1.0;
            if (phaserRamp < 0) fdphase = -fdphase;
            iphase = Math.abs((int) fphase);
            ipp = 0;
            java.util.Arrays.fill(phaserBuffer, 0);
            for (int i = 0; i < 32; i++) noiseBuffer[i] = frnd(2) - 1;
            repTime = 0;
            repLimit = (int) (Math.pow(1.0 - repeatSpeed, 2) * 20000 + 32);
            if (repeatSpeed == 0) repLimit = 0;
        }
    }

    private int phase;
    private double fltp, fltdp, fltw, fltwd, fltdmp, fltphp, flthp, flthpd;
    private double vibPhase, vibSpd, vibAmp;
    private double envVol;
    private int envStage, envTime;
    private final int[] envLength = new int[3];
    private double fphase, fdphase;
    private int iphase, ipp;
    private final double[] phaserBuffer = new double[1024];
    private final double[] noiseBuffer = new double[32];
    private int repTime, repLimit;

    /** Renders the sound to 16-bit PCM samples at 44.1 kHz. */
    public short[] render() {
        resetSample(false);
        java.util.ArrayList<Short> out = new java.util.ArrayList<>();
        boolean playing = true;
        int maxSamples = 44100 * 6;
        while (playing && out.size() < maxSamples) {
            repTime++;
            if (repLimit != 0 && repTime >= repLimit) { repTime = 0; resetSample(true); }
            arpTime++;
            if (arpLimit != 0 && arpTime >= arpLimit) { arpLimit = 0; fperiod *= arpMul; }
            fslide += fdslide;
            fperiod *= fslide;
            if (fperiod > fmaxperiod) {
                fperiod = fmaxperiod;
                if (freqLimit > 0) playing = false;
            }
            double rfperiod = fperiod;
            if (vibAmp > 0) {
                vibPhase += vibSpd;
                rfperiod = fperiod * (1.0 + Math.sin(vibPhase) * vibAmp);
            }
            period = (int) rfperiod;
            if (period < 8) period = 8;
            squareDuty += squareSlide;
            if (squareDuty < 0) squareDuty = 0;
            if (squareDuty > 0.5) squareDuty = 0.5;
            envTime++;
            if (envTime > envLength[envStage]) {
                envTime = 0;
                envStage++;
                if (envStage == 3) { playing = false; break; }
            }
            if (envStage == 0) envVol = envLength[0] == 0 ? 1 : (double) envTime / envLength[0];
            if (envStage == 1) envVol = 1.0 + Math.pow(1.0 - (double) envTime / Math.max(1, envLength[1]), 1.0) * 2.0 * punch;
            if (envStage == 2) envVol = 1.0 - (double) envTime / Math.max(1, envLength[2]);
            fphase += fdphase;
            iphase = Math.abs((int) fphase);
            if (iphase > 1023) iphase = 1023;
            if (flthpd != 0) {
                flthp *= flthpd;
                if (flthp < 0.00001) flthp = 0.00001;
                if (flthp > 0.1) flthp = 0.1;
            }
            double ssample = 0;
            for (int si = 0; si < 8; si++) {
                double sample;
                phase++;
                if (phase >= period) {
                    phase %= period;
                    if (wave == 3) for (int i = 0; i < 32; i++) noiseBuffer[i] = frnd(2) - 1;
                }
                double fp = (double) phase / period;
                switch (wave) {
                    case 0: sample = fp < squareDuty ? 0.5 : -0.5; break;
                    case 1: sample = 1.0 - fp * 2; break;
                    case 2: sample = Math.sin(fp * 2 * Math.PI); break;
                    default: sample = noiseBuffer[phase * 32 / period];
                }
                double pp = fltp;
                fltw *= fltwd;
                if (fltw < 0) fltw = 0;
                if (fltw > 0.1) fltw = 0.1;
                if (lpfFreq != 1.0) {
                    fltdp += (sample - fltp) * fltw;
                    fltdp -= fltdp * fltdmp;
                } else {
                    fltp = sample;
                    fltdp = 0;
                }
                fltp += fltdp;
                fltphp += fltp - pp;
                fltphp -= fltphp * flthp;
                sample = fltphp;
                phaserBuffer[ipp & 1023] = sample;
                sample += phaserBuffer[(ipp - iphase + 1024) & 1023];
                ipp = (ipp + 1) & 1023;
                ssample += sample * envVol;
            }
            ssample = ssample / 8 * volume * 2.0 * 0.5;
            if (ssample > 1) ssample = 1;
            if (ssample < -1) ssample = -1;
            out.add((short) (ssample * 32000));
        }
        short[] s = new short[out.size()];
        for (int i = 0; i < s.length; i++) s[i] = out.get(i);
        return s;
    }

    public byte[] wav() { return wav(render(), 44100); }

    public static byte[] wav(short[] samples, int rate) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int dataLen = samples.length * 2;
        writeStr(b, "RIFF");
        writeInt(b, 36 + dataLen);
        writeStr(b, "WAVE");
        writeStr(b, "fmt ");
        writeInt(b, 16);
        writeShort(b, 1);
        writeShort(b, 1);
        writeInt(b, rate);
        writeInt(b, rate * 2);
        writeShort(b, 2);
        writeShort(b, 16);
        writeStr(b, "data");
        writeInt(b, dataLen);
        for (short s : samples) writeShort(b, s);
        return b.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream b, String s) { for (char c : s.toCharArray()) b.write(c); }
    private static void writeInt(ByteArrayOutputStream b, int v) { b.write(v); b.write(v >> 8); b.write(v >> 16); b.write(v >> 24); }
    private static void writeShort(ByteArrayOutputStream b, int v) { b.write(v); b.write(v >> 8); }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wave", (double) wave);
        double[] v = values();
        for (int i = 0; i < NAMES.length; i++) m.put(NAMES[i], v[i]);
        return m;
    }

    public void fromMap(Map<String, Object> m) {
        if (m == null) return;
        Object w = m.get("wave");
        if (w instanceof Number) wave = ((Number) w).intValue();
        double[] v = values();
        for (int i = 0; i < NAMES.length; i++) {
            Object o = m.get(NAMES[i]);
            if (o instanceof Number) v[i] = ((Number) o).doubleValue();
        }
        setValues(v);
    }

    /** Editable parameters in UI order: name, min, max. */
    public static final String[] NAMES = {"baseFreq", "freqLimit", "freqRamp", "freqDramp", "duty", "dutyRamp", "vibStrength",
            "vibSpeed", "attack", "sustain", "punch", "decay", "lpfFreq", "lpfRamp", "lpfResonance", "hpfFreq", "hpfRamp",
            "phaserOffset", "phaserRamp", "repeatSpeed", "arpSpeed", "arpMod", "volume"};
    public static final String[] LABELS = {"Pitch", "Min pitch", "Slide", "Slide change", "Square duty", "Duty sweep", "Vibrato depth",
            "Vibrato speed", "Attack", "Sustain", "Punch", "Decay", "Low-pass", "Low-pass sweep", "Resonance", "High-pass",
            "High-pass sweep", "Phaser", "Phaser sweep", "Repeat", "Arpeggio speed", "Arpeggio change", "Volume"};
    public static final boolean[] SIGNED = {false, false, true, true, false, true, false, false, false, false, false, false,
            false, true, false, false, true, true, true, false, false, true, false};

    public double[] values() {
        return new double[]{baseFreq, freqLimit, freqRamp, freqDramp, duty, dutyRamp, vibStrength, vibSpeed, attack, sustain,
                punch, decay, lpfFreq, lpfRamp, lpfResonance, hpfFreq, hpfRamp, phaserOffset, phaserRamp, repeatSpeed,
                arpSpeed, arpMod, volume};
    }

    public void setValues(double[] v) {
        baseFreq = v[0]; freqLimit = v[1]; freqRamp = v[2]; freqDramp = v[3]; duty = v[4]; dutyRamp = v[5];
        vibStrength = v[6]; vibSpeed = v[7]; attack = v[8]; sustain = v[9]; punch = v[10]; decay = v[11];
        lpfFreq = v[12]; lpfRamp = v[13]; lpfResonance = v[14]; hpfFreq = v[15]; hpfRamp = v[16];
        phaserOffset = v[17]; phaserRamp = v[18]; repeatSpeed = v[19]; arpSpeed = v[20]; arpMod = v[21]; volume = v[22];
    }
}
