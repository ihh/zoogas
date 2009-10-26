
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

    static TransformRulePattern fromString (String abcdpv) {
	String[] args = abcdpv.split(" ",6);
	if (args.length != 6) {
	    throw new RuntimeException ("Rule '" + abcdpv + "' has " + args.length + " args; expected 6");
	}
	return new TransformRulePattern(args[0],args[1],args[2],args[3],Double.parseDouble(args[4]),args[5]);
    }

    // toString method
    public String toString() { return A + " " + B + " " + C + " " + D + " " + P + " " + V; }
}
