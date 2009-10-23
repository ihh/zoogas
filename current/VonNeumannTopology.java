import java.awt.*;
import java.util.*;

public class VonNeumannTopology {
    // method to sample a random neighbor of a given cell, returning the directional index
    static public int getRandomNeighbor (Point p, Point n, Random rnd) {
	int ni = rnd.nextInt(4);
	n.x = p.x;
	n.y = p.y;
	int delta = (ni & 2) == 0 ? -1 : +1;
	if ((ni & 1) == 0) { n.y += delta; } else { n.x += delta; }
	return ni;
    }

    // number of neighbors of any cell (some may be off-board and therefore inaccessible)
    static public int neighborhoodSize() { return 4; }

    // string representations of cardinal directions
    static private String[] dirStr = { "n", "w", "s", "e" };
    static public String dirString(int dir) { return dirStr[dir]; }

    // method to draw a cell
    static public void drawRect (Point p, Graphics g, int pixelsPerCell) {
	g.fillRect(p.x*pixelsPerCell,p.y*pixelsPerCell,pixelsPerCell,pixelsPerCell);
    }

    // method to return board size for a given number of cells
    static public int getBoardSize (int size, int pixelsPerCell) {
	return size*pixelsPerCell;
    }
}
