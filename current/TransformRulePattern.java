import java.util.*;

// Transformation rule patterns:
//  A B C D P V [lhsBond] [rhsBond]
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)
//  lhsBond are bonds that must be present on the LHS
//  rhsBond are bonds that will be formed on the LHS

public class TransformRulePattern extends RulePattern {
    // data
    String C = null, D = null, V = null;
    double P = 0;
    Vector<BondPattern> lhsBond = null, excludedLhsBond = null, rhsBond = null;

    // constructors
    public TransformRulePattern (String a, String b, String c, String d, double p, String v) {
	super(a,b);
	C = c;
	D = d;
	P = p;
	V = v;
    }

    // wrappers to add bonds
    public void addLhsBonds(String[] b) { lhsBond = addBonds(lhsBond,b); }
    public void addRhsBonds(String[] b) { rhsBond = addBonds(rhsBond,b); }
    public void addExcludedLhsBonds(String[] b) { excludedLhsBond = addBonds(excludedLhsBond,b); }

    private Vector<BondPattern> addBonds(Vector<BondPattern> bondVec, String[] b) {
	if (bondVec == null)
	    bondVec = new Vector<BondPattern>(1);
	for (int n = 0; n < b.length; ++n)
	    bondVec.add(BondPattern.fromString(b[n]));
	return bondVec;
    }
}
