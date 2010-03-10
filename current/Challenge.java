import java.awt.Polygon;

import java.awt.Rectangle;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class Challenge
{
    public Challenge(Board b) {
        this(b, null);
    }
    public Challenge(Board b, Condition c) {
	board = b;
        cond = c;
    }

    Board board;
    private String desc = "";
    Condition cond;

    public static Set<Set<Point>> getEnclosures(Board b) {
	SortedSet<Point> allWalls = getWallParticles(b);
	SortedSet<Point> walls = Collections.synchronizedSortedSet(new TreeSet<Point>(allWalls));
	HashSet<Set<Point>> enclosures = new HashSet<Set<Point>>();

	// Check if there are walls, then see if those walls make cages
	if(walls.size() > 3)
	{
	    // Consider only walls can form a side of a cage
	    SortedSet<Point> tempSet = new TreeSet<Point>(walls);
	    List<Point> neighbors;
	    {
		Point p = null;
		while(!tempSet.isEmpty()){
		    if(p == null)
			p = tempSet.first();

		    neighbors = getNeighbors(walls, p);
		    tempSet.remove(p);

		    if(neighbors.size() < 2)
		    {
			walls.remove(p);
			if(neighbors.size() == 1)
			{
			    p = neighbors.get(0);
			    continue;
			}
		    }

		    p = null;
		}
	    }

	    // Remove walls that don't touch a non-wall cell
	    Point[] tempArr = new Point[walls.size()];
	    walls.toArray(tempArr);
	    for(Point p : tempArr) {
		if(getNeighbors(walls, p).size() + getNeighbors(tempSet, p).size() + getDiagNeighbors(walls, p).size() + getDiagNeighbors(tempSet, p).size() == 8)
		{
		    walls.remove(p);
		    tempSet.add(p);
		}
	    }

	    Point start;
	    while(!walls.isEmpty()) {
		// Make a polygon
		start = walls.first();
		tempSet.clear();

		Polygon poly = tracePolygon(walls, tempSet, new HashSet<Point>(), start, start, null);
		if(poly == null) {
		    walls.remove(start);
		}
		else {
		    walls.removeAll(tempSet);

		    TreeSet<Point> tempSet2 = new TreeSet<Point>();
		    Rectangle bounds = poly.getBounds();
		    for(int x = (int)bounds.getX(); x < bounds.getX() + bounds.getWidth(); ++x) {
			for(int y = (int)bounds.getY(); y < bounds.getY() + bounds.getHeight(); ++y) {
			    Point q = new Point(x, y);
			    if(!allWalls.contains(q) && poly.contains(q))
				tempSet2.add(new Point(x, y));
			}
		    }
		    enclosures.add(tempSet2);
		}
	    }
	}
	return enclosures;
    }
    private static Polygon tracePolygon(Set<Point> walls, Set<Point> added, Set<Point> eliminated, Point start, Point current, Point last) {
	boolean finished = false;
	added.add(current);
	List<Point> neighbors = getNeighbors(walls, current);
	if(neighbors.size() < 2)
	    return null;

	int i;
	int temp;
	if(last != null) {
	    temp = neighbors.indexOf(last) % neighbors.size();
	    i = (temp + 1) % neighbors.size();
	}
	else {
	    temp = neighbors.size() - 1;
	    i = 0;
	}
	for(; i != temp; i = (i + 1) % neighbors.size()) {
	    Point p = neighbors.get(i % neighbors.size());
	    if(!(added.contains(p) || eliminated.contains(current))) {
		Polygon poly = tracePolygon(walls, added, eliminated, start, p, current);
		if(poly != null) {
		    poly.addPoint(current.x, current.y);
		    return poly;
		}
	    }
	    else if(p.equals(start))
		finished = true;
	}
	if(!finished)
	{
	    added.remove(current);
	    eliminated.add(current);
	    return null;
	}
	else {
	    Polygon poly = new Polygon();
	    poly.addPoint(current.x, current.y);
	    return poly;
	}
    }

    static String wallPrefix = "wall";
    public static TreeSet<Point> getWallParticles(Board b) {
	TreeSet<Point> walls = new TreeSet<Point>();

	if (b.gotPrefix(wallPrefix))
	    for(Particle p : b.getParticlesByPrefix(wallPrefix)) {
		Set<Point> wallSet = p.getOccupiedPoints();
		synchronized(wallSet) {
		    walls.addAll(wallSet);
		}
	    }

	return walls;
    }

    // Adds all neighboring walls inside the set to a SortedSet
    // Neighbors MUST be added in order: left, up, right, then down
    public static List<Point> getNeighbors(Set<Point> walls, Point p) {
	ArrayList<Point> neighbors = new ArrayList<Point>();
	Point q;
	for(int dir = 0; dir < 4; ++dir) {
	    q = new Point(p);
	    switch(dir)
	    {
		case 0:
		    q.x--;
		    break;
		case 1:
		    q.y--;
		    break;
		case 2:
		    q.x++;
		    break;
		case 3:
		    q.y++;
		    break;
	    }
	    if(walls.contains(q))
		neighbors.add(q);
	}

	return neighbors;
    }
    public static Set<Point> getDiagNeighbors(Set<Point> walls, Point p) {
	HashSet<Point> neighbors = new HashSet<Point>();
	Point q;
	for(int dir = 0; dir < 4; ++dir) {
	    q = new Point(p);
	    switch(dir)
	    {
		case 0:
		    q.x--;
		    q.y--;
		    break;
		case 1:
		    q.x++;
		    q.y--;
		    break;
		case 2:
		    q.x--;
		    q.y++;
		    break;
		case 3:
		    q.x++;
		    q.y++;
		    break;
	    }
	    if(walls.contains(q))
		neighbors.add(q);
	}

	return neighbors;
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

	public abstract boolean check();

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
        public EnclosuresCondition(Board b, Condition p, Condition condition, int n){
            board = b;
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
            for(Set<Point> area : getEnclosures(board)) {
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
	// TODO: particleName should be a regular expression so it can match multiple Particle names
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
            
            desc = "for at least " + 20 * count + " updates, " + cond.getDescription();
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
