package seance;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Pixel art for the ghosts. Every ghost shares the same sheet body, tinted in its own colour,
 * and wears its own accessory so the 32 ghosts can be told apart (the Lost Sailor's hat and
 * anchor, the Plague Doctor's beak, the Pale Child's balloon, and so on).
 *
 * Maps use one letter per pixel:
 *   '#' body colour   'x' hole (eyes, mouth)   '.' nothing
 *   k hat black  d dark detail  w white  s silver  g grey  y gold  o orange  r red  p pink
 *   b blue  B pale blue  c cyan  G green  n brown  N dark brown  v violet  V dark violet
 */
public final class GhostSprites {
    private GhostSprites() { }

    /** The shared body, 12 x 14. Columns 0-11, rows 0-13. */
    private static final String[] BODY = {
        "....#####...",
        "..#########.",
        ".###########",
        ".##xx###xx##",
        "###xx###xx##",
        "############",
        "#####xx#####",
        "#####xx#####",
        "#####xx#####",
        "############",
        ".###########",
        ".##.#####.##",
        "..#..###...#",
        "......##....",
    };

    /**
     * Each ghost's accessory: one or more parts, each placed at (column, row) relative to the
     * body's top-left corner. Rows above the head are negative.
     */
    private static final int[][] OFFSETS = new int[32][];
    private static final String[][][] PARTS = new String[32][][];

    private static void define(int id, int[] offsets, String[]... parts) {
        OFFSETS[id] = offsets;
        PARTS[id] = parts;
    }

