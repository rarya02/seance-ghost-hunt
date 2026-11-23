package seance;

import java.util.Random;

/**
 * Frequency Scan (confirms Voice): the ghost hides on a frequency of the rig's dial.
 * The clicks speed up as the dial gets closer. Hold the dial on it for 2 seconds to lock in.
 */
public class FrequencyScan extends Test {
    private static final Random RNG = new Random();
    private static final long LOCK_MS = 2000;
    private int target;
    private long lockSince = -1;
    private int distance = 1023;

    public FrequencyScan(Subject subject) {
        super(subject);                  // registers with the hub, as on the slides
    }

    protected long baseTimeMs() { return 30000; }

    protected void begin(Reading r) {
        target = 100 + RNG.nextInt(824);
        lockSince = -1;
    }

    protected int perform(Reading r) {
        long now = r.getTime();
        distance = Math.abs(r.getDial() - target);   // this test only needs the dial
        hw.rig.clicks(40 + distance * 560 / 1023);   // closer = faster clicks
        if (distance <= Config.DIAL_TOLERANCE) {
            if (lockSince < 0) lockSince = now;
            if (now - lockSince >= LOCK_MS) {
                hw.rig.beep(300);
                return PASSED;
            }
        } else {
            lockSince = -1;
        }
        return RUNNING;
    }

    protected void end() { hw.rig.clicks(0); }

    public String getName() { return "Frequency Scan"; }
    public String getInstructions() { return "Turn the dial. The clicks speed up as you close in. Hold it on the frequency for 2 s."; }
    public int getTrait() { return Traits.VOICE; }

    public double progress() { return 1.0 - distance / 1023.0; }
    public String status() { return lockSince >= 0 ? "LOCKING ON..." : "signal " + (int) (progress() * 100) + "%"; }
}
