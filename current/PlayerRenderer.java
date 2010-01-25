import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class PlayerRenderer extends BoardRenderer{
    PlayerRenderer (Board board, int size) {
        this.board = board;
        int pixelsPerSide = getBoardSize(size);

        image = new BufferedImage(pixelsPerSide, pixelsPerSide, BufferedImage.TYPE_3BYTE_BGR);
        bfGraphics = image.createGraphics();
    }
    PlayerRenderer (ZooGas gas, Board board, int size) {
        this.board = board;
        this.gas = gas;
        int pixelsPerSide = getBoardSize(size);

        image = new BufferedImage(pixelsPerSide, pixelsPerSide, BufferedImage.TYPE_3BYTE_BGR);
        bfGraphics = image.createGraphics();
    }

    ZooGas gas; // TODO: remove this temp object
    public Board board;
    Graphics bfGraphics;

    // BoardRenderer methods
    public void drawCell(Point p) {
        bfGraphics.setColor(board.readCell(p).color);
        Point q = getGraphicsCoords(p);
        bfGraphics.fillRect(q.x, q.y, pixelsPerCell, pixelsPerCell);
    }

    /**
     * Draw the verbs in the status panel
     * This method should be implemented differently
     * @Deprecated
     * @param p
     * @param n
     * @param oldSource
     * @param oldTarget
     * @param newPair
     */
    public void showVerb(Point p, Point n, Particle oldSource, Particle oldTarget, UpdateEvent newPair) {
        if (gas.verbsSinceLastRefresh == 0) {
            if (gas.cheatPressed || newPair.visibleVerb().length() > 0) {
                // check for duplicates
                boolean foundDuplicate = false;
                for (int v = 0; v < gas.verbHistoryLength; ++v)
                    if (newPair.verb.equals(gas.verbHistory[v]) && oldSource.color.equals(gas.nounHistory[v].color)) {
                        foundDuplicate = true;
                        break;
                    }
                if (!foundDuplicate) {
                    gas.verbHistoryPos = (gas.verbHistoryPos + 1) % gas.verbHistoryLength;
                    gas.verbHistory[gas.verbHistoryPos] = newPair.verb;
                    gas.nounHistory[gas.verbHistoryPos] = oldSource;
                    ++gas.verbsSinceLastRefresh;
                }
            }
        }
    }
}