    static {
        // 0 The Widow: wide mourning hat with a black veil
        define(0, new int[] { 0, -3 }, new String[] {
            "....kkk.....",
            "...kkkkk....",
            ".kkkkkkkkkk.",
            "....k.k.k...",
            "..k.k.k.k.k.",
            ".k.k.k.k.k.k",
        });
        // 1 The Drowned Boy: seaweed in his hair, dripping water
        define(1, new int[] { 0, -2, 0, 13 }, new String[] {
            "....G...G...",
            "...GG..GG...",
            "....GGG.G...",
            "...G...G.G..",
        }, new String[] {
            "..........b.",
            "...b........",
            "........b...",
        });
        // 2 The Lamplighter: a glowing lamp on a pole
        define(2, new int[] { 11, -3 }, new String[] {
            ".kk.", "kyyk", "kyyk", ".kk.",
            "..n.", "..n.", "..n.", "..n.", "..n.", "..n.", "..n.", "..n.", "..n.", "..n.",
        });
        // 3 The Hollow Saint: a golden halo
        define(3, new int[] { 0, -4 }, new String[] {
            "....yyyyy...",
            "...y.....y..",
            "....yyyyy...",
        });
        // 4 The Bride: flower crown and a long white veil
        define(4, new int[] { -1, -2 }, new String[] {
            "....p.y.p.y...",
            "...wwwwwwwww..",
            "..ss.......ss.",
            ".ss.........s.",
            "s............s",
            "s............s",
            "s............s",
            "s............s",
            "s............s",
            "s............s",
            "ss..........ss",
            ".s...........s",
        });
        // 5 The Gravedigger: flat cap and a shovel
        define(5, new int[] { 0, -2, 12, 0 }, new String[] {
            "....nnnnn...",
            "...nnnnnnnnN",
        }, new String[] {
            "nnn", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", "sss", "sss", "sss", ".s.",
        });
        // 6 The Weeping Nun: black habit with a white band, tears
        define(6, new int[] { 0, -1 }, new String[] {
            "...kkkkkk...",
            "..kkwwwwwk..",
            ".kk.......kk",
            "kk.........k",
            "k..........k",
            "k..........k",
            "k..c.....c.k",
            "k..c.....c.k",
            "k........c.k",
            "k..........k",
            "k..........k",
            ".k.........k",
        });
        // 7 The Organist: powdered wig and music notes
        define(7, new int[] { 0, -1, 9, -6 }, new String[] {
            "...gggggg...",
            "..ggggggggg.",
            "gg........gg",
            "gg........gg",
            ".g........g.",
        }, new String[] {
            "..ww", "..w.w", "..w.", "ww..", "ww..",
        });
        // 8 The Faceless Maid: no face at all, a frilly cap and an apron
        define(8, new int[] { 0, -2 }, new String[] {
            "....w.w.w...",
            "...wwwwwww..",
            "............",
            "............",
            "............",
            "...##...##..",
            "...##...##..",
            "............",
            ".....##.....",
            ".....##.....",
            ".....##.....",
            "....wwwww...",
            "....wwwww...",
            "....w.w.w...",
        });
        // 9 The Hanged Man: a tall top hat with a red band
        define(9, new int[] { 0, -5 }, new String[] {
            "....kkkk....",
            "....kkkk....",
            "....kkkk....",
            "....rrrr....",
            "..kkkkkkkk..",
        });
        // 10 The Plague Doctor: wide hat, goggles and the long beak mask
        define(10, new int[] { 0, -3, 0, 3 }, new String[] {
            "....kkkk....",
            "....kkkk....",
            ".kkkkkkkkkk.",
        }, new String[] {
            "...yy...yy......",
            "...yd...yd......",
            "......ssss......",
            ".....ssssssssss.",
            ".....sssssss....",
            "......sss.......",
        });
        // 11 The Silent Twin: mouth sewn shut, and a small twin beside it
        define(11, new int[] { 0, 6, -4, 7 }, new String[] {
            ".....##.....",
            "....dddd....",
            ".....##.....",
        }, new String[] {
            ".###.", "#x#x#", "#####", "##d##", "#####", "#.#.#",
        });
        // 12 The Chimney Sweep: cap, soot smudges and a chimney brush
        define(12, new int[] { 0, -2, -3, -4 }, new String[] {
            "...kkkkkk...",
            "..kkkkkkkkkk",
            "............",
            "............",
            "............",
            "............",
            "............",
            "..d.......d.",
            "............",
            "............",
            "............",
            ".d....d.....",
            "......dd....",
        }, new String[] {
            "k.k", ".k.", "k.k", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.",
        });
        // 13 The Moth Queen: moth wings and a crown
        define(13, new int[] { -4, 1, 12, 1, 0, -3 }, new String[] {
            "vv..", "vvv.", "vyvv", "vvvv", ".vvv", "..vv", ".vvv", "vvy.", ".vv.",
        }, new String[] {
            "..vv", ".vvv", "vvyv", "vvvv", "vvv.", "vv..", "vvv.", ".yvv", ".vv.",
        }, new String[] {
            "....y.y.y...",
            "....yyyyy...",
        });
        // 14 The Stitched Doll: odd button eyes, a stitched mouth and a patch
        define(14, new int[] { 0, 3 }, new String[] {
            "...rr...bb..",
            "...rr...bb..",
            "............",
            "....d.d.d...",
            "...dddddd...",
            "....d.d.d...",
            "........pp..",
            "........pp..",
        });
        // 15 The Bell Ringer: a golden bell
        define(15, new int[] { 11, 5 }, new String[] {
            "..n..", ".yyy.", ".yyy.", "yyyyy", "..d..",
        });
        // 16 The Mourner: a black bow and a white lily
        define(16, new int[] { 0, -2, 11, 1 }, new String[] {
            "...kk.kk....",
            "....kkk.....",
        }, new String[] {
            "w.w.", "www.", ".y..", ".G..", ".G..", ".G..", ".G..", "GG..",
        });
        // 17 The Burnt Choir: flames on its head and scorch marks
        define(17, new int[] { 0, -3 }, new String[] {
            "....o...o...",
            "...oyo.oyo..",
            "...oyyoyyo..",
            "............",
            "............",
            "............",
            "............",
            "............",
            ".d........d.",
            "............",
            "............",
            "............",
            "...d....d...",
            "............",
            "......d.....",
        });
        // 18 The Lost Sailor: a sailor's hat and an anchor
        define(18, new int[] { 0, -2, 2, 8 }, new String[] {
            "....wwww....",
            "...wwwwww...",
            "..wbbbbbbw..",
        }, new String[] {
            ".b.", "bbb", ".b.", "bbb",
        });
        // 19 The Night Nurse: a nurse's cap with a red cross
        define(19, new int[] { 0, -3 }, new String[] {
            "....wwwww...",
            "....wwrww...",
            "....wrrrw...",
            "....wwrww...",
        });
        // 20 The Pale Child: a red balloon and a pink bow
        define(20, new int[] { 11, -6, 4, -1 }, new String[] {
            ".rr.", "rrrr", "rrrr", ".rr.", "..s.", "..s.", ".s..", ".s..", "s...",
        }, new String[] {
            "pprpp",
        });
        // 21 The Butcher: paper cap, striped apron and a cleaver
        define(21, new int[] { 0, -2, 0, 9, 11, 4 }, new String[] {
            "...wwwwww...",
            "...wwwwww...",
        }, new String[] {
            "...wbwbwb...",
            "...wbwbwb...",
            "...wbwbwb...",
        }, new String[] {
            "ssss", "ssss", "sss.", ".n..", ".n..",
        });
        // 22 The Seamstress: round spectacles, a needle and red thread
        define(22, new int[] { 0, 2, 11, 5 }, new String[] {
            "..yyyy.yyyy.",
            "..y..yyy..y.",
            "..y..y.y..y.",
            "..yyyy.yyyy.",
        }, new String[] {
            "...s", "..s.", ".s..", "r...", "r...", ".r..", "..r.", "..r.",
        });
        // 23 The Ferryman: a dark hood, glowing eyes and an oar
        define(23, new int[] { 0, -1, -3, -3 }, new String[] {
            "...VVVVVV...",
            "..VVVVVVVV..",
            ".VV.......VV",
            "VV.........V",
            "V..yy...yy.V",
            "V..yy...yy.V",
            "V..........V",
            "V..........V",
            "V..........V",
            "V..........V",
            "V..........V",
            ".V.........V",
        }, new String[] {
            ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.", ".n.",
            "nnn", "nnn", "nnn", ".n.",
        });
        // 24 The Hollow King: a jewelled crown
        define(24, new int[] { 0, -4 }, new String[] {
            "....y.y.y...",
            "....yyyyy...",
            "....yrbry...",
            "....yyyyy...",
        });
        // 25 The Candle Thief: a bandit mask and a stolen candle
        define(25, new int[] { 0, 3, 11, 3 }, new String[] {
            "..dxxdddxxd.",
            "..dxxdddxxd.",
        }, new String[] {
            ".o.", ".y.", "www", "www", "www", "www", "yyy",
        });
        // 26 The Drowned Bride: a pale blue veil tangled with seaweed, dripping water
        define(26, new int[] { -1, -2, 0, 13 }, new String[] {
            "....G...G.....",
            "...BBBBBBBBB..",
            "..BB.......BB.",
            ".BB.........B.",
            "B............B",
            "B............B",
            "B............B",
            "B............B",
            "B............B",
            ".............B",
            "B.............",
        }, new String[] {
            "...b......b.",
            "............",
            ".......b....",
        });
        // 27 The Whistler: a small round mouth, music notes and a breeze
        define(27, new int[] { 0, 6, 12, -1 }, new String[] {
            ".....##.....",
        }, new String[] {
            "..ww", "..w.", ".ww.", ".ww.", "....", "....", "cc..", "..cc", "cc..", "..cc",
        });
        // 28 The Grey Lady: a hair bun and a pearl necklace
        define(28, new int[] { 0, -2, 0, 9 }, new String[] {
            ".....ggg....",
            "....ggggg...",
        }, new String[] {
            "..w.w.w.w.w.",
        });
        // 29 The Mirror Man: a hand mirror
        define(29, new int[] { 11, 3 }, new String[] {
            ".sss.", "scccs", "sccws", "scccs", ".sss.", "..n..", "..n..",
        });
        // 30 The Old Hound: floppy ears, a nose, a tongue and a collar with a tag
        define(30, new int[] { -2, 0, 0, 5 }, new String[] {
            "..nn........nn..",
            ".nnnn......nnnn.",
            "nnnn........nnnn",
            "nnn..........nnn",
            "nnn..........nnn",
            ".nn..........nn.",
            ".n............n.",
        }, new String[] {
            ".....dd.....",
            "............",
            "............",
            "............",
            ".....pp.....",
            ".rrrrrrrrrr.",
            "......y.....",
        });
        // 31 The Clockmaker: a monocle and a pocket watch
        define(31, new int[] { 0, 2, 10, 7 }, new String[] {
            ".......yyyy.",
            ".......y..y.",
            ".......y..y.",
            ".......yyyy.",
        }, new String[] {
            ".yyy.", "ywwwy", "ywdwy", "ywwwy", ".yyy.",
        });
    }

