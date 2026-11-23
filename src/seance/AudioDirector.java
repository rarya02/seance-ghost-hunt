package seance;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

/**
 * Plays the soundtrack: eerie above 60 sanity, tense from 25 to 60, heavy below 25, and a theme
 * on the title screen. The music cuts to silence during a hunt and comes back afterwards.
 *
 * The four tracks are composed in code with the Synth (8-bit arcade style), so no files are
 * needed. To use your own music instead, put eerie.wav, tense.wav, heavy.wav or title.wav in
 * the audio folder and it replaces that track.
 */
public class AudioDirector {
    private static final int EERIE = 0, TENSE = 1, HEAVY = 2, TITLE = 3;
    private final Clip[] tracks = new Clip[4];
    private int band = -1;
    private boolean cut = false;
    // A heartbeat that plays over the music: slow during tests, fast during the banishing
    private final Clip beatSlow, beatFast;
    private int beatLevel = 0;

    public AudioDirector(String folder) {
        String[] names = { "eerie.wav", "tense.wav", "heavy.wav", "title.wav" };
        for (int i = 0; i < tracks.length; i++) {
            File f = new File(folder, names[i]);
            if (f.exists()) tracks[i] = load(f);
            if (tracks[i] == null) tracks[i] = Synth.clip(compose(i));
        }
        beatSlow = Synth.clip(heartbeat(60, 0.35));
        beatFast = Synth.clip(heartbeat(105, 0.55));
        if (tracks[EERIE] == null) System.out.println("Audio: no sound output found, playing silently");
    }

    /** One looping heartbeat (lub-dub) at this many beats per minute. */
    private static float[] heartbeat(int bpm, double vol) {
        double period = 60.0 / bpm;
        float[] buf = Synth.buffer(period * 4);
        for (int i = 0; i < 4; i++) {
            Synth.thump(buf, i * period, vol);
            Synth.thump(buf, i * period + 0.22, vol * 0.7);
        }
        return buf;
    }

