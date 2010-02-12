import java.util.*;

public class TransformRulePattern extends RulePattern {
    // data
    String dir = null, C = null, D = null, V = null;
    double P = 0;
    Vector<BondPattern> optionalLhsBond = null, requiredLhsBond = null, excludedLhsBond = null, rhsBond = null;

    // constructors
    public TransformRulePattern (String w, String dir, String a, String b, String c, String d, double p, String v) {
	super(w,a,b);
	if (dir != null && dir.length() > 0)
	    this.dir = dir;
	C = c;
	D = d;
	P = p;
	V = v;
    }

    // wrappers to add bonds
    public void addRequiredLhsBonds(String[] b) { requiredLhsBond = addBonds(requiredLhsBond,b); }
    public void addOptionalLhsBonds(String[] b) { optionalLhsBond = addBonds(optionalLhsBond,b); }
    public void addExcludedLhsBonds(String[] b) { excludedLhsBond = addBonds(excludedLhsBond,b); }
    public void addRhsBonds(String[] b) { rhsBond = addBonds(rhsBond,b); }

    private Vector<BondPattern> addBonds(Vector<BondPattern> bondVec, String[] b) {
	if (bondVec == null)
	    bondVec = new Vector<BondPattern>(b.length);
	for (int n = 0; n < b.length; ++n)
	    bondVec.add(BondPattern.fromString(b[n]));
	return bondVec;
    }
}
