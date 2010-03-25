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


    public static List<List<Point>> getEnclosures (Board b, String wallPrefix) {
	Set<String> wallPrefixes = new TreeSet<String>();
	wallPrefixes.add(wallPrefix);
	return getEnclosures(b,wallPrefixes);
    }


    public static List<List<Point>> getEnclosures (Board b, Set<String> wallPrefixes) {

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
			for (int d = 0; d < dirs; ++d) {
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

    public static abstract class Condition {
        Condition parent = null; // null establishes that this is the root Condition
        String desc = "";

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
    
    // Returns true if there are count enclosures that meet a condition
    public static class EnclosuresCondition extends Condition {
        public EnclosuresCondition(ZooGas g, Condition p, Condition condition, int n){
            board = g.board;
            cond = new AreaCondition(this, condition, null);
            if(condition != null)
                condition.setParentCondition(cond);
            
            count = n;
            
            if(condition != null)
                desc = "in " + count + " enclosures, " + cond.getDescription();
            else
                desc = "make " + count + " enclosures ";
        }

        Board board;
        AreaCondition cond;
        private int count = 1;

        public boolean check() {
            int n = 0;
            for(List<Point> areaList : getEnclosures(board,"wall")) {
		TreeSet<Point> area = new TreeSet<Point> (areaList);
                cond.setArea(area);

                if(cond.check()) {
                    ++n;
                    if(n >= count)
                        return true;
                }
            }
            
            return false;
        }
    }

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
    
    public static class EncloseParticles extends Condition {
	// TODO: particleName should be a regular expression (so it can match multiple Particle names), or a set of Particles (or prefixes)
        public EncloseParticles(int count, String particleName, Board b) {
            c = count;
            board = b;
            this.particleName = particleName;
            
            if(!setParticle(particleName))
                desc = "???"; // TODO: fix this hack for allowing particles that are not initialized in the particle names set
        }
        public EncloseParticles(Condition p, int count, String particleName, Board b) {
            this(count, particleName, b);
            parent = p;
        }
        
        private int c = 1;
        Particle particle;
        String particleName;
        Board board;
        
        private boolean setParticle(String particleName) {
            particle = board.getParticleByName(particleName);
            if(particle == null)
                return false;
            
            if(getArea() != null)
                desc = "enclose " + c + " " + particle.visibleName() + (c > 1? "s " : " ");
            else
                desc = "place " + c + " " + particle.visibleName() + (c > 1? "s " : " ");
                //desc = "place " + c + " " + particle.visibleName() + (c > 1? "s " : " ") + "anywhere";
            
            return true;
        }
        
        public boolean check() {
            // TODO: throw an exception?
            if(particle == null) {
                if(!setParticle(particleName))
                    return false;
            }

            Set<Point> area = getArea();

            if(area == null) {
                return particle.getReferenceCount() >= c;
            }

            area.retainAll(particle.getOccupiedPoints());
            return area.size() >= c;
        }
    }
    
    public static class SucceedNTimes extends Condition {
        public SucceedNTimes(Condition p, Condition condition, int n){
            cond = condition;
            count = n;
            
            desc = "for at least " + count + " turns, " + cond.getDescription();
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
}
