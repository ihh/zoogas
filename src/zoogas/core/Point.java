// Point: a 2D integer vector/coordinate class
public class Point extends java.awt.Point implements Comparable{
    public Point() { super(); }
    public Point(int x,int y) { super(x,y); }
    public Point(java.awt.Point p) { super(); if (p != null) { x = p.x; y = p.y; } }

    public Point add(Point q) {
	return new Point (x+q.x, y+q.y);
    }

    public Point subtract(Point q) {
	return new Point (x-q.x, y-q.y);
    }

    public Point multiply(int s) {
	return new Point (x * s, y * s);
    }

    public String toString() {
	return "(" + x + "," + y + ")";
    }

    public int compareTo(Object o) {
	if(o instanceof Point)
	{
	    Point otherPoint = (Point)o;
	    if(x != otherPoint.x)
		return x - otherPoint.x;
	    else
		return y - otherPoint.y;
	}

	return -1;
    }
}
