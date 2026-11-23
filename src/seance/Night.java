package seance;

import java.util.Random;

/**
 * One night: eight suspects, one hidden ghost, the tests that work tonight, the omens,
 * and everything that happens until the ghost is banished or named wrongly.
 *
 * A Night is an Observer of the SensorHub (a ConcreteObserver): it registers itself when it is
 * made, and every Reading moves the night on (the dial picks menu entries and suspects, a tap
 * chooses). The night also brings other Observers in and out: it makes a new test, a hunt or the
 * banishing, each of which registers itself with the hub, and the night just reads their results.
 */
public class Night extends ConcreteObserver {
    public static final int INTRO = 0, MENU = 1, TEST = 2, NAMING = 3, BANISH = 4, OVER = 5;
    public static final int NAME_OPTION = 5;           // menu entry after the five tests

    public static final String[] OMEN_NAMES = {
        "Restless Spirits", "Thin Veil", "Heavy Air", "Fading Light", "Hungry Dark", "Weak Signal"
    };
    public static final String[] OMEN_TEXT = {
        "hunts come sooner", "the spirit's riddles are not written down", "tests cost 2 more sanity",
        "less time for every test", "hunts drain twice as fast", "hold the Sweep signal twice as long"
    };
    private static final int RESTLESS = 0, THIN_VEIL = 1, HEAVY_AIR = 2, FADING = 3, HUNGRY = 4, WEAK = 5;

    private static final Random RNG = new Random();
    private static final long INTRO_MS = 6000, OVER_MS = 7000;   // OVER: long enough for the banish or scare animation
    /** The trait each physical test confirms, in the same order as TEST_NAMES. */
    private static final int[] TEST_TRAITS = { Traits.PRESENCE, Traits.VOICE, Traits.AVERSION, Traits.TEMPERAMENT };

    private final Run run;
    private final Hardware hw;
    private final int nightNo;
    private final Ghost[] suspects;
    private final Ghost hidden;
    /** The five tests, in menu order. A new Test object is made each time one is started. */
    public static final String[] TEST_NAMES = { "Sweep", "Frequency Scan", "Shadow Test", "Stabilise", "Contact" };
    private final boolean[] works = new boolean[5];
    private final boolean[] omen = new boolean[OMEN_NAMES.length];

    // What the tests have proven: -1 unknown, 1 true, 0 false.
    private final int[] known = { -1, -1, -1, -1, -1 };
    // The spirit's riddles tonight, and which traits they were about (kept secret from the screen).
    private final boolean[] riddled = new boolean[Traits.COUNT];
    private final java.util.List<String> riddles = new java.util.ArrayList<String>();

    private int state = INTRO;
    private long stateSince;
    private Test current;
    private Hunt hunt;
    private long nextHuntAt;
    private Banishing banishing;
    private int menuIndex = 0;
    private String message = "";
    private long ledRestoreAt = 0;
    private boolean won = false;
    private long nightScore = 0;
    private int multiplier = 1;
    private boolean cleanBonus = false;
    private String endReason = "";
    private boolean escaped = false;
    private long timeLeftMs = Config.NIGHT_TIME_MS;     // the night timer, paused during hunts
    private long lastTimerAt = -1;
    private long huntEndedAt = -1;                     // for the short pause after a hunt
    private boolean levelled = false;                  // the wand is re-levelled during each intro
    private Ghost named;                               // the suspect the player named, if it was wrong
    private int showStep = -1;                         // the rig's light show after the night, step by step
    private Reading lastReading;
    // Debug tools that last across nights (set by the Game's debug keys)
    public static boolean huntsOff = false;            // no hunts start
    public static boolean timerFrozen = false;         // the night timer does not count down
    private boolean forceHunt = false;                 // key 6 starts a hunt even with hunts switched off                       // the newest Reading, for debug actions between ticks
    private int debugOmen = -1;                        // which single omen the debug ; key set last

    private Night(int nightNo, Run run, Hardware hw, Ghost[] suspects, Ghost hidden, long now) {
        super(hw.hub);                     // registers with the hub, as on the slides
        this.nightNo = nightNo;
        this.run = run;
        this.hw = hw;
        this.suspects = suspects;
        this.hidden = hidden;
        stateSince = now;
    }

