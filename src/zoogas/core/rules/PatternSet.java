package zoogas.core.rules;

import java.util.*;
import java.util.regex.*;
import java.awt.Color;
import java.io.*;

import zoogas.core.Board;
import zoogas.core.Particle;
import zoogas.core.Point;
import zoogas.core.topology.Topology;

import zoogas.gui.Icon;

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
	    String noun = s.getXmlTagValue("Name");
            ParticlePattern pp = new ParticlePattern(getPrefix(noun), noun, s.getXmlTagValue("Color"), s.getXmlTagValue("Energy"));
	    // get optional icon
	    if (s.hasXmlTagValue("Icon"))
		pp.icon = new Icon(s.getXmlTagValue("Icon"));
	    // store
            particlePattern.add(pp);
        }
        catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    // method to lay down a template for a transformation rule
    void addTransformRule (RuleSyntax s) {
	// subject
	String subject = s.getXmlTagValue("OldSource");

	// create the basic object
	TransformRulePattern p = new TransformRulePattern(getPrefix(subject),
							  s.getXmlTagValue("Dir"),
							  subject,
							  s.getXmlTagValue("OldTarget"),
							  s.getXmlTagValue("NewSource"),
							  s.getXmlTagValue("NewTarget"),
							  Double.parseDouble(s.getXmlTagValue("Prob")),
							  s.getXmlTagValue("Say"));

	// get optional bond attributes
	if (s.hasXmlTagValue("OptionalBond"))
	    p.addOptionalLhsBonds(s.getXmlTagValue("OptionalBond").split(" "));

	if (s.hasXmlTagValue("DeleteBond"))
	    p.addRequiredLhsBonds(s.getXmlTagValue("DeleteBond").split(" "));

	if (s.hasXmlTagValue("ExcludeBond"))
	    p.addExcludedLhsBonds(s.getXmlTagValue("ExcludeBond").split(" "));

	if (s.hasXmlTagValue("NewBond"))
	    p.addRhsBonds(s.getXmlTagValue("NewBond").split(" "));

	if (s.hasXmlTagValue("KeepOptionalBond")) {
	    String[] k = s.getXmlTagValue("KeepOptionalBond").split(" ");
	    p.addOptionalLhsBonds(k);
	    p.addRhsBonds(k);
	}

	if (s.hasXmlTagValue("KeepRequiredBond")) {
	    String[] k = s.getXmlTagValue("KeepRequiredBond").split(" ");
	    p.addRequiredLhsBonds(k);
	    p.addRhsBonds(k);
	}

	// add the pattern, and add pre-initialized matches for each neighborhood direction
	transformRulePattern.add (p);
	for (int d = 0; d < topology.neighborhoodSize(); ++d)
	    transformRuleMatch.get(d).add (new TransformRuleMatch(p, topology, d));
    }

    // method to lay down a template for an energy rule
    void addEnergyRule (RuleSyntax s) {
	String source = s.getXmlTagValue("Source");
	EnergyRulePattern p = new EnergyRulePattern(getPrefix(source),
						    source,
						    s.getXmlTagValue("Target"),
						    s.getXmlTagValue("Name"),
						    Double.parseDouble(s.getXmlTagValue("Energy")),
						    Double.parseDouble(s.getXmlTagValue("MinLen")),
						    Double.parseDouble(s.getXmlTagValue("MaxLen")),
						    Double.parseDouble(s.getXmlTagValue("LenTolerance")),
						    Double.parseDouble(s.getXmlTagValue("MinAngle")),
						    Double.parseDouble(s.getXmlTagValue("MaxAngle")),
						    Double.parseDouble(s.getXmlTagValue("AngleTolerance")));
	energyRulePattern.add(p);
	if (!energyRuleMatch.containsKey(p.bondName))
	    energyRuleMatch.put(p.bondName,new Vector<EnergyRuleMatch>());
	energyRuleMatch.get(p.bondName).add(new EnergyRuleMatch(p,topology));
    }

    // method to get a Particle from the Board object or create and add one
    public Particle getOrCreateParticle(String particleName, Board board) {
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
    public TransformRuleMatch[] getSourceTransformRules (String particleName, int dir) {
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
    static RuleSyntax nounSyntax = new RuleSyntax("NOUN n! c=ffffff e=0 i*", "n=Name c=Color e=Energy i=Icon");
    static RuleSyntax verbSyntax = new RuleSyntax("VERB s! t=.* S=$S T=$T d= p=1 v=_ b* c* x* B* k* K*", "s=OldSource t=OldTarget S=NewSource T=NewTarget d=Dir p=Prob v=Say b=OptionalBond c=DeleteBond x=ExcludeBond B=NewBond k=KeepOptionalBond K=KeepRequiredBond");
    static RuleSyntax bondSyntax = new RuleSyntax("BOND n! e= s=.* t=.* l=1 L=1.5 m=1 a=-1 A=1 b=1", "n=Name e=Energy s=Source t=Target l=MinLen L=MaxLen m=LenTolerance a=MinAngle A=MaxAngle b=AngleTolerance");

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

    public static PatternSet fromFile (String filename, Topology topology) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis, topology);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
