package seance;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Draws everything on screen in an arcade style: pixel font, neon colours, CRT scanlines.
 * It is an Observer of the SensorHub (a ConcreteObserver): every new Reading is kept (for the
 * instruments panel and the connection lights) and the screen is redrawn, 20 times a second.
 *
 * Java allows only one parent class, and Display's parent is ConcreteObserver, so the window
 * area itself is a small inner JPanel (Screen) that calls back into Display to draw.
 *
 * The game is drawn on a fixed 1280 x 780 canvas that is scaled to fit the window,
 * so the window can be resized or made full screen for the demo.
 */
public class Display extends ConcreteObserver {
    private static final int W = 1280, H = 780;

    // Neon arcade palette
    private static final Color BG = new Color(8, 5, 15);
    private static final Color PANEL = new Color(18, 10, 34, 235);
    private static final Color MAGENTA = new Color(255, 43, 214);
    private static final Color CYAN = new Color(34, 230, 255);
    private static final Color GREEN = new Color(61, 255, 122);
    private static final Color AMBER = new Color(255, 194, 26);
    private static final Color RED = new Color(255, 51, 85);
    private static final Color WHITE = new Color(244, 240, 255);
    private static final Color LAV = new Color(185, 176, 224);
    private static final Color DIM = new Color(111, 102, 144);
    private static final Color DARK = new Color(40, 30, 64);
    private static final Color[] GHOST_TINTS = { CYAN, MAGENTA, GREEN, AMBER, LAV, new Color(255, 120, 80), new Color(120, 160, 255), WHITE };

    /** An arrow pointing up; turned to point left, right or down when drawn. */
    private static final String[] ARROW = {
        "...#...",
        "..###..",
        ".#####.",
        "#######",
        "..###..",
        "..###..",
        "..###..",
    };

    private final Game game;
    private final Screen screen = new Screen();
    private Reading latest = null;             // the newest reading from the hub
    private final BufferedImage scanlines;
    private final BufferedImage vignette;
    private final Random shakeRng = new Random();

    // Remembered so animations know when something changed
    private int seenScreen = -1;
    private long screenSince = 0;
    private Night seenNight = null;
    private int seenState = -1;
    private long stateSince = 0;
    private Run seenRun = null;
    private int lastSanity = -1;
    private long flashUntil = 0;
    private boolean showInstruments = false; // toggled with the I key
    private Hunt seenHunt = null;           // the hunt on screen, and when its current phase began
    private int seenHuntPhase = -1;
    private long huntPhaseSince = 0;
    private long lungeUntil = 0;             // the spirit lunges when an exposed second costs sanity
    private final java.util.Map<Integer, int[]> ghostPixels = new java.util.HashMap<Integer, int[]>();

    // Background stars: fixed positions and speeds, moved by the clock
    private final int[] starX = new int[90], starY = new int[90], starSpeed = new int[90];