    /**
     * Builds a night: picks the ghost, the working tests, the 8 suspects and the omens.
     * The suspects are chosen so that tonight's working tests, done together, always leave
     * exactly one suspect: every other suspect differs from the ghost in at least one of them.
     */
    public static Night create(int nightNo, Run run, Hardware hw, Ghost[] all, long now) {
        Ghost hidden = all[RNG.nextInt(all.length)];

        // Contact always works; three of the four physical tests work tonight.
        boolean[] works = new boolean[5];
        works[4] = true;
        int chosen = 0;
        while (chosen < Config.WORKING_TESTS) {
            int t = RNG.nextInt(4);
            if (!works[t]) { works[t] = true; chosen++; }
        }

        Ghost[] suspects = new Ghost[Config.SUSPECTS];
        boolean[] used = new boolean[all.length];
        suspects[0] = hidden;
        used[hidden.getId()] = true;
        for (int i = 1; i < suspects.length; i++) {
            int pick;
            do { pick = RNG.nextInt(all.length); }
            while (used[pick] || sameOnWorkingTests(all[pick], hidden, works));
            used[pick] = true;
            suspects[i] = all[pick];
        }
        for (int i = suspects.length - 1; i > 0; i--) {            // shuffle so the ghost is not always first
            int j = RNG.nextInt(i + 1);
            Ghost t = suspects[i]; suspects[i] = suspects[j]; suspects[j] = t;
        }
        Night n = new Night(nightNo, run, hw, suspects, hidden, now);
        for (int i = 0; i < works.length; i++) n.works[i] = works[i];
        // One omen on night 1, up to two on nights 2 and 3, up to three after that.
        int maxOmens = nightNo == 1 ? 1 : (nightNo <= 3 ? 2 : 3);
        int count = 1 + RNG.nextInt(maxOmens);
        int placed = 0;
        while (placed < count) {
            int o = RNG.nextInt(OMEN_NAMES.length);
            if (!n.omen[o]) { n.omen[o] = true; placed++; }
        }

        n.scheduleHunt(now);
        hw.rig.moveToMark(Config.MARKS / 2);
        hw.rig.showSanity(run.getSanity());
        hw.audio.resume();
        return n;
    }

    // =====================================================================
    // Observer: called by the SensorHub with every new Reading
    // =====================================================================
    public void update(Reading r) {
        long now = r.getTime();
        lastReading = r;
        if (ledRestoreAt > 0 && now >= ledRestoreAt) {
            ledRestoreAt = 0;
            hw.rig.showSanity(run.getSanity());
        }

        // The night timer counts down on the board, in tests and while naming, and pauses during hunts.
        if (lastTimerAt >= 0 && isTimerRunning() && !timerFrozen) timeLeftMs -= now - lastTimerAt;
        lastTimerAt = now;
        if (timeLeftMs <= 0 && (state == MENU || state == TEST || state == NAMING || state == BANISH)) {
            escape(now);
            return;
        }

        // Hunts: during the warning the night carries on; once the hunt is active, everything waits.
        if (hunt != null) {
            checkHunt(now);
            if (hunt != null && hunt.getPhase() == Hunt.ACTIVE) return;
        } else if ((state == MENU || state == TEST) && now >= nextHuntAt && (!huntsOff || forceHunt)) {
            forceHunt = false;
            int cost = Config.EXPOSURE_COST * (omen[HUNGRY] ? 2 : 1);
            hunt = new Hunt(hw, run, cost, now);          // the hunt registers itself with the hub
            message = "A HUNT IS COMING. Holster the wand and go silent!";
        }

        switch (state) {
            case INTRO:
                // the intro says HOLD THE WAND LEVEL; re-level it near the end
                if (!levelled && now - stateSince >= INTRO_MS - 1500) {
                    hw.wand.calibrate();
                    levelled = true;
                }
                if (now - stateSince >= INTRO_MS) setState(MENU, now);
                break;
            case MENU:
                updateMenu(r);
                break;
            case TEST:
                checkTest(now);
                break;
            case NAMING:
                updateNaming(r);
                break;
            case BANISH:
                checkBanishing(now);
                break;
            case OVER:
                rigShow(now);
                break;
            default:
                break;
        }
    }

    /** Ends the night: removes it, and anything it started, from the hub's list of Observers. */
    public void close() {
        subject.removeObserver(this);
        if (current != null) current.abort();
        if (hunt != null) hunt.cancel();
        if (banishing != null) banishing.cancel();
        current = null;
        hunt = null;
    }

    private void setState(int s, long now) {
        state = s;
        stateSince = now;
    }

