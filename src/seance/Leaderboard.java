package seance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/** The top five scores, kept in scores.txt as lines of name,score,night. */
public class Leaderboard {
    private static final int SIZE = 5;
    private final File file;
    private final String[] names = new String[SIZE];
    private final long[] scores = new long[SIZE];
    private final int[] nights = new int[SIZE];
    private int count = 0;

    public Leaderboard(String path) {
        file = new File(path);
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null && count < SIZE) {
                String[] p = line.split(",");
                if (p.length < 3) continue;
                names[count] = p[0];
                scores[count] = Long.parseLong(p[1]);
                nights[count] = Integer.parseInt(p[2]);
                count++;
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("Could not read the leaderboard: " + e.getMessage());
        }
    }

    private void save() {
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            for (int i = 0; i < count; i++) out.println(names[i] + "," + scores[i] + "," + nights[i]);
        } catch (IOException e) {
            System.out.println("Could not save the leaderboard: " + e.getMessage());
        }
    }

    public boolean qualifies(long score) {
        return score > 0 && (count < SIZE || score > scores[count - 1]);
    }

    /** Inserts a score in order, keeping the top five. */
    public void add(String name, long score, int night) {
        if (!qualifies(score)) return;
        int pos = count < SIZE ? count : SIZE - 1;
        while (pos > 0 && scores[pos - 1] < score) {
            names[pos] = names[pos - 1];
            scores[pos] = scores[pos - 1];
            nights[pos] = nights[pos - 1];
            pos--;
        }
        names[pos] = name.replace(",", " ");
        scores[pos] = score;
        nights[pos] = night;
        if (count < SIZE) count++;
        save();
    }

    public int size() { return count; }
    public String nameAt(int i) { return names[i]; }
    public long scoreAt(int i) { return scores[i]; }
    public int nightAt(int i) { return nights[i]; }
}
