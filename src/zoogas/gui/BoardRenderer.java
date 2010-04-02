import java.awt.Image;
import java.awt.image.BufferedImage;

public abstract class BoardRenderer {
    public abstract void drawCell(Point p);
    public abstract void showVerb(UpdateEvent updateEvent);

    public int pixelsPerCell = 4; // width & height of each cell in pixels
    public BufferedImage image;

    public Point getGraphicsCoords(Point pCell) {
        return new Point(pCell.x * pixelsPerCell, pCell.y * pixelsPerCell);
    }

    public Point getCellCoords(java.awt.Point pGraphics) {
        return new Point((int)(pGraphics.x / pixelsPerCell), (int)(pGraphics.y / pixelsPerCell));
    }

    public int getBoardSize(int size) {
        return size * pixelsPerCell;
    }
}
