package seance;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A 5 x 7 pixel font, the kind used on old arcade machines, drawn with filled squares so it
 * needs no font files. Text is always shown in capitals. Each character is 6 pixels wide
 * (5 plus a gap) and 7 tall, multiplied by the scale.
 */
public final class PixelFont {
    private PixelFont() { }

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:!?'\"-+/()><%=_*;[]`#";
    private static final String[][] GLYPHS = {
        {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}, // A
        {"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."}, // B
        {".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."}, // C
        {"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."}, // D
        {"#####", "#....", "#....", "####.", "#....", "#....", "#####"}, // E
        {"#####", "#....", "#....", "####.", "#....", "#....", "#...."}, // F
        {".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"}, // G
        {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}, // H
        {".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."}, // I
        {"..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."}, // J
        {"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"}, // K
        {"#....", "#....", "#....", "#....", "#....", "#....", "#####"}, // L
        {"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"}, // M
        {"#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"}, // N
        {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}, // O
        {"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}, // P
        {".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"}, // Q
        {"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}, // R
        {".####", "#....", "#....", ".###.", "....#", "....#", "####."}, // S
        {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}, // T
        {"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}, // U
        {"#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."}, // V
        {"#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."}, // W
        {"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"}, // X
        {"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}, // Y
        {"#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"}, // Z
        {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."}, // 0
        {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."}, // 1
        {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"}, // 2
        {"#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."}, // 3
        {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."}, // 4
        {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."}, // 5
        {"..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."}, // 6
        {"#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."}, // 7
        {".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."}, // 8
        {".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."}, // 9
        {".....", ".....", ".....", ".....", ".....", ".....", "....."}, // space
        {".....", ".....", ".....", ".....", ".....", ".##..", ".##.."}, // .
        {".....", ".....", ".....", ".....", ".##..", "..#..", ".#..."}, // ,
        {".....", ".##..", ".##..", ".....", ".##..", ".##..", "....."}, // :
        {"..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."}, // !
        {".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."}, // ?
        {"..#..", "..#..", ".#...", ".....", ".....", ".....", "....."}, // '
        {".#.#.", ".#.#.", ".#.#.", ".....", ".....", ".....", "....."}, // "
        {".....", ".....", ".....", ".###.", ".....", ".....", "....."}, // -
        {".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."}, // +
        {".....", "....#", "...#.", "..#..", ".#...", "#....", "....."}, // /
        {"...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#."}, // (
        {".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#..."}, // )
        {".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#..."}, // >
        {"...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#."}, // <
        {"##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"}, // %
        {".....", ".....", "#####", ".....", "#####", ".....", "....."}, // =
        {".....", ".....", ".....", ".....", ".....", ".....", "#####"}, // _
        {".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", "....."}, // *
        {".....", ".##..", ".##..", ".....", ".##..", "..#..", ".#..."}, // ;
        {".###.", ".#...", ".#...", ".#...", ".#...", ".#...", ".###."}, // [
        {".###.", "...#.", "...#.", "...#.", "...#.", "...#.", ".###."}, // ]
        {".#...", "..#..", ".....", ".....", ".....", ".....", "....."}, // `
        {".#.#.", "#####", ".#.#.", ".#.#.", "#####", ".#.#.", "....."}, // #
    };

    /** For each character, the filled runs of each row as {x, y, length}, worked out once. */
    private static final int[][][] RUNS = new int[GLYPHS.length][][];

    static {
        for (int c = 0; c < GLYPHS.length; c++) {
            int[][] tmp = new int[35][];
            int n = 0;
            for (int y = 0; y < 7; y++) {
                String row = GLYPHS[c][y];
                int x = 0;
                while (x < 5) {
                    if (row.charAt(x) == '#') {
                        int start = x;
                        while (x < 5 && row.charAt(x) == '#') x++;
                        tmp[n++] = new int[] { start, y, x - start };
                    } else {
                        x++;
                    }
                }
            }
            RUNS[c] = new int[n][];
            for (int i = 0; i < n; i++) RUNS[c][i] = tmp[i];
        }
    }

    /** Width in screen pixels of a line of text at this scale. */
    public static int width(String s, int scale) {
        if (s.isEmpty()) return 0;
        return s.length() * 6 * scale - scale;
    }

    public static int height(int scale) { return 7 * scale; }

    /** Draws text with its top-left corner at (x, y) in the current colour. */
    public static void draw(Graphics2D g, String s, int x, int y, int scale) {
        String text = s.toUpperCase();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean accent = false;
            if (ch == 'É') { ch = 'E'; accent = true; }
            int index = CHARS.indexOf(ch);
            int cx = x + i * 6 * scale;
            if (index >= 0) {
                for (int[] run : RUNS[index]) {
                    g.fillRect(cx + run[0] * scale, y + run[1] * scale, run[2] * scale, scale);
                }
            }
            if (accent) {
                g.fillRect(cx + 3 * scale, y - 2 * scale, scale, scale);
                g.fillRect(cx + 2 * scale, y - 1 * scale, scale, scale);
            }
        }
    }

    /** Draws text in a colour. */
    public static void draw(Graphics2D g, String s, int x, int y, int scale, Color c) {
        g.setColor(c);
        draw(g, s, x, y, scale);
    }

    /** Draws text centred on cx. */
    public static void centred(Graphics2D g, String s, int cx, int y, int scale, Color c) {
        draw(g, s, cx - width(s, scale) / 2, y, scale, c);
    }

    /** Neon text: a soft blocky glow in the same colour behind bright text. */
    public static void glow(Graphics2D g, String s, int x, int y, int scale, Color c) {
        int step = Math.max(1, scale / 2);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
        draw(g, s, x - step, y, scale);
        draw(g, s, x + step, y, scale);
        draw(g, s, x, y - step, scale);
        draw(g, s, x, y + step, scale);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 25));
        draw(g, s, x - 2 * step, y, scale);
        draw(g, s, x + 2 * step, y, scale);
        draw(g, s, x, y - 2 * step, scale);
        draw(g, s, x, y + 2 * step, scale);
        draw(g, s, x, y, scale, c);
    }

    public static void glowCentred(Graphics2D g, String s, int cx, int y, int scale, Color c) {
        glow(g, s, cx - width(s, scale) / 2, y, scale, c);
    }

    /**
     * Splits text into lines that fit a width at this scale.
     * Returns the lines as an array (at most 12).
     */
    public static String[] wrap(String s, int maxWidth, int scale) {
        int maxChars = Math.max(1, (maxWidth + scale) / (6 * scale));
        String[] lines = new String[12];
        int n = 0;
        String line = "";
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (test.length() > maxChars && !line.isEmpty()) {
                if (n < lines.length) lines[n++] = line;
                line = word;
            } else {
                line = test;
            }
        }
        if (!line.isEmpty() && n < lines.length) lines[n++] = line;
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = lines[i];
        return out;
    }
}
