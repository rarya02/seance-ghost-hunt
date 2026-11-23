package seance;

/**
 * Observer pattern: the Observer interface (as on the lecture slides).
 *
 * It only specifies which function the Subject calls and what that function looks like.
 * update() is the contract between the Subject and all Observers.
 *
 * On the slides the clock first called update() with no data, then changed the contract to
 * update(Date d) so every mouse also got the time of the tick. Séance does the same thing with
 * update(Reading r): every Observer gets one Reading holding the time of the tick and the
 * latest values from every sensor, and looks only at the parts it needs.
 */
public interface Observer {
    void update(Reading r);
}
