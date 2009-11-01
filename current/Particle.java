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
    protected ArrayList<HashMap<String,RandomVariable<UpdateEvent>>> transform = null;  // production rules; array is indexed by neighbor direction, Map is indexed by Particle
    protected TransformRuleMatch[][] transformRuleMatch = null;  // generators for production rules; outer array is indexed by neighbor direction, inner array is the set of partially-bound rules for that direction
    protected double[] transformRate = null;  // sum of transformation regex rates, indexed by direction
    protected double totalTransformRate = 0;  // sum of transformation regex rates in all directions

    // energy rules
    protected ArrayList<HashMap<String,Double>> energy = null;  // interaction energies; Map is indexed by Particle
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
	transform = new ArrayList<HashMap<String,RandomVariable<UpdateEvent>>>(N);
	transformRuleMatch = new TransformRuleMatch[N][];
	transformRate = new double[N];
	energy = new ArrayList<HashMap<String,Double>>();
	energyRuleMatch = new EnergyRuleMatch[N][];
	
	for (int n = 0; n < N; ++n) {
	    transform.add(new HashMap<String,RandomVariable<UpdateEvent>>());
	    transformRuleMatch[n] = patternSet.getSourceTransformRules(name,n);

	    transformRate[n] = 0;
	    for (int i = 0; i < transformRuleMatch[n].length; ++i)
		transformRate[n] += transformRuleMatch[n][i].P();
	    totalTransformRate += transformRate[n];

	    energy.add(new HashMap<String,Double>());
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

    // normalizedTotalTransformRate
    public final double normalizedTotalTransformRate() { return Math.min (totalTransformRate / transformRuleMatch.length, 1); }

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
    public final UpdateEvent samplePair (int dir, Particle oldTarget, Random rnd, Point sourceCoords, Point targetCoords, Board board) {
	String desc = board.pairNeighborhoodDescription(sourceCoords,targetCoords);
	RandomVariable<UpdateEvent> rv = null;
	if (transform.get(dir).containsKey(desc)) {
	    rv = transform.get(dir).get(desc);
	} else {
	    // if no RV, look for rule generator(s) that match this neighbor, and use them to create a set of rules
	    if (patternSet != null) {
		rv = compileTransformRules(oldTarget,dir,sourceCoords,targetCoords);
		transform.get(dir).put (desc, rv);
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
	    if (energy.get(dir).containsKey(p.name)) {
		E = energy.get(dir).get(p.name).doubleValue();
	    } else {
		// look for rule generator(s) that match this neighbor, and use them to calculate energy
		if (patternSet != null) {
		    E = compileEnergyRules(p,dir);
		    energy.get(dir).put (p.name, new Double(E));
		}
	    }
	}
	// return
	return E;
    }

    // helper to calculate symmetric form of pairEnergy
    public final double symmetricPairEnergy (Particle p,int dir) {
	int opposite = board.reverseDir(dir);
	return pairEnergy(p,dir) + p.pairEnergy(this,opposite);
    }

    // method to compile transformation rules for a new target Particle
    RandomVariable<UpdateEvent> compileTransformRules (Particle target, int dir, Point sourceCoords, Point targetCoords) {
	//	System.err.println ("Compiling transformation rules for " + name + " " + target.name);
	RandomVariable<UpdateEvent> rv = new RandomVariable<UpdateEvent>();
	for (int n = 0; n < transformRuleMatch[dir].length; ++n) {

	    TransformRuleMatch rm = transformRuleMatch[dir][n];

	    if (rm.bindSource(name) && rm.bindTarget(target.name) && rm.bindBonds(sourceCoords,targetCoords)) {

		String cName = rm.C();
		String dName = rm.D();
		String verb = rm.V();
		double prob = rm.P() / transformRate[dir];

		HashMap<String,Integer> si = rm.sIncoming(), so = rm.sOutgoing(), ti = rm.tIncoming(), to = rm.tOutgoing();

		// we now have everything we need from rm
		// therefore, unbind it so we can call getOrCreateParticle (which may re-bind and therefore corrupt it)
		rm.unbindSourceAndTarget();

		Particle newSource = patternSet.getOrCreateParticle(cName,board);
		Particle newTarget = patternSet.getOrCreateParticle(dName,board);

		if (newSource == null || newTarget == null) {
		    System.err.println ("Null outcome of rule '" + rm.pattern + "': "
					+ name + " " + target.name + " -> " + cName + " " + dName);
		} else {
		    UpdateEvent pp = new UpdateEvent (newSource, newTarget, verb);
		    pp.sIncoming = si;
		    pp.sOutgoing = si;
		    pp.tIncoming = ti;
		    pp.tOutgoing = ti;
		    rv.add (pp, prob);
		}
	    }

	    rm.unbindSourceAndTarget();

	}
	rv.close (new UpdateEvent (this, target, defaultVerb));
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
	for (int d = 0; d < transform.size(); ++d) {
	    transform.get(d).clear();
	    energy.get(d).clear();
	}
    }

    // helpers to count number of compiled transformation & energy rules
    protected int transformationRules() {
	int r = 0;
	for (int d = 0; d < transform.size(); ++d)
	    r += transform.get(d).size();
	return r;
    }

    protected int energyRules() {
	return energy.size();
    }

    // helper to count number of compiled transformation rule outcomes
    protected int outcomes() {
	int o = 0;
	for (int d = 0; d < transform.size(); ++d)
	    for (Iterator<RandomVariable<UpdateEvent>> iter = transform.get(d).values().iterator(); iter.hasNext(); )
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
