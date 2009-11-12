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
    Particle oldSource = null, oldTarget = null;

    // methods
    // constructor
    public UpdateEvent (Particle sOld, Particle tOld, Particle s, Particle t, String v, TransformRulePattern p) {
	source = s;
	target = t;
	verb = v;
	pattern = p;
	oldSource = sOld;
	oldTarget = tOld;
	initBondLabel();
    }

    // bond pattern accessors
    protected boolean keepsSourceBonds() {
	return source == oldSource && sIncoming == null && sOutgoing == null;
    }

    protected boolean keepsTargetBonds() {
	return target == oldTarget && tIncoming == null && tOutgoing == null;
    }

    // bondLabel init method
    private void initBondLabel() {
	if ((pattern.requiredLhsBond != null && pattern.requiredLhsBond.size() > 0)
	    || (pattern.optionalLhsBond != null && pattern.optionalLhsBond.size() > 0)
	    || (pattern.excludedLhsBond != null && pattern.excludedLhsBond.size() > 0)
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

	    // apply the various tests
	    match = match && bindBonds(pattern.requiredLhsBond,board,true,false);
	    match = match && bindBonds(pattern.optionalLhsBond,board,false,false);
	    match = match && bindBonds(pattern.excludedLhsBond,board,false,true);

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

    private boolean bindBonds(Vector<BondPattern> bondExpr, Board board, boolean required, boolean excluded) {
	if (bondExpr != null) {
	    for (int n = 0; n < bondExpr.size(); ++n) {
		BondPattern bp = bondExpr.get(n);
		Point exprBeginPoint = bondLabel.get(bp.beginPointLabel);
		if (exprBeginPoint == null) {
		    // beginPointLabel is unbound
		    Point exprEndPoint = bondLabel.get(bp.endPointLabel);
		    if (exprEndPoint == null) {  // both ends are unbound?
			if (required)
			    return false;
		    } else if (board.onBoard(exprEndPoint)) {
			Point boardBeginPoint = board.incomingCoord(exprEndPoint,bp.bondName);
			if (excluded && boardBeginPoint != null) {
			    System.err.println("Excluding "+verb+" due to "+bp.bondName);
			    return false;
			}
			if (required && boardBeginPoint == null)
			    return false;
			if (boardBeginPoint != null)
			    bondLabel.put(bp.beginPointLabel,boardBeginPoint);
		    }
		} else {
		    // beginPointLabel is already bound
		    if (board.onBoard(exprBeginPoint)) {
			Point boardEndPoint = board.outgoing(exprBeginPoint,bp.bondName);
			Point exprEndPoint = bondLabel.get(bp.endPointLabel);
			if (exprEndPoint == null) {
			    if (excluded && boardEndPoint != null) {
				System.err.println("Excluding "+verb+" due to "+bp.bondName);
				return false;
			    }
			    if (required && boardEndPoint == null)
				return false;
			    if (boardEndPoint != null)
				bondLabel.put(bp.endPointLabel,boardEndPoint);
			} else {
			    if (board.onBoard(exprEndPoint)) {
				// check that the bond on the board is consistent with the bound labels in the bond expression
				Point boardBeginPoint = board.incomingCoord(exprEndPoint,bp.bondName);
				boolean exprMatchesBoard = exprEndPoint.equals(boardEndPoint) && exprBeginPoint.equals(boardBeginPoint);
				if (required && !exprMatchesBoard)
				    return false;
				if (excluded && exprMatchesBoard)
				    return false;
			    }
			}
		    }
		}
	    }
	}
	return true;
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
	boolean sourceChanged = sourceCoords != null && board.onBoard(sourceCoords) && !keepsSourceBonds();
	boolean targetChanged = targetCoords != null && board.onBoard(targetCoords) && !keepsTargetBonds();
	if (sourceChanged && targetChanged) {
	    oldEnergy = board.bondEnergy(sourceCoords,targetCoords);
	    newEnergy = board.bondEnergy(sourceCoords,targetCoords,source,target,sIncoming,sOutgoing,tIncoming,tOutgoing);
	} else if (sourceChanged && !targetChanged) {
	    oldEnergy = board.bondEnergy(sourceCoords);
	    newEnergy = board.bondEnergy(sourceCoords,source,sIncoming,sOutgoing);
	} else if (!sourceChanged && targetChanged) {
	    oldEnergy = board.bondEnergy(targetCoords);
	    newEnergy = board.bondEnergy(targetCoords,target,tIncoming,tOutgoing);
	}

	return newEnergy - oldEnergy;
    }

    public void write(Board board) {
	if (!keepsTargetBonds())
	    board.removeBonds(targetCoords);
	if (sourceCoords != null && board.onBoard(sourceCoords)) {
	    if (!keepsSourceBonds())
		board.removeBonds(sourceCoords);
	    board.writeCell(sourceCoords,source);
	    board.addIncoming(sourceCoords,sIncoming);
	    board.addOutgoing(sourceCoords,sOutgoing);
	}
	board.writeCell(targetCoords,target);
	board.addIncoming(targetCoords,tIncoming);
	board.addOutgoing(targetCoords,tOutgoing);
    }

    public String writeAndLog(Board board) {
	String oldSourceDesc = board.singleNeighborhoodDescription(sourceCoords,true);
	String oldTargetDesc = board.singleNeighborhoodDescription(targetCoords,true);
	double oldEnergy = board.bondEnergy(sourceCoords,targetCoords);

	write(board);

	String newSourceDesc = board.singleNeighborhoodDescription(sourceCoords,true);
	String newTargetDesc = board.singleNeighborhoodDescription(targetCoords,true);
	double newEnergy = board.bondEnergy(sourceCoords,targetCoords);

	return oldSourceDesc+" "+oldTargetDesc+" -> "+newSourceDesc+" "+newTargetDesc+"  energyDelta="+(newEnergy-oldEnergy)+"  verb="+verb;
    }

    // equals, hashCode
    public boolean equals (Object obj) {
	if (obj.getClass().equals(getClass())) {
	    UpdateEvent pp = (UpdateEvent) obj;
	    boolean eq = source.equals(pp.source) && target.equals(pp.target);
	    if (eq && pattern != null) {
		if (pattern.requiredLhsBond != null) eq = eq && pp.pattern != null && pattern.requiredLhsBond.equals(pp.pattern.requiredLhsBond);
		if (pattern.optionalLhsBond != null) eq = eq && pp.pattern != null && pattern.optionalLhsBond.equals(pp.pattern.optionalLhsBond);
		if (pattern.excludedLhsBond != null) eq = eq && pp.pattern != null && pattern.excludedLhsBond.equals(pp.pattern.excludedLhsBond);
		if (pattern.rhsBond != null) eq = eq && pp.pattern != null && pattern.rhsBond.equals(pp.pattern.rhsBond);
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
	    if (pattern.requiredLhsBond != null) code = code ^ pattern.requiredLhsBond.hashCode();
	    if (pattern.optionalLhsBond != null) code = code ^ pattern.optionalLhsBond.hashCode();
	    if (pattern.excludedLhsBond != null) code = code ^ pattern.excludedLhsBond.hashCode();
	    if (pattern.rhsBond != null) code = code ^ pattern.rhsBond.hashCode();
	}
	return code;
    }

    // part of verb visible to player
    public final String visibleVerb() {
	return Particle.visibleText(verb);
    }
};
