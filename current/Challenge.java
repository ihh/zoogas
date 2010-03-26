import java.awt.Rectangle;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

public class Challenge
{
    public Challenge(ZooGas g) {
        this(g, null);
    }
    public Challenge(ZooGas g, Condition c) {
	gas = g;
	board = g.board;
        cond = c;
    }

    ZooGas gas;
    Board board;
    private String desc = "";
    Condition cond;

    public static List<List<Point>> getEnclosures (Board b, String wallPrefix, boolean allowDiagonalConnections) {
	Set<String> wallPrefixes = new TreeSet<String>();
	wallPrefixes.add(wallPrefix);
	return getEnclosures(b,wallPrefixes,allowDiagonalConnections);
    }

    public static List<List<Point>> getEnclosures (Board b, Set<String> wallPrefixes, boolean allowDiagonalConnections) {

	// create an array of enclosure indices
	int size = b.size;
	int[][] mark = new int[size][size];

	// mark the walls as -1
	for (String wallPrefix : wallPrefixes)
	    if (b.gotPrefix(wallPrefix))
		for(Particle p : b.getParticlesByPrefix(wallPrefix))
		    for (Point q : p.getOccupiedPoints())
			mark[q.x][q.y] = -1;

	// create list-of-lists
	LinkedList<List<Point>> enclosures = new LinkedList<List<Point>>();

	// loop over the board, starting a breadth-first search from every unvisited cell
	int dirs = b.neighborhoodSize();
	int minDir = 0, dirStep = 1;
	if (!allowDiagonalConnections) {
	    // hardwire the fact that in the MooreTopology, the diagonal directions are even-numbered
	    // TODO: make this robust to changes in the implementation of MooreTopology
	    minDir = 1;
	    dirStep = 2;
	}
	Stack<Point> toVisit = new Stack<Point>();
	Point p = new Point(), n = new Point();
	int currentMark = 0;
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		if (mark[x][y] == 0) {
		    ++currentMark;
		    LinkedList<Point> newList = new LinkedList<Point>();

		    p.x = x;
		    p.y = y;

		    BreadthFirstSearch:
		    while (true) {
			for (int d = minDir; d < dirs; d += dirStep) {
			    b.getNeighbor(p,n,d);
			    if (b.onBoard(n) && mark[n.x][n.y] == 0)
				toVisit.push(new Point(n));
			}
			mark[p.x][p.y] = currentMark;
			newList.addFirst (new Point(p));
			while (mark[p.x][p.y] != 0) {
			    if (toVisit.empty())
				break BreadthFirstSearch;
			    p = toVisit.pop();
			}
		    }
		    enclosures.addLast(newList);
		}

	return enclosures;
    }

    public boolean check() {
        if(cond == null)
            return true;

        if(cond.check()) {
            cond = null;
            desc = "Done!";
            return true;
        }
        return false;
    }

    public String getDescription() {
        // TODO: remove these two lines?
        if(desc.length() == 0)
            return cond.getDescription();

        return desc;
    }

    public String getFeedback() {
	return cond.feedback;
    }

    public static abstract class Condition {
        Condition parent = null; // null establishes that this is the root Condition
        String desc = "", feedback = "";

	public abstract boolean check();   // returns true if the condition is satisfied

        public Set<Point> getArea() {
            if(parent != null)
                return parent.getArea();

            return null;
        }
        
        public String getDescription(){
            return desc;
        }
        
        public void setParentCondition(Condition c) {
            parent = c;
        }
        
        public void resetDescription(){
            desc = "";
        }
    }
    
    public static class AreaCondition extends Condition {
        public AreaCondition(Condition c){
            this(null, c, null);
        }

        public AreaCondition(Condition p, Condition c){
            this(p, c, null);
        }

        public AreaCondition(Condition c, Set<Point> a){
            this(null, c, a);
        }

        public AreaCondition(Condition p, Condition c, Set<Point> a){
            parent = p;
            cond = c;
            area = a;

            if(cond != null)
                desc = cond.getDescription();
        }

        Condition cond;
        Set<Point> area = null;
        
        public void setArea(Set<Point> a) {
            area = a;
        }

        public Set<Point> getArea() {
            if(area != null) {
                return new TreeSet<Point>(area);
            }
            
            return null;
        }

        public boolean check() {
            if(cond == null)
                return true;

            return cond.check();
        }
    }
    
    // Returns true if there are requiredEnclosures enclosures of area minArea<=A<=maxArea that meet a condition
    public static class EnclosuresCondition extends Condition {

