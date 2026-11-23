package seance;

import jssc.SerialPortException;

/**
 * The link to one board (the Uno over USB, or the xBee Explorer for the wand).
 * It opens the port with a SerialPortHandle, as in Lab 9. A background thread keeps calling
 * readLine() and hands every line to the SensorHub, so the game never waits on the radio.
 */
public class SerialLink implements Link, Runnable {
    private final SerialPortHandle sph;
    private final SensorHub hub;
    private final String label;
    private volatile boolean running = true;

    public SerialLink(String portName, SensorHub hub, String label) throws SerialPortException {
        this.hub = hub;
        this.label = label;
        sph = new SerialPortHandle(portName);
        Thread reader = new Thread(this, label + " reader");
        reader.setDaemon(true);
        reader.start();
        System.out.println("Connected to the " + label + " on " + portName);
    }

    /** The reader thread: the same loop as the Lab 9 driver, but each line goes to the hub. */
    public void run() {
        while (running) {
            try {
                String line = sph.readLine();
                hub.handleLine(line);
            } catch (SerialPortException e) {
                if (running) System.out.println("Lost the " + label + ": " + e.getMessage());
                running = false;
            }
        }
    }

    /** Sends one command line to the board. */
    public synchronized void send(String line) {
        try {
            sph.writeLine(line);
        } catch (SerialPortException e) {
            System.out.println("Could not send to the " + label + ": " + line);
        }
    }

    public void close() {
        running = false;
        sph.close();
    }

    /** Prints every serial port the computer can see, to help pick the right ones. */
    public static void listPorts() {
        System.out.println("Serial ports found:");
        for (String p : SerialPortHandle.listPorts()) System.out.println("  " + p);
        System.out.println("(on a Mac you can use the /dev/cu. version of each name)");
    }
}
