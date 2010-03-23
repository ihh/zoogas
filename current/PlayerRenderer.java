import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class PlayerRenderer extends BoardRenderer{
    static double balloonRate = .0001;

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
     * Store the verbs in the verb history
     * @param updateEvent
     */
    public void showVerb(UpdateEvent updateEvent) {
	double showBalloonProbability = balloonRate / updateEvent.pattern.P;
        if (gas.verbsSinceLastRefresh == 0) {
            if (gas.cheatPressed || updateEvent.visibleVerb().length() > 0) {
                // check for duplicates
                boolean foundDuplicate = false;
                for (int v = 0; v < gas.verbHistoryLength; ++v)
                    if (updateEvent.verb.equals(gas.verbHistory[v]) && updateEvent.oldSource.color.equals(gas.nounHistory[v].color)) {
                        foundDuplicate = true;
                        break;
                    }
                if (!foundDuplicate && Math.random() < showBalloonProbability) {
                    gas.verbHistoryPos = (gas.verbHistoryPos + 1) % gas.verbHistoryLength;
                    gas.verbHistory[gas.verbHistoryPos] = updateEvent.verb;
                    gas.nounHistory[gas.verbHistoryPos] = updateEvent.oldSource;
                    gas.placeHistory[gas.verbHistoryPos] = updateEvent.sourceCoords;
                    gas.verbHistoryAge[gas.verbHistoryPos] = 0;
                    ++gas.verbsSinceLastRefresh;
                }
            }
        }
    }
}
