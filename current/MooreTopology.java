import java.util.*;

public class MooreTopology {
    // method to sample a random neighbor of a given cell, returning the directional index
    static final public int getNeighbor (Point p, Point n, int ni) {
	n.x = p.x;
	n.y = p.y;
	if (ni < 3)
	    --n.y;
	else if (ni >= 4 && ni <= 6)
	    ++n.y;
	if (ni == 0 || ni >= 6)
	    --n.x;
	else if (ni >= 2 && ni <= 4)
	    ++n.x;
	return ni;
    }

    // number of neighbors of any cell (some may be off-board and therefore inaccessible)
    static final public int neighborhoodSize() { return 8; }

    // string representations of cardinal directions
    static private String[] dirStr = { "nw", "n", "ne", "e", "se", "s", "sw", "w" };
    static final public String dirString(int dir) { return dirStr[dir]; }

    // method to convert cell coords to graphics coords
    static final public void getGraphicsCoords (Point pCell, Point pGraphics, int pixelsPerCell) {
	pGraphics.x = pCell.x * pixelsPerCell;
	pGraphics.y = pCell.y * pixelsPerCell;
    }

    // method to convert graphics coords to cell coords
    static final public void getCellCoords (Point pGraphics, Point pCell, int pixelsPerCell) {
	pCell.x = pGraphics.x / pixelsPerCell;
	pCell.y = pGraphics.y / pixelsPerCell;
    }

    // method to return board size for a given number of cells
    static final public int getBoardSize (int size, int pixelsPerCell) {
	return size*pixelsPerCell;
    }
}
