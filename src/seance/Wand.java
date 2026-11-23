package seance;

/**
 * Commands for the wand (LilyPad) through the xBee.
 * The wand cannot receive while it is sending, so a command can be lost.
 * The wand cannot listen while it is sending its status lines, so commands get lost.
 * So: one-off commands are sent twice; a repeating vibration or heartbeat is re-sent every
 * second (the wand ignores an identical repeat, so the rhythm is not disturbed); and when
 * nothing should be vibrating, IDLE is re-sent every second, so a lost "stop" cannot leave
 * the wand buzzing for more than a second.
 * During the banishing the wand is put in silent mode: every vibration command is ignored and
 * "motor off" (VIB,0) is re-sent every second, so the motor cannot shake the accelerometer
 * while the poses are read, even if an older wand sketch missed a command.
 * It is an Observer of the SensorHub (a ConcreteObserver), so it gets a regular tick to do the
 * re-sending.
 */
public class Wand extends ConcreteObserver {
    private final Link link;
    private String repeating = null;     // the current repeating command (vibration or heartbeat)
    private long lastSent = 0;
    private long lastNow = 0;            // the time of the latest Reading, so one clock is used throughout
    private final String[] pending = new String[4];   // one-off commands waiting to be sent again
    private final long[] pendingAt = new long[4];
    private boolean silent = false;      // true during the banishing: the motor stays off

    public Wand(Subject subject, Link link) {
        super(subject);                  // registers with the hub, as on the slides
        this.link = link;
    }

    /** Repeating pulses: on for onMs, off for offMs, until stopped or changed. */
    public void pulse(int onMs, int offMs) {
        if (silent) return;
        repeat("VIB," + onMs + "," + offMs + ",0");
    }

    /** A lub-dub heartbeat in the wand, one beat every periodMs. */
    public void beat(int periodMs) {
        if (silent) return;
        repeat("BEAT," + periodMs);
    }

    private void repeat(String cmd) {
        if (cmd.equals(repeating)) return;
        repeating = cmd;
        link.send(cmd);
        lastSent = lastNow;
    }

    /** Stops any vibration and the heartbeat. */
    public void stop() {
        repeating = null;
        sendTwice("VIB,0");
    }

    /** Stops the heartbeat only, so a buzz sent at the same time still plays. */
    public void stopBeat() {
        if (repeating != null && repeating.startsWith("BEAT")) repeating = null;
        sendTwice("BEAT,0");
    }

    /** One vibration lasting ms milliseconds. */
    public void buzz(int ms) {
        if (silent) return;
        sendTwice("BUZZ," + ms);
    }

    /**
     * Silent mode on or off. Turning it on stops the motor at once, drops any vibration still
     * waiting to be re-sent, and from then on "motor off" is re-sent every second.
     */
    public void setSilent(boolean on) {
        silent = on;
        if (on) {
            repeating = null;
            for (int i = 0; i < pending.length; i++) {
                if (pending[i] != null && (pending[i].startsWith("VIB") || pending[i].startsWith("BUZZ") || pending[i].startsWith("BEAT"))) pending[i] = null;
            }
            sendTwice("VIB,0");
            lastSent = lastNow;
        }
    }
    public boolean isSilent() { return silent; }

    /**
     * Pause (the P key): the motor stops at once. While paused the hub sends no Readings, so
     * update() is not called; the Game calls keepOff() a few times a second instead.
     * The current heartbeat or vibration is remembered and sent again on resume.
     */
    public void pause() { link.send("VIB,0"); }
    public void keepOff() { link.send("VIB,0"); }
    public void unpause() { lastSent = lastNow - 1000; }   // re-send the remembered rhythm on the next tick

    /** Sends now and once more shortly after, in case the radio drops it. */
    private void sendTwice(String cmd) {
        link.send(cmd);
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] == null) {
                pending[i] = cmd;
                pendingAt[i] = lastNow + 150;
                return;
            }
        }
    }

    /** Takes the wand's current position as level. Hold it level when this is sent. */
    public void calibrate() { sendTwice("CAL"); }

    /** Observer: on every new Reading, re-sends commands that may have been lost. */
    public void update(Reading r) {
        long now = r.getTime();
        lastNow = now;
        if (now - lastSent >= 1000) {
            if (silent) link.send("VIB,0");                   // banishing: keep the motor off
            else link.send(repeating != null ? repeating : "IDLE");
            lastSent = now;
        }
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] != null && now >= pendingAt[i]) {
                link.send(pending[i]);
                pending[i] = null;
            }
        }
    }
}
