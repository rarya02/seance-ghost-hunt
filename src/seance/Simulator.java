package seance;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Keyboard stand-in for the hardware, so the game can be played and tested without it.
 *   Left / Right  turn the dial: one choice per press on menus, small steps otherwise
 *                                        Space  hold the grip (tap or hold)
 *   M             magnet under the wand  B      cover the light sensor
 *   U / J / K     point UP / LEFT / RIGHT N     point DOWN (holster)
 *   S             scream                 T      talk (noise)
 *   Enter         tap                    Esc    quit
 */
public class Simulator extends KeyAdapter {
    private static final int FINE_STEP = 16;      // dial step when there is no menu (Frequency Scan)
    private final SensorHub in;
    private final Game game;

    public Simulator(SensorHub in, Game game) {
        this.in = in;
        this.game = game;
    }

    /**
     * Turns the simulated dial. On a screen with choices it lands in the middle of the next
     * choice, the same way the game turns a dial reading into a choice (dial * count / 1024).
     */
    private void turn(int direction) {
        if (game.getScreen() == Game.ENTER_NAME) {          // the letter wheel: one letter per press
            in.simDial(direction * 1024 / 26);
            return;
        }
        int count = game.optionCount();
        if (count <= 0) {
            in.simDial(direction * FINE_STEP);
            return;
        }
        int index = Math.min(count - 1, in.simGetDial() * count / 1024);
        index = Math.max(0, Math.min(count - 1, index + direction));
        in.simSetDial((index * 1024 + 512) / count);
    }

    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:  turn(-1); break;
            case KeyEvent.VK_RIGHT: turn(1); break;
            case KeyEvent.VK_SPACE: in.simTouch(true); break;
            case KeyEvent.VK_M:     in.simHall(true); break;
            case KeyEvent.VK_B:     in.simLight(40); break;
            case KeyEvent.VK_U:     in.simPose("UP"); break;
            case KeyEvent.VK_J:     in.simPose("LEFT"); break;
            case KeyEvent.VK_K:     in.simPose("RIGHT"); break;
            case KeyEvent.VK_N:     in.simPose("DOWN"); break;
            case KeyEvent.VK_S:     in.simSound(700); break;
            case KeyEvent.VK_T:     in.simSound(250); break;       // above the hunt noise line
            default: break;
        }
    }

    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_SPACE: in.simTouch(false); break;
            case KeyEvent.VK_M:     in.simHall(false); break;
            case KeyEvent.VK_B:     in.simLight(600); break;
            case KeyEvent.VK_U: case KeyEvent.VK_J: case KeyEvent.VK_K: case KeyEvent.VK_N:
                in.simPose("LEVEL"); break;
            case KeyEvent.VK_S: case KeyEvent.VK_T:
                in.simSound(20); break;
            default: break;
        }
    }
}
