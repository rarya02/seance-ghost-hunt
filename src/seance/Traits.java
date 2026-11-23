package seance;

/**
 * The five yes or no traits every ghost has, and the words used to show them.
 * A trait value of true means the first word in its pair.
 */
public final class Traits {
    private Traits() { }

    public static final int PRESENCE = 0;
    public static final int VOICE = 1;
    public static final int AVERSION = 2;
    public static final int TEMPERAMENT = 3;
    public static final int REACH = 4;
    public static final int COUNT = 5;

    public static final String[] NAMES = { "Presence", "Voice", "Aversion", "Temperament", "Reach" };

    private static final String[][] VALUES = {
        { "Cold", "Warm" },
        { "Loud", "Silent" },
        { "Fears light", "Fears dark" },
        { "Restless", "Patient" },
        { "Near", "Far" }
    };

    /** The yes or no question Contact asks about each trait (a YES means the first word). */
    private static final String[] QUESTIONS = {
        "Is it cold?", "Is it loud?", "Does it fear the light?", "Is it restless?", "Does it stay near?"
    };

    /** How each value reads in a sentence: "the ghost IS COLD", "the ghost FEARS THE DARK". */
    private static final String[][] SENTENCES = {
        { "is cold", "is warm" },
        { "is loud", "is silent" },
        { "fears the light", "fears the dark" },
        { "is restless", "is patient" },
        { "stays near", "roams far" }
    };

    public static String question(int trait) { return QUESTIONS[trait]; }

    public static String sentence(int trait, boolean value) { return SENTENCES[trait][value ? 0 : 1]; }

    public static String label(int trait, boolean value) {
        return VALUES[trait][value ? 0 : 1];
    }

    public static String shortLabel(int trait, boolean value) {
        return label(trait, value).replace("Fears ", "");
    }
}
