package seance;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;

/**
 * Opens one serial port with the jSSC library (jssc-2.9.6.jar), the same way as in Lab 9.
 * The path is the port's name, for example "/dev/cu.usbserial-A601EREF" on a Mac or "COM8" on Windows.
 * Both boards send text lines at 9600 baud, so this class reads and writes whole lines.
 */
public class SerialPortHandle {
    private final SerialPort sp;
    private final String path;

    /** Opens the port at 9600 baud, 8 data bits, 1 stop bit, no parity (the Arduino default). */
    public SerialPortHandle(String path) throws SerialPortException {
        this.path = path;
        sp = new SerialPort(path);
        sp.openPort();
        sp.setParams(9600, 8, 1, 0);
    }

    /**
     * Waits for one full line from the board and returns it without the line ending.
     * It reads one byte at a time (a blocking read) until the '\n' that Serial.println() sends.
     */
    public String readLine() throws SerialPortException {
        StringBuilder line = new StringBuilder();
        while (true) {
            byte b = readByte();
            if (b == '\n') return line.toString();
            if (b != '\r') line.append((char) b);
        }
    }

    /** Sends one command line; the boards read up to the '\n'. */
    public void writeLine(String text) throws SerialPortException {
        sp.writeString(text + "\n");
    }

    /** Sends a single byte (from Lab 9, Exercise 4). */
    public void writeByte(byte b) throws SerialPortException {
        sp.writeBytes(new byte[] { b });
    }

    /** Waits for a single byte (from Lab 9, Exercise 4). */
    public byte readByte() throws SerialPortException {
        byte[] buffer = sp.readBytes(1); // blocking read
        if (buffer != null && buffer.length > 0) {
            return buffer[0];
        } else {
            throw new SerialPortException(path, "readByte", "No data received");
        }
    }

    public void close() {
        try {
            if (sp.isOpened()) sp.closePort();
        } catch (SerialPortException e) {
            // already closed
        }
    }

    public String getPath() { return path; }

    /** Every serial port jSSC can see on this computer. */
    public static String[] listPorts() {
        return SerialPortList.getPortNames();
    }
}
