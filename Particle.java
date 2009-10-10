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
    protected IdentityHashMap[] pattern = null;  // production rules, indexed by neighbor direction & Particle
    protected int count = 0;  // how many on the board

    // constructor
    public Particle (String name, Color color, ZooGas gas) {
	this.name = name;
	this.color = color;
	pattern = new IdentityHashMap[gas.neighborhoodSize()];
	for (int n = 0; n < pattern.length; ++n)
	    pattern[n] = new IdentityHashMap();
    }

    // methods
    // part of name visible to player
    String visibleName() {
	String[] partsOfName = name.split (visibleSeparatorChar, 2);
	return partsOfName[0].replaceAll (visibleSpaceChar, " ");
    }

    // helper to add a pattern
    void addPattern (Particle oldTarget, Particle newSource, Particle newTarget, double prob) {
	for (int dir = 0; dir < pattern.length; ++dir) {
	    RandomVariable rv = (RandomVariable) pattern[dir].get (oldTarget);
	    if (rv == null)
		pattern[dir].put (oldTarget, rv = new RandomVariable());
	    rv.add (new ParticlePair (newSource, newTarget), prob);
	}
    }

    // helper to "close" all patterns, adding a do-nothing rule for patterns whose RHS probabilities sum to <1
    void closePatterns() {
	for (int n = 0; n < pattern.length; ++n) {
	    Iterator iter = pattern[n].entrySet().iterator();
	    while (iter.hasNext()) {
		Map.Entry keyval = (Map.Entry)iter.next();
		Particle target = (Particle) keyval.getKey();
		//		System.err.println ("Closing pattern " + name + " " + target.name + " -> ...");
		RandomVariable rv =  (RandomVariable) keyval.getValue();
		if (rv != null)
		    rv.close(new ParticlePair (this, target));
	    }
	}
    }

    // method to test if a Particle is active (i.e. has any rules) in a given direction
    boolean isActive(int dir) { return pattern[dir].size() > 0; }

    // helper to sample a new (source,target) pair
    // returns null if no rule found
    ParticlePair samplePair (int dir, Particle oldTarget, Random rnd) {
	RandomVariable rv = (RandomVariable) pattern[dir].get (oldTarget);
	if (rv != null)
	    return (ParticlePair) rv.sample(rnd);
	return null;
    }
}
