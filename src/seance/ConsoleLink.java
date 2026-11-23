package seance;

/** Stands in for a board in simulator mode: commands are printed instead of sent. */
public class ConsoleLink implements Link {
    private final String label;

    public ConsoleLink(String label) { this.label = label; }

    public void send(String line) { System.out.println("[" + label + "] " + line); }

    public void close() { }
}
