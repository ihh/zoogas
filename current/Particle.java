import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;

// Particle class, encapsulating the behavior, appearance & summary statistics of a given CA state
public class Particle {
    // appearance
    public static int maxNameLength = 256;  // maximum length of a Particle name. Introduced to stop runaway regex rules from crashing the engine
    public String name = null;  // noun uniquely identifying this Particle (no whitespace)
    public String defaultVerb = null;
    public Color color = null;

    // the PatternSet, i.e. the authority for all transformation, energy and color rules about this Particle
    PatternSet patternSet = null;

    // transformation rules
    protected ArrayList<IdentityHashMap<Particle,RandomVariable<ParticlePair>>> pattern = null;  // production rules; array is indexed by neighbor direction, Map is indexed by Particle
    protected TransformRuleMatch[][] transformRuleMatch = null;  // generators for production rules; outer array is indexed by neighbor direction, inner array is the set of partially-bound rules for that direction
    protected double[] transformRate = null;  // sum of transformation regex rates, indexed by direction
    protected double totalTransformRate = 0;  // sum of transformation regex rates in all directions

    // energy rules
    protected ArrayList<IdentityHashMap<Particle,Double>> energy = null;  // interaction energies; Map is indexed by Particle
    protected EnergyRuleMatch[][] energyRuleMatch = null;  // generators for interaction energies

    // reference counting
    private Board board = null;
    private int count = 0;  // how many of this type on the board

    // static variables
    public static String
	visibleSeparatorChar = "/",
	visibleSpaceChar = "_";

    // constructor
    public Particle (String name, Color color, Board board, PatternSet ps) {
	if (name.length() > maxNameLength) {
	    System.err.println("Warning: truncating name " + name);
	    this.name = name.substring(0,maxNameLength);
	} else
	    this.name = name;
	this.color = color;
	this.board = board;
	this.patternSet = ps;
	// init transformation & energy rule patterns in each direction
	int N = board.neighborhoodSize();
	pattern = new ArrayList<IdentityHashMap<Particle, RandomVariable<ParticlePair>>>(N);
	transformRuleMatch = new TransformRuleMatch[N][];
	transformRate = new double[N];
	energy = new ArrayList<IdentityHashMap<Particle,Double>>();
	energyRuleMatch = new EnergyRuleMatch[N][];
	
	for (int n = 0; n < N; ++n) {
	    pattern.add(new IdentityHashMap<Particle,RandomVariable<ParticlePair>>());
	    transformRuleMatch[n] = patternSet.getSourceTransformRules(name,n);

	    transformRate[n] = 0;
	    for (int i = 0; i < transformRuleMatch[n].length; ++i)
		transformRate[n] += transformRuleMatch[n][i].P();
	    totalTransformRate += transformRate[n];

	    energy.add(new IdentityHashMap<Particle,Double>());
	    energyRuleMatch[n] = patternSet.getSourceEnergyRules(name,n);
	}
	// register with the Board for name lookup (NB this may prevent garbage collection, so we deregister later)
	board.registerParticle(this);
    }

    // methods
    // reference counting
    public final int getReferenceCount() { return count; }

    public final int incReferenceCount() {
	return ++count;
    }

    public final int decReferenceCount() {
	return --count;
    }

    // part of name visible to player
    public final String visibleName() {
	return visibleText(name);
    }

    static Pattern nonWhitespace = Pattern.compile("\\S");
    public static String visibleText(String s) {
	String[] partsOfName = s.split (visibleSeparatorChar, 2);
	String viz = partsOfName[0].replaceAll (visibleSpaceChar, " ");
	return nonWhitespace.matcher(viz).find() ? viz : "";
    }

    // helper to "close" all patterns, adding a do-nothing rule for patterns whose RHS probabilities sum to <1
    protected final void closePatterns() {
	for (int n = 0; n < pattern.size(); ++n) {
	    Iterator<Map.Entry<Particle,RandomVariable<ParticlePair>>> iter = pattern.get(n).entrySet().iterator();
	    while (iter.hasNext()) {
		Map.Entry<Particle,RandomVariable<ParticlePair>> keyval = (Map.Entry<Particle,RandomVariable<ParticlePair>>) iter.next();
		Particle target = (Particle) keyval.getKey();
		//		System.err.println ("Closing pattern " + name + " " + target.name + " -> ...");
		RandomVariable<ParticlePair> rv = (RandomVariable<ParticlePair>) keyval.getValue();
		if (rv != null)
		    rv.close(new ParticlePair (this, target, defaultVerb));
	    }
	}
    }

    // normalizedTotalTransformRate
    public final double normalizedTotalTransformRate() { return Math.min (totalTransformRate, 1); }

    // method to test if a Particle is active (i.e. has any transformation rules) in a given direction
    public final boolean isActive(int dir) { return transformRuleMatch[dir].length > 0; }

    // helper to sample a new direction
    public final int sampleDir(Random rnd) {
	double p = rnd.nextDouble() * totalTransformRate;
	int d = transformRate.length - 1;
	for (; d >= 0 && p > transformRate[d]; --d)
	    p -= transformRate[d];
	return d;
    }

