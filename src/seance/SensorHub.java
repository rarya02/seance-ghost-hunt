package seance;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/**
 * Observer pattern: the Subject of the game. Like the Clock on the lecture slides, it extends
 * ConcreteSubject (which keeps the list of Observers) and implements Runnable: its constructor
 * creates a thread and starts it, and run() notifies the Observers every tick.
 *
 * Where the clock ticked once a second and sent the Date, the hub ticks every Config.TICK_MS
 * (50 ms, 20 times a second) and sends a Reading: the latest values from both boards.
 *
 * The two serial reader threads (rig and wand) write the latest values into this object with
 * handleLine(). Each tick packs them into one Reading and calls notifyObservers().
 *
 * Observers join and leave while the game runs: a test registers when the player starts it and
 * removes itself when it passes or fails, a hunt registers when it starts and leaves when it is
 * over, a night registers when it begins and is removed when it ends.
 *
 * Rig line:  R,<dial>,<sound peak>,<servo angle>,<servo moving>
 * Wand line: W,<hall>,<touch>,<taps>,<light>,<pose>,<still>
 */
public class SensorHub extends ConcreteSubject implements Runnable {
    private final boolean simRig;        // rig played on the keyboard: always counts as connected
    private final boolean simWand;       // wand played on the keyboard: always counts as connected

    // Latest rig values
    private int dial = 512;
    private int soundPeak = 0;
    private int servoAngle = 90;
    private boolean servoMoving = false;
    private long lastRigLine = 0;

    // Latest wand values
    private boolean hall = false;
    private boolean touch = false;
    private int wandTaps = -1;           // running tap count reported by the wand (0 to 999)
    private int newTaps = 0;             // taps not yet sent out in a Reading
    private int light = 600;
    private String pose = "LEVEL";
    private boolean still = true;
    private long lastWandLine = 0;

    /** Both boards real (false) or both on the keyboard (true). */
    public SensorHub(boolean simulated) {
        this(simulated, simulated);
    }

    /**
     * Each board can be real or played on the keyboard, to test one board at a time.
     * As on the slides' Clock, the constructor creates a thread and starts it.
     */
    public SensorHub(boolean simRig, boolean simWand) {
        super();
        this.simRig = simRig;
        this.simWand = simWand;
        Thread t = new Thread(this);
        t.setDaemon(true);               // the thread stops when the window is closed
        t.start();
    }

