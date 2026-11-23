package seance;

/**
 * Observer pattern: the ConcreteObserver (as on the lecture slides).
 *
 *  - A ConcreteObserver registers itself: the constructor takes the Subject, keeps it, and
 *    calls subject.registerObserver(this).
 *  - It provides a generic implementation of update().
 *
 * Every Observer in Séance extends this class, the same way Mouse and Alice extend it on the
 * slides: they call super(subject) to register, and override update() to give themselves
 * behaviour. An Observer that is finished (a test that passed, a hunt that is over) leaves
 * with subject.removeObserver(this).
 */
public class ConcreteObserver implements Observer {

    protected Subject subject;

    public ConcreteObserver(Subject subject) {
        this.subject = subject;
        subject.registerObserver(this);
    }

    public void update(Reading r) {
        System.out.println("nothing to do at " + r.getTime() + " from " + this);
    }
}
