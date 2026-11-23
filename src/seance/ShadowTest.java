package seance;

import java.util.Random;

/**
 * Shadow Test (confirms Aversion): follow the calls. The screen calls out DARK or LIGHT in a
 * random rhythm: cover the wand's light sensor for DARK, uncover it for LIGHT, and hold each
 * until its bar fills (1 to 2 seconds). There are 4 to 6 steps, starting with DARK.
 * Getting a step wrong restarts that step. The rig beeps at each change.
 */
public class ShadowTest extends Test {
    private static final Random RNG = new Random();
    private boolean[] dark;          // what each step needs: true = covered, false = uncovered
    private long[] lengthMs;         // how long each step must be held
    private int threshold;
    private int step;
    private long rightSince = -1;    // when the sensor started matching the current step

    public ShadowTest(Subject subject) {
        super(subject);                  // registers with the hub, as on the slides
    }

    protected long baseTimeMs() { return 25000; }

    protected void begin(Reading r) {
        int open = Math.max(r.getLight(), 60);          // the room's light right now
        threshold = (int) (open * Config.DARK_RATIO);
        int steps = 4 + RNG.nextInt(3);                 // 4, 5 or 6 steps
        dark = new boolean[steps];
        lengthMs = new long[steps];
        for (int i = 0; i < steps; i++) {
            dark[i] = i % 2 == 0;                       // DARK, LIGHT, DARK, LIGHT ...
            lengthMs[i] = 1000 + RNG.nextInt(3) * 500;  // 1, 1.5 or 2 seconds
        }
        step = 0;
        rightSince = -1;
    }

    protected int perform(Reading r) {
        long now = r.getTime();
        boolean isDark = r.getLight() < threshold;      // this test only needs the light sensor
        if (isDark == dark[step]) {
            if (rightSince < 0) rightSince = now;
            if (now - rightSince >= lengthMs[step]) {
                step++;
                rightSince = -1;
                if (step == dark.length) return PASSED;
                hw.rig.beep(60);                        // the call changes
            }
        } else {
            rightSince = -1;                            // wrong: this step starts over
        }
        return RUNNING;
    }

    protected void end() { }

    public String getName() { return "Shadow Test"; }
    public String getInstructions() {
        return "Follow the calls: cover the light sensor for DARK, uncover it for LIGHT. Hold each until its bar fills.";
    }
    public int getTrait() { return Traits.AVERSION; }

    public double progress() { return dark == null ? 0 : (step + stepProgress()) / dark.length; }
    public String status() {
        if (dark == null || step >= dark.length) return "";
        return (dark[step] ? "DARK: cover it" : "LIGHT: uncover it") + (rightSince >= 0 ? "... hold" : "");
    }

    // ---------- For the screen ----------
    public int getStepCount() { return dark == null ? 0 : dark.length; }
    public int getStep() { return step; }
    public boolean isDarkStep(int i) { return dark[i]; }
    public long getStepLength(int i) { return lengthMs[i]; }
    /** How far the current step's bar has filled, 0 to 1. */
    public double stepProgress() {
        if (dark == null || step >= dark.length || rightSince < 0) return 0;
        return Math.min(1.0, (now - rightSince) / (double) lengthMs[step]);
    }
}
