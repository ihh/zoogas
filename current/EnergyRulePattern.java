// Syntax for energy rule patterns:
//  A B E
// where
//  A is a regexp that must globally match the source state
//  B is a regexp that must globally match the target state
//  E is a numeric constant that is the energy of this adjacency, in units of kT/log(10)

public class EnergyRulePattern extends RulePattern {
    // data
    String bondName = null, lenType = null;
    int minLen, maxLen;
    double minAngle, maxAngle;
    double E;
    
    // constructors
    public EnergyRulePattern (String a, String b, String n, double e, String lt, int l, int L, double minAngle, double maxAngle) {
	super(a,b);
	bondName = n;
	lenType = lt;
	minLen = l;
	maxLen = L;
	this.minAngle = minAngle;
	this.maxAngle = maxAngle;
	E = e;
    }

    // accessors
    public final boolean hasAngleConstraint() { return maxAngle - minAngle < 2; }
}
