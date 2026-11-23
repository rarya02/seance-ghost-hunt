package seance;

/**
 * Contact: call the spirit by holding the grip for 3 seconds. The spirit answers with a riddle
 * about one of the hidden ghost's traits that no test has proven yet. The riddle is always
 * true, but it never says which trait it is about: the player has to work out both the trait
 * and its value, and cross out the suspects themselves.
 * A riddle never crosses anyone out on the board and never changes the multiplier.
 *
 * After the spirit speaks, the riddle stays on screen until the player taps the grip.
 * The riddle is recorded the moment it is spoken, so that trait never gets a second riddle
 * tonight, even if a hunt interrupts the reading.
 */
public class Contact extends Test {
    private static final int CALL = 0, SPEAK = 1;
    private static final long HOLD_MS = 3000;
    private static final long READ_MS = 30000;          // the riddle closes by itself after this long
    private static final long RELEASE_GRACE_MS = 300;   // a blip of "released" this short does not reset the hold
    private int phase;
    private long holdSince = -1;
    private long lastTouchAt = -1;
    private long spokeAt;
    private int trait = Traits.REACH;
    private boolean value;
    private String riddle = "";

    public Contact(Subject subject) {
        super(subject);                  // registers with the hub, as on the slides
    }

    /** Long enough to hold the grip and read the riddle. */
    protected long baseTimeMs() { return 60000; }

    protected void begin(Reading r) {
        phase = CALL;
        holdSince = -1;
    }

    protected int perform(Reading r) {
        long now = r.getTime();
        if (phase == SPEAK) {
            // the player taps the grip when they have read it
            if (r.getTaps() > 0 || now - spokeAt >= READ_MS) return PASSED;
            return RUNNING;
        }
        if (r.isTouch()) {
            lastTouchAt = now;
            if (holdSince < 0) holdSince = now;
        } else if (holdSince >= 0 && now - lastTouchAt > RELEASE_GRACE_MS) {
            holdSince = -1;                               // really let go
        }
        if (holdSince >= 0 && now - holdSince >= HOLD_MS) speak(now);
        return RUNNING;
    }

    /** The spirit picks a trait it has not spoken about and no test has proven, and answers. */
    private void speak(long now) {
        int[] choices = night.contactTraits();
        trait = choices[(int) (Math.random() * choices.length)];
        value = night.getHidden().has(trait);
        riddle = night.riddleFor(trait, value);
        night.recordRiddle(trait, riddle);                // kept at once, even if a hunt cuts the reading short
        phase = SPEAK;
        spokeAt = now;
        hw.rig.led(160, 0, 255);                          // the spirit's colour while it speaks
    }

    /** Debug mode: the spirit speaks at once, without holding the grip. */
    public void debugSpeak(long now) {
        if (phase == CALL && getResult() == RUNNING) {
            this.now = now;
            speak(now);
        }
    }

    protected void end() { }

    public String getName() { return "Contact"; }
    public String getInstructions() {
        if (phase == CALL) return "Hold the grip for 3 s to call the spirit. It will answer in a riddle.";
        return "The spirit speaks. Tap the grip when you have read its riddle.";
    }
    /** The trait the riddle is about (secret: only the Night uses it). */
    public int getTrait() { return trait; }
    public boolean answer() { return value; }

    public boolean isSpeaking() { return phase == SPEAK; }
    public String getRiddle() { return riddle; }

    public double progress() {
        if (phase == SPEAK) return 1.0;
        return holdSince < 0 ? 0 : Math.min(1.0, (now - holdSince) / (double) HOLD_MS);
    }
    public String status() { return phase == SPEAK ? "" : (holdSince < 0 ? "hold the grip" : "the spirit is coming..."); }

    /** No heartbeat during Contact: the vibration sits next to the touch sensor that is being held. */
    protected boolean usesHeartbeat() { return false; }

    public int passCost() { return Config.CONTACT_COST; }
    public int failCost() { return Config.CONTACT_COST; }
}
