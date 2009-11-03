// Syntax for regexp-based production rule generators:
//  A B E
// where
//  A is a regexp that must globally match the source state
//  B is a regexp that must globally match the target state
//  E is a numeric constant that is the energy of this adjacency, in units of kT/log(10)

// The following "special variables" will be expanded in {A,B} as appropriate:
//  $1,$2,$3... => groups in A and B regexps (c.f. Perl)
//  $S => full name for source state
//  $-1 or $-1.1 => numerically one less than $1
//  $-2.1 => numerically two less than $1
//  $+1.1 => numerically one greater than $1
//  $%3+2.1 => ($1 + 2) mod 3

public class EnergyRuleMatch extends RuleMatch {

    // constructors
    public EnergyRuleMatch(EnergyRulePattern p) { super(p); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board) { super(p,board,-1); }

    // rule accessor
    public final EnergyRulePattern energyPattern() { return (EnergyRulePattern) pattern; }

    // override expandDir to do nothing (hacky; refactor at some point to put expandDir in TransformRuleMatch)
    protected String expandDir (String s) { return s; }

    // overload matches
    public boolean matches(String sourceName, String targetName, Point sourceToTarget) {
	EnergyRulePattern rp = energyPattern();
	long len = 0;
	if (rp.lenType.equals("t"))
	    len = board.taxicabLength(sourceToTarget);
	else if (rp.lenType.equals("m"))
	    len = board.mooreLength(sourceToTarget);
	else if (rp.lenType.equals("d"))
	    len = board.directLength(sourceToTarget);
	return super.matches(sourceName,targetName) && len >= rp.minLen && len <= rp.maxLen;
    }

    // other public methods
    public final double E() { return energyPattern().E; }
}