	static int defaultMinEnclosureSize = 30;
	static String defaultWallPrefix = "wall";

	// set maxArea=0 for unlimited area
        public EnclosuresCondition(ZooGas g, Condition condition, int requiredEnclosures, int minArea, int maxArea, boolean allowDiagonalConnections) {
            board = g.board;
            cond = new AreaCondition(this, condition, null);
            if(condition != null)
                condition.setParentCondition(cond);
            
            count = requiredEnclosures;
            
            if(condition != null)
                desc = "in " + count + " enclosures, " + cond.getDescription();
            else
                desc = "make " + count + " enclosures ";

	    this.minArea = minArea;
	    this.maxArea = maxArea;
	    this.allowDiagonalConnections = allowDiagonalConnections;

	    wallPrefixSet = new TreeSet<String>();
        }

        public EnclosuresCondition(ZooGas g, Condition condition, int requiredEnclosures, int minArea, int maxArea, boolean allowDiagonalConnections, String wallPrefix) {
	    this (g, condition, requiredEnclosures, minArea, maxArea, allowDiagonalConnections);
	    wallPrefixSet.add (wallPrefix);
	}

	// default constructor
        public EnclosuresCondition(ZooGas g, Condition condition) {
	    this(g,condition,2,defaultMinEnclosureSize,0,false,defaultWallPrefix);
	}

	Set<String> wallPrefixSet;
	boolean allowDiagonalConnections;
        Board board;
        AreaCondition cond;
        private int count, minArea, maxArea;

        public boolean check() {
            int n = 0, total = 0;
	    for(List<Point> areaList : getEnclosures(board,wallPrefixSet,allowDiagonalConnections)) {
		int areaSize = areaList.size();
		if (areaSize > minArea && (maxArea == 0 || areaSize < maxArea)) {
		    TreeSet<Point> area = new TreeSet<Point> (areaList);
		    cond.setArea(area);
		    ++total;
		    if(cond.check()) {
			++n;
			if(n >= count)
			    return true;
		    }
		}
            }
	    if (n > 0)
		feedback = "Passed " + n + " times so far";
            
            return false;
        }
    }

    // ThenCondition is like AndCondition, but guarantees to evaluate each Condition only once, and not to evaluate condition #2 until condition #1 is true
    public static class ThenCondition extends Condition {
        public ThenCondition(Condition c1, Condition c2){
            cond1 = c1;
            cond2 = c2;
            cond1.setParentCondition(this);
            cond2.setParentCondition(this);

	    passed1 = passed2 = false;
            
            desc = cond1.getDescription() + "then " + cond2.getDescription() + " ";
        }

        public ThenCondition(Condition p, Condition c1, Condition c2){
            this(c1, c2);
            parent = p;
        }

        Condition cond1, cond2;
	boolean passed1, passed2;

	public boolean check() {
	    if (!passed1)
		passed1 = cond1.check();
	    if (passed1 && !passed2)
		passed2 = cond2.check();
	    return passed1 && passed2;
	}
    }

    // AndCondition does not check condition #2 unless condition #1 is true
    public static class AndCondition extends Condition {
        public AndCondition(Condition c1, Condition c2){
            cond1 = c1;
            cond2 = c2;
            cond1.setParentCondition(this);
            cond2.setParentCondition(this);
            
            desc = cond1.getDescription() + "and " + cond2.getDescription() + " ";
        }

        public AndCondition(Condition p, Condition c1, Condition c2){
            this(c1, c2);
            parent = p;
        }

        Condition cond1, cond2;

	public boolean check() {
	    return cond1.check() && cond2.check();
	}
    }
    
    // OrCondition does not check condition #2 if condition #1 is true
    public static class OrCondition extends Condition {
        public OrCondition(Condition c1, Condition c2){
            cond1 = c1;
            cond2 = c2;
            cond1.setParentCondition(this);
            cond2.setParentCondition(this);
            
            desc = cond1.getDescription() + "or " + cond2.getDescription() + " ";
        }
        public OrCondition(Condition p, Condition c1, Condition c2){
            this(c1, c2);
            parent = p;
        }

        Condition cond1, cond2;

        public boolean check() {
	    return cond1.check() || cond2.check();
        }
    }

    // TrueCondition may seem trivial,
    //  but in combination with e.g. SucceedNTimes, ThenCondition and SprayCondition,
    //  it can be used to introduce delays, delayed conditions, and delayed spray events.
    // Of course, there might be a better way to do this (e.g. an explicit DelayedCondition class).
    public static class TrueCondition extends Condition {
	public boolean check() {
	    return true;
	}
    }
    

