package seance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The spirit's riddles. Contact answers with one of these: a riddle that is always true and
 * describes one of the hidden ghost's traits, without saying which trait it is about.
 * The player has to work out both the trait and its value.
 *
 * There are eight riddles for every value of every trait (80 in total). Each riddle only uses
 * imagery from its own trait (a riddle about cold never mentions the night, a riddle about a
 * restless ghost never mentions wandering), so it can only be read one way.
 * A run never repeats a riddle until every riddle for that answer has been used.
 */
public final class Riddles {
    private Riddles() { }

    private static final Random RNG = new Random();

    // [trait][0 = first value (cold, loud, fears light, restless, stays near), 1 = second value][riddle]
    private static final String[][][] BANK = {
        {   // Presence
            {   // cold
                "I leave no frost upon the ground, yet chills follow me all around. Wherever I linger, warmth disappears. What am I?",
                "Puddles turn to glass where I tread, and roses bow their weary head. What am I?",
                "Your tea grows bitter, your fingers numb, from the very moment that I come. What am I?",
                "The hearth still burns, yet gives no heat, whenever I take the empty seat. What am I?",
                "I am the winter in July, the frozen breath, the frosted sigh. What am I?",
                "Blankets fail and gloves fall short; my touch is of the frozen sort. What am I?",
                "The milk will keep, the ice won't melt, wherever my presence can be felt. What am I?",
                "Your breath turns white though the season's fair; the bees won't leave their hive when I am there. What am I?",
            },
            {   // warm
                "I carry no flame, yet comfort I bring. I turn the coldest days into the feeling of spring. What am I?",
                "Frost retreats where I have been; the window weeps, the glass grows clean. What am I?",
                "I am the hearth's embrace without its fire, the blanket's hug, the summer's choir. What am I?",
                "Snow will melt beneath my tread, and ice will weep where I have led. What am I?",
                "The cat curls close, the butter yields; I bring July to winter fields. What am I?",
                "Like a mug of tea held tight, I thaw the hands that frost would bite. What am I?",
                "Where I linger roses bloom, while winter knocks but finds no room. What am I?",
                "I am the ember's gentle kiss, the summer's breath you'll sorely miss. What am I?",
            },
        },
        {   // Voice
            {   // loud
                "I need no trumpet, yet fill the air. Even from afar, all know I'm there. What am I?",
                "Glasses tremble, windows shake, the sleeping dogs are kept awake. What am I?",
                "I rattle chains and slam the door; my echo bounces off the floor. What am I?",
                "No lullaby can hush my roar; the neighbours flee from floor to floor. What am I?",
                "My footsteps boom, my whispers crash; I make the silver cupboard clash. What am I?",
                "Plug your ears, it won't be enough; my every whisper is thunder-rough. What am I?",
                "The cat takes flight, the baby cries, the moment that my voice will rise. What am I?",
                "The old soldier wakes and reaches for his gun; he thinks the war has just begun. What am I?",
            },
            {   // silent
                "I speak no words, I make no sound. Yet I can be present all around. What am I?",
                "I cross the floor where boards would creak, yet not a single board will speak. What am I?",
                "The guard dog dozes, the hinges keep mum; I pass, and no one knows I've come. What am I?",
                "I am the pause between two notes, the hush that hangs where no voice floats. What am I?",
                "No bell, no step, no breath, no cry; you'll never hear me passing by. What am I?",
                "A falling feather makes more din than I do when I slip in. What am I?",
                "Like a held breath, like a closed book, I never call, so you must look. What am I?",
                "The ticking clock makes more ado; I come and go, unheard by you. What am I?",
            },
        },
        {   // Aversion
            {   // fears light
                "When the sun rises, I seek to hide. In the brightest places, I cannot abide. What am I?",
                "I flee the dawn and shun the noon; my only friend's a clouded moon. What am I?",
                "Draw back the curtains and I will flee; the morning is no friend to me. What am I?",
                "A single match can make me run; I count the hours till day is done. What am I?",
                "I live in cellars, wells and caves; a sunbeam sends me to my grave. What am I?",
                "Your lantern is my greatest foe; where the shadows go, I too must go. What am I?",
                "The rooster wakes, and I'm away; I cannot face the break of day. What am I?",
                "I hide in corners, under stairs, wherever no bright window glares. What am I?",
            },
            {   // fears dark
                "When daylight fades, I lose my way. I long for the sun and dread the end of day. What am I?",
                "Blow out the candle and I despair; I dread the night and its empty air. What am I?",
                "I keep a candle in each hand; the night is a place I cannot stand. What am I?",
                "When shadows lengthen, I grow afraid; I cling to sunlight and fear the shade. What am I?",
                "Like the moth, I'm drawn to every flame; the coming dusk I cannot tame. What am I?",
                "Never leave me without a lamp; I tremble when the dusk grows damp. What am I?",
                "The setting sun is my greatest dread; I keep the lamps lit by my bed. What am I?",
                "Midnight is a monster to me; I'd trade it all for a dawn to see. What am I?",
            },
        },
        {   // Temperament
            {   // restless
                "I never settle, I never stay. I must keep moving throughout the day. Stillness is something I cannot bear. What am I?",
                "I tap my fingers, I pace the floor; I cannot sit a minute more. What am I?",
                "I start a thousand things, not one complete; I fidget, shift, and never take a seat. What am I?",
                "The rocking chair rocks when I'm about; I can't sit still, I'm in and out. What am I?",
                "Impatient hands and tapping feet; for me, a minute is an endless beat. What am I?",
                "I toss and turn the whole day through; I can't sit still, whatever I do. What am I?",
                "The chessboard waits, the book lies shut; I've left them both, I can't stay put. What am I?",
                "I drum on tables, I shift in chairs; I'm up and down a hundred stairs. What am I?",
            },
            {   // patient
                "I do not hurry, I do not race. I wait for the moment, however long it takes. What am I?",
                "Seasons pass and still I sit; I'll wait forever, bit by bit. What am I?",
                "The spider weaves her web on me; I wait as calm as calm can be. What am I?",
                "I watch the hourglass slowly drain, and never once do I complain. What am I?",
                "A century is but an hour to me; I bide my time contentedly. What am I?",
                "Like a stone that waits for the stream to wear, I bide my time without a care. What am I?",
                "The guests have left, the clock struck nine; I sit unmoved, and that is fine. What am I?",
                "Ivy grows across my chair; a hundred years, I'm sitting there. What am I?",
            },
        },
        {   // Reach
            {   // stays near
                "I know these paths, these sights, this ground. Beyond my home, I am rarely found. What am I?",
                "I've never crossed the garden gate; this house alone is my estate. What am I?",
                "One room, one stair, one window pane; I never leave, I here remain. What am I?",
                "The village lies beyond my ken; I keep to these four walls again. What am I?",
                "I never stray, I never go; these walls are all I'll ever know. What am I?",
                "The road outside I've never known; I keep to the rooms that are my own. What am I?",
                "Not once have I passed the garden wall; this house, these rooms, are all in all. What am I?",
                "Where I fell, I stay, and there I'll be; you'll find me close, as close can be. What am I?",
            },
            {   // roams far
                "No border can hold me, no path too wide. I wander wherever adventure may hide. What am I?",
                "I've walked the moors and crossed the sea; no single house could hold on to me. What am I?",
                "From town to town, from coast to hill, I drift wherever the winds will. What am I?",
                "The ferryman, the innkeeper, the priest: they've all seen me, west to east. What am I?",
                "No wall, no gate, no hedge, no door can keep me home; I cross the moor. What am I?",
                "I've slept in barns from here to Spain; I never rest in one place again. What am I?",
                "My footprints mark a hundred roads; I carry tales from strange abodes. What am I?",
                "Mountains, rivers, borders, seas: I cross them all with perfect ease. What am I?",
            },
        },
    };

    /**
     * A riddle about this trait and value, one the run has not used yet when possible.
     * The used set lives in the Run, so riddles do not repeat from night to night.
     */
    public static String pick(int trait, boolean firstValue, Set<String> used) {
        String[] pool = BANK[trait][firstValue ? 0 : 1];
        List<String> fresh = new ArrayList<String>();
        for (String r : pool) if (!used.contains(r)) fresh.add(r);
        if (fresh.isEmpty()) {                       // every riddle for this answer was used: start again
            for (String r : pool) used.remove(r);
            for (String r : pool) fresh.add(r);
        }
        String r = fresh.get(RNG.nextInt(fresh.size()));
        used.add(r);
        return r;
    }

    /** How many riddles there are for one value of one trait. */
    public static int perAnswer() { return BANK[0][0].length; }

    /** Every riddle for one value of one trait (for the check program). */
    static String[] pool(int trait, boolean firstValue) { return BANK[trait][firstValue ? 0 : 1]; }
}
