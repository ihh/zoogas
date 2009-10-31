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
    void addParticlePattern (RuleSyntax s) {
	particlePattern.add (new ParticlePattern(s.getValue("n"),s.getValue("c")));
    }

    // method to lay down a template for a transformation rule
    void addTransformRule (RuleSyntax s) {
	TransformRulePattern p = new TransformRulePattern(s.getValue("s"),s.getValue("t"),s.getValue("S"),s.getValue("T"),
							  Double.parseDouble(s.getValue("p")),s.getValue("v"));
	transformRulePattern.add (p);
	for (int d = 0; d < board.neighborhoodSize(); ++d)
	    transformRuleMatch.get(d).add (new TransformRuleMatch(p,board,d));
    }

    // method to lay down a template for an energy rule
    void addEnergyRule (RuleSyntax s) {
	EnergyRulePattern p = new EnergyRulePattern(s.getValue("s"),s.getValue("t"),Double.parseDouble(s.getValue("e")));
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

    // i/o patterns and syntax parsers
    static Pattern endRegex = Pattern.compile("END.*");
    static Pattern commentRegex = Pattern.compile(" *#.*");
    static Pattern nonWhitespaceRegex = Pattern.compile("\\S");
    static RuleSyntax nounSyntax = new RuleSyntax("NOUN n= c=");
    static RuleSyntax verbSyntax = new RuleSyntax("VERB s= t=.* S=$S T=$T p=1 v=_");
    static RuleSyntax bondSyntax = new RuleSyntax("BOND s= t= e=");

    // i/o methods
    static PatternSet fromStream (InputStream in, Board board) {
	PatternSet ps = new PatternSet(board);
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		Matcher m = null;
		if (commentRegex.matcher(s).matches()) {
		    continue;
		} else if (nounSyntax.matches(s)) {
		    ps.addParticlePattern(nounSyntax);
		} else if (verbSyntax.matches(s)) {
		    ps.addTransformRule(verbSyntax);
		} else if (bondSyntax.matches(s)) {
		    ps.addEnergyRule(bondSyntax);
		} else if (endRegex.matcher(s).matches()) {
		    break;
		} else if (nonWhitespaceRegex.matcher(s).matches()) {
		    System.err.println("PatternSet: Ignoring unrecognized line: " + s);
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
