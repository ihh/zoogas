// Point: a 2D integer vector/coordinate class
public class Point extends java.awt.Point {
    public Point() { super(); }
    public Point(int x,int y) { super(x,y); }
    public Point(java.awt.Point p) { super(); if (p != null) { x = p.x; y = p.y; } }

    public void add(Point q,Point result) {
	result.x = x + q.x;
	result.y = y + q.y;
    }

    public Point add(Point q) {
	return new Point (x+q.x, y+q.y);
    }

    public void subtract(Point q,Point result) {
	result.x = x - q.x;
	result.y = y - q.y;
    }

    public Point subtract(Point q) {
	return new Point (x-q.x, y-q.y);
    }

    public boolean equals(Point p) {
	return p == null ? false : (x == p.x && y == p.y);
    }

    public int hashCode() {
	return x ^ Integer.reverse(y);
    }

    public String toString() {
	return "(" + x + "," + y + ")";
    }
}
