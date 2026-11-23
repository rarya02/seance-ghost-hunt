package seance;

/** Everything the game uses to talk to the physical world, passed around as one object. */
public class Hardware {
    public final SensorHub hub;
    public final Rig rig;
    public final Wand wand;
    public final AudioDirector audio;

    public Hardware(SensorHub hub, Rig rig, Wand wand, AudioDirector audio) {
        this.hub = hub;
        this.rig = rig;
        this.wand = wand;
        this.audio = audio;
    }
}
