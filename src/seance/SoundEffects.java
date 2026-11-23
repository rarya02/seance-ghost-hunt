package seance;

import javax.sound.sampled.Clip;

/**
 * Arcade sound effects, and an Observer of the SensorHub (a ConcreteObserver).
 *
 * With every new Reading it looks at what the game is doing and compares it with the
 * previous tick (the menu moved, a night started, a ghost was banished...) and plays the
 * matching sound. All sounds are made by the Synth.
 *
 * Moments the rig's buzzer or the wand already signal are left to the hardware, so the two
 * never double up: a test passing or failing, a wrong name, the hunt warning, being exposed
 * in a hunt, each banishing pose, and the test clicks and ticks. During the hiding part of a hunt the
 * computer stays quiet, so its speakers cannot give the player away through the mic.
 *
 * It also sets the heartbeat in the music: slow during a test, fast during the banishing,
 * and off during hunts and the silent finish, so the speakers never spoil a silence check.
 */
public class SoundEffects extends ConcreteObserver {
    private final Game game;
    private final AudioDirector audio;
    private final Clip move, select, start, pass, huntActive, huntOver, finish, banished,
                       nightStart, gameOver, dawn, spirit, scare;

    // What the game looked like on the previous tick
    private int lastScreen = -1;
    private Night lastNight = null;
    private int lastState = -1, lastIndex = -1, lastHunt = -1;
    private int lastBanishPhase = -1;
    private char lastLetter = 0;
    private int lastDawnCount = 0;
    private Test lastTest = null;
    private boolean lastSpeaking = false;

