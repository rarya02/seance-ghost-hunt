package seance;

/**
 * Banishing the named ghost. For each of the ghost's arrows: tap the grip once, then flick
 * the wand that way (the pose only has to be seen for FLICK_MS, about two of the wand's
 * readings, so a quick flick is enough). A wrong direction is simply ignored.
 * The clock starts with the first tap: a restless ghost gives 6 seconds, a patient one 9.
 * Too slow and the arrows start again.
 * Then finish it: a loud ghost needs a scream, a silent ghost needs 2 seconds of silence.
 * The music stops for the finish either way, so the microphone never hears it.
 * The finish waits until the player manages it.
 * The wand is kept silent (motor off) for the whole banishing, because the motor would shake
 * the accelerometer. The night's timer keeps running, so the ghost can still escape.
 *
 * It is an Observer of the SensorHub (a ConcreteObserver): it registers itself when it is created and removes
 * itself when the ghost is banished. It reads the taps, the pose and the sound.
 */
public class Banishing extends ConcreteObserver {
    public static final int SEQUENCE = 0, FINISH = 1, DONE = 2;
    // How long the right pose must be seen to count. The wand reports 10 times a second and
    // the hub ticks 20 times a second, so 150 ms means the pose was in two readings in a row:
    // a quick flick counts, but a wobble passing through that direction does not.
    private static final long FLICK_MS = 150;
    public static final long SILENCE_MS = 2000;
    private static final long LOUDER_AFTER_MS = 4000;     // when to nudge a quiet scream

    private final Ghost ghost;
    private final Hardware hw;
    private final long limitMs;
    private int phase = SEQUENCE;
    private int step = 0;
    private boolean started = false;
    private boolean armed = false;        // tapped for the current arrow, now waiting for the pose
    private long seqStart, poseSince = -1, finishStart, quietSince = -1;
    private final long createdAt;
    private String note = "";
    private long now;

    public Banishing(Ghost ghost, Hardware hw, long now) {
        super(hw.hub);                           // registers with the hub, as on the slides
        this.ghost = ghost;
        this.createdAt = now;
        this.now = now;
        this.hw = hw;
        limitMs = ghost.isRestless() ? 6000 : 9000;
        hw.wand.setSilent(true);                 // motor forced off for the whole banishing
    }

    /** Observer: checks the pose and the sound in every new Reading. */
    public void update(Reading in) {
        if (phase == DONE) return;
        // Made during a tick, it also gets that tick's Reading, which holds the tap that named
        // the ghost. Skip it, so that tap does not arm the first arrow.
        if (in.getTime() == createdAt) return;
        now = in.getTime();
        if (phase == SEQUENCE) {
            // a tap arms the current arrow (the first tap also starts the clock)
            if (!armed && in.getTaps() > 0) {
                armed = true;
                poseSince = -1;
                note = "";
                if (!started) {
                    started = true;
                    seqStart = now;
                }
                hw.rig.beep(40);
            }
            if (started && now - seqStart > limitMs) { restart("Too slow"); return; }
            if (armed) {
                if (in.getPose().equals(ghost.getSequence()[step])) {
                    if (poseSince < 0) poseSince = now;
                    if (now - poseSince >= FLICK_MS) {
                        step++;
                        armed = false;
                        poseSince = -1;
                        hw.rig.beep(120);
                        if (step == ghost.getSequence().length) startFinish(now);
                    }
                } else {
                    poseSince = -1;
                }
            }
        } else if (phase == FINISH) {
            int peak = in.getSoundPeak();
            if (ghost.isLoud()) {
                if (peak >= Config.SCREAM_LEVEL) done();
                else if (now - finishStart > LOUDER_AFTER_MS) note = "Louder!";
            } else {
                if (peak < Config.SILENT_LEVEL) {
                    if (quietSince < 0) quietSince = now;
                    if (now - quietSince >= SILENCE_MS) done();
                } else {
                    quietSince = -1;
                    if (now - finishStart > 1000) note = "Too loud. Stay silent.";
                }
            }
        }
    }

    private void startFinish(long now) {
        phase = FINISH;
        finishStart = now;
        quietSince = -1;
        note = "";
        hw.audio.cut();                          // the music stops for either finish, so the mic only hears the player
        hw.rig.led(255, 255, 255);
    }

    private void done() {
        phase = DONE;
        subject.removeObserver(this);
        hw.wand.setSilent(false);
        hw.wand.buzz(800);                       // banished: the arrows are over, so a buzz is safe now
        hw.audio.resume();
    }

    /** Too slow: back to the first arrow. */
    private void restart(String reason) {
        step = 0;
        started = false;
        armed = false;
        poseSince = -1;
        note = reason + ". Tap the grip to start again.";
        hw.rig.alarm(1);
    }

    /** Stops listening without finishing, when the night it belongs to ends. */
    public void cancel() {
        subject.removeObserver(this);
        hw.wand.setSilent(false);
        if (phase == FINISH) hw.audio.resume();   // the finish had cut the music
    }

    /** Debug mode: the next arrow counts as done, or the finish is done. */
    public String debugAdvance(long now) {
        this.now = now;
        if (phase == SEQUENCE) {
            if (!started) { started = true; seqStart = now; }
            step++;
            armed = false;
            poseSince = -1;
            if (step == ghost.getSequence().length) { startFinish(now); return "Arrows done: now the finish"; }
            return "Arrow " + step + " of " + ghost.getSequence().length;
        }
        if (phase == FINISH) { done(); return "Banished"; }
        return "Already banished";
    }

    public int getPhase() { return phase; }
    public int getStep() { return step; }
    public boolean isStarted() { return started; }
    public boolean isArmed() { return armed; }
    public String getNote() { return note; }
    public Ghost getGhost() { return ghost; }
    public long getLimitMs() { return limitMs; }
    public long timeLeftMs() { return started ? Math.max(0, limitMs - (now - seqStart)) : limitMs; }
    public double poseProgress() { return poseSince < 0 ? 0 : Math.min(1.0, (now - poseSince) / (double) FLICK_MS); }
}
