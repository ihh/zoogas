import java.util.*;
import java.util.regex.*;
import java.awt.Point;

// Syntax for regexp-based production rule generators:
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
    // private data
    private HashMap<String,Point> bondLabel = null;

    // constructors
    public TransformRuleMatch(TransformRulePattern p) { super(p); initBondLabel(); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir) { super(p,board,dir); initBondLabel(); }

    // bondLabel init method
    private void initBondLabel() {
	if (transformPattern().lhsBond != null && transformPattern().lhsBond.size() > 0)
	    bondLabel = new HashMap<String,Point>();
    }

    // rule accessor
    public final TransformRulePattern transformPattern() { return (TransformRulePattern) pattern; }

    // binding methods for bonds
    // this is currently not quite a first-class binding method (unlike bindSource or bindTarget), since it is ignored by the superclass matches() method
    public boolean bindBonds (Point sourceCoords, Point targetCoords) {
	boolean lhsBondMatches = true;
	if (bondLabel != null) {
	    bondLabel.clear();
	    bondLabel.put("s",sourceCoords);
	    bondLabel.put("t",targetCoords);
	    Vector<BondPattern> lhsBond = transformPattern().lhsBond;
	    if (lhsBond != null)
		for (int n = 0; lhsBondMatches && n < lhsBond.size(); ++n) {
		    BondPattern bp = lhsBond.get(n);
		    if (bondLabel.containsKey(bp.beginPointLabel)) {
			Point beginPoint = bondLabel.get(bp.beginPointLabel);
			Point bondEndPoint = board.outgoing(beginPoint,bp.bondName);
			if (bondLabel.containsKey(bp.endPointLabel)) {
			    Point endPoint = bondLabel.get(bp.endPointLabel);
			    Point bondBeginPoint = board.incoming(endPoint,bp.bondName);
			    if (!endPoint.equals(bondEndPoint) || !beginPoint.equals(bondBeginPoint))
				lhsBondMatches = false;
			} else {
			    if (bondEndPoint == null)
				lhsBondMatches = false;
			    else
				bondLabel.put(bp.endPointLabel,bondEndPoint);
			}
		    } else if (bondLabel.containsKey(bp.endPointLabel)) {
			Point endPoint = bondLabel.get(bp.endPointLabel);
			Point bondBeginPoint = board.incoming(endPoint,bp.bondName);
			if (bondBeginPoint == null)
			    lhsBondMatches = false;
			else
			    bondLabel.put(bp.beginPointLabel,bondBeginPoint);
		    }
		}
	}
	return lhsBondMatches;
    }

    public HashMap<String,Integer> sIncoming() { return getHashMap("s",false); }
    public HashMap<String,Integer> sOutgoing() { return getHashMap("s",true); }
    public HashMap<String,Integer> tIncoming() { return getHashMap("t",false); }
    public HashMap<String,Integer> tOutgoing() { return getHashMap("t",true); }

    private HashMap<String,Integer> getHashMap(String label,boolean begin) {
	HashMap<String,Integer> bondDir = null;
	
	if (bondLabel != null) {
	    Vector<BondPattern> rhsBond = transformPattern().rhsBond;
	    if (rhsBond != null) {
		Point coords = bondLabel.get(label);
		for (int n = 0; n < rhsBond.size(); ++n) {
		    BondPattern bp = rhsBond.get(n);
		    if (label.equals(begin ? bp.beginPointLabel : bp.endPointLabel)) {
			String otherLabel = begin ? bp.endPointLabel : bp.beginPointLabel;
			if (bondDir == null)
			    bondDir = new HashMap<String,Integer>();
			bondDir.put(bp.bondName,board.getNeighborDirection(coords,bondLabel.get(otherLabel)));
		    }
		}
	    }
	}
	return bondDir;
    }

    // other public methods
    public final String C() { return expand(transformPattern().C); }
    public final String D() { return expand(transformPattern().D); }
    public final String V() { return expand(transformPattern().V); }
    public final double P() { return transformPattern().P; }
}
