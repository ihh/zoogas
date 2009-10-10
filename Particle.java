import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Particle class, encapsulating the behavior, appearance & summary statistics of a given CA state
public class Particle {
    public String name = "Particle";
    public static String
	visibleSeparatorChar = "/",
	visibleSpaceChar = "_";
    public Color color = null;
    protected IdentityHashMap pattern = new IdentityHashMap();  // production rules, indexed by neighbor Particle
    protected int count = 0;  // how many on the board

    // constructor
    public Particle (String name, Color color) {
	this.name = name;
	this.color = color;
    }

    // methods
    // part of name visible to player
    String visibleName() {
	String[] partsOfName = name.split (visibleSeparatorChar, 2);
	return partsOfName[0].replaceAll (visibleSpaceChar, " ");
    }

    // visibleSpaceChar to add a pattern
    void addPattern (Particle oldTarget, Particle newSource, Particle newTarget, double prob) {
	RandomVariable rv = (RandomVariable) pattern.get (oldTarget);
	if (rv == null)
	    pattern.put (oldTarget, rv = new RandomVariable());
	rv.add (new ParticlePair (newSource, newTarget), prob);
    }

    // helper to "close" all patterns, adding a do-nothing rule for patterns whose RHS probabilities sum to <1
    void closePatterns() {
	Iterator iter = pattern.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry keyval = (Map.Entry)iter.next();
	    Particle target = (Particle) keyval.getKey();
	    //		System.err.println ("Closing pattern " + name + " " + target.name + " -> ...");
	    RandomVariable rv =  (RandomVariable) keyval.getValue();
	    if (rv != null)
		rv.close(new ParticlePair (this, target));
	}
    }

    // helper to sample a new (source,target) pair
    // returns null if no rule found
    ParticlePair samplePair (Particle oldTarget, Random rnd) {
	RandomVariable rv = (RandomVariable) pattern.get (oldTarget);
	if (rv != null)
	    return (ParticlePair) rv.sample(rnd);
	return null;
    }
}