    private static Color colour(char ch, Color tint, Color hole) {
        switch (ch) {
            case '#': return tint;
            case 'x': return hole;
            case 'k': return new Color(62, 52, 88);
            case 'd': return new Color(22, 16, 32);
            case 'w': return new Color(245, 245, 255);
            case 's': return new Color(190, 195, 210);
            case 'g': return new Color(150, 150, 165);
            case 'y': return new Color(255, 200, 40);
            case 'o': return new Color(255, 130, 30);
            case 'r': return new Color(230, 50, 70);
            case 'p': return new Color(255, 150, 200);
            case 'b': return new Color(60, 120, 240);
            case 'B': return new Color(150, 200, 240);
            case 'c': return new Color(34, 230, 255);
            case 'G': return new Color(60, 190, 90);
            case 'n': return new Color(150, 95, 50);
            case 'N': return new Color(95, 60, 30);
            case 'v': return new Color(170, 90, 220);
            case 'V': return new Color(78, 46, 120);
            default: return null;
        }
    }

    private static void map(Graphics2D g, String[] m, int x, int y, int scale, Color tint, Color hole, int alpha) {
        for (int r = 0; r < m.length; r++) {
            for (int c = 0; c < m[r].length(); c++) {
                Color col = colour(m[r].charAt(c), tint, hole);
                if (col == null) continue;
                if (alpha < 255) col = new Color(col.getRed(), col.getGreen(), col.getBlue(), col.getAlpha() * alpha / 255);
                g.setColor(col);
                g.fillRect(x + c * scale, y + r * scale, scale, scale);
            }
        }
    }

