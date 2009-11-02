// Point: a 2D integer vector/coordinate class
public class Point {
    public int x,y;

    public Point() { x = y = 0; }
    public Point(int x,int y) { this.x = x; this.y = y; }
    public Point(Point p) { if (p != null) { this.x = p.x; this.y = p.y; } }
    public Point(java.awt.Point p) { if (p != null) { this.x = p.x; this.y = p.y; } }

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

    public String toString() {
	return "(" + x + "," + y + ")";
    }
}
