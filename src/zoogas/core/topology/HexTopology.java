import java.util.*;

public class HexTopology extends Topology{
    // method to sample a random neighbor of a given cell, returning the directional index
    final public int getNeighbor (Point p, Point n, int ni) {
	n.x = p.x;
	n.y = p.y;
	int k = p.y & 1;
	if (ni < 2) {
	    n.x += k;
	    --n.y;
	} else if (ni == 3 || ni == 4) {
	    n.x += k;
	    ++n.y;
	}
	if (ni == 0 || ni >= 4)
	    --n.x;
	else if (ni == 2)
	    ++n.x;
	return ni;
    }

    // number of neighbors of any cell (some may be off-board and therefore inaccessible)
    final public int neighborhoodSize() { return 6; }

    // string representations of cardinal directions
    static private String[] dirStr = { "nw", "ne", "e", "se", "sw", "w" };
    final public String dirString(int dir) { return dirStr[dir]; }

    // method to convert cell coords to graphics coords
    final public Point getGraphicsCoords(Point pCell, int pixelsPerCell) {
        return new Point((pCell.x * 2 + (pCell.y & 1)) * pixelsPerCell / 2, pCell.y * pixelsPerCell);
    }

    // method to convert graphics coords to cell coords
    final public void getCellCoords (java.awt.Point pGraphics, Point pCell, int pixelsPerCell) {
	pCell.y = pGraphics.y / pixelsPerCell;
	pCell.x = (pGraphics.x - ((pCell.y & 1) * pixelsPerCell/2)) / pixelsPerCell;
    }

    // method to return board size for a given number of cells
    final public int getBoardSize (int size, int pixelsPerCell) {
	return size*pixelsPerCell + pixelsPerCell/2;
    }
}
