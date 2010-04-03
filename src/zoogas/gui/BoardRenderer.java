package zoogas.gui;

import java.awt.Image;
import java.awt.image.BufferedImage;

import zoogas.core.Point;
import zoogas.core.rules.UpdateEvent;

public abstract class BoardRenderer {
    public abstract void drawCell(Point p);
    public abstract void showVerb(UpdateEvent updateEvent);

    protected int pixelsPerCell = 4; // width & height of each cell in pixels
    protected BufferedImage image;

    public Point getGraphicsCoords(Point pCell) {
        return new Point(pCell.x * pixelsPerCell, pCell.y * pixelsPerCell);
    }

    public Point getCellCoords(java.awt.Point pGraphics) {
        return new Point((int)(pGraphics.x / pixelsPerCell), (int)(pGraphics.y / pixelsPerCell));
    }

    public int getBoardSize(int size) {
        return size * pixelsPerCell;
    }

    public int getPixelsPerCell() {
        return pixelsPerCell;
    }

    public BufferedImage getImage() {
        return image;
    }
}
