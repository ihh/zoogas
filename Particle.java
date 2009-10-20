import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;

// Particle class, encapsulating the behavior, appearance & summary statistics of a given CA state
public class Particle {
    // appearance
    public String name = null;  // noun uniquely identifying this Particle (no whitespace)
    public Color color = null;

    // behavior
    protected IdentityHashMap[] pattern = null;  // production rules; array is indexed by neighbor direction, Map is indexed by Particle
    protected RuleMatch[][] patternTemplate = null;  // generators for production rules; outer array is indexed by neighbor direction, inner array is the set of partially-bound rules for that direction
    PatternSet patternSet = null;

    // internals
    protected int count = 0;  // how many of this type on the board

    // static variables
    public static String
	visibleSeparatorChar = "/",
	visibleSpaceChar = "_";

    // constructors
    public Particle (String name, Color color, ZooGas gas, PatternSet ps) {
	this(name,color,gas);
	patternSet = ps;
    }

    public Particle (String name, Color color, ZooGas gas) {
	this.name = name;
	this.color = color;
	gas.registerParticle (name, this);
	pattern = new IdentityHashMap[gas.neighborhoodSize()];
	patternTemplate = new RuleMatch[gas.neighborhoodSize()][];
	for (int n = 0; n < pattern.length; ++n)
	    pattern[n] = new IdentityHashMap();
    }

    // methods
    // part of name visible to player
    String visibleName() {
	String[] partsOfName = name.split (visibleSeparatorChar, 2);
	return partsOfName[0].replaceAll (visibleSpaceChar, " ");
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
    ParticlePair samplePair (int dir, Particle oldTarget, Random rnd, ZooGas gas) {
	RandomVariable rv = (RandomVariable) pattern[dir].get (oldTarget);
	// if no RV, look for rule generator(s) that match this neighbor, and use them to create a set of rules
	if (rv == null) {
	    rv = new RandomVariable();
	    if (patternSet != null) {
		if (patternTemplate[dir] == null)
		    patternTemplate[dir] = patternSet.getSourceRules (name, gas, dir);
		for (int n = 0; n < patternTemplate[dir].length; ++n) {
		    RuleMatch rm = patternTemplate[dir][n];
		    //		    System.err.println ("Particle " + name + ": trying to match " + patternTemplate[dir][n]);
		    if (rm.bindTarget(oldTarget.name)) {
			Particle
			    newSource = gas.getOrCreateParticle (rm.C()),
			    newTarget = gas.getOrCreateParticle (rm.D());
			ParticlePair pp = new ParticlePair (newSource, newTarget);
			rv.add (pp, rm.P());
		    }
		    rm.unbindTarget();
		}
		rv.close (new ParticlePair (this, oldTarget));
		pattern[dir].put (oldTarget, rv);
	    }
	}
	// have we got an RV?
	if (rv != null)
	    return (ParticlePair) rv.sample(rnd);
	// no RV; return null
	return null;
    }
}
