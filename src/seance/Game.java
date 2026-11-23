package seance;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import jssc.SerialPortException;

/**
 * Séance: the game controller. Run it with the two serial ports, or with no arguments
 * to play with the keyboard simulator.
 *
 *   java seance.Game                         keyboard simulator, no hardware needed
 *   java seance.Game list                    print the serial ports this computer can see
 *   java seance.Game <rig port> <wand port>  play with the hardware
 *       rig port  = the Uno's USB port
 *       wand port = the xBee Explorer's port
 *   Either port can be the word sim to play that board on the keyboard, to test one at a time:
 *   java seance.Game /dev/cu.usbmodem101 sim         real rig, keyboard wand
 *   Add the word debug anywhere to start in debug mode (or press ` during the game):
 *   java seance.Game /dev/cu.usbmodem101 /dev/cu.usbserial-A700eDJO debug
 *
 *   P pauses the game at any time during a night; P again carries on.
 *
 * The program is built around the Observer pattern, set up the same way as on the lecture
 * slides. The SensorHub is the Subject (like the Clock): it extends ConcreteSubject, runs its
 * own thread, and 20 times a second passes a Reading to every Observer. Every Observer extends
 * ConcreteObserver (like Mouse and Alice), so making one registers it with the hub.
 *
 *   Always registered:  Wand (re-sends lost commands), Game (title, run end), Display (screen),
 *                       SoundEffects (plays a sound when something changes)
 *   While a night runs: Night (menu, naming, results)
 *   Join and leave:     the running Test, a Hunt, the Banishing
 *
 * Game itself is an Observer: it moves between the title, the run and the end screen.
 */
public class Game extends ConcreteObserver {
    public static final int TITLE = 0, PLAYING = 1, ENDED = 2, ENTER_NAME = 3;

    private final Hardware hw;
    private final Link rigLink, wandLink;
    private final boolean simulator;
    private boolean keyboardTaps = true;      // Enter counts as a tap only when the wand is on the keyboard
    private final Ghost[] ghosts = Ghost.createAll();
    private final Leaderboard board = new Leaderboard("scores.txt");
    private Display display;
    private Run run;
    private Night night;
    private int screen = TITLE;
    private boolean dawn = false;
    private long endedAt = 0;
    // High score initials, chosen arcade style: the dial picks a letter, a tap locks it in.
    private final char[] initials = { '_', '_', '_' };
    private int slot = 0;
    private char letter = 'A';
    // The letters sit around a wheel, so after Z comes A again. The wheel turns with the dial from
    // wherever the dial was when it appeared (that spot is A), so every letter is in reach whichever
    // way the knob is turned.
    private double letterPos = 0;            // the wheel's position in letters, smoothed, not wrapped (26 = A again)
    private int letterTurns = 0;             // the chosen position, not wrapped
    private int wheelBase = -1;              // the dial reading where the wheel shows A
    // Pause (P) and debug mode (`)
    private boolean paused = false;
    private Timer pauseTimer;                // while paused: keeps the wand motor off and the screen drawn
    private int debugView = 0;               // 0 = off, 1 = keys and panel, 2 = keys only (panel hidden)
    private String debugNote = "";           // what the last debug key did
    private boolean god = false;             // debug: sanity never goes down
    private boolean showMonitor = false;     // debug: the serial monitor panel (C)
    private static final double[] SPEEDS = { 0.25, 0.5, 1, 2, 4 };
    private int speedIndex = 2;
    private long rigTestAt = -1;             // debug: when the rig test (R) started
    private int rigTestMark = -1;
    private long debugNoteAt = 0;

