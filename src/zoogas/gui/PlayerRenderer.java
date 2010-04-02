package zoogas.gui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import zoogas.ZooGas;

import zoogas.core.Board;
import zoogas.core.Point;
import zoogas.core.rules.UpdateEvent;

public class PlayerRenderer extends BoardRenderer{
    static double balloonRate = .0001;  // probability that any given update verb will be printed in a speech balloon (TODO: make this a rule-specific parameter)

    public PlayerRenderer (Board board, int size) {
        this.board = board;
        int pixelsPerSide = getBoardSize(size);

        image = new BufferedImage(pixelsPerSide, pixelsPerSide, BufferedImage.TYPE_3BYTE_BGR);
        bfGraphics = image.createGraphics();
    }
    public PlayerRenderer (ZooGas gas, Board board, int size) {
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
	double showBalloonProbability = balloonRate / updateEvent.getPattern().getProbability();
        if (gas.getNumVerbsSinceLastRefresh() == 0) {
            if (gas.isCheatPressed() || updateEvent.visibleVerb().length() > 0) {
                // check for duplicates
                boolean foundDuplicate = false;
                for (int v = 0; v < gas.getVerbHistoryLength(); ++v)
                    if (updateEvent.getVerb().equals(gas.getVerbHistory(v)) && updateEvent.getOldSource().color.equals(gas.getParticleHistory(v).color)) {
                        foundDuplicate = true;
                        break;
                    }
                if (!foundDuplicate && Math.random() < showBalloonProbability) {
                    gas.verbHistoryPos = (gas.verbHistoryPos + 1) % gas.getVerbHistoryLength();
                    gas.verbHistory[gas.verbHistoryPos] = updateEvent.getVerb();
                    gas.particleHistory[gas.verbHistoryPos] = updateEvent.getOldSource();
                    gas.placeHistory[gas.verbHistoryPos] = updateEvent.getSourceCoords();
                    gas.verbHistoryAge[gas.verbHistoryPos] = 0;
                    ++gas.verbsSinceLastRefresh;
                }
            }
        }
    }
}