    private void updateMenu(Reading r) {
        long now = r.getTime();
        int[] options = getMenuOptions();
        menuIndex = Math.min(options.length - 1, r.getDial() * options.length / 1024);
        if (r.getTaps() > 0) {
            int choice = options[menuIndex];
            if (choice == NAME_OPTION && hunt != null) {
                message = "Not now, a hunt is coming!";
            } else if (choice == NAME_OPTION) {
                setState(NAMING, now);
                message = "Turn the dial to choose a suspect, then tap to name it.";
            } else {
                current = makeTest(choice);               // the new test registers itself with the hub
                current.start(this, hw, r);
                setState(TEST, now);
                message = current.getName() + ": " + current.getInstructions();
            }
        }
    }

    /** The running test updates itself from the hub; the night only waits for its result. */
    private void checkTest(long now) {
        int result = current.getResult();
        if (result == Test.RUNNING) return;
        int extra = omen[HEAVY_AIR] ? 2 : 0;
        if (result == Test.PASSED) {
            run.loseSanity(current.passCost() + extra);
            int t = current.getTrait();
            boolean value = current.answer();
            if (current instanceof Contact) {
                // the riddle was recorded when it was spoken; the trait it describes stays secret
                message = "The spirit: \"" + ((Contact) current).getRiddle() + "\"";
            } else {
                known[t] = value ? 1 : 0;
                message = "Confirmed: the ghost " + Traits.sentence(t, value).toUpperCase();
                hw.rig.beep(200);
            }
        } else {
            run.loseSanity(current.failCost() + extra);
            if (!(current instanceof Contact)) run.markFailedTest();
            message = current.getName() + " failed: out of time.";
            hw.rig.alarm(1);
        }
        current = null;
        hw.rig.showSanity(run.getSanity());
        setState(MENU, now);
    }

    private void updateNaming(Reading r) {
        long now = r.getTime();
        int count = suspects.length + 1;                      // the last option is Back
        menuIndex = Math.min(count - 1, r.getDial() * count / 1024);
        if (r.getTaps() == 0) return;
        if (menuIndex == suspects.length) {
            setState(MENU, now);
            message = "";
            return;
        }
        Ghost choice = suspects[menuIndex];
        if (choice == hidden) {
            multiplier = multiplierFor(provenCount());          // Contact answers do not lower it
            banishing = new Banishing(hidden, hw, now);            // registers itself with the hub
            setState(BANISH, now);
            message = "Correct! Now banish " + hidden.getName() + ". Tap the grip, then flick the wand along each arrow.";
        } else {
            named = choice;
            run.loseSanity(Config.WRONG_NAME_COST);
            hw.wand.buzz(1200);                                // the real ghost lunges at the player
            won = false;
            nightScore = 0;
            endReason = "Wrong. It was " + hidden.getName() + ". You lose " + Config.WRONG_NAME_COST + " sanity.";
            hw.rig.alarm(3);
            setState(OVER, now);
        }
    }

    /** The timer ran out: the ghost escapes and the night is lost. */
    private void escape(long now) {
        timeLeftMs = 0;
        if (current != null) current.abort();
        current = null;
        if (banishing != null) banishing.cancel();      // time ran out mid-banish: the ghost gets away
        run.loseSanity(Config.ESCAPE_COST);
        won = false;
        escaped = true;
        nightScore = 0;
        endReason = "Time's up! " + hidden.getName() + " slipped away. You lose " + Config.ESCAPE_COST + " sanity.";
        hw.rig.alarm(3);
        setState(OVER, now);
    }

    /**
     * The rig joins in after the night. Banished: the LED runs through the colours and the
     * servo pointer dances between two marks, then settles back in the middle.
     * Wrong name: the LED strobes red while the real ghost lunges on screen.
     */
    private void rigShow(long now) {
        long t = now - stateSince;
        if (won) {
            int step = (int) (t / 150);
            if (t < 2700 && step != showStep) {
                showStep = step;
                int[][] colours = { {255, 0, 0}, {255, 140, 0}, {255, 255, 0}, {0, 255, 0}, {0, 200, 255}, {160, 0, 255} };
                int[] c = colours[step % colours.length];
                hw.rig.led(c[0], c[1], c[2]);
                if (step % 5 == 0) hw.rig.moveToMark(step % 10 == 0 ? Config.MARKS / 2 - 1 : Config.MARKS / 2 + 1);
            } else if (t >= 2700 && showStep >= 0) {
                showStep = -1;
                hw.rig.moveToMark(Config.MARKS / 2);
                hw.rig.showSanity(run.getSanity());
            }
        } else if (!escaped) {
            int step = (int) (t / 90);
            if (t < 1400 && step != showStep) {
                showStep = step;
                if (step % 2 == 0) hw.rig.led(255, 0, 0); else hw.rig.led(0, 0, 0);
            } else if (t >= 1400 && showStep >= 0) {
                showStep = -1;
                hw.rig.led(255, 0, 0);
            }
        }
    }

