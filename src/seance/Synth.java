package seance;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.util.Random;

/**
 * A tiny 8-bit style synthesiser. It writes notes into a buffer of samples and turns the buffer
 * into a Java Sound Clip, so the game's music and sound effects are made in code and need no
 * audio files. The waves are the ones old arcade sound chips used: square, triangle, sine, noise.
 */
public final class Synth {
    private Synth() { }

    public static final float RATE = 22050f;
    public static final int SQUARE = 0, PULSE = 1, TRIANGLE = 2, SINE = 3, NOISE = 4;

    private static final Random RNG = new Random(421);

    /** An empty buffer long enough for this many seconds. */
    public static float[] buffer(double seconds) {
        return new float[(int) (seconds * RATE) + 1];
    }

    /** The frequency of a MIDI note number (69 = A4 = 440 Hz). */
    public static double midi(int note) {
        return 440.0 * Math.pow(2, (note - 69) / 12.0);
    }

    /** A note that holds its volume, with a quick fade in and out. Pitch slides from f0 to f1. */
    public static void tone(float[] buf, double start, double dur, double f0, double f1, int wave, double vol) {
        write(buf, start, dur, f0, f1, wave, vol, false, 0);
    }

    /** A plucked note that dies away, like a bell or a plucked string. */
    public static void pluck(float[] buf, double start, double dur, double freq, int wave, double vol) {
        write(buf, start, dur, freq, freq, wave, vol, true, 0);
    }

    /** A held note with vibrato (a gentle wobble in pitch), for melodies. */
    public static void sing(float[] buf, double start, double dur, double freq, int wave, double vol) {
        write(buf, start, dur, freq, freq, wave, vol, false, 0.012);
    }

    /**
     * A heartbeat or drum thump: a low sine that drops in pitch, with a soft click on top.
     * Kept above 55 Hz so laptop speakers can actually play it.
     */
    public static void thump(float[] buf, double start, double vol) {
        write(buf, start, 0.15, 140, 55, SINE, vol, true, 0);
        write(buf, start, 0.03, 1200, 1200, NOISE, vol * 0.12, true, 0);
    }

    /** A short burst of noise, like a hi-hat. */
    public static void hat(float[] buf, double start, double vol) {
        write(buf, start, 0.035, 6000, 6000, NOISE, vol, true, 0);
    }

    private static void write(float[] buf, double start, double dur, double f0, double f1, int wave,
                              double vol, boolean decay, double vibrato) {
        int s0 = (int) (start * RATE);
        int n = (int) (dur * RATE);
        double phase = 0;
        double held = 0;
        int attack = (int) (0.004 * RATE);
        int release = (int) (Math.min(0.03, dur * 0.3) * RATE);
        for (int i = 0; i < n && s0 + i < buf.length; i++) {
            double p = i / (double) n;
            double f = f0 + (f1 - f0) * p;
            if (vibrato > 0) f *= 1 + vibrato * Math.sin(2 * Math.PI * 5.5 * i / RATE);
            double before = phase;
            phase += f / RATE;
            phase -= Math.floor(phase);
            double v;
            switch (wave) {
                case SQUARE:   v = phase < 0.5 ? 1 : -1; break;
                case PULSE:    v = phase < 0.25 ? 1 : -1; break;
                case TRIANGLE: v = phase < 0.5 ? 4 * phase - 1 : 3 - 4 * phase; break;
                case SINE:     v = Math.sin(2 * Math.PI * phase); break;
                default:
                    if (phase < before) held = RNG.nextDouble() * 2 - 1;   // new noise value each cycle
                    v = held;
            }
            double env;
            if (decay) env = Math.exp(-5.0 * p);
            else env = 1;
            if (i < attack) env *= i / (double) attack;
            if (i > n - release) env *= (n - i) / (double) release;
            buf[s0 + i] += (float) (v * vol * env);
        }
    }

    /** Turns a buffer into a Clip ready to play, or returns null if this computer has no sound. */
    public static Clip clip(float[] buf) {
        try {
            byte[] bytes = new byte[buf.length * 2];
            for (int i = 0; i < buf.length; i++) {
                int s = (int) (Math.max(-1, Math.min(1, buf[i])) * 32767);
                bytes[2 * i] = (byte) s;
                bytes[2 * i + 1] = (byte) (s >> 8);
            }
            AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, buf.length);
            Clip c = AudioSystem.getClip();
            c.open(stream);
            return c;
        } catch (Exception | Error e) {
            return null;
        }
    }
}
