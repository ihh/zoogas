// Syntax for energy rule patterns:
//  A B E
// where
//  A is a regexp that must globally match the source state
//  B is a regexp that must globally match the target state
//  E is a numeric constant that is the energy of this adjacency, in units of kT/log(10)

public class EnergyRulePattern extends RulePattern {
    // data
    String bondName = null, lenType = null;
    double minLen, maxLen;
    double minAngle, maxAngle;
    double lenTolerance, angleTolerance;
    double E;
    
    // constructors
    public EnergyRulePattern (String w, String a, String b, String n, double e, double l, double L, double lTol, double minAngle, double maxAngle, double aTol) {
	super(w,a,b);
	bondName = n;
	minLen = l;
	maxLen = L;
	this.minAngle = minAngle;
	this.maxAngle = maxAngle;
	lenTolerance = lTol;
	angleTolerance = aTol;
	E = e;
    }

    // accessors
    public final boolean hasAngleConstraint() { return maxAngle - minAngle < 2 || angleTolerance < 1; }
}
