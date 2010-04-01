import java.awt.Rectangle;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics.*;

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
    // Challenge.Giver
    public static class Giver {
	static private int defaultTimePeriod = 5;  // time in seconds between states
	static private int spontaneousHintDelay = 20, maxHintDisplayTime = spontaneousHintDelay, minHintDisplayTime = 5;
	static private double spontaneousHintRate = .01;

	public enum State { GivingChallenge, Waiting, GivingFeedback, GivingHint, GivingReward, OutOfChallenges };
	public State state = State.OutOfChallenges;
	public int timeInState = 0;
	public int challengesCompleted = 0;

	private Vector<String> hints = new Vector<String>();
	private int currentHint = 0;
	public void addHint(String hint) { hints.add(hint); }

	private ZooGas gas;
	private LinkedList<Challenge> objectives = new LinkedList<Challenge>();
	private int speakingTime = defaultTimePeriod, rewardTime = defaultTimePeriod;

	Giver (ZooGas g) {
	    gas = g;
	}

	Giver (ZooGas g, int t) {
	    gas = g;
	    rewardTime = t;
	}

	public Challenge objective() {
	    if (objectives != null && objectives.size() > 0)
		return objectives.get(0);
	    return null;
	}

	public boolean hasObjective() {
	    return objective() != null;
	}

	public String getDescription() {
	    if (hasObjective())
		return (state == State.GivingReward ? "Passed: " : "Goal: ") + objective().getDescription();
	    return "";
	}

	private String lastFeedback = "";
	public String getFeedback() {
	    String oFeedback = (objective() == null ? null : objective().getFeedback());
	    if (oFeedback!=null && !lastFeedback.equals(oFeedback)) {
		lastFeedback = oFeedback;
		if ((state == State.GivingHint && timeInState > minHintDisplayTime)
		    || state == State.GivingFeedback || state == State.Waiting)
		    setState(State.GivingFeedback);
	    }

	    String f = "";
	    switch (state) {
	    case GivingHint:
		f = hints.elementAt(currentHint);
		break;
	    case Waiting:
	    case GivingFeedback:
	    case GivingReward:
		f = oFeedback;
		break;
	    default:
		break;
	    }
	    return f;
	}

	public void giveHint() {
	    if (state != State.GivingReward) {
		int lastHint = currentHint;
		do {
		    currentHint = (int) (Math.random() * hints.size());
		} while (currentHint == lastHint);
		setState(State.GivingHint);
	    }
	}

	// avatar
	// irregular (square-eye avatar jiggle) or clock-like (pacman mouth movement) displacements of the avatar
	private int avatarXoffset = 0, avatarYoffset = 0, maxOffset = 2;
	public void animate() { return; }   // slower animation hook

	private void rotateAvatarYoffset() {
	    avatarYoffset = avatarSpeaking()
		? ((avatarYoffset + 1) % maxOffset)
		: (avatarYoffset > 0 ? (avatarYoffset-1) : 0);
	}

	private void brownianAvatarOffset() {
	    if (Math.random() < .1) {
		if (avatarSpeaking()) {
		    if (Math.random() < .5) {
			if (avatarXoffset < maxOffset) ++avatarXoffset;
		    } else {
			if (avatarXoffset > 0) --avatarXoffset;
		    }
		    if (Math.random() < .5) {
			if (avatarYoffset < maxOffset) ++avatarYoffset;
		    } else {
			if (avatarYoffset > 0) --avatarYoffset;
		    }
		} else {
		    if (avatarXoffset > 0) --avatarXoffset;
		    if (avatarYoffset > 0) --avatarYoffset;
		}
	    }
	}

	// progression of the avatar leftwards
	private int avatarXpos = 0, avatarTargetXpos = 0;
	static private int challengeWidth = 32;
	public void drawAvatar(Graphics g,int x,int y,int w,int h) {
	    avatarXpos -= Integer.signum(avatarXpos - avatarTargetXpos);
	    int realXpos = Math.max(x-avatarXpos,0);
	    drawPacmanAvatar(g,realXpos,y,w,h);
	    //	    drawSquareAvatar(g,realXpos,y,w,h);
	    boolean hasDescription = hasObjective() && state != State.OutOfChallenges,
		hasFeedback = state == State.GivingHint || getFeedback() != "";
	    if (hasDescription || hasFeedback)
		drawAvatarBalloon(gas,g,Math.max(x-avatarXpos,0),y+h);
	}

	// speaking?
	public boolean avatarSpeaking() {
	    return (state == State.GivingChallenge || state == State.GivingReward || state == State.GivingFeedback || state == State.GivingHint)
		&& timeInState < speakingTime;
	}

	private boolean cheating() { return gas.cheatPressed; }
	public void drawAvatarBalloon(ZooGas gas,Graphics g,int x,int y) {
	    String[] cgText = new String[2];
	    cgText[0] = getDescription();
	    cgText[1] = getFeedback() + (cheating()? (" :" + timeInState): "");
	    Color[] cgColor = new Color[2];
	    int descIntensity = state==State.GivingReward? Math.max(255-255*timeInState/rewardTime,0): 255;
	    cgColor[0] = new java.awt.Color(descIntensity,descIntensity,descIntensity);
	    cgColor[1] = new java.awt.Color(state==State.GivingHint? Math.max(255-timeInState*4,0) :0,
					    Math.max(255-timeInState*3,0),
					    0);
	    java.awt.Point bSize = gas.balloonSize(g,cgText);
	    gas.drawSpeechBalloonAtGraphicsCoords (g,
						   new java.awt.Point (x, y),
						   new java.awt.Point (Math.max (x - bSize.x, 10), y + 3*bSize.y),
						   4,
						   cgText, cgColor,
						   avatarSpeaking() ? Color.white : Color.gray,
						   avatarSpeaking() ? Color.white : Color.black,
						   Color.black);

	}

	// square-faced avatar
	private void drawSquareAvatar(Graphics g,int x,int y,int w,int h) {

	    maxOffset = 2;
	    brownianAvatarOffset();

	    int i = avatarXoffset - maxOffset/2;
	    int j = avatarYoffset - maxOffset/2;

	    // face
	    g.setColor(Color.cyan);
	    g.drawRect(x+1,(int)(y+h*.4),w-3,h-2);

	    // eyes
	    g.setColor(Color.yellow);
	    g.fillRect((int)(x+w*.2),j+(int)(y+h*.5),(int)(w*.25),(int)(h*.25));
	    g.fillRect(-i+(int)(x+w*.55),(int)(y+h*.5),(int)(w*.25),(int)(h*.25));

	    g.setColor(Color.black);
	    g.fillRect((int)(x+w*.25),j+(int)(y+h*.7),(int)(w*.1),(int)(h*.1));
	    g.fillRect(-i+(int)(x+w*.6),(int)(y+h*.7),(int)(w*.1),(int)(h*.1));

	    // mouth
	    g.setColor(Color.yellow);
	    g.fillRect(-i+(int)(x+w*.3),-j+(int)(y+h*.9),2*i+(int)(w*.4),2*j+(int)(h*.4));

	    // hat
	    drawAvatarHat(g,x+i,y,w,h);
	}

	private void drawAvatarHat(Graphics g,int x,int y,int w,int h) {
	    // hat
	    g.setColor(new Color(0,64,0));
	    g.fillRect((int)(x-w*.3),(int)(y+h*.3),(int)(w*1.3),(int)(h*.1));
	    g.fillRect((int)(x),(int)(y),(int)(w*1),(int)(h*.4));

	    // logo
	    g.setColor(new Color(16,32,16));
	    g.drawString("zoo",(int)(x+w*.1),(int)(y+ZooGas.charHeight(g)/2));
	}

	// Pac-man avatar
	private void drawPacmanAvatar(Graphics g,int x,int y,int w,int h) {

	    maxOffset = 6;
	    rotateAvatarYoffset();

	    // face
	    g.setColor(Color.yellow);
	    g.fillOval(x,y,w-1,h-1);

	    // mouth
	    g.setColor(Color.black);
	    g.fillArc(x,y,w-1,h-1,180-4*avatarYoffset-15,8*avatarYoffset+30);

	    // hat
	    drawAvatarHat(g,(int)(x+w*.1),(int)(y-h*.1),w,h);
	}

	public void addObjective(Challenge c) {
	    objectives.add(c);
	}

	public void setState(State s) {
	    state = s;
	    timeInState = 0;
	}

	public boolean checkObjective() {
	    boolean passed = objective()==null? false: objective().check();
	    if (passed)
		giveReward();
	    return passed;
	}

	private void giveReward() {
	    setState(State.GivingReward);
	    ++challengesCompleted;
	    avatarTargetXpos = challengesCompleted * challengeWidth;
	}

	private void gotoWait() {
	    setState(hasObjective()? State.Waiting: State.OutOfChallenges);
	}

	public void check() {
	    ++timeInState;
	    switch (state) {
	    case GivingChallenge:
		if (timeInState > speakingTime)
		    gotoWait();
		break;
	    case GivingHint:
		if (timeInState >= maxHintDisplayTime)
		    gotoWait();
		checkObjective();
		break;
	    case GivingFeedback:
		if (timeInState >= speakingTime)
		    gotoWait();
		checkObjective();
		break;
	    case Waiting:
		if (!checkObjective())
		    if (timeInState > spontaneousHintDelay && Math.random() < spontaneousHintRate)
			giveHint();
		break;
	    case GivingReward:
		if (timeInState > rewardTime)
		    nextObjective();
		break;
	    case OutOfChallenges:
		if (hasObjective())
		    setState(State.GivingChallenge);
		break;
	    default:
		break;
	    }
	}

	private void nextObjective() {
	    objectives.removeFirst();
	    if (objective() == null)
		setState(State.OutOfChallenges);
	    else
		setState(State.GivingChallenge);
	}
    }


    // Challenge
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
    public String rewardText = "Done!";
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

    // expect check() to be called once per turn
    public boolean check() {
        if(cond == null)
            return true;

        if(cond.check()) {
	    desc = cond.getDescription();  // save description
            cond = null;
            return true;
        }
        return false;
    }

    public boolean passed() {
	return cond == null;
    }

    public String getDescription() {
        if(desc.length() == 0)
            return cond==null ? "" : cond.getDescription();

        return desc;
    }

    public String getFeedback() {
	return cond==null ? rewardText : cond.getFeedback();
    }

    // Challenge.Condition
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
        
        public String getFeedback(){
            return feedback;
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
		if (areaSize >= minArea && (maxArea == 0 || areaSize <= maxArea)) {
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
		feedback = "That's " + n;
            
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
            
            desc = cond1.getDescription() + "then " + cond2.getDescription();
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
            
            desc = cond1.getDescription();
	    if (desc.length() > 0 && cond2.getDescription().length() > 0)
		desc += " and ";
	    desc += cond2.getDescription();
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
            
            desc = cond1.getDescription();
	    if (desc.length() > 0 && cond2.getDescription().length() > 0)
		desc += " or ";
	    desc += cond2.getDescription();
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
	    desc = "place " + c + " " + Particle.visibleText(prefix) + (c > 1? "s" : "");
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
	    if (totalParticles > 0)
		feedback = "There " + (totalParticles>1 ? "are " : "is ") + totalParticles + " so far";
            return totalParticles >= c;
        }
    }

    // EnclosedParticleEntropy can be used to test the diversity of a population
    public static class EnclosedParticleEntropy extends EncloseParticles {
        public EnclosedParticleEntropy(int count, String prefix, double divScore, Board b) {
	    super(count,prefix,b);
	    this.minEntropy = Math.log(divScore);
	    desc = desc + " with diversity " + String.format("%.2f", divScore) + "+";
        }

        public EnclosedParticleEntropy(Condition p, int count, String prefix, double minEntropy, Board b) {
            this(count, prefix, minEntropy, b);
            parent = p;
        }
        
	double minEntropy;
	protected double entropy = 0, bestEntropy = 0;
	String entropyFeedback = "";
        public boolean check() {
	    super.check();
	    entropy = 0;
	    for (Set<Point> locations : particleLocations.values()) {
		if (locations.size() > 0) {
		    double p = (double) locations.size() / (double) totalParticles;
		    entropy -= p * Math.log(p);
		}
	    }
	    entropyFeedback = "diversity " + String.format("%.2f", Math.exp(entropy));
	    if (entropy > bestEntropy) {
		bestEntropy = entropy;
		entropyFeedback += " (new best!)";
	    } else
		entropyFeedback += " (best " + String.format("%.2f", Math.exp(bestEntropy)) + ")";
	    return entropy >= minEntropy;
        }

	public String getFeedback() {
	    return feedback + "; " + entropyFeedback;
	}
    }

    
    // SucceedNTimes can be used to test for a condition holding true over a continuous period of time.
    // The time period resets as soon as the condition stops being true.
    public static class SucceedNTimes extends Condition {
        public SucceedNTimes(ZooGas gas, int n, Condition condition){
            cond = condition;
            count = n;
            
            desc = "for " + count + "+ turns, " + cond.getDescription();
        }

        public SucceedNTimes(ZooGas gas, int n, Condition condition, Condition p){
	    this(gas,n,condition);
	    parent = p;
        }

        Condition cond;
        private int count = 1;
        private int successes = 0;

        public boolean check() {

            if(cond == null || cond.check()) {
                if(++successes >= count)
                    return true;
		feedback = "Turns so far: " + successes + "/" + count;
                return false;
            }

	    feedback = cond == null ? "" : cond.getFeedback();
            successes = 0;
            return false;
        }
    }

    // SprayEvent can be hooked up to a parent AreaCondition or EnclosuresCondition, otherwise it will spray anywhere on the board.
    // It succeeds if at least one particle was sprayed.
    public static class SprayEvent extends Condition {

	BoardRenderer renderer;
        public SprayEvent(Board board,BoardRenderer renderer,SprayTool tool,String oldPrefix){
	    this.board = board;
	    this.renderer = renderer;
	    this.tool = tool;
	    this.oldPrefix = oldPrefix;
	    this.desc = "cope with " + tool.particle.visibleName() + "s";
        }

        public SprayEvent(Board board,BoardRenderer renderer,SprayTool tool){
	    this (board, renderer, tool, board.spaceParticle.prefix);
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
	    boolean success = tool.spray (sprayPoint, board, renderer, oldPrefix);
	    tool.refill();
	    return success;
	}
    }
}
