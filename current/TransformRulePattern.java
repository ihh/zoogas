
// Syntax for transformation rule patterns:
//  A B C D P V
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)


public class TransformRulePattern extends RulePattern {
    // data
    String C = null, D = null, V = null;
    double P = 0;
    
    // constructors
    public TransformRulePattern (String a, String b, String c, String d, double p, String v) {
	super(a,b);
	C = c;
	D = d;
	P = p;
	V = v;
    }

    // toString method
    public String toString() { return A + " " + B + " " + C + " " + D + " " + P + " " + V; }
}