    /** True when a suspect matches the ghost on every test that works tonight, so the tests could not tell them apart. */
    private static boolean sameOnWorkingTests(Ghost g, Ghost hidden, boolean[] works) {
        for (int t = 0; t < TEST_TRAITS.length; t++) {
            if (works[t] && g.has(TEST_TRAITS[t]) != hidden.has(TEST_TRAITS[t])) return false;
        }
        return true;
    }

    private void checkBanishing(long now) {
        if (banishing.getPhase() != Banishing.DONE) return;
        winNight(now);
    }

    /** The ghost is banished: score the night. */
    private void winNight(long now) {
        won = true;
        nightScore = (100L + 8L * run.getSanity()) * multiplier * nightNo;
        run.bank(nightScore);
        cleanBonus = run.isCleanNight();
        if (cleanBonus) run.gainSanity(Config.CLEAN_NIGHT_BONUS);
        endReason = hidden.getName() + " is banished!";
        run.addBanished(hidden);
        hw.rig.led(255, 255, 255);
        setState(OVER, now);
    }

    /** The hunt updates itself from the hub; the night reacts to what phase it is in. */
    private void checkHunt(long now) {
        // A test that finished just before the hunt struck still counts.
        if (state == TEST && current.getResult() != Test.RUNNING) checkTest(now);
        // A test cannot start while a hunt is active, so a test still running now was caught by it.
        if (hunt.getPhase() == Hunt.ACTIVE && state == TEST) {
            current.abort();
            current = null;
            run.loseSanity(Config.CAUGHT_MID_TEST_COST);
            run.markFailedTest();
            setState(MENU, now);
            message = "The hunt caught you mid-test!";
        }
        if (hunt.getPhase() == Hunt.DONE) {
            if (hunt.wasExposed()) run.markHuntLoss();
            hunt = null;
            huntEndedAt = now;                         // the timer stays paused a moment longer
            scheduleHunt(now);
            hw.rig.showSanity(run.getSanity());
            message = "The hunt has passed.";
        }
    }

    private void scheduleHunt(long now) {
        long min, max;
        if (run.getSanity() < 25) { min = Config.HUNT_GAP_MIN_LOW; max = Config.HUNT_GAP_MAX_LOW; }
        else { min = Config.HUNT_GAP_MIN; max = Config.HUNT_GAP_MAX; }
        long gap = min + (long) (RNG.nextDouble() * (max - min));
        if (omen[RESTLESS]) gap = gap * 7 / 10;
        nextHuntAt = now + gap;
    }

    // =====================================================================
    // Helpers used by the tests
    // =====================================================================
    public Ghost getHidden() { return hidden; }
    public double timeFactor() { return omen[FADING] ? 0.75 : 1.0; }
    public long sweepHoldMs() { return omen[WEAK] ? 2000 : 1000; }
    public void restoreLedLater(long when) { ledRestoreAt = when; }

    /** A riddle about this trait and value that this run has not heard yet. */
    public String riddleFor(int trait, boolean value) {
        return Riddles.pick(trait, value, run.usedRiddles());
    }

    /**
     * Called by Contact the moment the spirit speaks. From then on that trait gets no more
     * riddles tonight, whether the player finishes reading it or a hunt cuts it short.
     */
    public void recordRiddle(int trait, String riddle) {
        if (riddled[trait]) return;
        riddled[trait] = true;
        riddles.add(riddle);
    }

    /**
     * The traits the spirit can still give a riddle about. A trait is left out once it has had a
     * riddle tonight, and once a test has proven it, so no answer is ever given twice in a night.
     */
    public int[] contactTraits() {
        int count = 0;
        for (int t = 0; t < Traits.COUNT; t++) if (known[t] == -1 && !riddled[t]) count++;
        int[] list = new int[count];
        int k = 0;
        for (int t = 0; t < Traits.COUNT; t++) if (known[t] == -1 && !riddled[t]) list[k++] = t;
        return list;
    }

