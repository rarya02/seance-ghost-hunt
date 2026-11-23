package seance;

/**
 * A hunt, and an Observer of the SensorHub.
 * The alarm sounds and the music cuts. The player has a few seconds to holster the wand
 * (tip down) and go silent. Every second they are exposed during the hunt costs sanity.
 *
 * Like the ConcreteObserver in the lecture, a hunt registers itself with the hub when it is
 * created, and it removes itself when it is over.
 */
public class Hunt extends ConcreteObserver {
    public static final int WARNING = 0, ACTIVE = 1, DONE = 2;

    private final Hardware hw;
    private final Run run;
    private final int costPerSecond;
    private int phase = WARNING;
    private long phaseStart;
    private long lastCheck;
    private long now;
    private boolean exposedAny = false;
    private boolean safeNow = false;
    private int ticksThisSecond = 0, exposedThisSecond = 0;   // readings in the current second
    private long nextFlicker = 0;        // the rig's light flickers red for the whole hunt (light only, no sound)
    private static final java.util.Random RNG = new java.util.Random();

    public Hunt(Hardware hw, Run run, int costPerSecond, long now) {
        super(hw.hub);                   // registers with the hub, as on the slides
        this.hw = hw;
        this.run = run;
        this.costPerSecond = costPerSecond;
        this.now = now;
        phaseStart = now;
        hw.audio.cut();
        hw.rig.alarm(6);
        hw.rig.led(255, 0, 0);
        hw.wand.buzz(600);
    }

    /** Observer: checks the pose and the sound in every new Reading. */
    public void update(Reading r) {
        if (phase == DONE) return;
        now = r.getTime();
        safeNow = r.isHidden();
        if (now >= nextFlicker) {
            int[] levels = { 0, 40, 120, 255, 255 };
            hw.rig.led(levels[RNG.nextInt(levels.length)], 0, 0);
            nextFlicker = now + 80 + RNG.nextInt(220);
        }
        if (phase == WARNING && now - phaseStart >= Config.HUNT_WARNING_MS) {
            phase = ACTIVE;
            phaseStart = now;
            lastCheck = now;
        } else if (phase == ACTIVE) {
            ticksThisSecond++;
            if (!safeNow) exposedThisSecond++;
            if (now - lastCheck >= 1000) {
                lastCheck += 1000;
                // A second only counts as exposed if the player was exposed for most of it,
                // so one noisy moment or a wobble of the wand does not cost sanity.
                if (exposedThisSecond * 2 > ticksThisSecond) {
                    run.loseSanity(costPerSecond);
                    exposedAny = true;
                    hw.wand.buzz(120);          // felt, not heard: a beep would be picked up by the mic
                }
                ticksThisSecond = 0;
                exposedThisSecond = 0;
            }
            if (now - phaseStart >= Config.HUNT_ACTIVE_MS) {
                phase = DONE;
                hw.audio.resume();
                subject.removeObserver(this);
            }
        }
    }

    /** Ends the hunt at once without a result, when the night it belongs to ends. */
    public void cancel() {
        phase = DONE;
        subject.removeObserver(this);
    }

    public int getPhase() { return phase; }
    public boolean isSafe() { return safeNow; }
    public boolean wasExposed() { return exposedAny; }

    public long secondsLeft() {
        long length = phase == WARNING ? Config.HUNT_WARNING_MS : Config.HUNT_ACTIVE_MS;
        return Math.max(0, (length - (now - phaseStart) + 999) / 1000);
    }
}
