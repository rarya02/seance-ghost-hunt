package seance;

/** One play-through: up to seven nights, sharing one sanity meter and one score. */
public class Run {
    private int nightNo = 1;
    private long score = 0;
    private int sanity = Config.START_SANITY;
    private boolean failedTestTonight = false;
    private boolean lostSanityInHuntTonight = false;
    private final java.util.Set<String> usedRiddles = new java.util.HashSet<String>();   // so riddles do not repeat

    public int getNightNo() { return nightNo; }
    public long getScore() { return score; }
    public int getSanity() { return sanity; }
    public boolean isOver() { return sanity <= 0; }

    public void loseSanity(int amount) { if (!god) sanity = Math.max(0, sanity - amount); }

    /** Debug god mode: sanity never goes down. */
    private boolean god = false;
    public void setGod(boolean on) { god = on; }

    /** Every ghost banished this run, in order (shown again at dawn). */
    private final java.util.List<Ghost> banished = new java.util.ArrayList<Ghost>();
    public void addBanished(Ghost g) { banished.add(g); }
    public java.util.List<Ghost> getBanished() { return banished; }
    public void gainSanity(int amount) { sanity = Math.min(Config.START_SANITY, sanity + amount); }

    public void markFailedTest() { failedTestTonight = true; }
    public void markHuntLoss() { lostSanityInHuntTonight = true; }

    /** A clean night: no failed test and no sanity lost in a hunt. */
    public boolean isCleanNight() { return !failedTestTonight && !lostSanityInHuntTonight; }

    public void bank(long points) { score += points; }

    /** The spirit's riddles already heard this run. */
    public java.util.Set<String> usedRiddles() { return usedRiddles; }

    /** Debug mode: start the run on a later night, to try the harder nights straight away. */
    public void debugSetNight(int n) { nightNo = Math.max(1, Math.min(Config.NIGHTS_TO_DAWN, n)); }

    public void nextNight() {
        nightNo++;
        failedTestTonight = false;
        lostSanityInHuntTonight = false;
    }
}