    // method to test if a Particle has energy rules
    public final boolean hasEnergy() { return energyRuleMatch.length > 0; }

    // helper to sample a new (source,target) pair
    // returns null if no rule found
    public final ParticlePair samplePair (int dir, Particle oldTarget, Random rnd, Board board) {
	RandomVariable<ParticlePair> rv = null;
	if (pattern.get(dir).containsKey(oldTarget)) {
	    rv = pattern.get(dir).get (oldTarget);
	} else {
	    // if no RV, look for rule generator(s) that match this neighbor, and use them to create a set of rules
	    if (patternSet != null) {
		rv = compileTransformRules(oldTarget,dir);
		pattern.get(dir).put (oldTarget, rv);
	    }
	}
	// have we got an RV?
	if (rv != null)
	    return rv.sample(rnd);
	// no RV; return null
	return null;
    }

    // helper to calculate pairwise interaction energy with another Particle (one-way)
    public final double pairEnergy (Particle p, int dir) {
	double E = 0;
	if (hasEnergy()) {
	    if (energy.get(dir).containsKey(p)) {
		E = energy.get(dir).get(p).doubleValue();
	    } else {
		// look for rule generator(s) that match this neighbor, and use them to calculate energy
		if (patternSet != null) {
		    E = compileEnergyRules(p,dir);
		    energy.get(dir).put (p, new Double(E));
		}
	    }
	}
	// return
	return E;
    }

    // helper to calculate symmetric form of pairEnergy
    public final double symmetricPairEnergy (Particle p,int dir) {
	int ns = board.neighborhoodSize();
	int opposite = (dir + ns/2) % ns;
	return pairEnergy(p,dir) + p.pairEnergy(this,opposite);
    }

    // method to compile transformation rules for a new target Particle
    RandomVariable<ParticlePair> compileTransformRules (Particle target, int dir) {
	//	System.err.println ("Compiling transformation rules for " + name + " " + target.name);
	RandomVariable<ParticlePair> rv = new RandomVariable<ParticlePair>();
	for (int n = 0; n < transformRuleMatch[dir].length; ++n) {

	    TransformRuleMatch rm = transformRuleMatch[dir][n];

	    if (rm.bindSource(name) && rm.bindTarget(target.name)) {

		String cName = rm.C();
		String dName = rm.D();
		String verb = rm.V();
		double prob = rm.P() / transformRate[dir];

		// we now have everything we need from rm (cName, dName, verb and prob)
		// therefore, unbind it so we can call getOrCreateParticle (which will re-bind it)
		rm.unbindSourceAndTarget();

		Particle newSource = patternSet.getOrCreateParticle(cName,board);
		Particle newTarget = patternSet.getOrCreateParticle(dName,board);

		if (newSource == null || newTarget == null) {
		    System.err.println ("Null outcome of rule '" + rm.pattern + "': "
					+ name + " " + target.name + " -> " + cName + " " + dName);
		} else {
		    ParticlePair pp = new ParticlePair (newSource, newTarget, verb);
		    rv.add (pp, prob);
		}
	    }

	    rm.unbindSourceAndTarget();

	}
	rv.close (new ParticlePair (this, target, defaultVerb));
	return rv;
    }

    // method to compile energy rules for a new target Particle
    double compileEnergyRules (Particle target, int dir) {
	//	System.err.println ("Compiling energy rules for " + name + " " + target.name);
	double E = 0;
	for (int n = 0; n < energyRuleMatch[dir].length; ++n) {
	    EnergyRuleMatch rm = energyRuleMatch[dir][n];
	    if (rm.matches(name,target.name))
		E += rm.E();
	}
	//	System.err.println ("Pair   " + name + " " + target.name + "   total energy is " + E);
	return E;
    }

    // helper to flush caches
    protected void flushCaches() {
	for (int d = 0; d < pattern.size(); ++d) {
	    pattern.get(d).clear();
	    energy.get(d).clear();
	}
    }

    // helpers to count number of compiled transformation & energy rules
    protected int transformationRules() {
	int r = 0;
	for (int d = 0; d < pattern.size(); ++d)
	    r += pattern.get(d).size();
	return r;
    }

    protected int energyRules() {
	return energy.size();
    }

    // helper to count number of compiled transformation rule outcomes
    protected int outcomes() {
	int o = 0;
	for (int d = 0; d < pattern.size(); ++d)
	    for (Iterator<RandomVariable<ParticlePair>> iter = pattern.get(d).values().iterator(); iter.hasNext(); )
		o += iter.next().size();
	return o;
    }

    // equals method
    public final boolean equals(Particle p) {
	return name.equals(p.name);
    }

    // hashcode method
    public int hashCode() {
	return name.hashCode();
    }

    // finalize method
    // uncomment and add code to test for garbage collection
    /*
    protected final void finalize() throws Throwable
    {
        //	System.err.println("Deleting " + name);

	// uncomment if this class is not a direct subclass of Object
	//	super.finalize();
    }
    */
}
