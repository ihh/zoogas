import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Patterns are to be transmitted in a "Particle definition" packet with the following structure:
// ParticlePatterns (one per line, format "NAME R G B", describing appearances of Particles to which this definition packet applies)
// RulePatterns (one per line, format "A B C D P V")

public class PatternSet {
    // data
    private Vector rulePattern = new Vector();
    private Vector particlePattern = new Vector();

    // method to lay down a template for a Particle
    void addParticlePattern (String patternString, Color col) {
	particlePattern.add (new ParticlePattern(patternString,col));
    }

    // method to lay down a template for a rule
    void addRulePattern (String patternString) {
	rulePattern.add (new RulePattern(patternString));
    }

    // method to get a Particle from the Board object or create and add one
    Particle getOrCreateParticle (String particleName, Board board) {
	// look for existing particle
	Particle p = board.getParticleByName (particleName);
	// if no such particle, look for a pattern that matches this particle
	for (int n = 0; p == null && n < particlePattern.size(); ++n) {
	    ParticlePattern pp = (ParticlePattern) particlePattern.get(n);
	    p = pp.makeParticle(particleName,board,this);  // returns null if fails to match
	}
	return p;
    }

    // method to compile new target rules for a Particle
    RandomVariable compileTargetRules (int dir, Particle source, Particle target, Board board) {
	RandomVariable rv = new RandomVariable();
	if (source.patternTemplate[dir] == null)
	    source.patternTemplate[dir] = getSourceRules (source.name, board, dir);
	for (int n = 0; n < source.patternTemplate[dir].length; ++n) {
	    RuleMatch rm = source.patternTemplate[dir][n];
	    //		    System.err.println ("Particle " + source.name + ": trying to match " + source.patternTemplate[dir][n]);
	    if (rm.bindTarget(target.name)) {
		Particle
		    newSource = board.getOrCreateParticle (rm.C()),
		    newTarget = board.getOrCreateParticle (rm.D());
		if (newSource == null || newTarget == null) {
		    System.err.println ("Null outcome of rule '" + rm.pattern + "': "
					+ source.name + " " + target.name + " -> "
					+ (newSource == null ? ("[null: " + rm.C() + "]") : newSource.name) + " "
					+ (newTarget == null ? ("[null: " + rm.D() + "]") : newTarget.name));
		} else {
		    ParticlePair pp = new ParticlePair (newSource, newTarget);
		    rv.add (pp, rm.P());
		}
	    }
	    rm.unbindTarget();
	}
	rv.close (new ParticlePair (source, target));
	source.pattern[dir].put (target, rv);
	return rv;
    }

    // helper to get a set of rules
    protected RuleMatch[] getSourceRules (String particleName, Board board, int dir) {
	//	System.err.println ("Trying to match particle " + particleName + " to rule generators");
	Vector v = new Vector();
	for (int n = 0; n < rulePattern.size(); ++n) {
	    //	    System.err.println ("Trying to match particle " + particleName + " to rule " + (RulePattern) rulePattern.get(n));
	    RuleMatch rm = new RuleMatch ((RulePattern) rulePattern.get(n), board, dir, particleName);
	    if (rm.matches())
		v.add (rm);
	}
	Object[] a = v.toArray();
	RuleMatch[] rm = new RuleMatch[a.length];
	for (int n = 0; n < a.length; ++n)
	    rm[n] = (RuleMatch) a[n];
	return rm;
    }

    // i/o
    void toStream (OutputStream out) {
	PrintStream print = new PrintStream(out);
	print.println ("PARTICLES");
	for (Enumeration e = particlePattern.elements(); e.hasMoreElements() ;)
	    print.println (((ParticlePattern) e.nextElement()).toString());
	print.println ("RULES");
	for (Enumeration e = rulePattern.elements(); e.hasMoreElements() ;)
	    print.println (((RulePattern) e.nextElement()).toString());
	print.println ("END");
    }


    void toFile(String filename) {
	try {
	    FileOutputStream fos = new FileOutputStream(filename);
	    toStream (fos);
	    fos.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    private enum State { unknown, inParticles, inRules, end }
    static PatternSet fromStream (InputStream in) {
	PatternSet ps = new PatternSet();
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	State state = State.unknown;
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		if (s.equals("PARTICLES")) {
		    state = State.inParticles;
		} else if (s.equals("RULES")) {
		    state = State.inRules;
		} else if (s.equals("END")) {
		    state = State.end;
		    break;
		} else if (state == State.inParticles) {
		    ps.particlePattern.add (new ParticlePattern(s));
		} else if (state == State.inRules) {
		    ps.rulePattern.add (new RulePattern(s));
		} else
		    System.err.println("Ignoring line: " + s);
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return ps;
    }

    static PatternSet fromFile (String filename) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