    public SoundEffects(Subject subject, Game game, AudioDirector audio) {
        super(subject);                  // registers with the hub, as on the slides
        this.game = game;
        this.audio = audio;
        float[] b;

        b = Synth.buffer(0.04); Synth.tone(b, 0, 0.03, 1320, 1320, Synth.SQUARE, 0.18); move = Synth.clip(b);
        b = Synth.buffer(0.12); Synth.tone(b, 0, 0.05, 660, 660, Synth.SQUARE, 0.2); Synth.tone(b, 0.05, 0.06, 990, 990, Synth.SQUARE, 0.2); select = Synth.clip(b);
        b = Synth.buffer(0.35); Synth.tone(b, 0, 0.08, Synth.midi(83), Synth.midi(83), Synth.SQUARE, 0.2); Synth.pluck(b, 0.08, 0.27, Synth.midi(88), Synth.SQUARE, 0.22); start = Synth.clip(b);

        b = Synth.buffer(0.5);
        int[] up = { 72, 76, 79, 84 };
        for (int i = 0; i < 4; i++) Synth.tone(b, i * 0.07, i == 3 ? 0.2 : 0.065, Synth.midi(up[i]), Synth.midi(up[i]), Synth.SQUARE, 0.2);
        pass = Synth.clip(b);

        // A hunt begins. All of this plays during the 5 s warning and ends before the hunt
        // itself, so the microphone never hears it: a shriek over a deep boom, then a low drone,
        // breathing and a heartbeat that speeds up until the hunt starts.
        b = Synth.buffer(4.6);
        Synth.tone(b, 0, 0.5, 2200, 900, Synth.NOISE, 0.28);
        Synth.tone(b, 0, 0.7, 1480, 620, Synth.SQUARE, 0.12);
        Synth.tone(b, 0.02, 0.7, 1568, 660, Synth.SQUARE, 0.10);       // a semitone apart: grating
        Synth.pluck(b, 0, 1.1, 48, Synth.SINE, 0.7);
        Synth.thump(b, 0, 0.6);
        Synth.tone(b, 0.6, 3.9, 62, 55, Synth.TRIANGLE, 0.24);         // the drone, sinking
        Synth.tone(b, 0.6, 3.9, 69, 61, Synth.SQUARE, 0.025);
        for (int i = 0; i < 3; i++) {                                  // slow breaths: in, then out
            double at = 1.0 + i * 1.15;
            Synth.tone(b, at, 0.5, 500, 900, Synth.NOISE, 0.06);
            Synth.tone(b, at + 0.5, 0.55, 800, 300, Synth.NOISE, 0.08);
        }
        double at = 1.2, gap = 0.62;
        while (at < 4.4) {                                             // the heartbeat races
            Synth.thump(b, at, 0.5);
            Synth.thump(b, at + 0.16, 0.35);
            at += gap;
            gap = Math.max(0.26, gap * 0.86);
        }
        huntActive = Synth.clip(b);
        b = Synth.buffer(0.4);
        int[] relief = { 72, 76, 79 };
        for (int i = 0; i < 3; i++) Synth.pluck(b, i * 0.09, 0.2, Synth.midi(relief[i]), Synth.TRIANGLE, 0.3);
        huntOver = Synth.clip(b);

        b = Synth.buffer(0.6);
        for (int n : new int[] { 69, 72, 76 }) Synth.pluck(b, 0, 0.6, Synth.midi(n), Synth.SQUARE, 0.1);
        finish = Synth.clip(b);

        // banished: a rising charge while it shudders, the burst, then a victory fanfare with sparkles
        b = Synth.buffer(3.4);
        Synth.tone(b, 0, 0.7, 180, 1400, Synth.SQUARE, 0.08);             // the charge (0.7 s, like the screen)
        Synth.tone(b, 0, 0.7, 90, 700, Synth.TRIANGLE, 0.18);
        Synth.tone(b, 0.7, 0.6, 4000, 200, Synth.NOISE, 0.35);            // the burst
        Synth.thump(b, 0.7, 0.8);
        int[] fanfare = { 67, 72, 76, 79 };
        for (int i = 0; i < 4; i++) Synth.tone(b, 0.9 + i * 0.1, 0.09, Synth.midi(fanfare[i]), Synth.midi(fanfare[i]), Synth.SQUARE, 0.2);
        Synth.sing(b, 1.3, 0.5, Synth.midi(84), Synth.SQUARE, 0.2);
        Synth.tone(b, 1.3, 0.5, Synth.midi(48), Synth.midi(48), Synth.TRIANGLE, 0.3);
        int[] answer = { 79, 81, 83 };
        for (int i = 0; i < 3; i++) Synth.tone(b, 1.85 + i * 0.12, 0.1, Synth.midi(answer[i]), Synth.midi(answer[i]), Synth.SQUARE, 0.18);
        Synth.sing(b, 2.21, 1.0, Synth.midi(84), Synth.SQUARE, 0.2);
        Synth.sing(b, 2.21, 1.0, Synth.midi(88), Synth.PULSE, 0.12);
        Synth.tone(b, 2.21, 1.0, Synth.midi(36), Synth.midi(36), Synth.TRIANGLE, 0.32);
        int[] sparkle = { 96, 100, 103, 108, 103, 100, 96, 103 };
        for (int i = 0; i < sparkle.length; i++) Synth.pluck(b, 0.95 + i * 0.16, 0.12, Synth.midi(sparkle[i]), Synth.SINE, 0.07);
        banished = Synth.clip(b);

        // a wrong name: the real ghost lunges at the screen
        b = Synth.buffer(1.4);
        Synth.tone(b, 0.25, 0.9, 2600, 300, Synth.SQUARE, 0.16);
        Synth.tone(b, 0.27, 0.9, 2760, 320, Synth.SQUARE, 0.14);
        Synth.tone(b, 0.25, 1.1, 5000, 400, Synth.NOISE, 0.32);
        Synth.thump(b, 0.25, 0.9);
        Synth.pluck(b, 0.25, 1.15, 41, Synth.SINE, 0.7);
        scare = Synth.clip(b);


        b = Synth.buffer(0.8);
        int[] spooky = { 69, 72, 76 };
        for (int i = 0; i < 3; i++) Synth.tone(b, i * 0.12, 0.11, Synth.midi(spooky[i]), Synth.midi(spooky[i]), Synth.PULSE, 0.2);
        Synth.pluck(b, 0.36, 0.44, Synth.midi(81), Synth.PULSE, 0.22);
        nightStart = Synth.clip(b);

        b = Synth.buffer(1.6);
        int[] down = { 64, 63, 62, 61 };
        for (int i = 0; i < 4; i++) Synth.tone(b, i * 0.22, 0.2, Synth.midi(down[i]), Synth.midi(down[i]), Synth.SQUARE, 0.2);
        Synth.tone(b, 0.88, 0.7, Synth.midi(60), Synth.midi(52), Synth.SQUARE, 0.2);
        gameOver = Synth.clip(b);

        // Dawn, timed with the screen: the clock ticks, strikes six at 0.7 s, the sun rises on a
        // swelling arpeggio, DAWN lands at 3 s on a crash, then a victory theme over fireworks.
        b = Synth.buffer(8.0);
        for (int i = 0; i < 3; i++) Synth.hat(b, 0.05 + i * 0.22, 0.25);                 // tick, tick, tick
        for (int i = 0; i < 3; i++) Synth.pluck(b, 0.7 + i * 0.16, 1.2, Synth.midi(i == 2 ? 84 : 79), Synth.SINE, 0.22);   // the chime
        Synth.tone(b, 1.2, 1.8, Synth.midi(48), Synth.midi(60), Synth.TRIANGLE, 0.25);  // the swell
        int[] climb = { 60, 64, 67, 72, 76, 79, 84, 88 };
        for (int i = 0; i < 16; i++) Synth.pluck(b, 1.2 + i * 0.112, 0.14, Synth.midi(climb[i % 8] + (i >= 8 ? 12 : 0)), Synth.PULSE, 0.07 + i * 0.004);
        Synth.tone(b, 2.4, 0.6, 300, 3000, Synth.NOISE, 0.08);                          // a whoosh into the hit
        Synth.thump(b, 3.0, 0.9);
        Synth.tone(b, 3.0, 0.9, 6000, 2000, Synth.NOISE, 0.22);                         // the crash
        int[][] theme = { {72, 2}, {76, 1}, {79, 1}, {84, 4}, {83, 1}, {84, 1}, {86, 2}, {88, 6} };
        double when = 3.0, beat = 0.16;
        for (int[] n : theme) {
            Synth.sing(b, when, n[1] * beat * 0.95, Synth.midi(n[0]), Synth.SQUARE, 0.17);
            Synth.sing(b, when, n[1] * beat * 0.95, Synth.midi(n[0] - 12), Synth.PULSE, 0.06);
            when += n[1] * beat;
        }
        int[] bass = { 48, 48, 53, 55, 48, 48 };
        for (int i = 0; i < bass.length; i++) Synth.tone(b, 3.0 + i * 0.5, 0.45, Synth.midi(bass[i]), Synth.midi(bass[i]), Synth.TRIANGLE, 0.3);
        for (int i = 0; i < 6; i++) Synth.thump(b, 3.0 + i * 0.5, 0.35);
        java.util.Random pops = new java.util.Random(5);
        for (int i = 0; i < 12; i++) {                                                   // firework pops and crackle
            double p = 3.02 + i * 0.38;                                              // as each one bursts on screen
            Synth.tone(b, p, 0.25, 2500, 400, Synth.NOISE, 0.12);
            for (int k = 0; k < 4; k++) Synth.hat(b, p + 0.08 + pops.nextDouble() * 0.3, 0.06);
        }
        Synth.sing(b, 6.4, 1.4, Synth.midi(84), Synth.SQUARE, 0.12);                    // the last long chord
        Synth.sing(b, 6.4, 1.4, Synth.midi(88), Synth.PULSE, 0.08);
        Synth.sing(b, 6.4, 1.4, Synth.midi(91), Synth.PULSE, 0.06);
        Synth.tone(b, 6.4, 1.4, Synth.midi(36), Synth.midi(36), Synth.TRIANGLE, 0.3);
        dawn = Synth.clip(b);

        // the spirit speaks: a slow, wavering rise of bell-like notes
        b = Synth.buffer(1.6);
        int[] call = { 57, 64, 69, 72 };
        for (int i = 0; i < call.length; i++) Synth.sing(b, i * 0.22, 1.6 - i * 0.22, Synth.midi(call[i]), Synth.SINE, 0.12);
        spirit = Synth.clip(b);
    }