    /**
     * Draws ghost number id with its top-left body pixel at (x, y).
     * alpha 255 is fully visible; lower values fade the whole ghost (struck out, floating away).
     */
    public static void draw(Graphics2D g, int id, int x, int y, int scale, Color tint, Color hole, int alpha) {
        map(g, BODY, x, y, scale, tint, hole, alpha);
        int i = ((id % 32) + 32) % 32;
        if (PARTS[i] == null) return;
        for (int p = 0; p < PARTS[i].length; p++) {
            map(g, PARTS[i][p], x + OFFSETS[i][2 * p] * scale, y + OFFSETS[i][2 * p + 1] * scale, scale, tint, hole, alpha);
        }
    }

    // =====================================================================
    // The thing that hunts you: a hooded wraith with a skull face, 40 x 46.
    //   '#' robe   'd' robe edge   'x' black   's' bone   'w' teeth   'c' fingers   'e' eyes
    // =====================================================================
    private static final String[] HUNTER = {
        ".................##dd##.................",
        "...............#ddd##ddd#...............",
        ".............#dd########dd#.............",
        "............#d############d#............",
        "...........#d####xxxxxx####d#...........",
        "...........d###xxxxssxxxx###d...........",
        ".....d....#d##dxxssssssxx###d#....d.....",
        "..d..c....d##dxxssssssssxx###d....c..d..",
        "..c..c..d#d##xxssssssssssxx##d#d..c..c..",
        "..c..c..cd###xsxxsssssssssx###dc..c.c...",
        "d..c.c.c.d###xxeexssssxxssx###d.c.c.c..d",
        ".c.c.c.c.d##xxxxxxsssxeexsxx##d.c.c.c.c.",
        "..cc.cc..d##xxsxxssssxxxxsxx##d..cc.cc..",
        "..c.ccc.#d##xssxssssssxxsssx##d#.cc.cc..",
        "...ccc...d##xssxsssxxssxsssx##d...ccc...",
        "....cc...d##xxsxsssxsssxssxx##d...cc....",
        "....#....d##xxsxswxwxwsssdxx##d....#....",
        "....##...d###xssxxwxwxwsdsx###d...##....",
        ".....#...#d##xssxxxxxxxsssx##d#...#.....",
        ".....##...d##xxsxxxxxxxssxx##d...##.....",
        "......#...#d##xxxxxxxxxsxx##d#..##......",
        "......##...d###xxxxxxxxxx###d...#.......",
        ".......##..#d#sxxxwxwxxxxs#d#..##.......",
        "........#######sxwxwxwxss#######........",
        "........########sxxxxxss########........",
        "........#########ssssss#########........",
        "........########################........",
        "........########################........",
        ".......##########################.......",
        ".......##########################.......",
        ".......##########################.......",
        ".......##########################.......",
        "......############################......",
        "......############################......",
        "......############################......",
        "......############################......",
        ".....##############################.....",
        ".....##############################.....",
        ".....####.#.####.#.#######.########.....",
        ".....####.#.####.#.#######.########.....",
        "....###.#.....##.#.#.#####.#######.#....",
        "....#.#.#......#.#.#.#####.##.#.##.#....",
        "....#.#.#......#.#.#.#####.##.#.##.#....",
        "....#.#.#......#...#.####..#..#.##.#....",
        "...##..........#...#.###...#..#.##.##...",
        "...##..........#.....#.#......#..#.##..."
    };
    public static final int HUNTER_W = 40, HUNTER_H = 46;
    /** Where its face is, in sprite pixels (for centring a close-up on it). */
    public static final double HUNTER_FACE_X = 19.5, HUNTER_FACE_Y = 14;