    // NotCondition
    public static class NotCondition extends Condition {
        public NotCondition(Condition c){
            cond = c;
            cond.setParentCondition(this);
            
            desc = "do not " + cond.getDescription();
        }

        public NotCondition(Condition p, Condition c){
            this(c);
            parent = p;
        }

        Condition cond;

	public boolean check() {
	    return !cond.check();
	}
    }

    // EncloseParticles is a base Condition that can be used to place tests on Particles with a given prefix word and minimum population
    public static class EncloseParticles extends Condition {
        public EncloseParticles(int count, String prefix, Board b) {
            c = count;
            board = b;
            this.particlePrefix = prefix;
	    desc = "place " + c + " " + prefix + (c > 1? "s" : "") + " ";
        }

        public EncloseParticles(Condition p, int count, String prefix, Board b) {
            this(count, prefix, b);
            parent = p;
        }
        
        private int c = 1;
        String particlePrefix;
        Board board;

	// member variables set by check()
	int totalParticles;
	Map<Particle,Set<Point>> particleLocations;
                
        public boolean check() {
	    Set<Point> area = getArea();
	    Set<Particle> particles = board.getParticlesByPrefix(particlePrefix);

	    particleLocations = new TreeMap<Particle,Set<Point>>();
	    totalParticles = 0;
	    for (Particle particle : particles) {
		Set<Point> pArea = particle.getOccupiedPoints();

		if (area != null)
		    pArea.retainAll(area);

		particleLocations.put (particle, pArea);
		totalParticles += pArea.size();
	    }
	    if (totalParticles > 1)
		feedback = "There are " + totalParticles + " so far";
            return totalParticles >= c;
        }
    }

    // EnclosedParticleEntropy can be used to test the diversity of a population
    public static class EnclosedParticleEntropy extends EncloseParticles {
        public EnclosedParticleEntropy(int count, String prefix, Board b, double minEntropy) {
	    super(count,prefix,b);
	    this.minEntropy = minEntropy;
	    desc = desc + " with diversity score " + Math.exp(minEntropy);
        }

        public EnclosedParticleEntropy(Condition p, int count, String prefix, Board b, double minEntropy) {
            this(count, prefix, b, minEntropy);
            parent = p;
        }
        
	double minEntropy;

        public boolean check() {
	    super.check();
	    double entropy = 0;
	    for (Set<Point> locations : particleLocations.values()) {
		double p = (double) locations.size() / (double) totalParticles;
		entropy -= p * Math.log(p);
	    }
	    return entropy >= minEntropy;
        }
    }
    
    // SucceedNTimes can be used to test for a condition holding true over a continuous period of time.
    // The time period resets as soon as the condition stops being true.
    public static class SucceedNTimes extends Condition {
        public SucceedNTimes(ZooGas gas, Condition p, Condition condition, int n){
            cond = condition;
            count = n;
            
            desc = "for at least " + ((double) count / (double) gas.targetUpdateRate) + " seconds, " + cond.getDescription();
        }

        Condition cond;
        private int count = 1;
        private int successes = 0;

        public boolean check() {

            if(cond == null || cond.check()) {
                if(++successes >= count)
                    return true;
                return false;
            }
            
            successes = 0;
            return false;
        }
    }

    // SprayEvent can be hooked up to a parent AreaCondition or EnclosuresCondition, otherwise it will spray anywhere on the board.
    // It succeeds if at least one particle was sprayed.
    public static class SprayEvent extends Condition {

        public SprayEvent(Board board,SprayTool tool,String oldPrefix){
	    this.board = board;
	    this.tool = tool;
	    this.oldPrefix = oldPrefix;
        }

        public SprayEvent(Board board,SprayTool tool){
	    this (board, tool, board.spaceParticle.prefix);
	}

	Board board;
	SprayTool tool;
	String oldPrefix;

	public boolean check() {
	    Set<Point> areaSet = getArea();
	    Point sprayPoint;
	    if (areaSet != null) {
		Vector<Point> area = new Vector<Point> (areaSet);
		int n = (int) (Math.random() * area.size());
		sprayPoint = area.elementAt(n);
	    } else {  // no parent area; spray anywhere on the board
		sprayPoint = new Point();
		sprayPoint.x = (int) (Math.random() * board.size);
		sprayPoint.y = (int) (Math.random() * board.size);
	    }
	    return tool.spray (sprayPoint, board, null, oldPrefix);
	}
    }
}
