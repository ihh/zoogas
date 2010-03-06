public abstract class Topology {
    public abstract int getNeighbor (Point p, Point n, int ni);
    public abstract int neighborhoodSize();

    public abstract String dirString(int dir);
    public abstract Point getGraphicsCoords(Point pCell, int pixelsPerCell);
    public abstract void getCellCoords(java.awt.Point pGraphics, Point pCell, int pixelsPerCell);
    /**
     * Returns the board size in pixels for a given number of cells
     * @Deprecated
     * @param size
     * @param pixelsPerCell
     * @return
     */
    public int getBoardSize (int size, int pixelsPerCell) {
        return size*pixelsPerCell;
    }
    
    /**
     *Gets the angle between two vectors in units of PI
     * @param p
     * @return
     */
    public final double angle(Point p, Point q) {
        double a = (Math.atan2(p.y,p.x) - Math.atan2(q.y,q.x)) / Math.PI;
        if (a <= -1)
            a += 2;
        else if (a > 1)
            a -= 2;
        return a;
    }
    
    /**
     *Gets the Euclidean distance from the origin to a point
     * @param p
     * @return
     */
    public final double directLength(Point p) {
        return Math.sqrt(p.x*p.x+p.y*p.y);
    }
}
