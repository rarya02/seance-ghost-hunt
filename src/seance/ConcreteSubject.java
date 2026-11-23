package seance;

import java.util.ArrayList;

/**
 * Observer pattern: the ConcreteSubject (as on the lecture slides).
 *
 * It keeps the list of Observers and implements the three Subject methods. It does not know
 * what any Observer does with the data: it just calls update() on each one.
 *
 * On the slides notifyObservers() sent update(new Date()). Here the data is a Reading, which the
 * subclass (the SensorHub) puts in the reading field just before it calls notifyObservers().
 */
public class ConcreteSubject implements Subject {
    private ArrayList<Observer> observers;
    protected Reading reading;            // the data sent to every Observer (the slides sent a Date)

    public ConcreteSubject() {
        observers = new ArrayList<Observer>();
    }

    public void notifyObservers() {
        for (int i = 0; i < observers.size(); i++) {
            Observer observer = observers.get(i);
            observer.update(reading);
        }
    }

    public void registerObserver(Observer o) {
        observers.add(o);
    }

    public void removeObserver(Observer o) {
        observers.remove(o);
    }
}
