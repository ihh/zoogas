import java.util.*;
import java.util.regex.*;

// Syntax for regex-based production rule generators:
//  A B C D P V
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)

// The following "special variables" will be expanded in {C,D,P,V} as appropriate:
//  $F,$L,$R,$B,$+L,$+R,$++L,$++R => directions relative to neighbor direction ($F=forward, $L=left, $R=right, $B=back, $+L=two left, $++L=three left)
//    (NB the above directional variables are also expanded in A and B)
//  $1,$2,$3... => groups in A and B regexps (c.f. Perl)
//    (these can also be accessed as \1,\2,\3... in A and B)
//  $S,$T => full names for old source,target states
//  $-1 or $-1.1 => numerically one less than $1
//  $-2.1 => numerically two less than $1
//  $+1.1 => numerically one greater than $1
//  $%3+2.1 => ($1 + 2) mod 3



public class TransformRuleMatch extends RuleMatch {
    // data
    private Pattern dirPattern = null;
    private boolean dirMatches = false;

    // constructors
    public TransformRuleMatch(TransformRulePattern p) {
	super(p);
	if (p.dir != null)
	    dirPattern = Pattern.compile(p.dir);
    }

    public TransformRuleMatch(TransformRulePattern p, Topology topology,int dir) {
	this(p);
	bindDir(topology, dir);
    }

    // rule accessor
    public final TransformRulePattern transformPattern() { return (TransformRulePattern) pattern; }

    // override bindDir()
    public boolean bindDir(Topology t, int d) {
	boolean boundOk = super.bindDir(t, d);
	if (dirPattern == null)
	    dirMatches = true;
	else {
	    String dirString = topology.dirString(d);
	    dirMatches = dirPattern.matcher(dirString).matches();
	    boundOk = boundOk && dirMatches;
	}
	return boundOk;
    }

    // override matches()
    public boolean matches() {
	if (dirBound() && !dirMatches)
	    return false;
	return super.matches();
    }

    // other public methods
    public final String C() { return expand(transformPattern().C); }
    public final String D() { return expand(transformPattern().D); }
    public final String V() { return expand(transformPattern().V); }
    public final double P() { return transformPattern().P; }
}