    public Display(Subject subject, Game game) {
        super(subject);                  // registers with the hub, as on the slides
        this.game = game;
        scanlines = makeScanlines();
        vignette = makeVignette();
        Random r = new Random(421);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextInt(W);
            starY[i] = r.nextInt(H);
            starSpeed[i] = 4 + r.nextInt(18);
        }
    }

    /** The panel that goes in the window. */
    public JPanel getPanel() { return screen; }

    /** The drawing area in the window. Swing calls paintComponent, which hands over to Display. */
    private class Screen extends JPanel {
        Screen() {
            setPreferredSize(new Dimension(W, H));
            setBackground(Color.BLACK);
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            draw(graphics, getWidth(), getHeight());
        }
    }

    /** Observer: keeps the newest Reading and redraws the screen. */
    public void update(Reading r) {
        latest = r;
        screen.repaint();
    }

    // =====================================================================
    // Main paint: scale the canvas to the window, draw the screen, add CRT effects
    // =====================================================================
    private void draw(Graphics graphics, int width, int height) {
        Graphics2D outer = (Graphics2D) graphics;
        outer.setColor(Color.BLACK);
        outer.fillRect(0, 0, width, height);

        double s = Math.min(width / (double) W, height / (double) H);
        Graphics2D g = (Graphics2D) outer.create();
        g.translate((width - W * s) / 2, (height - H * s) / 2);
        g.scale(s, s);
        g.clipRect(0, 0, W, H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        long now = System.currentTimeMillis();
        noticeChanges(now);

        int shake = shakeAmount();
        if (shake > 0) g.translate(shakeRng.nextInt(2 * shake + 1) - shake, shakeRng.nextInt(2 * shake + 1) - shake);

        int screen = game.getScreen();
        if (screen == Game.TITLE) drawTitle(g, now);
        else if (screen == Game.ENDED || screen == Game.ENTER_NAME) drawEnded(g, now);
        else drawPlaying(g, now);
        if (game.isDebug()) drawDebug(g, now);
        if (game.isDebug() && game.showMonitor()) drawMonitor(g);
        if (game.isPaused()) drawPaused(g, now);

        // CRT effects on top of everything
        if (now < flashUntil) {
            g.setColor(new Color(255, 0, 40, (int) (110 * (flashUntil - now) / 300.0)));
            g.fillRect(-10, -10, W + 20, H + 20);
        }
        g.drawImage(scanlines, 0, 0, null);
        int band = (int) ((now / 9) % (H + 240)) - 120;
        g.setPaint(new GradientPaint(0, band, new Color(255, 255, 255, 0), 0, band + 60, new Color(255, 255, 255, 14), true));
        g.fillRect(0, band, W, 120);
        g.drawImage(vignette, 0, 0, null);
        g.dispose();
    }

    private void noticeChanges(long now) {
        if (game.getScreen() != seenScreen) {
            // entering initials then seeing the table is one end screen, so the score tally does not restart
            boolean sameEnd = seenScreen == Game.ENTER_NAME && game.getScreen() == Game.ENDED;
            seenScreen = game.getScreen();
            if (!sameEnd) screenSince = now;
        }
        Night n = game.getNight();
        int st = n == null ? -1 : n.getState();
        if (n != seenNight || st != seenState) { seenNight = n; seenState = st; stateSince = now; }
        Hunt h = n == null ? null : n.getHunt();
        int hp = h == null ? -1 : h.getPhase();
        if (h != seenHunt || hp != seenHuntPhase) { seenHunt = h; seenHuntPhase = hp; huntPhaseSince = now; }
        Run run = game.getRun();
        if (run != seenRun) { seenRun = run; lastSanity = run == null ? -1 : run.getSanity(); }
        if (run != null) {
            if (lastSanity >= 0 && run.getSanity() < lastSanity && game.getScreen() == Game.PLAYING) {
                flashUntil = now + 300;
                if (h != null && hp == Hunt.ACTIVE) lungeUntil = now + 500;
            }
            lastSanity = run.getSanity();
        }
    }

    /** The screen shakes during a hunt, hardest while the player is exposed. */
    private int shakeAmount() {
        if (game.getScreen() != Game.PLAYING || game.getNight() == null) return 0;
        Hunt h = game.getNight().getHunt();
        if (h == null) return 0;
        if (h.getPhase() == Hunt.ACTIVE) return h.isSafe() ? 1 : 5;
        return 1;
    }

    // =====================================================================
    // Small drawing helpers
    // =====================================================================
    private static boolean blink(long now, int periodMs) { return (now / periodMs) % 2 == 0; }

    private static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    private static Color sanityColour(int s) { return s > 60 ? GREEN : (s >= 25 ? AMBER : RED); }

    /** A number that counts up from 0 over a short time, like an arcade score tally. */
    private static long countUp(long value, long since, long now, long durationMs) {
        double f = Math.min(1.0, (now - since) / (double) durationMs);
        return (long) (value * f);
    }

    private static String pad(long n, int digits) {
        String s = String.valueOf(n);
        while (s.length() < digits) s = "0" + s;
        return s;
    }

    /** Big title text with a cyan and magenta colour split, like an old CRT. */
    private void chroma(Graphics2D g, String s, int cx, int y, int scale, Color core) {
        int off = Math.max(2, scale / 3);
        int x = cx - PixelFont.width(s, scale) / 2;
        PixelFont.draw(g, s, x - off, y, scale, alpha(CYAN, 150));
        PixelFont.draw(g, s, x + off, y, scale, alpha(MAGENTA, 150));
        PixelFont.glow(g, s, x, y, scale, core);
    }

    /** A dark panel with dim edges, bright corner brackets and a label tab. */
    private void panel(Graphics2D g, int x, int y, int w, int h, Color c, String label) {
        g.setColor(PANEL);
        g.fillRect(x, y, w, h);
        g.setColor(alpha(c, 70));
        g.drawRect(x, y, w - 1, h - 1);
        g.setColor(alpha(c, 25));
        g.drawRect(x - 3, y - 3, w + 5, h + 5);
        g.setColor(c);
        int L = 16, T = 3;
        g.fillRect(x, y, L, T); g.fillRect(x, y, T, L);
        g.fillRect(x + w - L, y, L, T); g.fillRect(x + w - T, y, T, L);
        g.fillRect(x, y + h - T, L, T); g.fillRect(x, y + h - L, T, L);
        g.fillRect(x + w - L, y + h - T, L, T); g.fillRect(x + w - T, y + h - L, T, L);
        if (label != null) {
            int tw = PixelFont.width(label, 2) + 16;
            g.setColor(c);
            g.fillRect(x + 14, y - 9, tw, 18);
            PixelFont.draw(g, label, x + 22, y - 6, 2, BG);
        }
    }

    /** A bar made of separate blocks, filled up to the fraction. */
    private void segments(Graphics2D g, int x, int y, int w, int h, int count, double fraction, Color c) {
        int gap = 3;
        int segW = (w - gap * (count - 1)) / count;
        int lit = (int) Math.round(Math.max(0, Math.min(1, fraction)) * count);
        for (int i = 0; i < count; i++) {
            g.setColor(i < lit ? c : alpha(c, 35));
            g.fillRect(x + i * (segW + gap), y, segW, h);
        }
    }

    /** Draws ghost number id (with its own accessory), bobbing gently. alpha below 255 fades it. */
    private void ghost(Graphics2D g, int id, int x, int y, int scale, Color body, long now, int seed, int alpha) {
        int bob = (int) Math.round(Math.sin((now + seed * 377) / 300.0) * scale);
        GhostSprites.draw(g, id, x, y + bob, scale, body, BG, alpha);
    }

    /** A pixel arrow pointing UP, DOWN, LEFT or RIGHT; LEVEL draws a ring. */
    private void arrow(Graphics2D g, String dir, int x, int y, int scale, Color c) {
        g.setColor(c);
        for (int r = 0; r < 7; r++) {
            for (int col = 0; col < 7; col++) {
                boolean on;
                if (dir.equals("UP")) on = ARROW[r].charAt(col) == '#';
                else if (dir.equals("DOWN")) on = ARROW[6 - r].charAt(col) == '#';
                else if (dir.equals("LEFT")) on = ARROW[col].charAt(r) == '#';
                else if (dir.equals("RIGHT")) on = ARROW[6 - col].charAt(r) == '#';
                else on = (r == 0 || r == 6) ? (col > 1 && col < 5) : ((r == 1 || r == 5) ? (col == 1 || col == 5) : (col == 0 || col == 6));
                if (on) g.fillRect(x + col * scale, y + r * scale, scale, scale);
            }
        }
    }

    /** A small right-pointing triangle, used as the menu cursor. */
    private void cursor(Graphics2D g, int x, int y, int scale, Color c) {
        g.setColor(c);
        for (int i = 0; i < 4; i++) g.fillRect(x + i * scale, y + i * scale, scale, (7 - 2 * i) * scale);
    }

    private void lamp(Graphics2D g, int x, int y, boolean on, Color c, String label) {
        g.setColor(on ? c : alpha(c, 40));
        g.fillRect(x, y, 14, 14);
        if (on) { g.setColor(alpha(c, 60)); g.fillRect(x - 3, y - 3, 20, 20); }
        PixelFont.draw(g, label, x + 22, y, 2, on ? WHITE : DIM);
    }

    // =====================================================================
    // Backgrounds
    // =====================================================================
    private void stars(Graphics2D g, long now) {
        for (int i = 0; i < starX.length; i++) {
            int x = (int) ((starX[i] + now * starSpeed[i] / 1000) % W);
            int a = 60 + (int) (60 * Math.sin((now + i * 500) / 400.0));
            g.setColor(alpha(i % 7 == 0 ? MAGENTA : LAV, a));
            int size = starSpeed[i] > 16 ? 3 : 2;
            g.fillRect(x, starY[i], size, size);
        }
    }

    /** A synthwave grid rolling towards the player. */
    private void grid(Graphics2D g, long now, int horizon) {
        g.setPaint(new GradientPaint(0, horizon - 120, alpha(MAGENTA, 0), 0, horizon, alpha(MAGENTA, 50)));
        g.fillRect(0, horizon - 120, W, 120);
        g.setColor(alpha(MAGENTA, 160));
        g.fillRect(0, horizon, W, 2);
        double phase = (now % 1200) / 1200.0;
        for (int i = 0; i < 14; i++) {
            double d = (i + phase) / 14.0;
            int y = horizon + (int) (Math.pow(d, 2.2) * (H - horizon));
            g.setColor(alpha(MAGENTA, (int) (40 + 120 * d)));
            g.fillRect(0, y, W, 2);
        }
        int vx = W / 2;
        for (int i = -16; i <= 16; i++) {
            int bx = vx + i * 110;
            g.setColor(alpha(MAGENTA, 90));
            g.drawLine(vx + i * 8, horizon, bx, H);
        }
    }

    // =====================================================================
    // Title screen
    // =====================================================================
    private void drawTitle(Graphics2D g, long now) {
        g.setColor(BG);
        g.fillRect(0, 0, W, H);
        stars(g, now);
        grid(g, now, 560);

        ghost(g, 10, 120, 110, 7, CYAN, now, 1, 220);           // the Plague Doctor
        ghost(g, 24, W - 210, 130, 7, MAGENTA, now, 2, 220);   // the Hollow King
        for (int i = 0; i < 4; i++) {
            int x = (int) ((now / 12 + i * 340) % (W + 120)) - 60;
            ghost(g, (i * 7 + 18) % 32, x, 500 + (i % 2) * 14, 3, LAV, now, i + 5, 110);
        }

        chroma(g, "SÉANCE", W / 2, 96, 16, WHITE);
        PixelFont.glowCentred(g, "A GHOST HUNTING GAME", W / 2, 236, 3, CYAN);

        if (blink(now, 450)) {
            String start = game.usesKeyboardTaps() ? "PRESS ENTER TO START" : "TAP THE GRIP TO START";
            PixelFont.glowCentred(g, start, W / 2, 300, 4, AMBER);
        }

        drawHighScores(g, now, 360);
        drawFooter(g, now);
    }

    private void drawHighScores(Graphics2D g, long now, int y) {
        Leaderboard b = game.getBoard();
        int pw = 620, px = (W - pw) / 2;
        panel(g, px, y, pw, 260, MAGENTA, "HIGH SCORES");
        PixelFont.draw(g, "RANK", px + 40, y + 30, 2, DIM);
        PixelFont.draw(g, "SCORE", px + 170, y + 30, 2, DIM);
        PixelFont.draw(g, "NIGHT", px + 360, y + 30, 2, DIM);
        PixelFont.draw(g, "NAME", px + 480, y + 30, 2, DIM);
        if (b.size() == 0) {
            if (blink(now, 600)) PixelFont.centred(g, "NO SCORES YET. BE THE FIRST!", W / 2, y + 130, 3, LAV);
            return;
        }
        String[] ranks = { "1ST", "2ND", "3RD", "4TH", "5TH" };
        Color[] colours = { AMBER, WHITE, CYAN, GREEN, MAGENTA };
        for (int i = 0; i < b.size(); i++) {
            int ry = y + 66 + i * 38;
            Color c = colours[i];
            String name = b.nameAt(i);
            if (name.length() > 8) name = name.substring(0, 8);
            PixelFont.draw(g, ranks[i], px + 40, ry, 3, c);
            PixelFont.draw(g, pad(b.scoreAt(i), 7), px + 170, ry, 3, c);
            PixelFont.draw(g, String.valueOf(b.nightAt(i)), px + 390, ry, 3, c);
            PixelFont.draw(g, name, px + 480, ry, 3, c);
        }
    }

    private void drawFooter(Graphics2D g, long now) {
        boolean rigOk = latest != null && latest.isRigConnected();
        boolean wandOk = latest != null && latest.isWandConnected();
        int y = H - 26;
        lamp(g, 24, y, rigOk, rigOk ? GREEN : RED, "RIG");
        lamp(g, 110, y, wandOk, wandOk ? GREEN : RED, "WAND");
        if (game.getScreen() == Game.PLAYING) {
            String hint = "P: PAUSE   I: INSTRUMENTS";
            PixelFont.draw(g, hint, W - 24 - PixelFont.width(hint, 2), y, 2, DIM);
            return;
        }
        if (game.isSimulator()) {
            PixelFont.draw(g, "SIM: ARROWS DIAL  ENTER TAP  SPACE GRIP  M MAGNET  B DARK  U/J/K/N POSE  S SCREAM  T TALK  I INSTRUMENTS  ` DEBUG",
                    210, y + 3, 1, AMBER);
        }
        PixelFont.draw(g, "COE421L  GROUP 3", W - 24 - PixelFont.width("COE421L  GROUP 3", 2), y, 2, DIM);
    }

    // =====================================================================
    // End screen and high score initials
    // =====================================================================
    private void drawEnded(Graphics2D g, long now) {
        Run run = game.getRun();
        g.setColor(BG);
        g.fillRect(0, 0, W, H);
        stars(g, now);

        long uiAt = 0;
        if (game.reachedDawn()) {
            drawDawn(g, run, now, now - screenSince);
            uiAt = DAWN_UI_MS;
            if (now - screenSince < uiAt) { drawFooter(g, now); return; }
            // a dark glass panel fades in behind the score and the table, so they read over the sunrise
            int a = (int) Math.min(185, (now - screenSince - uiAt) / 3);
            g.setColor(new Color(10, 4, 20, a));
            g.fillRect(W / 2 - 400, 238, 800, H - 278);
            g.setColor(alpha(MAGENTA, a / 2));
            g.drawRect(W / 2 - 400, 238, 799, H - 279);
        } else {
            grid(g, now, 560);
            Color c = blink(now, 120) || now - screenSince > 900 ? RED : alpha(RED, 120);
            chroma(g, "GAME OVER", W / 2, 80, 12, c);
            PixelFont.glowCentred(g, "YOUR MIND IS LOST ON NIGHT " + run.getNightNo(), W / 2, 200, 3, WHITE);
        }

        PixelFont.centred(g, "FINAL SCORE", W / 2, 250, 2, CYAN);
        PixelFont.glowCentred(g, pad(countUp(run.getScore(), screenSince + uiAt, now, uiAt > 0 ? 2500 : 1500), 7), W / 2, 276, 6, WHITE);

        if (game.getScreen() == Game.ENTER_NAME) {
            drawInitials(g, now);
        } else {
            drawHighScores(g, now, 360);
            if (now - screenSince > uiAt + 3000 && blink(now, 450)) {
                PixelFont.glowCentred(g, game.usesKeyboardTaps() ? "PRESS ENTER TO CONTINUE" : "TAP THE GRIP TO CONTINUE", W / 2, 660, 3, AMBER);
            }
        }
        drawFooter(g, now);
    }

    private void drawInitials(Graphics2D g, long now) {
        Color rainbow = Color.getHSBColor((now % 1500) / 1500f, 0.75f, 1f);
        PixelFont.glowCentred(g, "NEW HIGH SCORE!", W / 2, 360, 5, rainbow);
        PixelFont.centred(g, "TURN THE DIAL TO PICK A LETTER, TAP TO LOCK IT IN", W / 2, 416, 2, LAV);

        char[] ini = game.getInitials();
        int slot = game.getSlot();
        int box = 96, gap = 30;
        int x0 = W / 2 - (3 * box + 2 * gap) / 2;
        for (int i = 0; i < 3; i++) {
            int bx = x0 + i * (box + gap);
            boolean current = i == slot;
            g.setColor(current ? alpha(MAGENTA, 60) : alpha(DARK, 200));
            g.fillRect(bx, 450, box, 110);
            g.setColor(current ? MAGENTA : DIM);
            g.fillRect(bx, 556, box, 4);
            String ch = i < slot ? String.valueOf(ini[i]) : (current ? String.valueOf(game.getLetter()) : "_");
            PixelFont.glowCentred(g, ch, bx + box / 2, 470, 10, current ? MAGENTA : WHITE);
            if (current && blink(now, 400)) { g.setColor(WHITE); g.fillRect(bx, 556, box, 4); }
        }
        // the letter wheel: the alphabet sits around a dial, A next to Z, and turns with the knob
        double pos = game.getLetterPos();
        int cx = W / 2, R = 300, cy = 620 + R;
        g.setColor(alpha(DARK, 230));
        g.fillOval(cx - R - 44, cy - R - 44, 2 * (R + 44), 2 * (R + 44));
        g.setColor(BG);
        g.fillOval(cx - R + 40, cy - R + 40, 2 * (R - 40), 2 * (R - 40));
        g.setColor(alpha(MAGENTA, 140));
        g.setStroke(new BasicStroke(3));
        g.drawOval(cx - R - 44, cy - R - 44, 2 * (R + 44), 2 * (R + 44));
        g.drawOval(cx - R + 40, cy - R + 40, 2 * (R - 40), 2 * (R - 40));
        g.setStroke(new BasicStroke(1));
        for (int i = 0; i < 26; i++) {
            double off = i - pos;
            off -= 26 * Math.floor((off + 13) / 26);             // the shortest way round the wheel
            double a = off * 2 * Math.PI / 26;
            int lx = cx + (int) Math.round(R * Math.sin(a));
            int ly = cy - (int) Math.round(R * Math.cos(a));
            if (ly > H + 30) continue;
            // a notch between letters
            double na = (off + 0.5) * 2 * Math.PI / 26;
            g.setColor(alpha(LAV, 70));
            g.fillRect(cx + (int) Math.round((R + 30) * Math.sin(na)) - 2, cy - (int) Math.round((R + 30) * Math.cos(na)) - 2, 4, 4);
            boolean on = (char) ('A' + i) == game.getLetter();
            double d = Math.abs(off);
            int scale = d < 0.5 ? 6 : (d < 1.5 ? 4 : 3);
            Color c = on ? WHITE : alpha(LAV, (int) Math.max(60, 230 - d * 35));
            String ch = String.valueOf((char) ('A' + i));
            if (on) PixelFont.glow(g, ch, lx - PixelFont.width(ch, scale) / 2, ly - scale * 7 / 2, scale, c);
            else PixelFont.draw(g, ch, lx - PixelFont.width(ch, scale) / 2, ly - scale * 7 / 2, scale, c);
        }
        // the pointer at the top of the wheel
        int[] px = { cx - 16, cx + 16, cx };
        int[] py = { 572, 572, 590 };
        g.setColor(MAGENTA);
        g.fillPolygon(px, py, 3);
    }

    // =====================================================================
    // During a night: one screen per moment, so only what matters right now is shown
    //   choosing a move -> the board (evidence, suspects, moves)
    //   running a test  -> the test on its own
    //   naming          -> the suspect lineup
    //   banishing       -> the banishing on its own
    // =====================================================================
    private void drawPlaying(Graphics2D g, long now) {
        Night night = game.getNight();
        g.setColor(BG);
        g.fillRect(0, 0, W, H);
        stars(g, now);
        drawHud(g, now);

        int state = night.getState();
        if (state == Night.TEST && night.getCurrent() != null) {
            drawTestScreen(g, night.getCurrent(), now);
        } else if (state == Night.NAMING) {
            drawNamingScreen(g, night, now);
            drawMessage(g, night.getMessage(), now);
        } else if (state == Night.BANISH && night.getBanishing() != null) {
            drawBanishScreen(g, night.getBanishing(), now);
        } else {
            drawBoard(g, night, now);
            drawMessage(g, night.getMessage(), now);
        }
        drawFooter(g, now);

        if (showInstruments) drawInstruments(g, now);
        if (state == Night.INTRO) drawIntro(g, night, now);
        if (state == Night.OVER) drawNightOver(g, night, now);
        if (night.getHunt() != null) drawHunt(g, night.getHunt(), now);
    }

    /** The I key shows or hides the live sensor readings. */
    public void toggleInstruments() { showInstruments = !showInstruments; }

    private void drawHud(Graphics2D g, long now) {
        Run run = game.getRun();
        Night night = game.getNight();
        PixelFont.draw(g, "SCORE", 24, 14, 2, CYAN);
        PixelFont.glow(g, pad(run.getScore(), 7), 24, 36, 4, WHITE);
        long hi = Math.max(game.getHighScore(), run.getScore());
        PixelFont.draw(g, "HI-SCORE", 250, 14, 2, MAGENTA);
        PixelFont.draw(g, pad(hi, 7), 250, 36, 4, LAV);
        PixelFont.draw(g, "NIGHT", 520, 14, 2, AMBER);
        PixelFont.glow(g, night.getNightNo() + "/" + Config.NIGHTS_TO_DAWN, 520, 36, 4, AMBER);

        // the night timer: paused (and dimmed) during hunts, red and blinking near the end
        long left = night.getTimeLeftMs();
        long secs = (left + 999) / 1000;
        String clock = (secs / 60) + ":" + pad(secs % 60, 2);
        boolean paused = night.isHuntOn();
        Color tc = paused ? CYAN : (secs <= 20 ? (blink(now, 250) ? RED : alpha(RED, 110)) : WHITE);
        PixelFont.draw(g, paused ? "PAUSED" : "TIME", 650, 14, 2, paused ? CYAN : AMBER);
        if (paused) PixelFont.draw(g, clock, 650, 36, 4, alpha(tc, 150));
        else PixelFont.glow(g, clock, 650, 36, 4, tc);

        int sanity = run.getSanity();
        Color sc = sanityColour(sanity);
        if (sanity < 25 && !blink(now, 250)) sc = alpha(sc, 110);
        PixelFont.draw(g, "SANITY", 800, 14, 2, sc);
        String sv = String.valueOf(sanity);
        PixelFont.glow(g, sv, W - 24 - PixelFont.width(sv, 3), 10, 3, sc);
        segments(g, 800, 38, W - 824, 26, 25, sanity / 100.0, sc);

        g.setColor(alpha(MAGENTA, 50));
        g.fillRect(0, 76, W, 6);
        g.setColor(MAGENTA);
        g.fillRect(0, 78, W, 2);
    }

    // ---------- the board: choosing a move ----------
    private void drawBoard(Graphics2D g, Night night, long now) {
        drawEvidenceStrip(g, night, 100);
        drawSuspectGrid(g, night, now, 172, -1);

        int y0 = 506;
        PixelFont.draw(g, "CHOOSE YOUR MOVE", 20, y0, 2, MAGENTA);
        if (night.autoStrike()) {                         // nights 1 and 2: how close the deduction is
            int left = night.suspectsLeft();
            if (left == 1) {
                if (blink(now, 400)) PixelFont.draw(g, "ONE SUSPECT LEFT: NAME IT!", 260, y0, 2, GREEN);
            } else {
                PixelFont.draw(g, left + " SUSPECTS LEFT", 260, y0, 2, LAV);
            }
        }
        String help = "DIAL: MOVE   TAP: SELECT";
        PixelFont.draw(g, help, W - 20 - PixelFont.width(help, 2), y0, 2, DIM);

        int[] options = night.getMenuOptions();
        int gap = 14;
        int tw = (W - 40 - gap * (options.length - 1)) / options.length;
        for (int i = 0; i < options.length; i++) {
            int tx = 20 + i * (tw + gap), ty = y0 + 26;
            boolean sel = i == night.getMenuIndex() && night.getState() == Night.MENU;
            boolean naming = options[i] == Night.NAME_OPTION;
            String label = naming ? "NAME THE GHOST" : night.testName(options[i]);
            String side = naming ? "SOLVE IT" : (options[i] == 4 ? "HEAR A RIDDLE" : "TESTS " + Traits.NAMES[night.testTrait(options[i])]);
            Color c = naming ? AMBER : MAGENTA;
            if (sel) {
                g.setColor(alpha(c, 60));
                g.fillRect(tx - 4, ty - 4, tw + 8, 104);
                g.setColor(c);
                g.fillRect(tx, ty, tw, 96);
                if (blink(now, 250)) cursor(g, tx + 12, ty + 22, 3, BG);
                tileText(g, label, side, tx, ty, tw, BG, BG);
            } else {
                g.setColor(PANEL);
                g.fillRect(tx, ty, tw, 96);
                g.setColor(alpha(c, 80));
                g.drawRect(tx, ty, tw - 1, 95);
                tileText(g, label, side, tx, ty, tw, WHITE, DIM);
            }
        }

        StringBuilder off = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (!night.testWorks(i)) off.append(off.length() > 0 ? ", " : "").append(night.testName(i));
        }
        String offline = "OFFLINE: " + off;
        PixelFont.draw(g, offline, W - 20 - PixelFont.width(offline, 2), y0 + 142, 2, DIM);
        PixelFont.draw(g, "OMENS:", 20, y0 + 142, 2, RED);
        drawOmens(g, night, 104, y0 + 142, W - 20 - PixelFont.width(offline, 2) - 24 - 104);
    }

    /**
     * Each omen's name with what it does, for example "HEAVY AIR: tests cost 2 more sanity".
     * They fill up to two lines; if they do not fit, they are drawn smaller.
     */
    private void drawOmens(Graphics2D g, Night night, int x, int y, int width) {
        java.util.List<String[]> list = new java.util.ArrayList<String[]>();
        for (int o = 0; o < Night.OMEN_NAMES.length; o++) {
            if (night.omenActive(o)) list.add(new String[] { Night.OMEN_NAMES[o] + ": ", Night.OMEN_TEXT[o] });
        }
        for (int scale = 2; scale >= 1; scale--) {
            int lineH = scale == 2 ? 20 : 12;
            int maxLines = scale == 2 ? 2 : 3;
            int gap = PixelFont.width("   ", scale);
            // lay the omens out left to right, starting a new line when one does not fit
            int lines = 1, cx = 0;
            for (String[] om : list) {
                int w = PixelFont.width(om[0] + om[1], scale);
                if (cx > 0 && cx + gap + w > width) { lines++; cx = 0; }
                cx += (cx > 0 ? gap : 0) + w;
            }
            if (lines > maxLines && scale == 2) continue;
            int line = 0;
            cx = 0;
            for (String[] om : list) {
                int w = PixelFont.width(om[0] + om[1], scale);
                if (cx > 0 && cx + gap + w > width) { line++; cx = 0; }
                if (cx > 0) cx += gap;
                int ty = y + line * lineH;
                PixelFont.draw(g, om[0], x + cx, ty, scale, AMBER);
                PixelFont.draw(g, om[1], x + cx + PixelFont.width(om[0], scale), ty, scale, LAV);
                cx += w;
            }
            return;
        }
    }

    /** A move tile's name (on two lines if it is too wide) and the small line under it. */
    private void tileText(Graphics2D g, String label, String side, int tx, int ty, int tw, Color main, Color sub) {
        String[] lines = PixelFont.wrap(label, tw - 28, 3);
        if (lines.length == 1) {
            PixelFont.centred(g, label, tx + tw / 2, ty + 24, 3, main);
            PixelFont.centred(g, side, tx + tw / 2, ty + 62, 2, sub);
        } else {
            PixelFont.centred(g, lines[0], tx + tw / 2, ty + 10, 3, main);
            PixelFont.centred(g, lines[1], tx + tw / 2, ty + 36, 3, main);
            PixelFont.centred(g, side, tx + tw / 2, ty + 70, 2, sub);
        }
    }

    /** The five traits across the top: ??? until a test or Contact reveals them. */
    private void drawEvidenceStrip(Graphics2D g, Night night, int y) {
        int gap = 10, cw = (W - 40 - gap * 4) / 5;
        for (int t = 0; t < Traits.COUNT; t++) {
            int x = 20 + t * (cw + gap);
            int k = night.knownValue(t);
            Color c = k == -1 ? DIM : GREEN;
            g.setColor(PANEL);
            g.fillRect(x, y, cw, 54);
            g.setColor(alpha(c, k == -1 ? 60 : 160));
            g.drawRect(x, y, cw - 1, 53);
            if (k != -1) g.fillRect(x, y, 4, 54);
            PixelFont.draw(g, Traits.NAMES[t], x + 14, y + 10, 2, DIM);
            String value = k == -1 ? "???" : Traits.label(t, k == 1);
            PixelFont.draw(g, value, x + 14, y + 32, 2, k == -1 ? alpha(DIM, 140) : c);
        }
    }

    /** The eight suspects. Traits stay dim until evidence lights them green (fits) or red (does not). */
    private void drawSuspectGrid(Graphics2D g, Night night, long now, int y0, int selected) {
        Ghost[] s = night.getSuspects();
        int gap = 10, cw = (W - 40 - gap * 3) / 4, ch = 150;
        for (int i = 0; i < s.length; i++) {
            int x = 20 + (i % 4) * (cw + gap);
            int y = y0 + (i / 4) * (ch + gap);
            boolean out = night.autoStrike() && night.isEliminated(s[i]);
            boolean sel = i == selected;
            Color tint = GHOST_TINTS[s[i].getId() % GHOST_TINTS.length];

            if (sel) {
                g.setColor(alpha(MAGENTA, 60));
                g.fillRect(x - 5, y - 5, cw + 10, ch + 10);
            }
            g.setColor(sel ? new Color(48, 14, 60) : PANEL);
            g.fillRect(x, y, cw, ch);
            g.setColor(sel ? (blink(now, 200) ? MAGENTA : WHITE) : alpha(out ? DIM : tint, 60));
            g.drawRect(x, y, cw - 1, ch - 1);
            if (sel) g.drawRect(x + 1, y + 1, cw - 3, ch - 3);

            ghost(g, s[i].getId(), x + 18, y + 40, 4, out ? DIM : tint, now, i, out ? 80 : 255);
            String name = s[i].getName().toUpperCase().replace("THE ", "");
            if (name.length() > 17) name = name.substring(0, 17);
            PixelFont.draw(g, name, x + 94, y + 16, 2, out ? DIM : WHITE);
            for (int t = 0; t < Traits.COUNT; t++) {
                boolean v = s[i].has(t);
                int k = night.knownValue(t);
                Color c = alpha(LAV, 120);
                if (k != -1) {
                    boolean fits = v == (k == 1);
                    c = fits ? GREEN : RED;                                           // proven by a test
                }
                if (out) c = alpha(DIM, 110);
                PixelFont.draw(g, Traits.shortLabel(t, v), x + 94, y + 46 + t * 20, 2, c);
            }
            if (game.isDebug() && s[i] == night.getHidden()) {          // debug: mark the hidden ghost
                g.setColor(AMBER);
                g.fillRect(x + cw - 74, y + ch - 26, 66, 18);
                PixelFont.draw(g, "GHOST", x + cw - 70, y + ch - 22, 2, BG);
            }
            if (out) {
                g.setColor(alpha(RED, 170));
                for (int p = 0; p < 34; p++) {
                    int px = x + 14 + p * (cw - 34) / 34;
                    g.fillRect(px, y + 12 + p * (ch - 30) / 34, 6, 6);
                    g.fillRect(px, y + ch - 24 - p * (ch - 30) / 34, 6, 6);
                }
            }
        }
    }

    // ---------- a test on its own ----------
    private void drawTestScreen(Graphics2D g, Test t, long now) {
        int cx = W / 2;
        chroma(g, t.getName(), cx, 112, 6, CYAN);
        String what = t instanceof Contact ? "CALL THE SPIRIT FOR A RIDDLE" : "TESTS THE GHOST'S " + Traits.NAMES[t.getTrait()];
        PixelFont.centred(g, what, cx, 172, 2, LAV);

        long secs = (t.timeLeftMs() + 999) / 1000;
        Color tc = secs <= 5 ? (blink(now, 200) ? RED : alpha(RED, 90)) : AMBER;
        String ts = pad(secs, 2);
        PixelFont.draw(g, "TIME", W - 40 - PixelFont.width("TIME", 2), 104, 2, DIM);
        PixelFont.glow(g, ts, W - 40 - PixelFont.width(ts, 7), 126, 7, tc);

        String[] lines = PixelFont.wrap(t.getInstructions(), 960, 3);
        int ty = 222;
        for (String line : lines) { PixelFont.centred(g, line, cx, ty, 3, WHITE); ty += 32; }

        if (t instanceof Contact && ((Contact) t).isSpeaking()) {
            drawRiddle(g, ((Contact) t).getRiddle(), ty + 20, now);
            return;
        }
        if (t instanceof ShadowTest) {
            drawShadowCalls(g, (ShadowTest) t, now, ty);
            return;
        }
        int bw = 900, bx = cx - bw / 2;
        segments(g, bx, ty + 26, bw, 40, 30, t.progress(), GREEN);
        PixelFont.glowCentred(g, t.status(), cx, ty + 90, 3, AMBER);
        drawScope(g, bx, ty + 140, bw, H - 90 - (ty + 140), t.progress(), now);
    }

    /** The spirit's riddle in a panel, with a ghostly shimmer, and how to close it. */
    private void drawRiddle(Graphics2D g, String riddle, int y, long now) {
        int bw = 1040, bx = W / 2 - bw / 2;
        String[] lines = PixelFont.wrap(riddle.toUpperCase(), bw - 80, 3);
        int bh = 60 + lines.length * 40;
        g.setColor(alpha(new Color(160, 0, 255), 40));
        g.fillRect(bx, y, bw, bh);
        g.setColor(alpha(new Color(190, 120, 255), 160));
        g.drawRect(bx, y, bw - 1, bh - 1);
        int ly = y + 30;
        for (int i = 0; i < lines.length; i++) {
            int wob = (int) Math.round(2 * Math.sin(now / 260.0 + i));
            PixelFont.glowCentred(g, lines[i], W / 2 + wob, ly, 3, new Color(220, 190, 255));
            ly += 40;
        }
        if (blink(now, 600)) PixelFont.centred(g, "TAP THE GRIP WHEN YOU HAVE READ IT", W / 2, y + bh + 24, 2, LAV);
    }

    /** Shadow Test: the current call in big letters, then the row of calls with the current one filling. */
    private void drawShadowCalls(Graphics2D g, ShadowTest t, long now, int ty) {
        int cx = W / 2;
        int n = t.getStepCount();
        int cur = Math.min(t.getStep(), n - 1);
        boolean wantDark = t.isDarkStep(cur);
        Color callColour = wantDark ? MAGENTA : AMBER;
        PixelFont.glowCentred(g, wantDark ? "DARK" : "LIGHT", cx, ty + 16, 10, callColour);
        PixelFont.centred(g, wantDark ? "COVER THE SENSOR" : "UNCOVER IT", cx, ty + 100, 3, WHITE);

        int gap = 14, bw = 130, bh = 84;
        int x0 = cx - (n * bw + (n - 1) * gap) / 2, by = ty + 146;
        for (int i = 0; i < n; i++) {
            int bx = x0 + i * (bw + gap);
            boolean done = i < t.getStep(), current = i == t.getStep();
            Color c = done ? GREEN : (current ? (t.isDarkStep(i) ? MAGENTA : AMBER) : DIM);
            g.setColor(t.isDarkStep(i) ? new Color(10, 6, 20) : new Color(46, 36, 20));
            g.fillRect(bx, by, bw, bh);
            if (current) {
                g.setColor(alpha(c, 70));
                g.fillRect(bx, by + bh - (int) (bh * t.stepProgress()), bw, (int) (bh * t.stepProgress()));
            }
            g.setColor(c);
            g.drawRect(bx, by, bw - 1, bh - 1);
            if (current && blink(now, 300)) g.drawRect(bx + 1, by + 1, bw - 3, bh - 3);
            PixelFont.centred(g, done ? "DONE" : (t.isDarkStep(i) ? "DARK" : "LIGHT"), bx + bw / 2, by + 22, 3, c);
            PixelFont.centred(g, (t.getStepLength(i) / 1000.0 + "S").replace(".0S", "S"), bx + bw / 2, by + 56, 2, done ? GREEN : LAV);
        }
        segments(g, cx - 450, by + bh + 34, 900, 22, 30, t.progress(), GREEN);
    }

    /** An oscilloscope: pure noise at the start of a test, a clean wave as it gets close to passing. */
    private void drawScope(Graphics2D g, int x, int y, int w, int h, double clarity, long now) {
        g.setColor(alpha(DARK, 200));
        g.fillRect(x, y, w, h);
        g.setColor(alpha(GREEN, 40));
        for (int gx = x; gx < x + w; gx += 30) g.fillRect(gx, y, 1, h);
        g.fillRect(x, y + h / 2, w, 1);
        Random noise = new Random(now / 60);
        int mid = y + h / 2;
        int prev = mid;
        g.setColor(GREEN);
        for (int i = 0; i < w; i += 3) {
            double wave = Math.sin(i / 14.0 + now / 120.0) * (h / 2.4) * clarity;
            double junk = (noise.nextDouble() - 0.5) * h * 0.8 * (1 - clarity);
            int py = mid + (int) (wave + junk);
            py = Math.max(y + 2, Math.min(y + h - 4, py));
            g.fillRect(x + i, Math.min(prev, py), 3, Math.abs(py - prev) + 3);
            prev = py;
        }
    }

    // ---------- naming: the suspect lineup ----------
    private void drawNamingScreen(Graphics2D g, Night night, long now) {
        drawEvidenceStrip(g, night, 100);
        int count = night.getSuspects().length;
        drawSuspectGrid(g, night, now, 172, night.getMenuIndex() < count ? night.getMenuIndex() : -1);

        int y0 = 504;
        PixelFont.glow(g, "WHO IS IT?", 20, y0 + 6, 6, MAGENTA);
        java.util.List<String> heard = night.getRiddles();
        if (heard.isEmpty() || !night.riddlesKept()) {
            PixelFont.draw(g, "TURN THE DIAL TO PICK A SUSPECT, TAP TO NAME IT.", 20, y0 + 70, 2, WHITE);
            PixelFont.draw(g, "A WRONG NAME COSTS " + Config.WRONG_NAME_COST + " SANITY AND ENDS THE NIGHT.", 20, y0 + 94, 2, DIM);
            if (!heard.isEmpty()) PixelFont.draw(g, "THIN VEIL: THE SPIRIT'S RIDDLES WERE NOT WRITTEN DOWN", 20, y0 + 118, 2, alpha(new Color(190, 120, 255), 200));
        } else {
            // the spirit's riddles, newest first, as many as fit
            PixelFont.draw(g, "THE SPIRIT SAID (NEWEST FIRST):", 20, y0 + 62, 2, new Color(190, 120, 255));
            int ly = y0 + 84, bottom = 686;
            for (int i = heard.size() - 1; i >= 0 && ly + 14 <= bottom; i--) {
                String[] lines = PixelFont.wrap(heard.get(i).toUpperCase(), 720, 2);
                for (String line : lines) {
                    if (ly + 14 > bottom) break;
                    PixelFont.draw(g, line, 20, ly, 2, new Color(220, 190, 255));
                    ly += 18;
                }
                ly += 6;
            }
        }

        int k = night.provenCount();
        int mult = night.currentMultiplier();
        int mx = 760;
        PixelFont.draw(g, "MULTIPLIER", mx, y0, 2, AMBER);
        PixelFont.glow(g, "X" + mult, mx, y0 + 24, 9, AMBER);
        PixelFont.draw(g, k + " PROVEN TRAIT" + (k == 1 ? "" : "S") + " (CONTACT IS FREE)", mx, y0 + 100, 2, LAV);

        boolean back = night.getMenuIndex() == count;
        int bx = W - 220, by = y0 + 20, bw = 200, bh = 70;
        if (back) {
            g.setColor(MAGENTA);
            g.fillRect(bx, by, bw, bh);
            if (blink(now, 250)) cursor(g, bx + 18, by + 25, 3, BG);
            PixelFont.centred(g, "BACK", bx + bw / 2, by + 24, 4, BG);
        } else {
            g.setColor(PANEL);
            g.fillRect(bx, by, bw, bh);
            g.setColor(alpha(MAGENTA, 80));
            g.drawRect(bx, by, bw - 1, bh - 1);
            PixelFont.centred(g, "BACK", bx + bw / 2, by + 24, 4, WHITE);
        }
    }

    // ---------- banishing on its own ----------
    private void drawBanishScreen(Graphics2D g, Banishing b, long now) {
        Ghost ghost = b.getGhost();
        Color tint = GHOST_TINTS[ghost.getId() % GHOST_TINTS.length];
        int jx = b.isStarted() ? shakeRng.nextInt(5) - 2 : 0;
        String[] seq = ghost.getSequence();
        ghost(g, ghost.getId(), 70 + jx, 230, 13, tint, now, 0, 255);
        PixelFont.centred(g, "BANISHING", 148, 104, 3, AMBER);

        int cx = 800;
        chroma(g, ghost.getName(), cx, 112, 5, AMBER);
        if (b.getPhase() == Banishing.SEQUENCE) {
            String dir = seq[Math.min(b.getStep(), seq.length - 1)];
            String hint;
            if (!b.isStarted()) hint = "TAP THE GRIP, THEN POINT THE WAND " + dir + ". THE CLOCK STARTS WITH YOUR TAP.";
            else if (!b.isArmed()) hint = "TAP THE GRIP FOR THE NEXT ARROW.";
            else hint = "NOW FLICK THE WAND " + dir + ".";
            PixelFont.centred(g, hint, cx, 172, 2, WHITE);

            int tile = 200, gap = 36;
            int tx0 = cx - (seq.length * tile + (seq.length - 1) * gap) / 2;
            for (int i = 0; i < seq.length; i++) {
                int tx = tx0 + i * (tile + gap), ty = 220;
                boolean done = i < b.getStep(), current = i == b.getStep();
                Color c = done ? GREEN : (current ? MAGENTA : DIM);
                if (current) {
                    g.setColor(alpha(MAGENTA, blink(now, 300) ? 60 : 25));
                    g.fillRect(tx - 6, ty - 6, tile + 12, 182);
                }
                g.setColor(PANEL);
                g.fillRect(tx, ty, tile, 170);
                g.setColor(c);
                g.drawRect(tx, ty, tile - 1, 169);
                g.drawRect(tx + 1, ty + 1, tile - 3, 167);
                arrow(g, seq[i], tx + (tile - 98) / 2, ty + 22, 14, c);
                String label = done ? "DONE" : (current ? (b.isArmed() ? "FLICK " + seq[i] : "TAP FIRST") : seq[i]);
                PixelFont.centred(g, label, tx + tile / 2, ty + 140, 2, c);
            }
            int bw = seq.length * tile + (seq.length - 1) * gap;
            segments(g, tx0, 420, bw, 26, 12, b.poseProgress(), MAGENTA);
            String tl = String.format("%.1fS", b.timeLeftMs() / 1000.0);
            PixelFont.glow(g, tl, tx0, 474, 6, b.isStarted() ? AMBER : DIM);
            String temper = (ghost.isRestless() ? "RESTLESS: " : "PATIENT: ") + (b.getLimitMs() / 1000) + " SECONDS";
            PixelFont.draw(g, temper, tx0 + bw - PixelFont.width(temper, 2), 490, 2, LAV);
        } else if (b.getPhase() == Banishing.FINISH) {
            if (ghost.isLoud()) {
                int sx = shakeRng.nextInt(11) - 5, sy = shakeRng.nextInt(11) - 5;
                PixelFont.glowCentred(g, "SCREAM!", cx + sx, 250 + sy, 15, AMBER);
                PixelFont.centred(g, "DRIVE IT OUT!", cx, 400, 4, WHITE);
            } else {
                Color c = alpha(CYAN, 120 + (int) (120 * Math.sin(now / 300.0)));
                PixelFont.glowCentred(g, "SILENCE", cx, 250, 12, c);
                PixelFont.centred(g, "NOT A SOUND FOR " + (Banishing.SILENCE_MS / 1000) + " SECONDS", cx, 380, 3, WHITE);
            }
        }
        if (!b.getNote().isEmpty()) PixelFont.glowCentred(g, b.getNote(), cx, 600, 3, RED);
    }

    /** One line for the latest message; it scrolls like a marquee when it is too long. */
    private void drawMessage(Graphics2D g, String msg, long now) {
        int x = 20, y = 692, w = W - 40, h = 30;
        g.setColor(alpha(AMBER, 60));
        g.fillRect(x, y + h - 2, w, 2);
        if (blink(now, 400)) cursor(g, x, y + 8, 2, AMBER);
        int tx = x + 24, tw = w - 24;
        String text = msg.toUpperCase();
        int width = PixelFont.width(text, 2);
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(tx, y, tw, h);
        if (width <= tw) {
            PixelFont.draw(c, text, tx, y + 8, 2, AMBER);
        } else {
            int loop = width + 120;
            int off = (int) ((now / 12) % loop);
            PixelFont.draw(c, text, tx - off, y + 8, 2, AMBER);
            PixelFont.draw(c, text, tx - off + loop, y + 8, 2, AMBER);
        }
        c.dispose();
    }

    /** Live readings from the hardware (press I): handy for the demo and for tuning. */
    private void drawInstruments(Graphics2D g, long now) {
        int w = 760, h = 200, x = (W - w) / 2, y = 470;
        g.setColor(new Color(5, 2, 10, 200));
        g.fillRect(x - 10, y - 20, w + 20, h + 30);
        panel(g, x, y, w, h, CYAN, "INSTRUMENTS (I)");
        if (latest == null) return;
        int lx = x + 20, bx = x + 120, bw = 300;
        PixelFont.draw(g, "DIAL", lx, y + 30, 2, LAV);
        segments(g, bx, y + 28, bw, 16, 20, latest.getDial() / 1023.0, CYAN);
        PixelFont.draw(g, "SOUND", lx, y + 72, 2, LAV);
        double snd = Math.min(1.0, latest.getSoundPeak() / 800.0);
        Color sndC = latest.getSoundPeak() >= Config.SCREAM_LEVEL ? AMBER : (latest.getSoundPeak() < Config.SILENT_LEVEL ? GREEN : CYAN);
        segments(g, bx, y + 70, bw, 16, 20, snd, sndC);
        g.setColor(WHITE);
        g.fillRect(bx + (int) (bw * Config.SILENT_LEVEL / 800.0), y + 66, 2, 24);     // silence
        g.fillRect(bx + (int) (bw * Config.SCREAM_LEVEL / 800.0), y + 66, 2, 24);     // loud finish
        g.setColor(RED);
        g.fillRect(bx + (int) (bw * Config.HUNT_NOISE_LEVEL / 800.0), y + 66, 2, 24); // gives you away in a hunt
        PixelFont.draw(g, "LIGHT", lx, y + 114, 2, LAV);
        segments(g, bx, y + 112, bw, 16, 20, latest.getLight() / 1023.0, AMBER);
        PixelFont.draw(g, "VALUES  DIAL " + latest.getDial() + "  SOUND " + latest.getSoundPeak() + "  LIGHT " + latest.getLight(),
                lx, y + 160, 2, DIM);

        int px = x + 460;
        PixelFont.draw(g, "POSE", px, y + 30, 2, LAV);
        String pose = latest.getPose();
        g.setColor(alpha(DARK, 220));
        g.fillRect(px, y + 52, 90, 90);
        arrow(g, pose, px + 17, y + 69, 8, pose.equals("DOWN") ? GREEN : MAGENTA);

        int lx2 = x + 590;
        lamp(g, lx2, y + 30, latest.isTouch(), GREEN, "GRIP");
        lamp(g, lx2, y + 64, latest.isHall(), MAGENTA, "MAGNET");
        lamp(g, lx2, y + 98, latest.isStill(), CYAN, "STILL");
        lamp(g, lx2, y + 132, latest.isHidden(), GREEN, "HIDDEN");
    }

    // ---------- pause (P) and debug mode (`) ----------
    private void drawPaused(Graphics2D g, long now) {
        g.setColor(new Color(5, 2, 10, 200));
        g.fillRect(0, 0, W, H);
        chroma(g, "PAUSED", W / 2, 290, 12, CYAN);
        PixelFont.centred(g, "PRESS P TO CONTINUE", W / 2, 420, 3, blink(now, 500) ? WHITE : alpha(WHITE, 120));
        PixelFont.centred(g, "THE TIMERS, MUSIC AND VIBRATION ARE STOPPED.  ESC QUITS.", W / 2, 470, 2, LAV);
    }

    /** The debug serial monitor (C): the latest line from each board and the last commands sent. */
    private void drawMonitor(Graphics2D g) {
        SensorHub hub = game.getHardware().hub;
        java.util.List<String> sent = hub.getSentLog();
        int x = 14, w = 600, y = 96, h = 70 + 18 * Math.max(1, sent.size()) + 40;
        g.setColor(new Color(5, 2, 10, 235));
        g.fillRect(x, y, w, h);
        panel(g, x, y, w, h, CYAN, "SERIAL (C)");
        PixelFont.draw(g, "FROM RIG:  " + cut(hub.getLastRigText(), 36), x + 14, y + 22, 2, GREEN);
        PixelFont.draw(g, "FROM WAND: " + cut(hub.getLastWandText(), 36), x + 14, y + 42, 2, GREEN);
        PixelFont.draw(g, "SENT (NEWEST LAST, IDLE INCLUDED):", x + 14, y + 68, 2, DIM);
        int ly = y + 90;
        for (String line : sent) {
            PixelFont.draw(g, cut(line, 46), x + 14, ly, 2, line.startsWith("RIG") ? AMBER : MAGENTA);
            ly += 18;
        }
    }
    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    private static final String[] STATE_NAMES = { "INTRO", "BOARD", "TEST", "NAMING", "BANISH", "NIGHT OVER" };
    private static final String[] KEY_HELP = {
        "1-5 START TEST   6 HUNT / END HUNT",
        "7 PASS / NEXT    8 FAIL TEST",
        "9 +30S TIMER     0 TIMER TO 10S",
        "- = SANITY -10 / +10",
        "[ NAMING         ] BANISH",
        ", WIN NIGHT      . DAWN   / LOSE",
        "; NEXT OMEN      ' RANDOM OMENS",
        "E ESCAPE  Y NEW NIGHT  D PRINT STATE",
        "G GOD  H HUNTS  F FREEZE TIMER",
        "Z/X SPEED  C SERIAL  R RIG  W WAND",
        "ENTER TAP  P PAUSE  ` HIDE PANEL",
    };

    /** The debug panel: everything the game is hiding, and the debug keys. */
    private void drawDebug(Graphics2D g, long now) {
        long gameNow = game.getHardware().hub.gameTime();
        String note = game.getDebugNote();
        boolean showNote = !note.isEmpty() && gameNow - game.getDebugNoteAt() < 3000;
        if (!game.showDebugPanel()) {
            // keys only: a small tag in the corner and the last action
            int ty = H - 58;                              // just above the footer
            g.setColor(AMBER);
            g.fillRect(W - 92, ty, 80, 20);
            PixelFont.draw(g, "DEBUG", W - 84, ty + 4, 2, BG);
            if (showNote) {
                String n = note.toUpperCase();
                int w = PixelFont.width(n, 2) + 20;
                g.setColor(new Color(5, 2, 10, 230));
                g.fillRect(W - 98 - w, ty, w, 20);
                PixelFont.draw(g, n, W - 88 - w, ty + 4, 2, AMBER);
            }
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<String>();
        java.util.List<Color> colours = new java.util.ArrayList<Color>();
        Night n = game.getNight();
        if (game.getScreen() == Game.TITLE) {
            lines.add("TITLE SCREEN"); colours.add(WHITE);
            lines.add("1-7 START A RUN ON THAT NIGHT"); colours.add(LAV);
            lines.add("ENTER START ON NIGHT 1"); colours.add(LAV);
        } else if (game.getScreen() == Game.PLAYING && n != null) {
            Ghost h = n.getHidden();
            lines.add("GHOST: " + h.getName().toUpperCase()); colours.add(AMBER);
            StringBuilder tr = new StringBuilder();
            for (int t = 0; t < Traits.COUNT; t++) tr.append(t == 0 ? "" : " ").append(Traits.shortLabel(t, h.has(t)).toUpperCase());
            lines.add(tr.toString()); colours.add(AMBER);
            StringBuilder seq = new StringBuilder("BANISH:");
            for (String p : h.getSequence()) seq.append(' ').append(p);
            seq.append(h.isLoud() ? ", SCREAM" : ", SILENCE");
            lines.add(seq.toString()); colours.add(LAV);
            lines.add("STATE: " + STATE_NAMES[n.getState()] + "   NIGHT " + n.getNightNo()); colours.add(WHITE);
            long left = n.getTimeLeftMs() / 1000;
            String hunt;
            Hunt hu = n.getHunt();
            if (hu != null) hunt = hu.getPhase() == Hunt.WARNING ? "HUNT WARNING " + hu.secondsLeft() + "S" : "HUNT ACTIVE " + hu.secondsLeft() + "S";
            else hunt = "NEXT HUNT " + Math.max(0, n.debugNextHuntIn(gameNow) / 1000) + "S";
            lines.add("TIMER " + (left / 60) + ":" + pad(left % 60, 2) + (n.isTimerRunning() ? "" : " (STOPPED)") + "   " + hunt); colours.add(WHITE);
            lines.add("SANITY " + game.getRun().getSanity() + "   MULTIPLIER X" + n.currentMultiplier()); colours.add(WHITE);
            StringBuilder works = new StringBuilder("WORKING: ");
            String[] shortTests = { "SWEEP", "SCAN", "SHADOW", "STABLE" };
            for (int i = 0; i < 4; i++) if (n.testWorks(i)) works.append(shortTests[i]).append(' ');
            lines.add(works.toString().trim()); colours.add(CYAN);
            StringBuilder om = new StringBuilder("OMENS: ");
            int count = 0;
            for (int o = 0; o < Night.OMEN_NAMES.length; o++) if (n.omenActive(o)) { om.append(count++ > 0 ? ", " : "").append(Night.OMEN_NAMES[o].toUpperCase()); }
            if (count == 0) om.append("NONE");
            lines.add(om.toString()); colours.add(new Color(190, 120, 255));
            StringBuilder pr = new StringBuilder("PROVEN: ");
            StringBuilder rd = new StringBuilder("RIDDLED: ");
            for (int t = 0; t < Traits.COUNT; t++) {
                if (n.isProven(t)) pr.append(Traits.shortLabel(t, n.knownValue(t) == 1).toUpperCase()).append(' ');
                if (n.isRiddled(t)) rd.append(Traits.shortLabel(t, h.has(t)).toUpperCase()).append(' ');
            }
            lines.add(pr.toString().trim()); colours.add(GREEN);
            lines.add(rd.toString().trim()); colours.add(new Color(190, 120, 255));
            Test t = n.getCurrent();
            if (t != null) {
                String extra = "";
                if (t instanceof Contact && ((Contact) t).isSpeaking()) extra = " ABOUT " + Traits.NAMES[((Contact) t).getTrait()].toUpperCase();
                lines.add("TEST: " + t.getName().toUpperCase() + " " + (t.timeLeftMs() / 1000) + "S LEFT" + extra); colours.add(CYAN);
            }
            Banishing b = n.getBanishing();
            if (n.getState() == Night.BANISH && b != null) {
                String ph = b.getPhase() == Banishing.SEQUENCE ? "ARROW " + (b.getStep() + 1) + " OF " + b.getGhost().getSequence().length
                        : (b.getPhase() == Banishing.FINISH ? "FINISH" : "DONE");
                lines.add("BANISHING: " + ph); colours.add(CYAN);
            }
        } else {
            lines.add("END SCREEN"); colours.add(WHITE);
            if (game.reachedDawn()) { lines.add(". PLAY THE DAWN AGAIN"); colours.add(LAV); }
        }
        // the tools that are switched on
        StringBuilder on = new StringBuilder();
        if (game.isGod()) on.append("GOD  ");
        if (Night.huntsOff) on.append("NO HUNTS  ");
        if (Night.timerFrozen) on.append("TIMER FROZEN  ");
        if (game.getHardware().hub.getSpeed() != 1) on.append("SPEED X").append(game.speedText());
        if (on.length() > 0) { lines.add("ON: " + on.toString().trim()); colours.add(RED); }
        lines.add(""); colours.add(WHITE);
        if (game.getScreen() == Game.PLAYING) for (String k : KEY_HELP) { lines.add(k); colours.add(LAV); }
        else {
            lines.add("G GOD  H HUNTS  F FREEZE TIMER"); colours.add(LAV);
            lines.add("Z/X SPEED  C SERIAL  R RIG  W WAND"); colours.add(LAV);
            lines.add("` HIDE PANEL"); colours.add(LAV);
        }

        int pw = 480, x = W - pw - 14, y = 96;
        int ph = 30 + lines.size() * 18 + (showNote ? 30 : 6);
        g.setColor(new Color(5, 2, 10, 230));
        g.fillRect(x, y, pw, ph);
        panel(g, x, y, pw, ph, AMBER, "DEBUG");
        int ly = y + 22;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            while (PixelFont.width(line, 2) > pw - 28 && line.length() > 4) line = line.substring(0, line.length() - 1);
            PixelFont.draw(g, line, x + 14, ly, 2, colours.get(i));
            ly += 18;
        }
        if (showNote) {
            g.setColor(alpha(AMBER, 60));
            g.fillRect(x + 8, ly + 2, pw - 16, 22);
            String nl = "> " + note.toUpperCase();
            while (PixelFont.width(nl, 2) > pw - 28 && nl.length() > 4) nl = nl.substring(0, nl.length() - 1);
            PixelFont.draw(g, nl, x + 14, ly + 6, 2, AMBER);
        }
    }

    // ---------- full-screen moments ----------
    private void drawIntro(Graphics2D g, Night night, long now) {
        g.setColor(new Color(5, 2, 10, 235));
        g.fillRect(0, 0, W, H);
        stars(g, now);
        chroma(g, "NIGHT " + night.getNightNo(), W / 2, 120, 12, MAGENTA);
        if (blink(now, 350)) PixelFont.glowCentred(g, "GET READY!", W / 2, 240, 5, AMBER);
        PixelFont.centred(g, "HOLD THE WAND LEVEL", W / 2, 292, 2, WHITE);

        StringBuilder tools = new StringBuilder();
        for (int i = 0; i < 5; i++) if (night.testWorks(i)) tools.append(tools.length() > 0 ? "  +  " : "").append(night.testName(i));
        PixelFont.centred(g, "WORKING TONIGHT", W / 2, 320, 2, CYAN);
        PixelFont.glowCentred(g, tools.toString(), W / 2, 344, 3, GREEN);

        PixelFont.centred(g, "OMENS", W / 2, 410, 2, RED);
        int y = 436;
        for (int o = 0; o < Night.OMEN_NAMES.length; o++) {
            if (!night.omenActive(o)) continue;
            PixelFont.glowCentred(g, Night.OMEN_NAMES[o], W / 2, y, 3, AMBER);
            PixelFont.centred(g, Night.OMEN_TEXT[o], W / 2, y + 30, 2, LAV);
            y += 66;
        }
    }

    private void drawNightOver(Graphics2D g, Night night, long now) {
        long t = now - stateSince;
        if (night.isWon()) drawBanished(g, night, now, t);
        else if (night.isEscaped()) drawEscaped(g, night, now, t);
        else drawWrongName(g, night, now, t);
    }

    // ---------- dawn: the run is won ----------
    private static final long DAWN_UI_MS = 4300;           // the score and table appear after the show
    private static final long CLOCK_TURNS = 700, SUN_FROM = 1200, TITLE_AT = 3000;

    /** Replays the current screen's animation from the start (debug). */
    public void replayScreen() { seenScreen = -1; }

    /**
     * Dawn, the end of the run: the clock strikes 6:00, the sky turns from night to sunrise,
     * a striped sun climbs over the grid behind turning rays, every ghost banished on the way
     * rises from the horizon and bursts into light, fireworks go off, confetti falls and DAWN
     * slams down. The score and the high-score table follow.
     */
    private void drawDawn(Graphics2D g, Run run, long now, long t) {
        int horizon = 560;
        double sky = Math.min(1.0, Math.max(0, (t - SUN_FROM) / 2600.0));

        // the sky warms from night to sunrise; the stars fade
        g.setPaint(new GradientPaint(0, 0, blend(new Color(10, 6, 30), new Color(70, 20, 110), sky),
                                     0, horizon, blend(new Color(30, 10, 50), new Color(255, 110, 70), sky)));
        g.fillRect(0, 0, W, horizon);
        if (sky < 1) {
            Graphics2D sg = (Graphics2D) g.create();
            sg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) (1 - sky)));
            stars(sg, now);
            sg.dispose();
        }

        // the sun climbs behind turning rays
        double rise = Math.min(1.0, Math.max(0, (t - SUN_FROM) / 2200.0));
        rise = 1 - Math.pow(1 - rise, 3);
        int radius = 210, cy = (int) (horizon + radius - rise * 360);
        if (t > SUN_FROM) {
            g.setPaint(new RadialGradientPaint(W / 2, cy, 520, new float[] { 0f, 1f },
                    new Color[] { alpha(new Color(255, 200, 120), (int) (170 * rise)), alpha(new Color(255, 90, 120), 0) }));
            g.fillRect(0, 0, W, horizon);
            double turn = now / 2600.0;
            for (int i = 0; i < 20; i++) {
                double a0 = turn + i * Math.PI / 10, a1 = a0 + Math.PI / 24;
                int[] xs = { W / 2, W / 2 + (int) (1300 * Math.cos(a0)), W / 2 + (int) (1300 * Math.cos(a1)) };
                int[] ys = { cy, cy + (int) (1300 * Math.sin(a0)), cy + (int) (1300 * Math.sin(a1)) };
                g.setColor(alpha(i % 2 == 0 ? GOLD : new Color(255, 120, 170), (int) (rise * 38)));
                g.fillPolygon(xs, ys, 3);
            }
            for (int r = 0; r < 42; r++) {
                int y = cy - radius + r * 10;
                if (y > horizon) break;
                int half = (int) Math.sqrt(Math.max(0, radius * radius - (y + 5 - cy) * (y + 5 - cy)));
                Color c = blend(new Color(255, 230, 90), new Color(255, 70, 140), r / 42.0);
                int gapCut = r < 18 ? 0 : Math.min(8, (r - 17) / 2 + 1);
                g.setColor(c);
                g.fillRect(W / 2 - half, y, 2 * half, Math.max(1, Math.min(10 - gapCut, horizon - y)));
            }
        }

        // the floor and its grid, with the sun's reflection
        g.setPaint(new GradientPaint(0, horizon, new Color(40, 8, 40), 0, H, BG));
        g.fillRect(0, horizon, W, H - horizon);
        if (t > SUN_FROM) {
            for (int i = 0; i < 8; i++) {
                int y = horizon + 6 + i * 22;
                int half = (int) (radius * rise * (1 - i * 0.1));
                g.setColor(alpha(GOLD, (int) (90 * rise * (1 - i / 8.0))));
                g.fillRect(W / 2 - half, y, 2 * half, 4);
            }
        }
        grid(g, now, horizon);

        // the clock strikes six
        if (t < SUN_FROM + 300) {
            int a = t < SUN_FROM ? 255 : (int) (255 * (1 - (t - SUN_FROM) / 300.0));
            String clock = t < CLOCK_TURNS ? "5:59" : "6:00";
            int jx = t >= CLOCK_TURNS && t < CLOCK_TURNS + 250 ? shakeRng.nextInt(11) - 5 : 0;
            Color cc = t < CLOCK_TURNS ? (blink(now, 250) ? RED : alpha(RED, 120)) : AMBER;
            PixelFont.glowCentred(g, clock, W / 2 + jx, 200, 18, alpha(cc, a));
            PixelFont.centred(g, t < CLOCK_TURNS ? "THE LAST NIGHT ENDS..." : "MORNING", W / 2, 360, 3, alpha(WHITE, a));
            if (t >= CLOCK_TURNS && t < CLOCK_TURNS + 200) {
                g.setColor(alpha(WHITE, (int) (200 * (1 - (t - CLOCK_TURNS) / 200.0))));
                g.fillRect(-10, -10, W + 20, H + 20);
            }
        }

        // every ghost banished this run rises from the horizon, free, and bursts into light
        java.util.List<Ghost> freed = new java.util.ArrayList<Ghost>(run.getBanished());
        if (freed.isEmpty()) for (int i = 0; i < Config.NIGHTS_TO_DAWN; i++) freed.add(new Ghost((i * 5 + 3) % 32));   // a debug dawn
        int n = Math.min(freed.size(), 7);
        for (int i = 0; i < n; i++) {
            Ghost gh = freed.get(freed.size() - n + i);
            long v = t - (1800 + i * 260);
            if (v < 0) continue;
            int x0 = W * (i + 1) / (n + 1) - 24;
            Color tint = blend(GHOST_TINTS[gh.getId() % GHOST_TINTS.length], WHITE, 0.45);
            long burstAt = 2400 + (i % 3) * 200;
            if (v < burstAt) {
                int y = (int) (horizon - 40 - v * 0.17);
                int x = x0 + (int) (Math.sin(v / 260.0 + i) * 22);
                GhostSprites.draw(g, gh.getId(), x, y, 4, tint, BG, (int) Math.min(255, v / 2));
            } else {
                double u = (v - burstAt) / 1000.0;
                if (u > 1.6) continue;
                int bx = x0 + (int) (Math.sin(burstAt / 260.0 + i) * 22) + 24, by = (int) (horizon - 40 - burstAt * 0.17) + 28;
                int[] px = pixelsOf(gh.getId(), tint);
                Random rng = new Random(gh.getId() * 31L + i);
                for (int p = 0; p < px.length; p += 3) {
                    double ang = rng.nextDouble() * 2 * Math.PI, sp = 120 + rng.nextDouble() * 380;
                    int x = (int) (bx + px[p] * 4 - 24 + Math.cos(ang) * sp * u);
                    int y = (int) (by + px[p + 1] * 4 - 28 + Math.sin(ang) * sp * u + 160 * u * u);
                    g.setColor(alpha(rng.nextInt(3) == 0 ? WHITE : GOLD, (int) (255 * (1 - u / 1.6))));
                    g.fillRect(x, y, 4, 4);
                }
                if (u < 0.6) {
                    int r = (int) (10 + u * 260);
                    g.setStroke(new BasicStroke(4));
                    g.setColor(alpha(WHITE, (int) (220 * (1 - u / 0.6))));
                    g.drawOval(bx - r, by - r, 2 * r, 2 * r);
                    g.setStroke(new BasicStroke(1));
                }
            }
        }

        // fireworks, one after another, for as long as the screen stays up
        if (t > 2600) {
            long k1 = (t - 2600) / 380;
            for (long k = Math.max(0, k1 - 4); k <= k1; k++) {
                long kt = t - 2600 - k * 380;
                Random fr = new Random(k * 7349 + 11);
                int fx = 140 + fr.nextInt(W - 280), fy = 80 + fr.nextInt(260);
                Color fc = CONFETTI[fr.nextInt(CONFETTI.length)];
                if (kt < 420) {                                   // the rocket climbs
                    double f = kt / 420.0;
                    int ry = (int) (horizon - (horizon - fy) * f);
                    g.setColor(alpha(WHITE, 220));
                    g.fillRect(fx - 2, ry, 4, 8);
                    g.setColor(alpha(fc, 120));
                    g.fillRect(fx - 1, ry + 8, 2, 26);
                } else {                                          // and bursts
                    double u = (kt - 420) / 1000.0;
                    if (u > 1.4) continue;
                    if (u < 0.08) { g.setColor(alpha(WHITE, 200)); g.fillOval(fx - 26, fy - 26, 52, 52); }
                    for (int p = 0; p < 46; p++) {
                        double ang = p * 2 * Math.PI / 46 + fr.nextDouble() * 0.1, sp = 200 + fr.nextDouble() * 90;
                        int x = (int) (fx + Math.cos(ang) * sp * u);
                        int y = (int) (fy + Math.sin(ang) * sp * u + 120 * u * u);
                        if (shakeRng.nextInt(5) == 0) continue;           // twinkle
                        g.setColor(alpha(p % 4 == 0 ? WHITE : fc, (int) (255 * (1 - u / 1.4))));
                        g.fillRect(x, y, 5, 5);
                    }
                }
            }
        }

        // confetti
        if (t > TITLE_AT) {
            double u = (t - TITLE_AT) / 1000.0;
            Random cr = new Random(77);
            for (int i = 0; i < 150; i++) {
                double delay = cr.nextDouble() * 2.5, sp = 140 + cr.nextDouble() * 220, x0 = cr.nextDouble() * W, ph = cr.nextDouble() * 6.28;
                Color c = CONFETTI[cr.nextInt(CONFETTI.length)];
                double v = u - delay;
                if (v <= 0) continue;
                int y = (int) (-20 + (v * sp) % (H + 40));            // keeps falling for as long as the screen stays
                int x = (int) (x0 + Math.sin(v * 4 + ph) * 26);
                int w = 2 + (int) (8 * Math.abs(Math.cos(v * 7 + ph)));
                g.setColor(c);
                g.fillRect(x, y, w, 10);
            }
        }

        // DAWN slams down in rainbow colours, with a flash and a shake
        if (t > TITLE_AT) {
            long s = t - TITLE_AT;
            int sc = (int) Math.max(15, 36 - s / 12);
            int j = s < 600 ? 8 : 0;
            int jx = j > 0 ? shakeRng.nextInt(2 * j + 1) - j : 0, jy = j > 0 ? shakeRng.nextInt(2 * j + 1) - j : 0;
            Color rainbow = Color.getHSBColor((now % 1200) / 1200f, 0.7f, 1f);
            chroma(g, "DAWN", W / 2 + jx, 40 + (15 - sc) * 3 + jy, sc, s < 250 ? WHITE : rainbow);
            if (s > 500) {
                String line = "YOU SURVIVED ALL " + Config.NIGHTS_TO_DAWN + " NIGHTS";
                int shown = (int) Math.min(line.length(), (s - 500) / 35);
                PixelFont.glowCentred(g, line.substring(0, shown), W / 2, 200, 3, WHITE);
            }
            if (s < 220) {
                g.setColor(alpha(WHITE, (int) (240 * (1 - s / 220.0))));
                g.fillRect(-10, -10, W + 20, H + 20);
            }
        }
    }

    // ---------- banished: the ghost bursts apart ----------
    private static final long BURST_AT = 700;              // when the ghost breaks apart
    private static final Color GOLD = new Color(255, 214, 90);
    private static final Color[] CONFETTI = { GREEN, CYAN, MAGENTA, AMBER, WHITE, new Color(160, 120, 255) };

    private void drawBanished(Graphics2D g, Night night, long now, long t) {
        Ghost gone = night.getHidden();
        Color tint = GHOST_TINTS[gone.getId() % GHOST_TINTS.length];
        int scale = 8, gx = W / 2 - 48, gy = 440, cx = W / 2, cy = gy + 56;

        g.setColor(new Color(5, 2, 10, 238));
        g.fillRect(-10, -10, W + 20, H + 20);
        stars(g, now);

        // light rays turning slowly behind everything, from the moment it bursts
        if (t > BURST_AT) {
            double f = Math.min(1.0, (t - BURST_AT) / 600.0);
            double turn = now / 2200.0;
            for (int i = 0; i < 16; i++) {
                double a0 = turn + i * Math.PI / 8, a1 = a0 + Math.PI / 20;
                int[] xs = { cx, cx + (int) (1100 * Math.cos(a0)), cx + (int) (1100 * Math.cos(a1)) };
                int[] ys = { cy, cy + (int) (1100 * Math.sin(a0)), cy + (int) (1100 * Math.sin(a1)) };
                g.setColor(alpha(i % 2 == 0 ? GOLD : GREEN, (int) (f * (i % 2 == 0 ? 34 : 22))));
                g.fillPolygon(xs, ys, 3);
            }
            g.setPaint(new RadialGradientPaint(cx, cy, 260, new float[] { 0f, 1f },
                    new Color[] { alpha(WHITE, (int) (f * 90)), alpha(GREEN, 0) }));
            g.fillRect(cx - 260, cy - 260, 520, 520);
        }

        if (t < BURST_AT) {
            // it shudders, glows and swells as the spell takes hold
            double f = t / (double) BURST_AT;
            int j = (int) (2 + 10 * f);
            int dx = shakeRng.nextInt(2 * j + 1) - j, dy = shakeRng.nextInt(2 * j + 1) - j;
            int sc = scale + (int) (3 * f);
            int ox = cx - 6 * sc + dx, oy = cy - 7 * sc + dy;
            Color hot = blend(tint, WHITE, f);
            GhostSprites.draw(g, gone.getId(), ox - 10, oy, sc, alpha(CYAN, 120), BG, 120);
            GhostSprites.draw(g, gone.getId(), ox + 10, oy, sc, alpha(MAGENTA, 120), BG, 120);
            GhostSprites.draw(g, gone.getId(), ox, oy, sc, hot, BG, 255);
        } else {
            double u = (t - BURST_AT) / 1000.0;
            // shockwaves
            g.setStroke(new BasicStroke(6));
            for (int k = 0; k < 3; k++) {
                double uk = u - k * 0.14;
                if (uk <= 0 || uk > 1.1) continue;
                int r = (int) (40 + uk * 760);
                g.setColor(alpha(k == 0 ? WHITE : (k == 1 ? GREEN : CYAN), (int) (220 * (1 - uk / 1.1))));
                g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
            }
            g.setStroke(new BasicStroke(1));
            // the ghost's own pixels fly apart
            if (u < 1.8) {
                int[] px = pixelsOf(gone.getId(), tint);
                Random rng = new Random(gone.getId() * 7919L);
                double fade = Math.max(0, 1 - u / 1.8);
                int size = Math.max(2, (int) (scale * (1 - u / 2.2)));
                for (int i = 0; i < px.length; i += 3) {
                    int sx = px[i], sy = px[i + 1];
                    double x0 = gx + sx * scale, y0 = gy + sy * scale;
                    double ang = Math.atan2(y0 - cy, x0 - cx) + (rng.nextDouble() - 0.5) * 0.9;
                    double speed = 300 + rng.nextDouble() * 650;
                    double x = x0 + Math.cos(ang) * speed * u;
                    double y = y0 + Math.sin(ang) * speed * u - 180 * u + 420 * u * u;
                    Color c = new Color(px[i + 2], true);
                    if (rng.nextInt(4) == 0) c = WHITE;
                    g.setColor(alpha(c, (int) (255 * fade)));
                    g.fillRect((int) x, (int) y, size, size);
                }
            }
            // its freed soul drifts up and away
            if (u > 0.3 && u < 4.5) {
                double v = u - 0.3;
                int a = (int) Math.max(0, 170 * (1 - v / 4.2));
                int sy = (int) (cy - 40 - v * 110);
                int sx = cx - 18 + (int) (Math.sin(v * 3) * 30);
                GhostSprites.draw(g, gone.getId(), sx, sy, 3, alpha(WHITE, 200), alpha(BG, 200), a);
            }
            // confetti
            Random cr = new Random(421);
            for (int i = 0; i < 110; i++) {
                double delay = cr.nextDouble() * 1.2, sp = 160 + cr.nextDouble() * 260, x0 = cr.nextDouble() * W, ph = cr.nextDouble() * 6.28;
                Color c = CONFETTI[cr.nextInt(CONFETTI.length)];
                double v = u - 0.15 - delay;
                if (v <= 0) continue;
                int y = (int) (-20 + v * sp);
                if (y > H) continue;
                int x = (int) (x0 + Math.sin(v * 4 + ph) * 26);
                int w = 2 + (int) (8 * Math.abs(Math.cos(v * 7 + ph)));
                g.setColor(c);
                g.fillRect(x, y, w, 10);
            }
        }

        // white flash as it bursts
        if (t >= BURST_AT && t < BURST_AT + 260) {
            g.setColor(alpha(WHITE, (int) (230 * (1 - (t - BURST_AT) / 260.0))));
            g.fillRect(-10, -10, W + 20, H + 20);
        }

        // BANISHED slams down, then the score counts up
        if (t > BURST_AT + 150) {
            long s = t - BURST_AT - 150;
            int sc = (int) Math.max(9, 22 - s / 12);
            int jy = s < 400 ? shakeRng.nextInt(7) - 3 : 0;
            chroma(g, "BANISHED!", W / 2 + jy, 80 + (9 - sc) * 3, sc, blink(now, 120) && s < 900 ? WHITE : GREEN);
            PixelFont.centred(g, night.getEndReason().toUpperCase(), W / 2, 170, 2, WHITE);
        }
        long scoreAt = BURST_AT + 700;
        if (t > scoreAt) {
            PixelFont.centred(g, "NIGHT SCORE", W / 2, 220, 2, CYAN);
            PixelFont.glowCentred(g, pad(countUp(night.getNightScore(), stateSince + scoreAt, now, 1500), 7), W / 2, 246, 6, WHITE);
        }
        if (t > scoreAt + 1600) {
            long s = t - scoreAt - 1600;
            int sc = s < 200 ? 5 : 3;                       // the multiplier pops in
            PixelFont.glowCentred(g, "X" + night.getMultiplier() + " MULTIPLIER", W / 2, 312 - (sc - 3) * 4, sc, AMBER);
            if (night.hadCleanBonus() && blink(now, 300)) {
                PixelFont.glowCentred(g, "CLEAN NIGHT!  +" + Config.CLEAN_NIGHT_BONUS + " SANITY", W / 2, 356, 3, GREEN);
            }
        }
    }

    /** The ghost's pixels (x, y, colour) at scale 1, for the burst. Worked out once per ghost. */
    private int[] pixelsOf(int id, Color tint) {
        int[] cached = ghostPixels.get(id);
        if (cached != null) return cached;
        int off = 14;
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = img.createGraphics();
        GhostSprites.draw(ig, id, off, off, 1, tint, BG, 255);
        ig.dispose();
        java.util.List<Integer> list = new java.util.ArrayList<Integer>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                list.add(x - off); list.add(y - off); list.add(argb);
            }
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        ghostPixels.put(id, out);
        return out;
    }

    private static Color blend(Color a, Color b, double f) {
        f = Math.max(0, Math.min(1, f));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * f),
                         (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                         (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }

    // ---------- wrong name: the real ghost lunges out of the dark ----------
    private static final long SCARE_FROM = 250, SCARE_TO = 1350, REVEAL_AT = 1650;

    private void drawWrongName(Graphics2D g, Night night, long now, long t) {
        Ghost real = night.getHidden();
        g.setColor(Color.BLACK);
        g.fillRect(-10, -10, W + 20, H + 20);
        Color bone = new Color(226, 220, 204);
        Color blood = new Color(255, 20, 30);

        if (t < SCARE_FROM) {
            // a moment of silence: something small and far away, flickering
            if (shakeRng.nextInt(3) > 0) GhostSprites.draw(g, real.getId(), W / 2 - 9, H / 2 - 10, 2, alpha(bone, 160), blood, 160);
            return;
        }
        if (t < SCARE_TO) {
            double f = (t - SCARE_FROM) / (double) (SCARE_TO - SCARE_FROM);
            int sc = (int) (24 + 14 * Math.min(1, f * 2.5));             // it rushes at the screen
            int j = 14;
            int ox = W / 2 - 6 * sc + shakeRng.nextInt(2 * j + 1) - j;
            int oy = H / 2 - 6 * sc + shakeRng.nextInt(2 * j + 1) - j;
            g.setColor(new Color(90, 0, 10));
            g.fillRect(-10, -10, W + 20, H + 20);
            GhostSprites.draw(g, real.getId(), ox - 16, oy, sc, alpha(blood, 130), Color.BLACK, 130);
            GhostSprites.draw(g, real.getId(), ox + 16, oy + 6, sc, alpha(CYAN, 90), Color.BLACK, 90);
            GhostSprites.draw(g, real.getId(), ox, oy, sc, bone, blood, 255);
            staticNoise(g, 900, 70);
            glitchBands(g, 6);
            if (t - SCARE_FROM < 90) { g.setColor(alpha(WHITE, 220)); g.fillRect(-10, -10, W + 20, H + 20); }
            return;
        }
        if (t < REVEAL_AT) {
            staticNoise(g, 1400, 120);
            return;
        }

        // the reveal: who it really was
        long s = t - REVEAL_AT;
        g.setPaint(new RadialGradientPaint(W / 2, 470, 330, new float[] { 0f, 1f },
                new Color[] { new Color(120, 0, 16, 200), new Color(0, 0, 0, 0) }));
        g.fillRect(W / 2 - 330, 140, 660, 660);
        int jx = s < 500 ? shakeRng.nextInt(13) - 6 : 0;
        chroma(g, "WRONG!", W / 2 + jx, 60, 12, RED);
        PixelFont.centred(g, "IT WAS", W / 2, 190, 3, LAV);
        PixelFont.glowCentred(g, real.getName().toUpperCase(), W / 2, 226, 5, RED);
        int bob = (int) (Math.sin(now / 260.0) * 8);
        int sc = 12;
        GhostSprites.draw(g, real.getId(), W / 2 - 6 * sc, 330 + bob, sc, bone, blood, 255);
        // the eyes glow
        if (blink(now, 700)) { g.setColor(alpha(blood, 40)); g.fillRect(W / 2 - 6 * sc, 330 + bob + 2 * sc, 12 * sc, 4 * sc); }
        if (night.getNamed() != null) {
            PixelFont.centred(g, "YOU NAMED " + night.getNamed().getName().toUpperCase(), W / 2, 676, 2, LAV);
        }
        PixelFont.glowCentred(g, "-" + night.getLossCost() + " SANITY", W / 2, 712, 4, RED);
    }

    /** Random grey specks, like a television losing its signal. */
    private void staticNoise(Graphics2D g, int count, int alphaMax) {
        for (int i = 0; i < count; i++) {
            int v = 80 + shakeRng.nextInt(176);
            g.setColor(new Color(v, v, v, shakeRng.nextInt(alphaMax + 1)));
            g.fillRect(shakeRng.nextInt(W), shakeRng.nextInt(H), 3 + shakeRng.nextInt(6), 2 + shakeRng.nextInt(3));
        }
    }

    /** Torn horizontal bands, as if the picture is glitching. */
    private void glitchBands(Graphics2D g, int count) {
        for (int i = 0; i < count; i++) {
            int y = shakeRng.nextInt(H), h = 4 + shakeRng.nextInt(26);
            g.setColor(shakeRng.nextBoolean() ? new Color(0, 0, 0, 200) : new Color(255, 30, 60, 90));
            g.fillRect(0, y, W, h);
        }
    }

    // ---------- time ran out: the ghost slips away, laughing ----------
    private void drawEscaped(Graphics2D g, Night night, long now, long t) {
        g.setColor(new Color(5, 2, 10, 230));
        g.fillRect(0, 0, W, H);
        stars(g, now);
        Ghost gone = night.getHidden();
        Color tint = GHOST_TINTS[gone.getId() % GHOST_TINTS.length];
        int x = (int) (W / 2 - 48 + t * 0.32);
        int y = (int) (430 + Math.sin(t / 180.0) * 26);
        int a = (int) Math.max(0, 255 - t / 14);
        GhostSprites.draw(g, gone.getId(), x, y, 8, tint, BG, a);
        for (int i = 0; i < 6; i++) {                       // its laugh pops up above it and floats off
            long born = 300 + i * 420;
            if (t < born) continue;
            long age = t - born;
            int la = (int) Math.max(0, 230 - age / 5);
            int lx = (int) (W / 2 - 48 + born * 0.32) + (i % 2 == 0 ? -10 : 40);
            int ly = (int) (400 - age * 0.06);
            PixelFont.draw(g, i % 3 == 2 ? "HA!" : "HEE", lx, ly, 3, alpha(tint, la));
        }
        int jx = t < 600 ? shakeRng.nextInt(13) - 6 : 0;
        chroma(g, "TIME'S UP!", W / 2 + jx, 120, 10, RED);
        PixelFont.centred(g, night.getEndReason(), W / 2, 260, 2, WHITE);
        PixelFont.glowCentred(g, "-" + night.getLossCost() + " SANITY", W / 2, 310, 4, RED);
    }

    // ---------- hunts: the thing that hunts you ----------
    private static final long POP_MS = 750;        // its face fills the screen for a moment when a hunt begins
    private static final String[] WHISPERS = {
        "I KNOW YOU'RE HERE", "COME OUT", "I CAN HEAR YOU", "WHERE ARE YOU", "FOUND YOU?", "DON'T BREATHE", "CLOSER"
    };

    private void drawHunt(Graphics2D g, Hunt hunt, long now) {
        boolean warning = hunt.getPhase() == Hunt.WARNING;
        long t = now - huntPhaseSince;

        // the room goes dark, with a red heartbeat at the edges
        double beat = Math.pow(Math.max(0, Math.sin(now / (warning ? 110.0 : 140.0))), 6);
        g.setColor(new Color(6, 0, 3, 242));
        g.fillRect(-10, -10, W + 20, H + 20);
        g.setPaint(new RadialGradientPaint(W / 2, H / 2, 760, new float[] { 0.4f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(150, 0, 20, (int) (110 + 120 * beat)) }));
        g.fillRect(-10, -10, W + 20, H + 20);

        if (warning && t < POP_MS) {
            // the pop-up: its face right in front of you
            double f = t / (double) POP_MS;
            int sc = (int) (20 + 8 * f);
            int j = 10;
            int x = (int) (W / 2 - GhostSprites.HUNTER_FACE_X * sc) + shakeRng.nextInt(2 * j + 1) - j;
            int y = (int) (H / 2 - GhostSprites.HUNTER_FACE_Y * sc) + shakeRng.nextInt(2 * j + 1) - j;
            GhostSprites.drawHunter(g, x, y, sc, 255, 1.0);
            staticNoise(g, 500, 60);
            glitchBands(g, 3);
            if (t < 90) { g.setColor(alpha(WHITE, 200)); g.fillRect(-10, -10, W + 20, H + 20); }
            return;
        }

        boolean hidden = !warning && hunt.isSafe();
        boolean lunge = now < lungeUntil;
        if (hidden) {
            drawUnderBed(g, now, t);
        } else {
            int sc, top, al;
            double hx, glow;
            if (warning) {
                // it sinks back into the dark, then rises slowly while you get ready
                double f = Math.min(1.0, (t - POP_MS) / 2600.0);
                f = 1 - (1 - f) * (1 - f);                   // eases in, slowing as it arrives
                sc = 10;
                hx = W / 2 + Math.sin(t / 700.0) * 30;
                top = (int) (H + 40 - f * (H + 40 - 230));
                al = (int) (90 + 140 * f);
                glow = 0.4 + 0.3 * Math.sin(now / 200.0);
            } else {
                // out in the open: it comes straight for you
                sc = lunge ? 16 : 12;
                hx = W / 2 + Math.sin(t / 600.0) * 120;
                if (lunge) hx = W / 2;
                top = lunge ? 90 : 170 + (int) (Math.sin(t / 300.0) * 12);
                al = 245;
                glow = 0.8 + 0.2 * Math.sin(now / 80.0);
            }
            int left = (int) (hx - GhostSprites.HUNTER_W * sc / 2.0);
            if (lunge) { left += shakeRng.nextInt(17) - 8; top += shakeRng.nextInt(17) - 8; }
            GhostSprites.drawHunter(g, left, top, sc, al, glow);
            if (!warning) {
                g.setColor(new Color(255, 0, 30, (int) (40 + 60 * beat)));   // nowhere to hide: the whole room is red
                g.fillRect(-10, -10, W + 20, H + 20);
                clawMarks(g, now);
            }
        }
        whispers(g, now, warning ? 2 : (hidden ? 2 : 4));
        if (lunge) staticNoise(g, 400, 80);

        hazard(g, 100, now);
        hazard(g, 644, now);
        if (warning) {
            if (blink(now, 250)) chroma(g, "WARNING", W / 2, 150, 10, AMBER);
            PixelFont.glowCentred(g, "SOMETHING IS COMING", W / 2, 248, 4, WHITE);
            textBand(g, 520, 92);
            PixelFont.centred(g, "HOLSTER THE WAND (TIP DOWN) AND STAY SILENT", W / 2, 530, 3, WHITE);
            PixelFont.glowCentred(g, "HIDE IN " + hunt.secondsLeft(), W / 2, 570, 5, AMBER);
        } else if (hidden) {
            chroma(g, "HUNT", W / 2, 150, 10, RED);
            PixelFont.glowCentred(g, String.valueOf(hunt.secondsLeft()), W / 2, 238, 5, WHITE);
            PixelFont.glowCentred(g, "HIDDEN", W / 2, 296, 5, GREEN);
            PixelFont.centred(g, "DON'T MOVE. DON'T MAKE A SOUND.", W / 2, 352, 2, LAV);
        } else {
            chroma(g, "HUNT", W / 2, 150, 10, RED);
            PixelFont.glowCentred(g, String.valueOf(hunt.secondsLeft()), W / 2, 244, 5, WHITE);
            textBand(g, 540, 90);
            if (blink(now, 150)) PixelFont.glowCentred(g, "IT SEES YOU!", W / 2, 548, 5, RED);
            PixelFont.centred(g, "HOLSTER THE WAND (TIP DOWN) AND GO SILENT", W / 2, 600, 2, WHITE);
        }

        // the lights fail now and then; more often when it can see you
        int chance = warning ? 14 : (hidden ? 18 : 6);
        if (shakeRng.nextInt(chance) == 0) {
            g.setColor(new Color(0, 0, 0, 200 + shakeRng.nextInt(56)));
            g.fillRect(-10, -10, W + 20, H + 20);
        }
    }

    /** A dark strip behind text, so it stays readable in front of the spirit. */
    private void textBand(Graphics2D g, int y, int h) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, y - 14, W, h);
    }

    /** Words scratched into the dark, flickering in and out at random places. */
    private void whispers(Graphics2D g, long now, int count) {
        Random r = new Random(now / 650);                  // the same places for a moment, then they move
        for (int i = 0; i < count; i++) {
            String w = WHISPERS[r.nextInt(WHISPERS.length)];
            int sc = 2;
            int tw = PixelFont.width(w, sc);
            // only at the sides, never over the words in the middle
            int x = r.nextBoolean() ? 30 + r.nextInt(Math.max(1, W / 2 - 250 - tw - 30)) : W / 2 + 250 + r.nextInt(Math.max(1, W / 2 - 250 - tw - 30));
            int y = 140 + r.nextInt(480);
            if (shakeRng.nextInt(3) == 0) continue;            // flicker
            int jx = shakeRng.nextInt(5) - 2, jy = shakeRng.nextInt(5) - 2;
            PixelFont.draw(g, w, x + jx, y + jy, sc, new Color(220, 30, 40, 90 + r.nextInt(80)));
        }
    }

    /** Three claw marks torn across the screen, as if it swiped at the glass. */
    private void clawMarks(Graphics2D g, long now) {
        Random r = new Random(seenHunt == null ? 1 : seenHunt.hashCode());
        int x0 = 200 + r.nextInt(500), y0 = 160 + r.nextInt(120);
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < 46; i++) {
                int w = (int) (10 * Math.sin(Math.PI * i / 46.0)) + 2;
                int x = x0 + k * 46 + i * 9, y = y0 + i * 8 + k * 14;
                g.setColor(new Color(120, 0, 10, 150));
                g.fillRect(x, y, w + 4, 8);
                g.setColor(new Color(255, 40, 50, 170));
                g.fillRect(x + 2, y + 2, w, 4);
            }
        }
    }

    /**
     * Hiding: the view from under a bed. Above is the underside of the bed; through the gap
     * between the bed rail and the floor its robe drags past. Halfway through it stops,
     * bends down and looks under the bed, then moves on: it cannot see a player who stays hidden.
     */
    private void drawUnderBed(Graphics2D g, long now, long t) {
        int y0 = 136, bedY = 430, floorY = 644;
        // the gap: a dim red room, and the floorboards
        g.setPaint(new GradientPaint(0, bedY, new Color(110, 14, 26), 0, floorY, new Color(26, 2, 8)));   // the room, lit red behind it
        g.fillRect(0, bedY, W, floorY - bedY);
        g.setColor(new Color(30, 12, 10));
        for (int y = 596; y < floorY; y += 16) g.fillRect(0, y, W, 2);
        for (int i = 0; i < 9; i++) { int x = (i * 173 + (i % 2) * 60) % W; g.fillRect(x, 596 + (i % 3) * 16, 2, 16); }

        // it passes by, and halfway it bends down to look under the bed
        long peekFrom = 2300, peekTo = 3900;
        double walkT = t < peekFrom ? t : (t < peekTo ? peekFrom : t - (peekTo - peekFrom));
        int sc = 13;
        int cx = (int) (W + 260 - walkT * 0.28);
        java.awt.Shape oldClip = g.getClip();
        g.clipRect(0, bedY, W, floorY - bedY);
        GhostSprites.drawHunter(g, cx - GhostSprites.HUNTER_W * sc / 2, floorY + 8 - GhostSprites.HUNTER_H * sc, sc, 255, 0);
        if (t >= peekFrom && t < peekTo) {
            long p = t - peekFrom;
            double down = p < 350 ? p / 350.0 : (p > peekTo - peekFrom - 350 ? (peekTo - peekFrom - p) / 350.0 : 1.0);
            int fs = 9;
            int faceY = (int) (bedY - 200 + down * 300);                     // the face sinks into the gap
            int fx = cx - 60 + (int) (Math.sin(p / 260.0) * 50);             // and turns, searching
            GhostSprites.drawHunter(g, (int) (fx - GhostSprites.HUNTER_FACE_X * fs), (int) (faceY - GhostSprites.HUNTER_FACE_Y * fs), fs, 255, 0.9);
        }
        g.setClip(oldClip);

        // the underside of the bed, the rail and the legs
        g.setColor(new Color(9, 4, 6));
        g.fillRect(0, y0, W, bedY - y0);
        g.setColor(new Color(34, 24, 30));
        for (int row = 0; row < 4; row++) {                               // springs
            int sy = y0 + 40 + row * 60;
            for (int x = 30 + (row % 2) * 30; x < W; x += 60) {
                for (int k = 0; k < 5; k++) g.fillRect(x + (k % 2) * 8, sy + k * 6, 10, 2);
            }
        }
        g.setColor(new Color(40, 22, 14));
        g.fillRect(0, bedY - 26, W, 26);
        g.setColor(new Color(78, 44, 26));
        g.fillRect(0, bedY - 4, W, 3);
        g.setColor(new Color(28, 15, 10));
        g.fillRect(70, bedY, 30, floorY - bedY);
        g.fillRect(W - 100, bedY, 30, floorY - bedY);
        // the dark closes in around the edges
        g.setPaint(new RadialGradientPaint(W / 2, 520, 700, new float[] { 0.5f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 220) }));
        g.fillRect(0, y0, W, floorY - y0);
    }

    /** A strip of moving black and amber warning stripes. */
    private void hazard(Graphics2D g, int y, long now) {
        int h = 36;
        g.setColor(AMBER);
        g.fillRect(0, y, W, h);
        g.setColor(BG);
        int off = (int) ((now / 15) % 60);
        for (int x = -60 + off; x < W + 60; x += 60) {
            int[] xs = { x, x + 30, x + 30 - h, x - h };
            int[] ys = { y, y, y + h, y + h };
            g.fillPolygon(xs, ys, 4);
        }
    }

    // ---------- CRT overlays, made once ----------
    private static BufferedImage makeScanlines() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int dark = new Color(0, 0, 0, 70).getRGB();
        for (int y = 0; y < H; y += 3) {
            for (int x = 0; x < W; x++) img.setRGB(x, y, dark);
        }
        return img;
    }

    private static BufferedImage makeVignette() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float[] stops = { 0f, 0.65f, 1f };
        Color[] colours = { new Color(0, 0, 0, 0), new Color(0, 0, 0, 30), new Color(0, 0, 0, 190) };
        g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, W * 0.72f, stops, colours));
        g.fillRect(0, 0, W, H);
        g.dispose();
        return img;
    }
}
