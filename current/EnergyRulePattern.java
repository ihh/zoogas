// Syntax for energy rule patterns:
//  A B E
// where
//  A is a regexp that must globally match the source state
//  B is a regexp that must globally match the target state
//  E is a numeric constant that is the energy of this adjacency, in units of kT/log(10)

public class EnergyRulePattern extends RulePattern {
    // data
    String bondName = null;
    int minLen = 1, maxLen = 1;
    double E = 0;
    
    // constructors
    public EnergyRulePattern (String a, String b, String n, double e, int k, int l) {
	super(a,b);
	bondName = n;
	minLen = k;
	maxLen = l;
	E = e;
    }
}
