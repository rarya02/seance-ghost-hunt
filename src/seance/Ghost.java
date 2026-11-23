package seance;

import java.util.Random;

/**
 * One of the 32 ghosts: a name, five traits, and the pointing sequence that banishes it.
 * Ghost number i has trait t when bit t of i is 1, so all 32 combinations exist exactly once.
 */
public class Ghost {
    private static final String[] NAMES = {
        "The Widow", "The Drowned Boy", "The Lamplighter", "The Hollow Saint",
        "The Bride", "The Gravedigger", "The Weeping Nun", "The Organist",
        "The Faceless Maid", "The Hanged Man", "The Plague Doctor", "The Silent Twin",
        "The Chimney Sweep", "The Moth Queen", "The Stitched Doll", "The Bell Ringer",
        "The Mourner", "The Burnt Choir", "The Lost Sailor", "The Night Nurse",
        "The Pale Child", "The Butcher", "The Seamstress", "The Ferryman",
        "The Hollow King", "The Candle Thief", "The Drowned Bride", "The Whistler",
        "The Grey Lady", "The Mirror Man", "The Old Hound", "The Clockmaker"
    };

    private static final String[] POSES = { "UP", "LEFT", "RIGHT" };

    private final int id;
    private final String name;
    private final boolean[] traits = new boolean[Traits.COUNT];
    private final String[] sequence;

    public Ghost(int id) {
        this.id = id;
        this.name = NAMES[id];
        for (int t = 0; t < Traits.COUNT; t++) {
            traits[t] = ((id >> t) & 1) == 1;
        }
        // Near ghosts need 2 poses, far ghosts need 3. Same ghost, same sequence, every game.
        int length = isNear() ? 2 : 3;
        sequence = new String[length];
        Random r = new Random(id * 7919L);
        String previous = "";
        for (int i = 0; i < length; i++) {
            String p;
            do { p = POSES[r.nextInt(POSES.length)]; } while (p.equals(previous));
            sequence[i] = p;
            previous = p;
        }
    }

    /** Creates all 32 ghosts. */
    public static Ghost[] createAll() {
        Ghost[] all = new Ghost[32];
        for (int i = 0; i < 32; i++) all[i] = new Ghost(i);
        return all;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public boolean has(int trait) { return traits[trait]; }
    public String[] getSequence() { return sequence; }
    public boolean isLoud() { return traits[Traits.VOICE]; }
    public boolean isRestless() { return traits[Traits.TEMPERAMENT]; }
    public boolean isNear() { return traits[Traits.REACH]; }
}
