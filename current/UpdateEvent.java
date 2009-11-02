import java.util.*;

public class UpdateEvent {
    // data
    Particle source = null, target = null;
    TransformRulePattern pattern = null;
    // everything below here ignored by equals() and hashCode() methods
    String verb = null;
    private HashMap<String,Point> bondLabel = null;
    HashMap<String,Point> sIncoming = null, sOutgoing = null, tIncoming = null, tOutgoing = null;
    Point sourceCoords = null, targetCoords = null;

    // methods
    // constructor
    public UpdateEvent (Particle s, Particle t, String v, TransformRulePattern p) {
	source = s;
	target = t;
	verb = v;
	pattern = p;
	initBondLabel();
    }

    // bondLabel init method
    private void initBondLabel() {
	if ((pattern.lhsBond != null && pattern.lhsBond.size() > 0)
	    || (pattern.rhsBond != null && pattern.rhsBond.size() > 0)) {
	    bondLabel = new HashMap<String,Point>();
	}
    }

    // binding methods for bonds
    public boolean bindBonds (Point sc, Point tc, Board board) {
	boolean match = true;
	sourceCoords = sc;
	targetCoords = tc;
	if (bondLabel != null) {
	    bondLabel.clear();

	    // s and t are bound to source & target cells
	    bondLabel.put("s",sourceCoords);
	    bondLabel.put("t",targetCoords);
	    // sT and tS are bound to source & target cells on LHS, but are switched on RHS (to help with diffusion moves)
	    bondLabel.put("sT",sourceCoords);
	    bondLabel.put("tS",targetCoords);
	    Vector<BondPattern> lhsBond = pattern.lhsBond;
	    Vector<BondPattern> excludedLhsBond = pattern.excludedLhsBond;

	    // test for excluded bonds
	    if (excludedLhsBond != null)
		for (int n = 0; match && n < excludedLhsBond.size(); ++n) {
		    BondPattern bp = excludedLhsBond.get(n);
		    if (bondLabel.get(bp.beginPointLabel) != null) {
			Point beginPoint = bondLabel.get(bp.beginPointLabel);
			if (board.onBoard(beginPoint)) {
			    Point bondEndPoint = board.outgoing(beginPoint,bp.bondName);
			    if (bondLabel.get(bp.endPointLabel) != null) {
				Point endPoint = bondLabel.get(bp.endPointLabel);
				if (endPoint.equals(bondEndPoint))
				    match = false;  // bond exists exactly as described, with both end labels bound; reject
			    } else if (bondEndPoint != null)
				match = false;  // bond exists (although only beginpoint label is bound); that's enough to reject
			}
		    } else if (bondLabel.get(bp.endPointLabel) != null) {
			Point endPoint = bondLabel.get(bp.endPointLabel);
			if (board.onBoard(endPoint)) {
			    Point bondBeginPoint = board.incoming(endPoint,bp.bondName);
			    if (bondBeginPoint != null)
				match = false;  // bond exists (although only endpoint label is bound); that's enough to reject
			}
		    }
		}

	    // bind bond labels
	    if (match && lhsBond != null)
		for (int n = 0; n < lhsBond.size(); ++n) {
		    BondPattern bp = lhsBond.get(n);
		    if (bondLabel.get(bp.beginPointLabel) != null) {
			Point beginPoint = bondLabel.get(bp.beginPointLabel);
			if (board.onBoard(beginPoint)) {
			    Point bondEndPoint = board.outgoing(beginPoint,bp.bondName);
			    if (bondLabel.get(bp.endPointLabel) != null) {
				Point endPoint = bondLabel.get(bp.endPointLabel);
				if (board.onBoard(endPoint)) {
				    Point bondBeginPoint = board.incoming(endPoint,bp.bondName);
				    if (!endPoint.equals(bondEndPoint) || !beginPoint.equals(bondBeginPoint)) {
					match = false;
				    }
				}
			    } else {
				if (bondEndPoint != null)
				    bondLabel.put(bp.endPointLabel,bondEndPoint);
			    }
			}
		    } else if (bondLabel.get(bp.endPointLabel) != null) {
			Point endPoint = bondLabel.get(bp.endPointLabel);
			if (board.onBoard(endPoint)) {
			    Point bondBeginPoint = board.incoming(endPoint,bp.bondName);
			    if (bondBeginPoint != null)
				bondLabel.put(bp.beginPointLabel,bondBeginPoint);
			}
		    }
		}

	    // swap sT and tS
	    bondLabel.put("sT",targetCoords);
	    bondLabel.put("tS",sourceCoords);
	}

	// set up sIncoming, sOutgoing, tIncoming, tOutgoing
	if (match) {
	    sIncoming = getHashMap("s",false,"");
	    sOutgoing = getHashMap("s",true,"");
	    tIncoming = getHashMap("t",false,"s");
	    tOutgoing = getHashMap("t",true,"s");
	}

	// return
	return match;
    }