    /** Tonight's riddles, oldest first. */
    public java.util.List<String> getRiddles() { return riddles; }

    /** With the Thin Veil omen the riddles are only shown once, not kept on the naming screen. */
    public boolean riddlesKept() { return !omen[THIN_VEIL]; }



    private static int multiplierFor(int traitsKnown) {
        if (traitsKnown <= 1) return 10;
        if (traitsKnown == 2) return 5;
        if (traitsKnown == 3) return 3;
        return 1;
    }

    /**
     * Makes a new Test object for menu entry i. Making it registers it with the hub (the
     * ConcreteObserver constructor), the same way new Mouse(c1) joins the clock on the slides.
     */
    private Test makeTest(int i) {
        switch (i) {
            case 0:  return new Sweep(hw.hub);
            case 1:  return new FrequencyScan(hw.hub);
            case 2:  return new ShadowTest(hw.hub);
            case 3:  return new Stabilise(hw.hub);
            default: return new Contact(hw.hub);
        }
    }

    // =====================================================================
    // Read by the Display
    // =====================================================================
    public int getState() { return state; }
    public int getNightNo() { return nightNo; }
    public Ghost[] getSuspects() { return suspects; }
    public boolean testWorks(int i) { return works[i]; }
    public String testName(int i) { return TEST_NAMES[i]; }
    /** The trait test i proves (the four physical tests only; Contact can ask about any trait). */
    public int testTrait(int i) { return TEST_TRAITS[i]; }
    public Test getCurrent() { return current; }
    public Hunt getHunt() { return hunt; }
    public Banishing getBanishing() { return banishing; }
    public int getMenuIndex() { return menuIndex; }
    public String getMessage() { return message; }
    public boolean isWon() { return won; }
    public long getNightScore() { return nightScore; }
    public int getMultiplier() { return multiplier; }
    public boolean hadCleanBonus() { return cleanBonus; }
    public String getEndReason() { return endReason; }
    public boolean omenActive(int o) { return omen[o]; }
    public int knownValue(int trait) { return known[trait]; }
    public boolean autoStrike() { return nightNo <= 2; }
    public boolean isFinished(long now) { return state == OVER && now - stateSince >= OVER_MS; }
    public boolean isEscaped() { return escaped; }
    /** The suspect the player wrongly named (null if they did not name a wrong one). */
    public Ghost getNamed() { return named; }
    public int getLossCost() { return escaped ? Config.ESCAPE_COST : Config.WRONG_NAME_COST; }
    public long getTimeLeftMs() { return Math.max(0, timeLeftMs); }
    /** The timer runs on the board, in tests and while naming, but not during a hunt. */
    public boolean isTimerRunning() {
        return (state == MENU || state == TEST || state == NAMING || state == BANISH) && !isHuntOn();
    }
    /** True during a hunt and for a short grace after it: the timer is paused and shows PAUSED. */
    public boolean isHuntOn() {
        return hunt != null || (huntEndedAt >= 0 && lastTimerAt - huntEndedAt < Config.HUNT_GRACE_MS);
    }

    public int knownCount() {
        int c = 0;
        for (int k : known) if (k != -1) c++;
        return c;
    }

    /**
     * Traits proven by a physical test. Only these set the multiplier: the spirit's riddles are
     * free extra information, so they never lower it.
     */
    public int provenCount() {
        int c = 0;
        for (int t = 0; t < Traits.COUNT; t++) if (known[t] != -1) c++;
        return c;
    }

    /** The multiplier the player would get by naming the ghost right now. */
    public int currentMultiplier() { return multiplierFor(provenCount()); }

    /**
     * A suspect is ruled out when a PROVEN trait disagrees with it. The spirit's riddles never
     * rule anyone out on the board: solving them is up to the player.
     */
    public boolean isEliminated(Ghost g) {
        for (int t = 0; t < Traits.COUNT; t++) {
            if (known[t] != -1 && g.has(t) != (known[t] == 1)) return true;
        }
        return false;
    }

