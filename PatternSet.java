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

    // method to lay down a template for a rule
    void addRulePattern (String patternString) {
	rulePattern.add (new RulePattern(patternString));
    }

    // method to get a Particle from the ZooGas object or create and add one
    Particle getOrCreateParticle (String particleName, ZooGas gas) {
	// look for existing particle
	Particle p = gas.getParticleByName (particleName);
	// if no such particle, look for a pattern that matches this particle
	if (p == null) {
	    for (int n = 0; n < particlePattern.size(); ++n) {
		ParticlePattern pp = (ParticlePattern) particlePattern.get(n);
		Matcher m = pp.namePattern.matcher(particleName);
		if (m.matches()) {
		    p = new Particle (particleName, pp.color, gas, this);
		    break;
		}
	    }
	}
	return p;
    }

    protected RuleMatch[] getSourceRules (String particleName, ZooGas gas, int dir) {
	//	System.err.println ("Trying to match particle " + particleName + " to rule generators");
	Vector v = new Vector();
	for (int n = 0; n < rulePattern.size(); ++n) {
	    //	    System.err.println ("Trying to match particle " + particleName + " to rule " + (RulePattern) rulePattern.get(n));
	    RuleMatch rm = new RuleMatch ((RulePattern) rulePattern.get(n), gas, dir, particleName);
	    if (rm.matches())
		v.add (rm);
	}
	Object[] a = v.toArray();
	RuleMatch[] rm = new RuleMatch[a.length];
	for (int n = 0; n < a.length; ++n)
	    rm[n] = (RuleMatch) a[n];
	return rm;
    }
}
