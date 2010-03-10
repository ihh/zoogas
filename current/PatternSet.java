import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.awt.Color;
import java.text.*;
import java.net.*;
import java.io.*;

/**
 * A set that contains rules parsed into definitions of pairwise interactions
 */
public class PatternSet extends RuleSet{
    public PatternSet() {
        super();
    }
    
    PatternSet (Topology topology) {
        super();

        this.topology = topology;
        int ns = topology.neighborhoodSize();
        transformRuleMatch = new ArrayList<Vector<TransformRuleMatch>>(ns);
        for (int d = 0; d < ns; ++d)
            transformRuleMatch.add (new Vector<TransformRuleMatch>());
    }
    
    // data
    private Topology topology = null;
    private Vector<EnergyRulePattern> energyRulePattern = new Vector<EnergyRulePattern>();
    private Vector<TransformRulePattern> transformRulePattern = new Vector<TransformRulePattern>();
    private Vector<ParticlePattern> particlePattern = new Vector<ParticlePattern>();

    // direction-bound transformation rules
    // outer vector is indexed by neighbor direction, inner vector is the set of partially-bound rules for that direction
    private ArrayList<Vector<TransformRuleMatch>> transformRuleMatch = null;  // generators for production rules

    // energy rules
    private HashMap<String,Vector<EnergyRuleMatch>> energyRuleMatch = new HashMap<String,Vector<EnergyRuleMatch>>();

    // getPrefix
    static Pattern prefixPattern = Pattern.compile("([A-Za-z0-9_]+).*");
    String getPrefix(String noun) {
	Matcher prefixMatcher = prefixPattern.matcher(noun);
	if (prefixMatcher.matches()) {
	    return prefixMatcher.group(1);
	}
	return noun;
    }

    // method to lay down a template for a Particle
    void addParticlePattern (RuleSyntax s) {
        try {
	    String noun = s.getValue("n");
            particlePattern.add(new ParticlePattern(getPrefix(noun), noun,s.getValue("c"),s.getValue("e")));
        }
        catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    // method to lay down a template for a transformation rule
    void addTransformRule (RuleSyntax s) {
	String subject = s.getValue("s");
	TransformRulePattern p = new TransformRulePattern(getPrefix(subject), s.getValue("d"),subject,s.getValue("t"),s.getValue("S"),s.getValue("T"),
							  Double.parseDouble(s.getValue("p")),s.getValue("v"));
	if (s.hasValue("b"))
	    p.addOptionalLhsBonds(s.getValue("b").split(" "));

	if (s.hasValue("c"))
	    p.addRequiredLhsBonds(s.getValue("c").split(" "));

	if (s.hasValue("x"))
	    p.addExcludedLhsBonds(s.getValue("x").split(" "));

	if (s.hasValue("B"))
	    p.addRhsBonds(s.getValue("B").split(" "));

	if (s.hasValue("k")) {
	    String[] k = s.getValue("k").split(" ");
	    p.addOptionalLhsBonds(k);
	    p.addRhsBonds(k);
	}

	if (s.hasValue("K")) {
	    String[] k = s.getValue("K").split(" ");
	    p.addRequiredLhsBonds(k);
	    p.addRhsBonds(k);
	}

	transformRulePattern.add (p);
	for (int d = 0; d < topology.neighborhoodSize(); ++d)
	    transformRuleMatch.get(d).add (new TransformRuleMatch(p, topology, d));
    }

    // method to lay down a template for an energy rule
    void addEnergyRule (RuleSyntax s) {
	String source = s.getValue("s");
	EnergyRulePattern p = new EnergyRulePattern(getPrefix(source), source,s.getValue("t"),s.getValue("n"),Double.parseDouble(s.getValue("e")),
						    Double.parseDouble(s.getValue("l")),Double.parseDouble(s.getValue("L")),Double.parseDouble(s.getValue("m")),
						    Double.parseDouble(s.getValue("a")),Double.parseDouble(s.getValue("A")),Double.parseDouble(s.getValue("b")));
	energyRulePattern.add(p);
	if (!energyRuleMatch.containsKey(p.bondName))
	    energyRuleMatch.put(p.bondName,new Vector<EnergyRuleMatch>());
	energyRuleMatch.get(p.bondName).add(new EnergyRuleMatch(p,topology));
    }

    // method to get a Particle from the Board object or create and add one
    Particle getOrCreateParticle(String particleName, Board board) {
	// look for existing particle
	Particle p = board.getParticleByName (particleName);

	// if no such particle, look for a pattern that matches this particle
	for (int n = 0; p == null && n < particlePattern.size(); ++n) {
	    ParticlePattern pp = particlePattern.get(n);
	    p = pp.makeParticle(particleName,board,this);  // returns null if fails to match
	}

	// if still no such particle, create a bright white default with this PatternSet 
        if (p == null) {
	    p = new Particle(particleName, "*/", Color.white, 0, board, this);
        }
	// return
	return p;
    }

    // helper to get a set of transformation rules for a given Particle/direction
    protected TransformRuleMatch[] getSourceTransformRules (String particleName, int dir) {
	Vector<TransformRuleMatch> v = new Vector<TransformRuleMatch>();
	Vector<TransformRuleMatch> w = transformRuleMatch.get(dir);
	for (int n = 0; n < w.size(); ++n) {
	    TransformRuleMatch rm = w.get(n);
	    if (rm.matches(particleName))
		v.add (rm);
	}
	return (TransformRuleMatch[])v.toArray(new TransformRuleMatch[v.size()]);
    }

    // helper to get bond energy for a given particle pair
    public double getEnergy(String sourceName,String targetName,String bondName,Point sourceToTarget,Point prevToSource) {
	double E = 0;
	Vector<EnergyRuleMatch> rmVec = energyRuleMatch.get(bondName);
	if (rmVec != null) {
	    for (int n = 0; n < rmVec.size(); ++n)
		if (rmVec.get(n).matches(sourceName,targetName,sourceToTarget,prevToSource))
		    E += rmVec.get(n).E();
	}
	return E;
    }

    // i/o patterns and syntax parsers
    final static Pattern endRegex = Pattern.compile("END.*");
    static RuleSyntax nounSyntax = new RuleSyntax("NOUN n= c=ffffff e=0");
    static RuleSyntax verbSyntax = new RuleSyntax("VERB s= t=.* S=$S T=$T d= p=1 v=_ b* c* x* B* k* K*");
    static RuleSyntax bondSyntax = new RuleSyntax("BOND n= e= s=.* t=.* l=1 L=1.5 m=1 a=-1 A=1 b=1");

    // i/o methods
    static PatternSet fromStream (InputStream in, Topology topology) {
	PatternSet ps = new PatternSet(topology);
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		if (!isRule(s)) {
		    continue;
		}
                
                if(ps.add(s)) {
                    ps.byteSize += 1 + s.getBytes().length;

                    if (nounSyntax.matches(s)) {
                        ps.addParticlePattern(nounSyntax);
                    } else if (verbSyntax.matches(s)) {
                        ps.addTransformRule(verbSyntax);
                    } else if (bondSyntax.matches(s)) {
                        ps.addEnergyRule(bondSyntax);
                    } else if (endRegex.matcher(s).matches()) {
                        break;
                    } else {
                        System.err.println("PatternSet: Ignoring unrecognized line: " + s);
                    }
                }
            }

	    buff.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return ps;
    }

    static PatternSet fromFile (String filename, Topology topology) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis, topology);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
