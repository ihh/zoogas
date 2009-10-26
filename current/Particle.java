import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;

// Particle class, encapsulating the behavior, appearance & summary statistics of a given CA state
public class Particle {
    // appearance
    public static int maxNameLength = 256;  // maximum length of a Particle name. Introduced to stop runaway regex rules from crashing the engine
    public String name = null;  // noun uniquely identifying this Particle (no whitespace)
    public Color color = null;

    // the PatternSet, i.e. the authority for all transformation, energy and color rules about this Particle
    PatternSet patternSet = null;

    // transformation rules
    protected IdentityHashMap<Particle,RandomVariable<ParticlePair>>[] pattern = null;  // production rules; array is indexed by neighbor direction, Map is indexed by Particle
    protected TransformRuleMatch[][] patternTemplate = null;  // generators for production rules; outer array is indexed by neighbor direction, inner array is the set of partially-bound rules for that direction

    // energy rules
    protected IdentityHashMap<Particle,Double> energy = null;  // interaction energies; Map is indexed by Particle
    protected EnergyRuleMatch[] energyTemplate = null;  // generators for interaction energies

    // reference counting
    private Board board = null;
    private int count = 0;  // how many of this type on the board
    private IdentityHashMap<Particle,Object> pastNeighbors = new IdentityHashMap<Particle,Object>();   // this is a dummy map (values ignored) solely to force reference identity

    // static variables
    public static String
	visibleSeparatorChar = "/",
	visibleSpaceChar = "_";

    // constructor
    public Particle (String name, Color color, Board board, PatternSet ps) {
	this.name = name.length() > maxNameLength ? name.substring(0,maxNameLength) : name;
	this.color = color;
	this.board = board;
	this.patternSet = ps;
	// init transformation rule patterns
	int N = board.neighborhoodSize();
	// The following is what we really want here, but backward compatibility of Java generics prevents initialization of an array of generics:
	//	pattern = new IdentityHashMap<Particle,RandomVariable<ParticlePair>> [N];
	pattern = new IdentityHashMap[N];   // causes an unavoidable warning. Thanks, Java!
	patternTemplate = new TransformRuleMatch[N][];
	for (int n = 0; n < pattern.length; ++n) {
	    pattern[n] = new IdentityHashMap<Particle,RandomVariable<ParticlePair>>();
	    patternTemplate[n] = patternSet.getSourceTransformRules (name, board, n);
	}
	// init energy rule patterns
	energy = new IdentityHashMap<Particle,Double>();
	energyTemplate = patternSet.getSourceEnergyRules(name);
	// register with the Board to prevent garbage collection
	board.registerParticle(this);
    }

    // methods
    // reference counting
    public final int incReferenceCount() {
	return ++count;
    }

    public final int decReferenceCount() {
	if (--count <= 0) {
	    //	    System.err.println("Zero reference count for " + name);

	    board.deregisterParticle(this);
	    for (Iterator<Particle> iter = pastNeighbors.keySet().iterator(); iter.hasNext(); )
		iter.next().forget(this);
	    pastNeighbors.clear();
	}
	return count;
    }

    // part of name visible to player
    public final String visibleName() {
	
	String[] partsOfName = name.split (visibleSeparatorChar, 2);
	String viz = partsOfName[0].replaceAll (visibleSpaceChar, " ");

	// Uncomment to reveal invisible metainfo to player
	//	viz = name;

	return viz;
    }

    // helper to "close" all patterns, adding a do-nothing rule for patterns whose RHS probabilities sum to <1
    protected final void closePatterns() {
	for (int n = 0; n < pattern.length; ++n) {
	    Iterator<Map.Entry<Particle,RandomVariable<ParticlePair>>> iter = pattern[n].entrySet().iterator();
	    while (iter.hasNext()) {
		Map.Entry<Particle,RandomVariable<ParticlePair>> keyval = (Map.Entry<Particle,RandomVariable<ParticlePair>>) iter.next();
		Particle target = (Particle) keyval.getKey();
		//		System.err.println ("Closing pattern " + name + " " + target.name + " -> ...");
		RandomVariable<ParticlePair> rv = (RandomVariable<ParticlePair>) keyval.getValue();
		if (rv != null)
		    rv.close(new ParticlePair (this, target));
	    }
	}
    }

    // method to test if a Particle is active (i.e. has any transformation rules) in a given direction
    public final boolean isActive(int dir) { return patternTemplate[dir].length > 0; }

    // method to test if a Particle has energy rules
    public final boolean hasEnergy() { return energyTemplate.length > 0; }

    // helper to sample a new (source,target) pair
    // returns null if no rule found
    public final ParticlePair samplePair (int dir, Particle oldTarget, Random rnd, Board board) {
	RandomVariable<ParticlePair> rv = null;
	if (pattern[dir].containsKey(oldTarget)) {
	    rv = pattern[dir].get (oldTarget);
	} else {
	    // if no RV, look for rule generator(s) that match this neighbor, and use them to create a set of rules
	    if (patternSet != null) {
		rv = patternSet.compileTransformRules(dir,this,oldTarget,board);
		pattern[dir].put (oldTarget, rv);
		remember(oldTarget);
	    }
	}
	// have we got an RV?
	if (rv != null)
	    return (ParticlePair) rv.sample(rnd);
	// no RV; return null
	return null;
    }

    // helper to calculate pairwise interaction energy with another Particle (one-way)
    public final double pairEnergy (Particle p) {
	double E = 0;
	if (hasEnergy()) {
	    if (energy.containsKey(p)) {
		E = energy.get(p).doubleValue();
	    } else {
		// look for rule generator(s) that match this neighbor, and use them to calculate energy
		if (patternSet != null) {
		    E = patternSet.compileEnergyRules(this,p);
		    energy.put (p, new Double(E));
		    remember(p);
		}
	    }
	}
	// return
	return E;
    }

    // helper to calculate symmetric form of pairEnergy
    public final double symmetricPairEnergy (Particle p) {
	return pairEnergy(p) + p.pairEnergy(this);
    }

    // helpers to remember/forget a neighbor
    private final void remember (Particle p) {
	if (p == null)
	    throw new RuntimeException ("Tried to remember a null Particle");
	else if (p != this) {  // don't create circular references
	    // NB we don't need to remember p, only ensure that p remembers us
	    // The purpose of this remembering is to ensure that we delete p from our compiled rule tables when p's reference count hits zero
	    p.pastNeighbors.put(this,null);
	}
    }

    private final void forget (Particle p) {
	for (int d = 0; d < pattern.length; ++d)
	    pattern[d].remove(p);
	energy.remove(p);
	pastNeighbors.remove(p);
    }

    // equals method
    public final boolean equals(Particle p) {
	return name.equals(p.name);
    }

    // finalize method
    // uncomment to debug garbage collection
    /*
    protected final void finalize() throws Throwable
    {
	System.err.println("Deleting " + name);

	// uncomment if this class is not a direct subclass of Object
	//	super.finalize();
    }
    */
}