    private HashMap<String,Point> getHashMap(String label,boolean begin,String ignoreLabel) {
	HashMap<String,Point> bondDir = null;

	if (bondLabel != null) {
	    Vector<BondPattern> rhsBond = pattern.rhsBond;
	    if (rhsBond != null) {
		Point coords = bondLabel.get(label);
		for (int n = 0; n < rhsBond.size(); ++n) {
		    BondPattern bp = rhsBond.get(n);
		    String bpLabel = begin ? bp.beginPointLabel : bp.endPointLabel;
		    if (bondLabel.get(bpLabel) != null && bondLabel.get(bpLabel).equals(coords)) {
			String otherLabel = begin ? bp.endPointLabel : bp.beginPointLabel;
			if (bondLabel.get(otherLabel) != null && !otherLabel.equals(ignoreLabel)) {
			    Point otherCoords = bondLabel.get(otherLabel);
			    if (bondDir == null)
				bondDir = new HashMap<String,Point>();
			    bondDir.put(bp.bondName,otherCoords.subtract(coords));
			}
		    }
		}
	    }
	}
	return bondDir;
    }

    // helpers
    public double energyDelta(Board board) {
	double oldEnergy = 0, newEnergy = 0;
	boolean sourceHere = sourceCoords != null && board.onBoard(sourceCoords);
	boolean targetHere = sourceCoords != null && board.onBoard(sourceCoords);
	if (sourceHere && targetHere) {
	    oldEnergy = board.bondEnergy(sourceCoords,targetCoords);
	    newEnergy = board.bondEnergy(sourceCoords,targetCoords,source,target,sIncoming,sOutgoing,tIncoming,tOutgoing);
	} else if (sourceHere && !targetHere) {
	    oldEnergy = board.bondEnergy(sourceCoords);
	    newEnergy = board.bondEnergy(sourceCoords,source,sIncoming,sOutgoing);
	} else if (!sourceHere && targetHere) {
	    oldEnergy = board.bondEnergy(targetCoords);
	    newEnergy = board.bondEnergy(targetCoords,target,tIncoming,tOutgoing);
	}
	return newEnergy - oldEnergy;
    }

    public void write(Board board) {
	board.removeBonds(targetCoords);
	if (sourceCoords != null && board.onBoard(sourceCoords)) {
	    board.removeBonds(sourceCoords);
	    board.writeCell(sourceCoords,source);
	    board.addIncoming(sourceCoords,sIncoming);
	    board.addOutgoing(sourceCoords,sOutgoing);
	}
	board.writeCell(targetCoords,target);
	board.addIncoming(targetCoords,tIncoming);
	board.addOutgoing(targetCoords,tOutgoing);
    }

    // equals, hashCode
    public boolean equals (Object obj) {
	if (obj.getClass().equals(getClass())) {
	    UpdateEvent pp = (UpdateEvent) obj;
	    boolean eq = source.equals(pp.source) && target.equals(pp.target);
	    if (eq && pattern != null) {
		if (pattern.lhsBond != null) eq = eq && pp.pattern != null && pattern.lhsBond.equals(pp.pattern.lhsBond);
		if (pattern.rhsBond != null) eq = eq && pp.pattern != null && pattern.rhsBond.equals(pp.pattern.rhsBond);
		if (pattern.excludedLhsBond != null) eq = eq && pp.pattern != null && pattern.excludedLhsBond.equals(pp.pattern.excludedLhsBond);
	    }
	    return eq;
	}
	return false;
    }

    public int hashCode() {
	int code = 0;
	if (source != null) code = code ^ source.hashCode();
	if (target != null) code = code ^ target.hashCode();
	if (pattern != null) {
	    if (pattern.lhsBond != null) code = code ^ pattern.lhsBond.hashCode();
	    if (pattern.rhsBond != null) code = code ^ pattern.rhsBond.hashCode();
	    if (pattern.excludedLhsBond != null) code = code ^ pattern.excludedLhsBond.hashCode();
	}
	return code;
    }

    // part of verb visible to player
    public final String visibleVerb() {
	return Particle.visibleText(verb);
    }
};
