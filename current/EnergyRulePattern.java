// Syntax for energy rule patterns:
//  A B E
// where
//  A is a regexp that must globally match the source state
//  B is a regexp that must globally match the target state
//  E is a numeric constant that is the energy of this adjacency, in units of kT/log(10)

public class EnergyRulePattern extends RulePattern {
    // data
    double E = 0;
    
    // constructors
    public EnergyRulePattern (String a, String b, double e) {
	super(a,b);
	E = e;
    }

    // toString method
    public String toString() { return A + " " + B + " " + E; }
}
