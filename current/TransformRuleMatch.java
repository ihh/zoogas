import java.util.regex.*;

// Syntax for regexp-based production rule generators:
//  A B C D P V
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)

// The following "special variables" will be expanded in {A,B,C,D} as appropriate:
//  $1,$2,$3... => groups in A and B regexps (c.f. Perl)
//  $S,$T => full names for old source,target states
//  $F,$L,$R,$B,$+L,$+R,$++L,$++R => directions relative to neighbor direction ($F=forward, $L=left, $R=right, $B=back, $+L=two left, $++L=three left)
//  $-1 or $-1.1 => numerically one less than $1
//  $-2.1 => numerically two less than $1
//  $+1.1 => numerically one greater than $1
//  $%3+2.1 => ($1 + 2) mod 3


// Example usage:
//   TransformRulePattern rp = TransformRulePattern.fromString ("critter/(\d+) _ $T $S .098 move");
//   TransformRuleMatch rm = new TransformRuleMatch(rp);
//   rm.bindDir(board,0);
//   rm.bindSource("critter/1");
//   rm.bindTarget("_");
//   String newSource = rm.C(), newTarget = rm.D(), verb = rm.V();
//   double prob = rm.P();
//   rm.unbind();


public class TransformRuleMatch extends RuleMatch {
    // data
    private Board board = null;
    private int dir = -1;

    // constructors
    public TransformRuleMatch(TransformRulePattern p) { super(p); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir) { super(p); bindDir(board,dir); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir,String a) { super(p); bindDir(board,dir); bindSource(a); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir,String a,String b) { super(p); bindDir(board,dir); bindSource(a); bindTarget(b); }

    // private methods
    private final TransformRulePattern transformPattern() { return (TransformRulePattern) pattern; }

    // binding methods
    public final boolean bindDir(Board b,int d) {
	if (!dirBound()) {
	    board = b;
	    dir = d;
	    return true;
	}
	// throw AlreadyBoundException
	return false;
    }

    public final boolean dirBound() { return dir >= 0; }

    public void unbind() {
	super.unbind();
	dir = -1;
    }

    // override regexA and regexB
    public String regexA() { return expandDir(super.regexA()); }
    public String regexB() { return expandDir(super.regexB()); }

    // expandDir
    private static Pattern dirPattern = Pattern.compile("\\$(F|B|L|R|\\+L|\\+\\+L|\\+R|\\+\\+R)");
    protected String expandDir (String s) {
	Matcher m = dirPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String var = m.group(1);
	    int nbrs = board.neighborhoodSize();
	    if (var.equals("F"))
		m.appendReplacement(sb,board.dirString(dir));
	    else if (var.equals("B"))
		m.appendReplacement(sb,board.dirString((dir + nbrs/2) % nbrs));
	    else if (var.equals("L"))
		m.appendReplacement(sb,board.dirString((dir + nbrs-1) % nbrs));
	    else if (var.equals("+L"))
		m.appendReplacement(sb,board.dirString((dir + nbrs-2) % nbrs));
	    else if (var.equals("++L"))
		m.appendReplacement(sb,board.dirString((dir + nbrs-3) % nbrs));
	    else if (var.equals("R"))
		m.appendReplacement(sb,board.dirString((dir + 1) % nbrs));
	    else if (var.equals("+R"))
		m.appendReplacement(sb,board.dirString((dir + 2) % nbrs));
	    else if (var.equals("++R"))
		m.appendReplacement(sb,board.dirString((dir + 3) % nbrs));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    // expansion of C and D
    protected final String expandRHS (String s) {
	return expandTarget(expandLHS(expandDir(s)));
    }

    // public methods
    public final String C() { return expandRHS(transformPattern().C); }
    public final String D() { return expandRHS(transformPattern().D); }
    public final String V() { return expandRHS(transformPattern().V); }
    public final double P() { return transformPattern().P; }
}