    /**
     * Draws the hunting spirit with its top-left corner at (x, y). glow (0 to 1) makes the eyes
     * flare; alpha fades the whole thing (it comes out of the dark).
     */
    public static void drawHunter(Graphics2D g, int x, int y, int scale, int alpha, double glow) {
        Color robe = new Color(16, 5, 14), edge = new Color(72, 22, 52), bone = new Color(206, 200, 184);
        Color teeth = new Color(236, 228, 206), fingers = new Color(196, 188, 170), eye = new Color(255, 36, 36);
        for (int r = 0; r < HUNTER.length; r++) {
            for (int c = 0; c < HUNTER[r].length(); c++) {
                Color col;
                switch (HUNTER[r].charAt(c)) {
                    case '#': col = robe; break;
                    case 'd': col = edge; break;
                    case 'x': col = Color.BLACK; break;
                    case 's': col = bone; break;
                    case 'w': col = teeth; break;
                    case 'c': col = fingers; break;
                    case 'e': col = eye; break;
                    default: continue;
                }
                g.setColor(alpha < 255 ? new Color(col.getRed(), col.getGreen(), col.getBlue(), alpha) : col);
                g.fillRect(x + c * scale, y + r * scale, scale, scale);
            }
        }
        if (glow <= 0) return;
        // the eyes burn: soft red squares around each eye pixel
        for (int r = 0; r < HUNTER.length; r++) {
            for (int c = 0; c < HUNTER[r].length(); c++) {
                if (HUNTER[r].charAt(c) != 'e') continue;
                for (int k = 3; k >= 1; k--) {
                    int a = (int) (glow * alpha * 0.22);
                    g.setColor(new Color(255, 30, 30, Math.max(0, Math.min(255, a))));
                    g.fillRect(x + (c - k) * scale, y + (r - k) * scale, (2 * k + 1) * scale, (2 * k + 1) * scale);
                }
            }
        }
    }
}
