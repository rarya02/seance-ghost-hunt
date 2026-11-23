package seance;

/**
 * One snapshot of every sensor, taken by the SensorHub and passed to each Observer's update().
 * It never changes after it is made, so every Observer in the same round sees exactly the
 * same values, including the same grip taps.
 */
public class Reading {
    private final long time;
    // Rig (Arduino Uno)
    private final int dial;
    private final int soundPeak;
    private final int servoAngle;
    private final boolean servoMoving;
    private final boolean rigConnected;
    // Wand (LilyPad)
    private final boolean hall;
    private final boolean touch;
    private final int taps;              // grip taps since the previous reading
    private final int light;
    private final String pose;
    private final boolean still;
    private final boolean wandConnected;

    public Reading(long time, int dial, int soundPeak, int servoAngle, boolean servoMoving, boolean rigConnected,
                   boolean hall, boolean touch, int taps, int light, String pose, boolean still, boolean wandConnected) {
        this.time = time;
        this.dial = dial;
        this.soundPeak = soundPeak;
        this.servoAngle = servoAngle;
        this.servoMoving = servoMoving;
        this.rigConnected = rigConnected;
        this.hall = hall;
        this.touch = touch;
        this.taps = taps;
        this.light = light;
        this.pose = pose;
        this.still = still;
        this.wandConnected = wandConnected;
    }

    public long getTime() { return time; }
    public int getDial() { return dial; }
    public int getSoundPeak() { return soundPeak; }
    public int getServoAngle() { return servoAngle; }
    public boolean isServoMoving() { return servoMoving; }
    public boolean isRigConnected() { return rigConnected; }
    public boolean isHall() { return hall; }
    public boolean isTouch() { return touch; }
    public int getTaps() { return taps; }
    public int getLight() { return light; }
    public String getPose() { return pose; }
    public boolean isStill() { return still; }
    public boolean isWandConnected() { return wandConnected; }

    /** True when the wand is holstered (tip down) and the room is quiet: safe from a hunt. */
    /** Hidden from a hunt: the wand is pointed tip down and the room is not loud. */
    public boolean isHidden() { return pose.equals("DOWN") && soundPeak < Config.HUNT_NOISE_LEVEL; }
}
