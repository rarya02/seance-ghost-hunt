package seance;

import java.util.Random;

/**
 * Sweep (confirms Presence): the servo hides the ghost's signal magnet under one random mark
 * and it stays there. The player sweeps the wand over the field; the rig clicks fast while the
 * wand's Hall sensor is over the magnet. Stay on it for 1 second to pass (2 seconds with the
 * Weak Signal omen).
 */
public class Sweep extends Test {
    private static final Random RNG = new Random();
    private long holdMs = 1000;
    private int mark;
    private long onSince = -1;
    private boolean onSignal = false;

    public Sweep(Subject subject) {
        super(subject);                  // registers with the hub, as on the slides
    }

    protected long baseTimeMs() { return 30000; }

    protected void begin(Reading r) {
        mark = RNG.nextInt(Config.MARKS);
        hw.rig.moveToMark(mark);                // placed once; it does not move again
        holdMs = night.sweepHoldMs();
        onSince = -1;
        onSignal = false;
    }

    protected int perform(Reading r) {
        long now = r.getTime();
        onSignal = r.isHall();             // this test only needs the Hall sensor
        if (onSignal) {
            if (onSince < 0) onSince = now;
            hw.rig.clicks(60);
            if (now - onSince >= holdMs) return PASSED;
        } else {
            onSince = -1;
            hw.rig.clicks(0);
        }
        return RUNNING;
    }

    protected void end() { hw.rig.clicks(0); }

    public String getName() { return "Sweep"; }
    public String getInstructions() {
        return "Sweep the wand over the scanner field. When it clicks, stay on the signal for " + (holdMs / 1000) + " s.";
    }
    public int getTrait() { return Traits.PRESENCE; }

    public double progress() { return onSince < 0 ? 0 : Math.min(1.0, (now - onSince) / (double) holdMs); }
    public String status() { return onSignal ? "SIGNAL FOUND, hold still" : "searching..."; }
}