    /** The menu: tonight's working tests, then "Name the ghost". */
    /**
     * The menu: tonight's working tests that have not been passed yet (a failed test stays so it
     * can be retried), Contact while the spirit still has a riddle to give, then "Name the ghost".
     */
    public int[] getMenuOptions() {
        int[] list = new int[works.length + 1];
        int k = 0;
        for (int i = 0; i < 4; i++) {
            if (works[i] && !isProven(TEST_TRAITS[i])) list[k++] = i;
        }
        if (works[4] && contactTraits().length > 0) list[k++] = 4;
        list[k++] = NAME_OPTION;
        int[] options = new int[k];
        for (int i = 0; i < k; i++) options[i] = list[i];
        return options;
    }

    /** True once a physical test has confirmed this trait. */
    public boolean isProven(int trait) { return known[trait] != -1; }

    /** The traits no test has proven yet. */
    public int[] unknownTraits() {
        int count = 0;
        for (int t = 0; t < Traits.COUNT; t++) if (known[t] == -1) count++;
        int[] list = new int[count];
        int k = 0;
        for (int t = 0; t < Traits.COUNT; t++) if (known[t] == -1) list[k++] = t;
        return list;
    }

    /** How many suspects the proven traits have not ruled out. */
    public int suspectsLeft() {
        int c = 0;
        for (Ghost g : suspects) if (!isEliminated(g)) c++;
        return c;
    }

    // =====================================================================
    // Debug mode (the ` key). Each method is one debug key; they are called by the Game,
    // on the Swing thread between two ticks, and return a line to show on screen.
    // =====================================================================
    private boolean inPlay() { return state == MENU || state == TEST || state == NAMING; }
    private boolean huntActive() { return hunt != null && hunt.getPhase() == Hunt.ACTIVE; }

    /** Stops the running test without any cost or result. */
    private void dropTest() {
        if (current != null) current.abort();
        current = null;
    }

    /** Keys 1 to 5: start any test at once, even one that does not work tonight. */
    public String debugStartTest(int i) {
        if (!inPlay() || lastReading == null) return "Tests can only start from the board.";
        if (huntActive()) return "Not during an active hunt.";
        if (i == 4 && contactTraits().length == 0) return "The spirit has no riddles left tonight.";
        dropTest();
        current = makeTest(i);
        current.start(this, hw, lastReading);
        setState(TEST, lastReading.getTime());
        message = current.getName() + ": " + current.getInstructions();
        return "Started " + current.getName() + (works[i] ? "" : " (not working tonight)");
    }

    /** Key E: the timer runs out now, so the ghost escapes. */
    public String debugEscape(long now) {
        if (!inPlay() && state != BANISH) return "Not now.";
        if (huntActive()) return "Not during an active hunt.";
        if (hunt != null) debugHunt(now);
        escape(now);
        return "Timer ran out";
    }

    /** Key D: everything about tonight, printed to the console (Eclipse's Console view). */
    public String debugDump(long now) {
        StringBuilder b = new StringBuilder();
        b.append("---- Night ").append(nightNo).append(", state ").append(state).append(", timer ").append(timeLeftMs / 1000).append(" s ----\n");
        b.append("Hidden ghost: ").append(hidden.getName());
        for (int t = 0; t < Traits.COUNT; t++) b.append(", ").append(Traits.label(t, hidden.has(t)));
        b.append("\nArrows: ").append(String.join(" ", hidden.getSequence())).append(hidden.isLoud() ? ", then a loud noise" : ", then silence");
        b.append("\nWorking tests: ");
        for (int i = 0; i < 5; i++) if (works[i]) b.append(TEST_NAMES[i]).append("  ");
        b.append("\nOmens: ");
        for (int o = 0; o < omen.length; o++) if (omen[o]) b.append(OMEN_NAMES[o]).append(" (").append(OMEN_TEXT[o]).append(")  ");
        b.append("\nSuspects:");
        for (Ghost g : suspects) {
            b.append("\n  ").append(g == hidden ? "* " : "  ").append(g.getName());
            for (int t = 0; t < Traits.COUNT; t++) b.append(", ").append(Traits.label(t, g.has(t)));
            if (isEliminated(g)) b.append("  (ruled out)");
        }
        b.append("\nRiddles tonight:");
        for (String r : riddles) b.append("\n  ").append(r);
        b.append("\nNext hunt in ").append(hunt != null ? "(hunt on now)" : ((nextHuntAt - now) / 1000) + " s");
        b.append(", sanity ").append(run.getSanity()).append(", score ").append(run.getScore());
        System.out.println(b);
        return "State printed to the console";
    }

