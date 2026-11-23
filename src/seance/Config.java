package seance;

/**
 * Every number that tunes the game, in one place.
 * Set the sensor thresholds from the values you recorded in the Appendix A tests.
 */
public final class Config {
    private Config() { }

    // ---------- Run and night ----------
    public static final int START_SANITY = 100;
    public static final int NIGHTS_TO_DAWN = 7;
    public static final int SUSPECTS = 8;
    public static final int WORKING_TESTS = 3;        // physical tests that work each night (plus Contact)
    public static final long NIGHT_TIME_MS = 90000;   // each night's timer (1:30); it pauses during hunts

    // ---------- Sanity costs and rewards ----------
    public static final int PASS_COST = 4;            // a test that is passed
    public static final int FAIL_COST = 8;            // a test that fails or times out
    public static final int CONTACT_COST = 2;         // one Contact question
    public static final int WRONG_NAME_COST = 25;     // naming the wrong ghost
    public static final int ESCAPE_COST = 20;         // the night timer runs out and the ghost escapes
    public static final int CAUGHT_MID_TEST_COST = 20;// still in a test when a hunt starts
    public static final int EXPOSURE_COST = 2;        // each second exposed during a hunt
    public static final int CLEAN_NIGHT_BONUS = 10;   // no failed test, no sanity lost in a hunt

    // ---------- Hunts (milliseconds) ----------
    public static final long HUNT_GAP_MIN = 45000, HUNT_GAP_MAX = 65000;          // between hunts
    public static final long HUNT_GAP_MIN_LOW = 28000, HUNT_GAP_MAX_LOW = 38000;   // below 25 sanity
    public static final long HUNT_GRACE_MS = 3000;    // the night timer stays paused this long after a hunt
    public static final long HUNT_WARNING_MS = 5000;  // time to holster and go silent
    public static final long HUNT_ACTIVE_MS = 6000;   // how long to stay hidden before the hunt passes

    // ---------- Sensor thresholds (tune these) ----------
    // The instruments panel (I key) shows the sound as 20 bars, 40 per bar (0 to 800).
    public static final int SILENT_LEVEL = 180;       // silence: up to 4 bars (a quiet room reads around 120)
    public static final int HUNT_NOISE_LEVEL = 200;   // in a hunt, only sound above 5 bars gives you away

    // Wand heartbeat during tests: time between beats in ms (lower = faster).
    // There is none during the banishing: the motor would shake the accelerometer.
    public static final int TEST_BEAT_MS = 1300;
    public static final int SCREAM_LEVEL = 400;       // loud finish: above 10 bars (a loud snap, clap or shout right at the mic)
    public static final double DARK_RATIO = 0.35;     // dark = below 35% of the open light reading
    public static final int DIAL_TOLERANCE = 40;      // how close the dial must be to the frequency

    // ---------- Scanner field ----------
    public static final int MARK_STEP = 30;           // servo marks at 0, 30, 60 ... 180
    public static final int MARKS = 7;
    public static final int SERVO_SPEED = 12;         // ms per degree (lower is faster)

    // ---------- Timing ----------
    public static final int TICK_MS = 50;             // the game updates 20 times a second
}
