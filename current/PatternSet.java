import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Patterns are to be transmitted in a "Particle definition" packet with the following structure:
// ParticlePatterns (one per line, format "NOUN NameRegex R G B", describing appearances of Particles to which this definition packet applies)
// RulePatterns (one per line, format "VERB A B C D P V" - see RuleMatch.java for semantics)

public class PatternSet {
    // data
    private Board board = null;
    private Vector<EnergyRulePattern> energyRulePattern = new Vector<EnergyRulePattern>();
    private Vector<TransformRulePattern> transformRulePattern = new Vector<TransformRulePattern>();
    private Vector<ParticlePattern> particlePattern = new Vector<ParticlePattern>();

    // direction-bound transformation rules
    // outer vector is indexed by neighbor direction, inner vector is the set of partially-bound rules for that direction
    private ArrayList<Vector<TransformRuleMatch>> transformRuleMatch = null;  // generators for production rules

    // direction-bound energy rules
    private ArrayList<Vector<EnergyRuleMatch>> energyRuleMatch = null;  // generators for interaction energies

    // constructor
    PatternSet (Board board) {
	this.board = board;
	int ns = board.neighborhoodSize();
	transformRuleMatch = new ArrayList<Vector<TransformRuleMatch>>(ns);
	energyRuleMatch = new ArrayList<Vector<EnergyRuleMatch>>(ns);
	for (int d = 0; d < ns; ++d) {
	    transformRuleMatch.add (new Vector<TransformRuleMatch>());
	    energyRuleMatch.add (new Vector<EnergyRuleMatch>());
	}
    }

    // method to lay down a template for a Particle
    void addParticlePattern (String patternString, Color col) {
	particlePattern.add (new ParticlePattern(patternString,col));
    }

    // method to lay down a template for a transformation rule
    void addTransformRule (String patternString) {
	TransformRulePattern p = TransformRulePattern.fromString(patternString);
	transformRulePattern.add (p);
	for (int d = 0; d < board.neighborhoodSize(); ++d)
	    transformRuleMatch.get(d).add (new TransformRuleMatch(p,board,d));
    }

    // method to lay down a template for an energy rule
    void addEnergyRule (String patternString) {
	EnergyRulePattern p = EnergyRulePattern.fromString(patternString);
	energyRulePattern.add (p);
	for (int d = 0; d < board.neighborhoodSize(); ++d)
	    energyRuleMatch.get(d).add (new EnergyRuleMatch(p,board,d));
    }

    // method to get a Particle from the Board object or create and add one
    Particle getOrCreateParticle (String particleName, Board board) {
	// look for existing particle
	Particle p = board.getParticleByName (particleName);
	// if no such particle, look for a pattern that matches this particle
	for (int n = 0; p == null && n < particlePattern.size(); ++n) {
	    ParticlePattern pp = particlePattern.get(n);
	    p = pp.makeParticle(particleName,board,this);  // returns null if fails to match
	}
	// if still no such particle, create a bright white default with this PatternSet 
	if (p == null)
	    p = new Particle (particleName, Color.white, board, this);
	// return
	return p;
    }

    // method to compile transformation rules for a new target Particle
    RandomVariable<ParticlePair> compileTransformRules (Particle source, Particle target, int dir) {
	//	System.err.println ("Compiling transformation rules for " + source.name + " " + target.name);
	RandomVariable<ParticlePair> rv = new RandomVariable<ParticlePair>();
	for (int n = 0; n < source.transformRuleMatch[dir].length; ++n) {

	    TransformRuleMatch rm = source.transformRuleMatch[dir][n];

	    if (rm.bindSource(source.name) && rm.bindTarget(target.name)) {

		String cName = rm.C();
		String dName = rm.D();
		String verb = rm.V();
		double prob = rm.P();

		// the following calls may corrupt rm, but we now have everything we need from rm (cName, dName, verb and prob)
		Particle newSource = getOrCreateParticle(cName,board);
		Particle newTarget = getOrCreateParticle(dName,board);

		if (newSource == null || newTarget == null) {
		    System.err.println ("Null outcome of rule '" + rm.pattern + "': "
					+ source.name + " " + target.name + " -> " + cName + " " + dName);
		} else {
		    ParticlePair pp = new ParticlePair (newSource, newTarget, verb);
		    rv.add (pp, prob);
		}
	    }

	    rm.unbindSourceAndTarget();

	}
	rv.close (new ParticlePair (source, target, source.defaultVerb));
	return rv;
    }

