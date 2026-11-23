package seance;

/** Commands for the rig (Arduino Uno): servo, RGB LED and buzzer. */
public class Rig {
    private final Link link;
    private int lastR = -1, lastG = -1, lastB = -1;
    private int lastClickGap = -1;

    public Rig(Link link) { this.link = link; }

    public void moveToMark(int mark) {
        mark = Math.max(0, Math.min(Config.MARKS - 1, mark));
        link.send("SERVO," + (mark * Config.MARK_STEP));
    }

    public void setSpeed(int msPerDegree) { link.send("SPEED," + msPerDegree); }

    /** Sets the LED, only sending when the colour actually changes. */
    public void led(int r, int g, int b) {
        if (r == lastR && g == lastG && b == lastB) return;
        lastR = r; lastG = g; lastB = b;
        link.send("LED," + r + "," + g + "," + b);
    }

    /** Repeating clicks with this gap in ms; 0 stops them. Only sends on change. */
    public void clicks(int gapMs) {
        if (gapMs == lastClickGap) return;
        lastClickGap = gapMs;
        link.send("CLICKS," + gapMs);
    }

    public void alarm(int count) { lastClickGap = 0; link.send("ALARM," + count); }
    public void beep(int ms) { lastClickGap = 0; link.send("BEEP," + ms); }
    public void silence() { lastClickGap = 0; link.send("STOP"); }

    /** Shows the sanity band: green above 60, yellow from 25 to 60, red below 25. */
    public void showSanity(int sanity) {
        if (sanity > 60) led(0, 255, 0);
        else if (sanity >= 25) led(255, 120, 0);
        else led(255, 0, 0);
    }
}
