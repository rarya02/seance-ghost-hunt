package seance;

/**
 * Stabilise (confirms Temperament): the wand shakes hard in the hand, in long full-strength
 * buzzes that start almost continuous. Tap the grip in a slow, steady rhythm and the gaps
 * between buzzes grow. A tap only counts if it comes at least half a second after the last
 * one that counted; quicker taps are ignored. That way a false touch caused by the shaking
 * (the motor sits next to the touch pad) can never undo progress. 7 calm taps reach calm.
 */
public class Stabilise extends Test {
    private static final int ON_MS = 300;          // long buzzes: the motor reaches full strength
    private static final int FAST_GAP = 60;        // at the start the shaking is almost continuous
    private static final int CALM_GAP = 900;
    private static final long MIN_TAP_GAP = 500;   // a tap sooner than this after the last counted one is ignored
    private int gap;
    private long lastTap = -1;

    public Stabilise(Subject subject) {
        super(subject);                  // registers with the hub, as on the slides
    }

    protected long baseTimeMs() { return 25000; }

    protected void begin(Reading r) {
        gap = FAST_GAP;
        lastTap = -1;
        hw.wand.pulse(ON_MS, gap);
    }

    protected int perform(Reading r) {
        long now = r.getTime();
        int taps = r.getTaps();                       // this test only needs the grip taps
        if (taps > 0 && (lastTap < 0 || now - lastTap >= MIN_TAP_GAP)) {
            gap += 120;                                   // a calm tap: the gaps between buzzes grow
            lastTap = now;
        }
        if (gap >= CALM_GAP) return PASSED;
        hw.wand.pulse(ON_MS, gap);
        return RUNNING;
    }

    protected void end() { hw.wand.stop(); }

    protected boolean usesHeartbeat() { return false; }   // the wand is already pulsing

    public String getName() { return "Stabilise"; }
    public String getInstructions() { return "The wand is shaking! Tap the grip in a slow, steady rhythm (about once a second) to calm it."; }
    public int getTrait() { return Traits.TEMPERAMENT; }

    public double progress() { return Math.min(1.0, (gap - FAST_GAP) / (double) (CALM_GAP - FAST_GAP)); }
    public String status() { return "pulse calm " + (int) (progress() * 100) + "%"; }
}