    // helper to get a set of transformation rules for a given Particle/direction
    protected TransformRuleMatch[] getSourceTransformRules (String particleName, int dir) {
	//	System.err.println ("Trying to match particle " + particleName + " to transformation rule generators");
	Vector<TransformRuleMatch> v = new Vector<TransformRuleMatch>();
	Vector<TransformRuleMatch> w = transformRuleMatch.get(dir);
	for (int n = 0; n < w.size(); ++n) {
	    //	    System.err.println ("Trying to match particle " + particleName + " to transformation rule " + (RulePattern) transformRulePattern.get(n));
	    TransformRuleMatch rm = w.get(n);
	    if (rm.matches(particleName))
		v.add (rm);
	}
	TransformRuleMatch[] rm = new TransformRuleMatch[v.size()];
	for (int n = 0; n < v.size(); ++n)
	    rm[n] = v.get(n);
	return rm;
    }

    // method to compile energy rules for a new target Particle
    double compileEnergyRules (Particle source, Particle target, int dir) {
	//	System.err.println ("Compiling energy rules for " + source.name + " " + target.name);
	double E = 0;
	for (int n = 0; n < source.energyRuleMatch[dir].length; ++n) {
	    EnergyRuleMatch rm = source.energyRuleMatch[dir][n];
	    if (rm.matches(source.name,target.name))
		E += rm.E();
	}
	//	System.err.println ("Pair   " + source.name + " " + target.name + "   total energy is " + E);
	return E;
    }

    // helper to get a set of energy rules for a given Particle/direction
    protected EnergyRuleMatch[] getSourceEnergyRules (String particleName, int dir) {
	//	System.err.println ("Trying to match particle " + particleName + " to energy rule generators");
	Vector<EnergyRuleMatch> v = new Vector<EnergyRuleMatch>();
	for (int n = 0; n < energyRuleMatch.get(dir).size(); ++n) {
	    //	    System.err.println ("Trying to match particle " + particleName + " to energy rule " + (RulePattern) energyRulePattern.get(n));
	    EnergyRuleMatch rm = energyRuleMatch.get(dir).get(n);
	    if (rm.matches(particleName))
		v.add (rm);
	}
	EnergyRuleMatch[] rm = new EnergyRuleMatch[v.size()];
	for (int n = 0; n < v.size(); ++n)
	    rm[n] = v.get(n);
	return rm;
    }

    // i/o
    void toStream (OutputStream out) {
	PrintStream print = new PrintStream(out);
	for (Enumeration e = particlePattern.elements(); e.hasMoreElements() ;)
	    print.println ("NOUN " + (e.nextElement()).toString());
	for (Enumeration e = transformRulePattern.elements(); e.hasMoreElements() ;)
	    print.println ("VERB " + (e.nextElement()).toString());
	for (Enumeration e = energyRulePattern.elements(); e.hasMoreElements() ;)
	    print.println ("ENERGY " + (e.nextElement()).toString());
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

    static PatternSet fromStream (InputStream in, Board board) {
	PatternSet ps = new PatternSet(board);
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	Pattern nounRegex = Pattern.compile("NOUN (.*)");
	Pattern verbRegex = Pattern.compile("VERB (.*)");
	Pattern energyRegex = Pattern.compile("ENERGY (.*)");
	Pattern endRegex = Pattern.compile("END.*");
	Pattern commentRegex = Pattern.compile(" *#.*");
	Pattern nonWhitespaceRegex = Pattern.compile("\\S");
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		Matcher m = null;
		if (commentRegex.matcher(s).matches()) {
		    continue;
		} else if ((m = nounRegex.matcher(s)).matches()) {
		    ps.particlePattern.add (new ParticlePattern(m.group(1)));
		} else if ((m = verbRegex.matcher(s)).matches()) {
		    ps.addTransformRule(m.group(1));
		} else if ((m = energyRegex.matcher(s)).matches()) {
		    ps.addEnergyRule(m.group(1));
		} else if (endRegex.matcher(s).matches()) {
		    break;
		} else if (nonWhitespaceRegex.matcher(s).matches()) {
		    System.err.println("Ignoring line: " + s);
		}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return ps;
    }

    static PatternSet fromFile (String filename, Board board) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis,board);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
