import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Here is a hypothetical syntax for regexp-based pattern generators:
//  A B C D P V
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)

// The following "special variables" will be expanded in {A,B,C,D} as appropriate:
//  $1,$2,$3... => groups in A and B regexps (c.f. Perl)
//  $S,$T => full names for old source,target states
//  $F,$L,$R,$B => directions relative to neighbor direction ($F=forward, $L=left, $R=right, $B=back)
//  $-1 => numerically one less than $1, interpreted as an alphadecimal number (i.e. base 36)
//  $--1 => numerically two less than $1 (and $---1 is three less, etc.); negative numbers evaluate to the empty string
//  $+1 => numerically one greater than $1
// Similarly for $-2, $++3, etc.

// A matching rule should overwrite any previously matched rules, allowing us to create exceptions
// (e.g. "destroy any particle; DO NOT destroy basalt").
// UPDATE: this is now implicit in RandomVariable.add()

// Patterns such as this are to be transmitted in a "Particle definition" packet with the following structure:
// NAMES & COLORS (one per line, format "NAME R G B", describing appearances of Particles to which this definition packet applies)
// RULES (one per line, format "A B C D P V")

// The class (ParticleDefinition? ParticleTemplate?) encapsulating these definition data
// is a regexp-based generator for Particles and their production rules.


// Particle class, encapsulating the behavior, appearance & summary statistics of a given CA state
public class Particle {
    // appearance
    public String name = null;  // noun uniquely identifying this Particle (no whitespace)
    public Color color = null;

    // behavior
    protected IdentityHashMap[] pattern = null;  // production rules, indexed by neighbor direction & Particle

    // internals
    protected int count = 0;  // how many of this type on the board

    // static variables
    public static String
	visibleSeparatorChar = "/",
	visibleSpaceChar = "_";

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