    /** Key 6: a hunt now, or end the current one at once. */
    public String debugHunt(long now) {
        if (hunt != null) {
            hunt.cancel();
            hunt = null;
            huntEndedAt = -1;
            scheduleHunt(now);
            hw.audio.resume();
            hw.rig.silence();
            hw.rig.showSanity(run.getSanity());
            message = "The hunt has passed.";
            return "Hunt ended";
        }
        if (state == INTRO) setState(MENU, now);
        if (state != MENU && state != TEST) return "Hunts only come on the board or in a test.";
        nextHuntAt = now;
        forceHunt = true;
        return "Hunt starting";
    }

    /** Key 7: pass the test (Contact: the spirit speaks, then closes), or the next banishing step. */
    public String debugPass(long now) {
        if (state == INTRO) { setState(MENU, now); return "Intro skipped"; }
        if (state == TEST && current != null) {
            if (current instanceof Contact && !((Contact) current).isSpeaking()) {
                ((Contact) current).debugSpeak(now);
                return "Spirit speaks: 7 again closes it";
            }
            current.forceResult(Test.PASSED);
            return current.getName() + " passed";
        }
        if (state == BANISH && banishing != null) return banishing.debugAdvance(now);
        return "Nothing to pass here.";
    }

    /** Key 8: fail the running test. */
    public String debugFail() {
        if (state != TEST || current == null) return "No test is running.";
        current.forceResult(Test.FAILED);
        return current.getName() + " failed";
    }

    /** Keys 9 and 0: add time to the night timer, or set it to ten seconds. */
    public String debugAddTime(long ms) { timeLeftMs = Math.max(1000, timeLeftMs + ms); return "Timer " + (timeLeftMs / 1000) + " s"; }
    public String debugSetTime(long ms) { timeLeftMs = ms; return "Timer " + (ms / 1000) + " s"; }

    /** Keys - and =: lose or gain sanity. */
    public String debugSanity(int delta) {
        if (delta < 0) run.loseSanity(-delta); else run.gainSanity(delta);
        hw.rig.showSanity(run.getSanity());
        return "Sanity " + run.getSanity();
    }

    /** Key [: straight to naming the ghost. */
    public String debugNaming(long now) {
        if (!inPlay() && state != INTRO) return "Not now.";
        if (huntActive()) return "Not during an active hunt.";
        dropTest();
        setState(NAMING, now);
        message = "Turn the dial to choose a suspect, then tap to name it.";
        return "Naming";
    }

    /** Key ]: the ghost is named correctly, straight to the banishing. */
    public String debugBanish(long now) {
        if (!inPlay() && state != INTRO) return "Not now.";
        if (huntActive()) return "Not during an active hunt.";
        dropTest();
        if (hunt != null) debugHunt(now);
        multiplier = multiplierFor(provenCount());
        banishing = new Banishing(hidden, hw, now);
        setState(BANISH, now);
        message = "Correct! Now banish " + hidden.getName() + ". Tap the grip, then flick the wand along each arrow.";
        return "Banishing " + hidden.getName();
    }

    /** Key , : win the night at once. */
    public String debugWin(long now) {
        if (state == OVER) return "The night is already over.";
        dropTest();
        if (hunt != null) { hunt.cancel(); hunt = null; hw.audio.resume(); }
        if (banishing != null) banishing.cancel();
        else multiplier = multiplierFor(provenCount());
        winNight(now);
        return "Night won";
    }

    /** Key ; : tonight's omen becomes the next one on the list (then none, then round again). */
    public String debugNextOmen() {
        debugOmen = (debugOmen + 1) % (OMEN_NAMES.length + 1);
        for (int i = 0; i < omen.length; i++) omen[i] = i == debugOmen;
        return debugOmen < OMEN_NAMES.length ? "Omen: " + OMEN_NAMES[debugOmen] : "No omens";
    }

    /** Key ' : a new random set of omens, as night 4 onwards would roll them. */
    public String debugRerollOmens() {
        for (int i = 0; i < omen.length; i++) omen[i] = false;
        int count = 1 + RNG.nextInt(3);
        int placed = 0;
        while (placed < count) {
            int o = RNG.nextInt(OMEN_NAMES.length);
            if (!omen[o]) { omen[o] = true; placed++; }
        }
        return "Omens rerolled";
    }

    /** For the debug panel. */
    public boolean isRiddled(int trait) { return riddled[trait]; }
    public long debugNextHuntIn(long now) { return nextHuntAt - now; }
}
