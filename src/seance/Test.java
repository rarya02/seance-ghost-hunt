package seance;

/**
 * A test, and an Observer of the SensorHub (a ConcreteObserver).
 *
 * A new Test object is made each time the player starts a test. Its constructor registers it
 * with the hub (super(subject), as on the slides), and start() sets it up. From then on the hub calls update()
 * with every new Reading. update() checks the time limit, then calls the test's own perform()
 * to look at the parts of the reading it needs. When the test passes or fails it removes
 * itself from the hub, and the Night reads the result with getResult().
 *
 * Each of the five tests is a subclass that fills in begin(), perform() and end().
 */
public abstract class Test extends ConcreteObserver {
    public static final int RUNNING = 0, PASSED = 1, FAILED = 2;

    protected Night night;
    protected Hardware hw;
    protected long startedAt;
    protected long limitMs;
    protected long now;
    private int result = RUNNING;

    /** Registers the new test with the hub, like the Mouse on the slides. */
    protected Test(Subject subject) {
        super(subject);
    }

    /** Sets the test up. Called by the Night straight after it makes the test. */
    public final void start(Night night, Hardware hw, Reading r) {
        this.night = night;
        this.hw = hw;
        now = r.getTime();
        startedAt = now;
        limitMs = (long) (baseTimeMs() * night.timeFactor());
        result = RUNNING;
        begin(r);
        if (usesHeartbeat()) hw.wand.beat(Config.TEST_BEAT_MS);   // a slow heartbeat in the hand
    }

    /** Observer: called by the SensorHub with every new Reading while this test is running. */
    public final void update(Reading r) {
        if (result != RUNNING) return;
        // A test made during a tick also gets that tick's Reading (it joined the list mid-loop).
        // That Reading holds the tap that chose the test, so it is skipped.
        if (r.getTime() == startedAt) return;
        now = r.getTime();
        if (now - startedAt >= limitMs) {
            finish(FAILED);
            return;
        }
        int outcome = perform(r);
        if (outcome != RUNNING) finish(outcome);
    }

    /** Stops the test early, for example when a hunt catches the player mid-test. */
    public final void abort() {
        if (result == RUNNING) finish(FAILED);
    }

    /** Ends the test and leaves the hub's list, so it gets no more readings. */
    private void finish(int outcome) {
        result = outcome;
        end();
        if (usesHeartbeat()) hw.wand.stopBeat();
        subject.removeObserver(this);
    }

    /** Debug mode: ends the test now with this result, as if the player had done it. */
    public final void forceResult(int outcome) {
        if (result == RUNNING) finish(outcome);
    }

    public int getResult() { return result; }
    public long timeLeftMs() { return Math.max(0, limitMs - (now - startedAt)); }

    // ---------- Filled in by each test ----------
    protected abstract long baseTimeMs();
    /** Set-up, with the reading at the moment the test starts. */
    protected abstract void begin(Reading r);
    /** Looks at one new reading and returns RUNNING, PASSED or FAILED. */
    protected abstract int perform(Reading r);
    /** Clean-up: stop clicks or vibration. */
    protected abstract void end();

    public abstract String getName();
    public abstract String getInstructions();
    /** The trait this test confirms. */
    public abstract int getTrait();

    /** What the test reveals about the hidden ghost's trait. Contact overrides this, because it can lie. */
    public boolean answer() { return night.getHidden().has(getTrait()); }

    /** 0 to 1, for the progress bar on screen. */
    public double progress() { return 0; }

    /** A short live status line, for the screen. */
    public String status() { return ""; }

    /** A slow heartbeat in the wand while the test runs. Stabilise turns it off (it pulses the wand itself). */
    protected boolean usesHeartbeat() { return true; }

    public int passCost() { return Config.PASS_COST; }
    public int failCost() { return Config.FAIL_COST; }
}
