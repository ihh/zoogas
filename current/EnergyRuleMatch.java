// Syntax for regexp-based production rule generators:
//  A B E
// where
//  A is a regexp that must globally match the source state
//  B is a regexp that must globally match the target state
//  E is a numeric constant that is the energy of this adjacency, in units of kT/log(10)

// The following "special variables" will be expanded in {A,B} as appropriate:
//  $1,$2,$3... => groups in A and B regexps (c.f. Perl)
//  $S => full name for source state
//  $-1 or $-1.1 => numerically one less than $1
//  $-2.1 => numerically two less than $1
//  $+1.1 => numerically one greater than $1
//  $%3+2.1 => ($1 + 2) mod 3

public class EnergyRuleMatch extends RuleMatch {
    // private data, set when bound
    double len, angle;

    // constructors
    public EnergyRuleMatch(EnergyRulePattern p) { super(p); }
    public EnergyRuleMatch(EnergyRulePattern p, Topology topology) { super(p,topology,-1); }

    // rule accessor
    public final EnergyRulePattern energyPattern() { return (EnergyRulePattern) pattern; }

    // override expandDir to do nothing (hacky; refactor at some point to put expandDir in TransformRuleMatch)
    protected String expandDir (String s) { return s; }

    // overload matches
    public boolean matches(String sourceName, String targetName, Point sourceToTarget, Point prevToSource) {
	EnergyRulePattern rp = energyPattern();
	boolean match;

	len = topology.directLength(sourceToTarget);
	match = len >= rp.minLen && len <= rp.maxLen;

	if (match && prevToSource != null && rp.hasAngleConstraint()) {
	    angle = topology.angle(prevToSource,sourceToTarget);
	    match = angle >= rp.minAngle && angle <= rp.maxAngle;
	    //	    System.err.println("Angle between "+prevToSource+" and "+sourceToTarget+" is "+angle+"; match="+(match?"t":"f"));
	}

	return match && super.matches(sourceName,targetName);
    }

    // other public methods
    public final double E() {
	EnergyRulePattern rp = energyPattern();
	double angleRange = rp.maxAngle - rp.minAngle, lenRange = rp.maxLen - rp.minLen;
	double angleDev = angleRange > 0 ? 2*Math.abs((angle - rp.minAngle) / angleRange - .5) : 0;
	double lenDev = lenRange > 0 ? 2*Math.abs((len - rp.minLen) / lenRange - .5) : 0;
	double angleWeight = angleDev > rp.angleTolerance ? (1 - (angleDev-rp.angleTolerance)/(1-rp.angleTolerance)) : 1;
	double lenWeight = lenDev > rp.lenTolerance ? (1 - (lenDev-rp.lenTolerance)/(1-rp.lenTolerance)) : 1;

	//	if (rp.angleTolerance < 1 || rp.lenTolerance < 1)
	//	    System.err.println("angleDev="+angleDev+" angleWeight="+angleWeight+" lenDev="+lenDev+" lenWeight="+lenWeight);

	return rp.E * angleWeight * lenWeight;
    }
}
