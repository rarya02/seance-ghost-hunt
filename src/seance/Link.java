package seance;

/** Anything the game can send a command line to: a real serial port or the simulator. */
public interface Link {
    void send(String line);
    void close();
}