    /** 0 none, 1 slow (a test is running), 2 fast (the banishing arrows). Never during a hunt or the finish. */
    private int heartbeatLevel() {
        Night night = game.getNight();
        if (game.getScreen() != Game.PLAYING || night == null || night.getHunt() != null) return 0;
        if (night.getState() == Night.TEST) return 1;
        if (night.getState() == Night.BANISH && night.getBanishing() != null) {
            Banishing b = night.getBanishing();
            if (b.getPhase() == Banishing.SEQUENCE) return 2;
        }
        return 0;
    }

    private void play(Clip c) {
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        c.start();
    }

    /** Observer: compare the game with the previous tick and play a sound for what changed. */
    public void update(Reading r) {
        audio.setHeartbeat(heartbeatLevel());
        int screen = game.getScreen();
        if (game.getDawnCount() != lastDawnCount) {          // dawn (or a debug replay of it)
            lastDawnCount = game.getDawnCount();
            play(dawn);
        }
        if (screen != lastScreen) {
            if (lastScreen == Game.TITLE && screen == Game.PLAYING) play(start);
            if (lastScreen == Game.PLAYING && screen != Game.PLAYING && !game.reachedDawn()) play(gameOver);
            if (lastScreen == Game.ENTER_NAME && screen == Game.ENDED) play(pass);
            lastScreen = screen;
        }

        if (screen == Game.ENTER_NAME) {               // locking a letter in beeps on the rig
            if (lastLetter != 0 && game.getLetter() != lastLetter) play(move);
            lastLetter = game.getLetter();
            return;
        }
        lastLetter = 0;
        if (screen != Game.PLAYING || game.getNight() == null) return;

        Night night = game.getNight();
        if (night != lastNight) {
            lastNight = night;
            lastState = -1;
            lastHunt = -1;
            play(nightStart);
        }
        int state = night.getState();

        // moving between screens of the night
        // (a physical test passing or failing, and a wrong name, are signalled by the rig's buzzer)
        if (state != lastState) {
            if (lastState == Night.MENU && (state == Night.TEST || state == Night.NAMING)) play(select);
            boolean contactAnswered = lastTest instanceof Contact && lastTest.getResult() == Test.PASSED;
            if (lastState == Night.TEST && contactAnswered) play(pass);
            if (lastState == Night.NAMING && state == Night.MENU) play(move);
            if (lastState == Night.NAMING && state == Night.BANISH) play(pass);
            if (state == Night.OVER && night.isWon()) play(banished);
            if (lastState == Night.NAMING && state == Night.OVER && !night.isWon()) play(scare);
            lastState = state;
            lastIndex = -1;
            lastBanishPhase = -1;
        }

        // the dial moving through a menu or the suspects
        if (state == Night.MENU || state == Night.NAMING) {
            int index = night.getMenuIndex();
            if (lastIndex >= 0 && index != lastIndex) play(move);
            lastIndex = index;
        }

        // inside Contact: the spirit begins to speak its riddle
        if (state == Night.TEST && night.getCurrent() instanceof Contact) {
            Contact c = (Contact) night.getCurrent();
            if (c.isSpeaking() && !lastSpeaking) play(spirit);
            lastSpeaking = c.isSpeaking();
        } else {
            lastSpeaking = false;
        }
        lastTest = state == Night.TEST ? night.getCurrent() : null;

        // banishing: each pose beeps on the rig; a chord when the last step (scream or silence) begins
        if (state == Night.BANISH && night.getBanishing() != null) {
            Banishing b = night.getBanishing();
            if (lastBanishPhase >= 0 && b.getPhase() != lastBanishPhase && b.getPhase() == Banishing.FINISH) play(finish);
            lastBanishPhase = b.getPhase();
        }

        // hunts: the warning sounds on the rig's buzzer; the computer marks the hunt starting and ending
        int hunt = night.getHunt() == null ? -1 : night.getHunt().getPhase();
        if (hunt != lastHunt) {
            if (hunt == Hunt.WARNING && lastHunt == -1) play(huntActive);   // with the warning, so the mic is not checked yet
            else if (hunt == -1 && lastHunt == Hunt.ACTIVE) play(huntOver);
            lastHunt = hunt;
        }
    }
}