    /**
     * The tick, as in the slides' Clock: notify the Observers, sleep, repeat.
     * The game and the screen are Swing objects, and Swing must only be used from its own
     * thread, so each tick is handed to the Swing thread with invokeAndWait. Every Observer
     * therefore runs on that one thread, and two ticks never overlap.
     */
    public void run() {
        while (true) {
            try {
                SwingUtilities.invokeAndWait(this::tick);
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();        // an Observer failed this tick: report it, keep ticking
            } catch (InterruptedException e) {
                return;
            }
            try {
                Thread.sleep(Config.TICK_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * One tick: pack the latest values into a new Reading and notify every Observer.
     * While the game is paused nothing is sent, so every Observer stands still.
     */
    public void tick() {
        if (paused) return;
        reading = takeReading();
        notifyObservers();
    }

    /** The time stamped on each Reading. */
    protected long clockTime() { return System.currentTimeMillis(); }

    // =====================================================================
    // Game time: pause (the P key) and the debug speed control
    // =====================================================================
    // Game time follows the clock, but stands still while paused and can run slower or faster
    // in debug mode. Every Reading is stamped with game time, so every timer in the game
    // (the night, the tests, the hunts, the banishing) follows it.
    private boolean paused = false;
    private double speed = 1.0;          // 1 = real time; the debug keys Z and X change it
    private double gameNow = -1;         // game time at lastClock
    private long lastClock = 0;

    /** Brings game time up to the clock. */
    private void advance() {
        long clock = clockTime();
        if (gameNow < 0) gameNow = clock;
        else if (!paused) gameNow += (clock - lastClock) * speed;
        lastClock = clock;
    }

    /**
     * Pauses or resumes the game. While paused the hub sends no Readings and game time stands
     * still, so on resume every timer carries on from where it stopped. Taps made while paused
     * are thrown away, so they do not choose anything.
     */
    public synchronized void setPaused(boolean on) {
        if (on == paused) return;
        advance();
        paused = on;
        if (!on) newTaps = 0;
    }
    public synchronized boolean isPaused() { return paused; }

    /** Debug: run game time slower or faster than real time (0.25 to 4). */
    public synchronized void setSpeed(double s) { advance(); speed = s; }
    public synchronized double getSpeed() { return speed; }

    /** Game time now. */
    public synchronized long gameTime() { advance(); return (long) gameNow; }

    // =====================================================================
    // Debug serial monitor: the latest line from each board and the commands sent to them
    // =====================================================================
    private String lastRigText = "", lastWandText = "";
    private final java.util.LinkedList<String> sentLog = new java.util.LinkedList<String>();
    private String lastSent = "";
    private int lastSentRepeats = 0;

    /** Records a command sent to a board (repeats of the same line are counted, not listed again). */
    public synchronized void logSent(String board, String line) {
        String entry = board + " < " + line;
        if (entry.equals(lastSent)) { lastSentRepeats++; if (!sentLog.isEmpty()) sentLog.set(sentLog.size() - 1, entry + "  x" + (lastSentRepeats + 1)); return; }
        lastSent = entry;
        lastSentRepeats = 0;
        sentLog.add(entry);
        while (sentLog.size() > 10) sentLog.removeFirst();
    }
    public synchronized java.util.List<String> getSentLog() { return new java.util.ArrayList<String>(sentLog); }
    public synchronized String getLastRigText() { return lastRigText; }
    public synchronized String getLastWandText() { return lastWandText; }

    // =====================================================================
    // Data coming in from the boards (called by the serial reader threads)
    // =====================================================================

    /** Reads one line from either board. Lines that do not parse are ignored. */
    public synchronized void handleLine(String line) {
        if (line == null) return;
        String[] p = line.trim().split(",");
        try {
            if (p[0].equals("R")) lastRigText = line.trim();
            if (p[0].equals("W")) lastWandText = line.trim();
            if (p[0].equals("R") && p.length >= 5) {
                dial = Integer.parseInt(p[1]);
                soundPeak = Integer.parseInt(p[2]);
                servoAngle = Integer.parseInt(p[3]);
                servoMoving = p[4].equals("1");
                lastRigLine = System.currentTimeMillis();
            } else if (p[0].equals("W") && p.length >= 7) {
                hall = p[1].equals("1");
                boolean touchNow = p[2].equals("1");
                int taps = Integer.parseInt(p[3]);
                int added = wandTaps >= 0 ? (taps - wandTaps + 1000) % 1000 : 0;
                // Safeguard: if the grip went from released to pressed but the wand's tap
                // count did not move, still count it as one tap.
                if (added == 0 && touchNow && !touch && wandTaps >= 0) added = 1;
                newTaps += added;
                wandTaps = taps;
                touch = touchNow;
                light = Integer.parseInt(p[4]);
                pose = p[5];
                still = p[6].equals("1");
                lastWandLine = System.currentTimeMillis();
            }
        } catch (NumberFormatException e) {
            // a damaged line from the radio: skip it
        }
    }

    /** A tap from the keyboard (Enter), counted the same as a grip tap. */
    public synchronized void addTap() { newTaps++; }

    /** Packs the latest values into one Reading and resets the tap count. */
    protected synchronized Reading takeReading() {
        long wall = System.currentTimeMillis();
        boolean rigOk = simRig || wall - lastRigLine < 1500;
        boolean wandOk = simWand || wall - lastWandLine < 1500;
        advance();
        Reading r = new Reading((long) gameNow, dial, soundPeak, servoAngle, servoMoving, rigOk,
                                hall, touch, newTaps, light, pose, still, wandOk);
        newTaps = 0;
        return r;
    }

    // =====================================================================
    // Used by the keyboard simulator
    // =====================================================================
    public synchronized void simDial(int delta) { dial = Math.max(0, Math.min(1023, dial + delta)); }
    public synchronized void simSetDial(int value) { dial = Math.max(0, Math.min(1023, value)); }
    public synchronized int simGetDial() { return dial; }
    public synchronized void simSound(int level) { soundPeak = level; }
    public synchronized void simHall(boolean on) { hall = on; }
    public synchronized void simTouch(boolean on) {
        if (on && !touch) newTaps++;
        touch = on;
    }
    public synchronized void simLight(int value) { light = value; }
    public synchronized void simPose(String p) { pose = p; }
}
