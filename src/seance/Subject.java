package seance;

/**
 * Observer pattern: the Subject interface (as on the lecture slides).
 *
 * The Subject needs to do three things:
 *   1. Register observers
 *   2. Remove observers
 *   3. Notify observers, telling them more data is available.
 *
 * In Séance the Subject is the SensorHub: it collects what the rig and the wand send and
 * hands a new Reading to every Observer 20 times a second.
 */
public interface Subject {
    public void registerObserver(Observer o);
    public void removeObserver(Observer o);
    public void notifyObservers();
}
