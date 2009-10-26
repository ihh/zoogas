import java.util.regex.*;

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