    /** 0 = no heartbeat, 1 = slow (tests), 2 = fast (banishing). Only changes when the level changes. */
    public void setHeartbeat(int level) {
        if (level == beatLevel) return;
        beatLevel = level;
        if (beatSlow != null) beatSlow.stop();
        if (beatFast != null) beatFast.stop();
        Clip want = level == 1 ? beatSlow : (level == 2 ? beatFast : null);
        if (want != null) {
            want.setFramePosition(0);
            want.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    private static Clip load(File f) {
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(f);
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (Exception e) {
            System.out.println("Audio: could not load " + f.getPath() + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static int bandFor(int sanity) {
        if (sanity > 60) return EERIE;
        if (sanity >= 25) return TENSE;
        return HEAVY;
    }

    /** Called every tick during a night; switches track only when the sanity band changes. */
    public void update(int sanity) {
        int b = bandFor(sanity);
        if (b == band) return;
        band = b;
        if (!cut) playBand();
    }

    /** The title theme; called every tick on the title screen. */
    public void playTitle() {
        if (band == TITLE) return;
        band = TITLE;
        cut = false;
        playBand();
    }

    private void playBand() {
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] != null && i != band) tracks[i].stop();
        }
        if (band >= 0 && tracks[band] != null && !tracks[band].isRunning()) {
            tracks[band].setFramePosition(0);
            tracks[band].loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    /** Silence, for hunts. */
    public void cut() {
        cut = true;
        for (Clip c : tracks) if (c != null) c.stop();
        setHeartbeat(0);
    }

    /** Music back on after a hunt. */
    public void resume() {
        cut = false;
        playBand();
    }

    // Clips that were playing when the game was paused, to start again on resume
    private final java.util.List<Clip> held = new java.util.ArrayList<Clip>();

    /** Pause (the P key): stops whatever is playing, remembering where it was. */
    public void pause() {
        held.clear();
        Clip[] all = { tracks[0], tracks[1], tracks[2], tracks[3], beatSlow, beatFast };
        for (Clip c : all) {
            if (c != null && c.isRunning()) {
                held.add(c);
                c.stop();                         // stop() keeps the position
            }
        }
    }

    /** Resume: carries on each paused clip from where it stopped. */
    public void unpause() {
        for (Clip c : held) c.loop(Clip.LOOP_CONTINUOUSLY);
        held.clear();
    }

    public void stopAll() {
        for (Clip c : tracks) if (c != null) c.stop();
        band = -1;
        setHeartbeat(0);
    }

    // =====================================================================
    // The tracks, written note by note (MIDI note numbers, 57 = A3, 69 = A4)
    // =====================================================================
    private static float[] compose(int track) {
        switch (track) {
            case EERIE: return level(eerie());
            case TENSE: return level(tense());
            case HEAVY: return level(heavy());
            default:    return level(title());
        }
    }

    /** Evens out the loudness of the tracks, so switching band does not jump in volume. */
    private static float[] level(float[] buf) {
        double sum = 0, peak = 0;
        for (float f : buf) { sum += f * f; peak = Math.max(peak, Math.abs(f)); }
        double rms = Math.sqrt(sum / buf.length);
        double gain = Math.min(0.09 / rms, 0.85 / peak);
        for (int i = 0; i < buf.length; i++) buf[i] *= gain;
        return buf;
    }

    /** Calm but uneasy: slow arpeggios, a soft bass and a few bell notes. A minor, 84 bpm. */
    private static float[] eerie() {
        double beat = 60.0 / 84;
        int[] roots = { 45, 41, 48, 40, 45, 41, 50, 40 };
        int[][] chords = { {57, 60, 64}, {53, 57, 60}, {55, 60, 64}, {56, 59, 64},
                           {57, 60, 64}, {53, 57, 60}, {53, 57, 62}, {56, 59, 64} };
        int[][] bells = { {76, 72}, {77, 72}, {79, 76}, {80, 76}, {81, 76}, {77, 81}, {74, 77}, {76, 71} };
        float[] buf = Synth.buffer(8 * 4 * beat);
        int[] pattern = { 0, 1, 2, 1, 0, 1, 2, 1 };
        for (int bar = 0; bar < 8; bar++) {
            double t = bar * 4 * beat;
            Synth.pluck(buf, t, 4 * beat, Synth.midi(roots[bar]), Synth.TRIANGLE, 0.22);
            for (int i = 0; i < 8; i++) {
                int note = chords[bar][pattern[i]] + 12;
                Synth.pluck(buf, t + i * beat / 2, beat / 2, Synth.midi(note), Synth.PULSE, 0.045);
            }
            Synth.pluck(buf, t, 1.6 * beat, Synth.midi(bells[bar][0]), Synth.SINE, 0.08);
            Synth.pluck(buf, t + 2 * beat, 1.6 * beat, Synth.midi(bells[bar][1]), Synth.SINE, 0.06);
        }
        return buf;
    }

    /** Getting worse: a heartbeat, a pulsing bass and fast arpeggios. A minor, 120 bpm. */
    private static float[] tense() {
        double beat = 0.5;
        int[] roots = { 45, 45, 46, 45, 45, 45, 41, 40 };
        int[][] chords = { {57, 60, 64}, {57, 60, 64}, {58, 62, 65}, {57, 60, 64},
                           {57, 60, 64}, {57, 60, 63}, {53, 57, 60}, {56, 59, 64} };
        float[] buf = Synth.buffer(8 * 4 * beat);
        for (int bar = 0; bar < 8; bar++) {
            double t = bar * 4 * beat;
            for (int i = 0; i < 8; i++) {
                Synth.tone(buf, t + i * beat / 2, beat / 2 * 0.8, Synth.midi(roots[bar] - 12), Synth.midi(roots[bar] - 12), Synth.SQUARE, 0.07);
            }
            for (int i = 0; i < 16; i++) {
                int note = chords[bar][i % 3] + 12 + (i % 6 >= 3 ? 12 : 0);
                Synth.pluck(buf, t + i * beat / 4, beat / 4, Synth.midi(note), Synth.PULSE, 0.035);
            }
            Synth.thump(buf, t, 0.45);
            Synth.thump(buf, t + 0.28 * beat, 0.3);
            Synth.thump(buf, t + 2 * beat, 0.45);
            Synth.thump(buf, t + 2.28 * beat, 0.3);
        }
        return buf;
    }

    /** Panic: a low droning tritone, a racing heartbeat and a creeping chromatic line. 138 bpm. */
    private static float[] heavy() {
        double beat = 60.0 / 138;
        float[] buf = Synth.buffer(8 * 4 * beat);
        int[] creep = { 57, 58, 57, 56 };
        for (int bar = 0; bar < 8; bar++) {
            double t = bar * 4 * beat;
            int drone = bar % 2 == 0 ? 33 : 39;
            Synth.tone(buf, t, 4 * beat, Synth.midi(drone), Synth.midi(drone), Synth.SQUARE, 0.06);
            Synth.tone(buf, t, 4 * beat, Synth.midi(drone + 12), Synth.midi(drone + 12), Synth.TRIANGLE, 0.08);
            for (int i = 0; i < 16; i++) {
                Synth.pluck(buf, t + i * beat / 4, beat / 4, Synth.midi(creep[i % 4] + (bar % 4 == 3 ? 1 : 0)), Synth.PULSE, 0.03);
            }
            for (int b = 0; b < 4; b++) {
                Synth.thump(buf, t + b * beat, 0.45);
                Synth.thump(buf, t + (b + 0.25) * beat, 0.28);
            }
            if (bar % 2 == 1) {
                Synth.pluck(buf, t + 3 * beat, beat, Synth.midi(57), Synth.SQUARE, 0.05);
                Synth.pluck(buf, t + 3 * beat, beat, Synth.midi(63), Synth.SQUARE, 0.05);
            }
            Synth.tone(buf, t, 4 * beat, 3000, 3000, Synth.NOISE, 0.012);
        }
        return buf;
    }

    /** The title theme: bouncing bass, arpeggios, drums and a minor-key melody. 112 bpm. */
    private static float[] title() {
        double beat = 60.0 / 112;
        int[] roots = { 45, 43, 41, 40, 45, 43, 41, 40 };
        int[][] chords = { {57, 60, 64}, {55, 59, 62}, {53, 57, 60}, {56, 59, 64},
                           {57, 60, 64}, {55, 59, 62}, {53, 57, 60}, {56, 59, 64} };
        int[][] melody = {
            {69, -1, 72, -1, 76, -1, 74, 72},
            {71, -1, 67, -1, 71, 74, -1, -1},
            {72, -1, 69, -1, 72, 77, 76, 74},
            {76, -1, 68, -1, 71, -1, 76, -1},
            {81, -1, 79, -1, 76, -1, 72, -1},
            {74, -1, 71, -1, 67, -1, 71, 74},
            {72, -1, 77, -1, 76, -1, 74, 72},
            {71, -1, 68, -1, 64, -1, -1, -1},
        };
        float[] buf = Synth.buffer(8 * 4 * beat);
        double eighth = beat / 2;
        for (int bar = 0; bar < 8; bar++) {
            double t = bar * 4 * beat;
            for (int i = 0; i < 8; i++) {
                int bass = roots[bar] + (i % 2 == 1 ? 12 : 0);
                Synth.tone(buf, t + i * eighth, eighth * 0.85, Synth.midi(bass), Synth.midi(bass), Synth.TRIANGLE, 0.2);
                Synth.hat(buf, t + i * eighth + eighth / 2, 0.04);
            }
            for (int i = 0; i < 16; i++) {
                Synth.pluck(buf, t + i * beat / 4, beat / 4, Synth.midi(chords[bar][i % 3] + 12), Synth.PULSE, 0.03);
            }
            Synth.thump(buf, t, 0.4);
            Synth.thump(buf, t + 2 * beat, 0.4);
            for (int i = 0; i < 8; i++) {
                int note = melody[bar][i];
                if (note < 0) continue;
                int len = 1;
                while (i + len < 8 && melody[bar][i + len] < 0 && len < 2) len++;
                Synth.sing(buf, t + i * eighth, len * eighth * 0.9, Synth.midi(note), Synth.SQUARE, 0.06);
            }
        }
        return buf;
    }
}