    public Game(Hardware hw, Link rigLink, Link wandLink, boolean simulator) {
        super(hw.hub);                            // registers with the hub, as on the slides
        this.hw = hw;
        this.rigLink = rigLink;
        this.wandLink = wandLink;
        this.simulator = simulator;
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            SerialLink.listPorts();
            return;
        }
        // Everything is built on the Swing thread, the thread every tick of the hub runs on.
        SwingUtilities.invokeLater(() -> launch(args));
    }

    /**
     * Like Main on the slides: make the Subject, then the Observers that watch it.
     * (On the slides: Clock c1 = new Clock(); Mouse m1 = new Mouse(c1); ...)
     */
    private static void launch(String[] allArgs) {
        // the word debug can go anywhere; the rest are the ports
        boolean debug = false;
        java.util.List<String> list = new java.util.ArrayList<String>();
        for (String a : allArgs) {
            if (a.equalsIgnoreCase("debug")) debug = true;
            else list.add(a);
        }
        String[] args = list.toArray(new String[0]);
        boolean simRig = args.length < 2 || args[0].equalsIgnoreCase("sim");
        boolean simWand = args.length < 2 || args[1].equalsIgnoreCase("sim");
        SensorHub hub = new SensorHub(simRig, simWand);          // the Subject (the slides' Clock)
        Link rigLink, wandLink;
        if (simRig && simWand) System.out.println("No ports given: starting the keyboard simulator.");
        try {
            rigLink = simRig ? new ConsoleLink("rig") : new SerialLink(args[0], hub, "rig");
            wandLink = simWand ? new ConsoleLink("wand") : new SerialLink(args[1], hub, "wand");
        } catch (SerialPortException e) {
            System.out.println("Could not open a port: " + e.getMessage());
            System.out.println("Close the Arduino Serial Monitor and check the port names below.");
            SerialLink.listPorts();
            System.exit(1);
            return;
        }
        rigLink = logged(rigLink, "RIG", hub);                        // for the debug serial monitor
        wandLink = logged(wandLink, "WAND", hub);
        if (simRig != simWand) {
            System.out.println("Rig:  " + (simRig ? "keyboard" : "real, on " + args[0]));
            System.out.println("Wand: " + (simWand ? "keyboard" : "real, on " + args[1]));
        }
        Wand wand = new Wand(hub, wandLink);                         // Observer
        Hardware hw = new Hardware(hub, new Rig(rigLink), wand, new AudioDirector("audio"));
        Game game = new Game(hw, rigLink, wandLink, simRig || simWand);   // Observer
        game.keyboardTaps = simWand;              // with the real wand, only the touch grip confirms
        if (debug) game.debugView = 1;
        game.start();                             // opens the window; adds the Display and SoundEffects
    }

    /** Wraps a link so every command sent through it is also noted for the debug serial monitor. */
    private static Link logged(Link link, String board, SensorHub hub) {
        return new Link() {
            public void send(String line) {
                hub.logSent(board, line);
                link.send(line);
            }
            public void close() { link.close(); }
        };
    }

    private void start() {
        JFrame frame = new JFrame("Séance");
        display = new Display(hw.hub, this);                         // Observer
        new SoundEffects(hw.hub, this, hw.audio);                    // Observer
        frame.add(display.getPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);                 // the screen scales to any window size
        frame.setFocusTraversalKeysEnabled(false);
        frame.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_ESCAPE) quit();
                if (k == KeyEvent.VK_P) togglePause();
                if (k == KeyEvent.VK_BACK_QUOTE || k == KeyEvent.VK_F1) toggleDebug();
                if (k == KeyEvent.VK_I) display.toggleInstruments();
                if (paused) return;                     // nothing else while paused
                // Enter is the tap when the wand is on the keyboard, and always in debug mode
                if (k == KeyEvent.VK_ENTER && (keyboardTaps || isDebug())) hw.hub.addTap();
                if (k == KeyEvent.VK_L) hw.wand.calibrate();     // re-level the wand (hold it level)
                if (isDebug()) debugKey(k);
            }
        });
        pauseTimer = new Timer(300, e -> {
            hw.wand.keepOff();
            display.getPanel().repaint();
        });
        if (simulator) frame.addKeyListener(new Simulator(hw.hub, this));
        frame.setVisible(true);

        // The Uno restarts when its port opens, so wait before sending the first commands.
        Timer setup = new Timer(2500, e -> {
            hw.rig.setSpeed(Config.SERVO_SPEED);
            hw.rig.moveToMark(Config.MARKS / 2);
            hw.rig.led(0, 0, 255);
            hw.wand.calibrate();
        });
        setup.setRepeats(false);
        setup.start();

        // The Wand, the Game, the Display and SoundEffects registered themselves when they were
        // made. A Night, a Test, a Hunt and the Banishing join and leave as the game goes on.
    }

    /** Observer: called by the SensorHub with every new Reading. Handles the screens around the nights. */
    public void update(Reading r) {
        long now = r.getTime();
        int taps = r.getTaps();
        if (rigTestAt >= 0) rigTest(now);

        if (screen == TITLE) {
            hw.audio.playTitle();
            if (taps > 0) newRun(now);
        } else if (screen == PLAYING) {
            hw.audio.update(run.getSanity());
            if (run.isOver()) {
                endRun(false, now);
            } else if (night.isFinished(now)) {
                night.close();
                if (night.isWon() && run.getNightNo() >= Config.NIGHTS_TO_DAWN) {
                    endRun(true, now);
                } else {
                    run.nextNight();
                    startNight(now);
                }
            }
        } else if (screen == ENTER_NAME) {
            // The wheel follows the dial smoothly, and the chosen letter only changes once the
            // wheel is clearly on the next letter, so it never flickers between two.
            if (wheelBase < 0) { wheelBase = r.getDial(); letterPos = 0; letterTurns = 0; }
            double target = (r.getDial() - wheelBase) * 26 / 1024.0;
            letterPos += (target - letterPos) * 0.35;
            if (Math.abs(letterPos - letterTurns) > 0.6) letterTurns = (int) Math.round(letterPos);
            letter = (char) ('A' + Math.floorMod(letterTurns, 26));
            if (dawn) dawnShow(now);
            if (taps > 0 && now - endedAt > (dawn ? 5000 : 1500)) {
                initials[slot] = letter;
                slot++;
                hw.rig.beep(60);
                if (slot == initials.length) {
                    board.add(new String(initials), run.getScore(), run.getNightNo());
                    screen = ENDED;
                    endedAt = now;
                }
            }
        } else if (screen == ENDED) {
            if (dawn) dawnShow(now);
            if (taps > 0 && now - endedAt > (dawn ? 7000 : 3000)) {
                screen = TITLE;
                hw.rig.led(0, 0, 255);
            }
        }
    }

    // =====================================================================
    // Pause (P)
    // =====================================================================
    /**
     * Pauses or resumes a night. While paused the hub sends no Readings, so every timer stops:
     * the night timer, the test, the hunt and the banishing all carry on from the same moment.
     * The music, the rig's buzzer and the wand's motor are stopped, and come back on resume.
     */
    private void togglePause() {
        if (!paused && screen != PLAYING) return;          // only a night can be paused
        paused = !paused;
        hw.hub.setPaused(paused);
        if (paused) {
            hw.audio.pause();
            hw.rig.silence();
            hw.wand.pause();
            pauseTimer.start();
        } else {
            pauseTimer.stop();
            hw.audio.unpause();
            hw.wand.unpause();
        }
        display.getPanel().repaint();
    }

    // =====================================================================
    // Debug mode (` or F1, or start the game with the word debug)
    // =====================================================================
    private void toggleDebug() {
        debugView = (debugView + 1) % 3;
        debugNote = debugView == 0 ? "" : (debugView == 1 ? "Debug on" : "Debug panel hidden (keys still work)");
        debugNoteAt = hw.hub.gameTime();
        display.getPanel().repaint();
    }

    /** One debug key. Runs on the Swing thread, between two ticks, so it never races the game. */
    private void debugKey(int k) {
        long now = hw.hub.gameTime();
        String note = devTool(k, now);                 // tools that work on every screen
        if (note != null) {
            // done
        } else if (screen == ENDED && k == KeyEvent.VK_PERIOD) {
            // . on the end screen: play the dawn again
            dawn = true;
            endedAt = now;
            dawnStep = -1;
            display.replayScreen();
            dawnCount++;
            note = "Dawn again";
        } else if (screen == TITLE) {
            // 1 to 7 on the title screen: start a run on that night
            if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_7) {
                newRun(now, k - KeyEvent.VK_0);
                note = "Run started on night " + (k - KeyEvent.VK_0);
            }
        } else if (screen == PLAYING && night != null) {
            switch (k) {
                case KeyEvent.VK_1: case KeyEvent.VK_2: case KeyEvent.VK_3: case KeyEvent.VK_4: case KeyEvent.VK_5:
                    note = night.debugStartTest(k - KeyEvent.VK_1); break;
                case KeyEvent.VK_6:             note = night.debugHunt(now); break;
                case KeyEvent.VK_7:             note = night.debugPass(now); break;
                case KeyEvent.VK_8:             note = night.debugFail(); break;
                case KeyEvent.VK_9:             note = night.debugAddTime(30000); break;
                case KeyEvent.VK_0:             note = night.debugSetTime(10000); break;
                case KeyEvent.VK_MINUS:         note = night.debugSanity(-10); break;
                case KeyEvent.VK_EQUALS:        note = night.debugSanity(10); break;
                case KeyEvent.VK_OPEN_BRACKET:  note = night.debugNaming(now); break;
                case KeyEvent.VK_CLOSE_BRACKET: note = night.debugBanish(now); break;
                case KeyEvent.VK_COMMA:         note = night.debugWin(now); break;
                case KeyEvent.VK_SEMICOLON:     note = night.debugNextOmen(); break;
                case KeyEvent.VK_QUOTE:         note = night.debugRerollOmens(); break;
                case KeyEvent.VK_PERIOD:        endRun(true, now); note = "Dawn"; break;
                case KeyEvent.VK_SLASH:         run.setGod(false); run.loseSanity(Config.START_SANITY); note = "Sanity gone: game over"; break;
                case KeyEvent.VK_E:             note = night.debugEscape(now); break;
                case KeyEvent.VK_D:             note = night.debugDump(now); break;
                case KeyEvent.VK_Y:
                    night.close();
                    startNight(now);
                    note = "A new night " + run.getNightNo();
                    break;
                default: break;
            }
        }
        if (note != null) {
            debugNote = note;
            debugNoteAt = now;
            System.out.println("[debug] " + note);
            display.getPanel().repaint();
        }
    }

    /** Debug tools for every screen. Returns what it did, or null if k is not one of them. */
    private String devTool(int k, long now) {
        switch (k) {
            case KeyEvent.VK_G:
                god = !god;
                if (run != null) run.setGod(god);
                return god ? "God mode on: sanity never drops" : "God mode off";
            case KeyEvent.VK_H:
                Night.huntsOff = !Night.huntsOff;
                return Night.huntsOff ? "Hunts off (6 still starts one)" : "Hunts on";
            case KeyEvent.VK_F:
                Night.timerFrozen = !Night.timerFrozen;
                return Night.timerFrozen ? "Night timer frozen" : "Night timer running";
            case KeyEvent.VK_Z: case KeyEvent.VK_X:
                speedIndex = Math.max(0, Math.min(SPEEDS.length - 1, speedIndex + (k == KeyEvent.VK_X ? 1 : -1)));
                hw.hub.setSpeed(SPEEDS[speedIndex]);
                return "Game speed x" + speedText();
            case KeyEvent.VK_C:
                showMonitor = !showMonitor;
                return showMonitor ? "Serial monitor on" : "Serial monitor off";
            case KeyEvent.VK_R:
                rigTestAt = now;
                rigTestMark = -1;
                hw.rig.beep(150);
                return "Rig test: LED colours, every servo mark, buzzer";
            case KeyEvent.VK_W:
                hw.wand.buzz(400);
                return "Wand buzz sent (L re-levels it)";
            default:
                return null;
        }
    }

    public String speedText() {
        double s = SPEEDS[speedIndex];
        return s == (int) s ? String.valueOf((int) s) : String.valueOf(s);
    }

    /** The rig test (debug R): every LED colour, the servo through all 7 marks, a beep at each end. */
    private void rigTest(long now) {
        long t = now - rigTestAt;
        if (t >= 4900) {
            rigTestAt = -1;
            hw.rig.moveToMark(Config.MARKS / 2);
            if (run != null && screen == PLAYING) hw.rig.showSanity(run.getSanity()); else hw.rig.led(0, 0, 255);
            hw.rig.beep(150);
            return;
        }
        int[][] colours = { {255, 0, 0}, {0, 255, 0}, {0, 0, 255}, {255, 255, 255} };
        int[] c = colours[(int) (t / 300) % colours.length];
        hw.rig.led(c[0], c[1], c[2]);
        int mark = (int) (t / 650);
        if (mark != rigTestMark) { rigTestMark = mark; hw.rig.moveToMark(mark); }
    }

    // =====================================================================
    // Dawn: the rig celebrates with the screen
    // =====================================================================
    private int dawnStep = -1;
    private int dawnCount = 0;               // how many times dawn has started (the sound plays each time)
    public int getDawnCount() { return dawnCount; }

    /**
     * For the first 7 seconds of dawn: the LED races through the colours, the servo pointer
     * sweeps across every mark and back, and the wand buzzes as DAWN lands on the screen.
     */
    private void dawnShow(long now) {
        long t = now - endedAt;
        if (t >= 7000) {
            if (dawnStep >= 0) {
                dawnStep = -1;
                hw.rig.moveToMark(Config.MARKS / 2);
                hw.rig.led(255, 200, 80);                  // a warm sunrise colour to finish
            }
            return;
        }
        int step = (int) (t / 100);
        if (step == dawnStep) return;
        dawnStep = step;
        if (t < 1200) hw.rig.led(0, 0, 40 + step * 15);                    // night, getting lighter
        else {
            float hue = (step % 20) / 20f;
            java.awt.Color c = java.awt.Color.getHSBColor(hue, 1f, 1f);
            hw.rig.led(c.getRed(), c.getGreen(), c.getBlue());
        }
        if (t >= 1200 && step % 4 == 0) {                                  // the pointer sweeps 0..6..0
            int k = (step / 4) % (2 * (Config.MARKS - 1));
            hw.rig.moveToMark(k < Config.MARKS ? k : 2 * (Config.MARKS - 1) - k);
        }
        if (step == 30) hw.wand.buzz(900);                                   // as DAWN slams down
        if (step == 44 || step == 50 || step == 56) hw.wand.buzz(150);       // with the fireworks
    }

    private void newRun(long now) { newRun(now, 1); }

    private void newRun(long now, int firstNight) {
        run = new Run();
        run.setGod(god);
        if (firstNight > 1) run.debugSetNight(firstNight);
        screen = PLAYING;
        dawn = false;
        startNight(now);
    }

    /** Builds the next night. Making it registers it with the hub's list of Observers. */
    private void startNight(long now) {
        night = Night.create(run.getNightNo(), run, hw, ghosts, now);
    }

    private void endRun(boolean reachedDawn, long now) {
        night.close();
        dawn = reachedDawn;
        screen = ENDED;
        endedAt = now;
        dawnStep = -1;
        if (reachedDawn) dawnCount++;
        hw.audio.stopAll();
        hw.rig.silence();
        hw.wand.stop();
        if (reachedDawn) hw.rig.led(255, 255, 255);
        else hw.rig.led(255, 0, 0);
        if (board.qualifies(run.getScore())) {
            screen = ENTER_NAME;                   // a top-five score: enter three initials
            slot = 0;
            wheelBase = -1;                        // the wheel starts on A wherever the dial is
            for (int i = 0; i < initials.length; i++) initials[i] = '_';
        }
    }

    private void quit() {
        hw.audio.stopAll();
        hw.rig.silence();
        hw.wand.stop();
        rigLink.close();
        wandLink.close();
        System.exit(0);
    }

    /** How many choices the dial picks between on the current screen (0 = no menu). Used by the simulator. */
    public int optionCount() {
        if (screen != PLAYING || night == null) return 0;
        int state = night.getState();
        if (state == Night.MENU) return night.getMenuOptions().length;
        if (state == Night.NAMING) return night.getSuspects().length + 1;
        return 0;
    }

    // ---------- Read by the Display ----------
    public int getScreen() { return screen; }
    public Run getRun() { return run; }
    public Night getNight() { return night; }
    public boolean reachedDawn() { return dawn; }
    public Leaderboard getBoard() { return board; }
    public boolean isSimulator() { return simulator; }
    /** True when Enter is the tap (wand on the keyboard); false when only the touch grip confirms. */
    public boolean usesKeyboardTaps() { return keyboardTaps; }
    public char[] getInitials() { return initials; }
    public int getSlot() { return slot; }
    public char getLetter() { return letter; }
    public double getLetterPos() { return letterPos; }
    public boolean isPaused() { return paused; }
    public boolean isDebug() { return debugView > 0; }
    public boolean showDebugPanel() { return debugView == 1; }
    public String getDebugNote() { return debugNote; }
    public long getDebugNoteAt() { return debugNoteAt; }
    public Hardware getHardware() { return hw; }
    public boolean isGod() { return god; }
    public boolean showMonitor() { return showMonitor; }
    public long getHighScore() { return board.size() > 0 ? board.scoreAt(0) : 0; }
}
