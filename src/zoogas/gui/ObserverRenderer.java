import java.awt.Color;
import java.awt.Dimension;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import java.util.HashMap;

import javax.swing.JPanel;

public class ObserverRenderer extends BoardRenderer {
    public ObserverRenderer(int size) {
        super();
        pixelsPerCell = 1;
        pixelsPerSide = getBoardSize(size);
        image = new BufferedImage(pixelsPerSide, pixelsPerSide, BufferedImage.TYPE_4BYTE_ABGR); // TODO: can this be replaced with 3BYTE_BGR?
        panel = new JPanel() {
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.drawImage(image, 0, 0, null);
                }
        };
        panel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        Color c = new Color(100,100,150);
        /*Color c = new Color(100+(int)(Math.random() * 155),
                            100+(int)(Math.random() * 155),
                            100+(int)(Math.random() * 155));*/
        panel.setBackground(c);
    }

    JPanel panel;
    boolean hasPlayer = false;
    int pixelsPerSide;
        HashMap<Point, Color> particles;

    public JPanel getJPanel() {
        return panel;
    }

    public boolean setHasPlayer(boolean b) {
        hasPlayer = b;
        if(b)
            panel.setBackground(Color.BLACK);
        return hasPlayer;
    }

    public void drawCell(Point p) {
        Graphics bfGraphics = image.getGraphics();
        bfGraphics.setColor(particles.get(p));
        Point q = getGraphicsCoords(p);
        bfGraphics.fillRect(q.x, q.y, pixelsPerCell, pixelsPerCell);
    }
    public void drawCell(Point p, Color c) {
        Graphics bfGraphics = image.getGraphics();
        bfGraphics.setColor(c);
        Point q = getGraphicsCoords(p);
        bfGraphics.fillRect(q.x, q.y, pixelsPerCell, pixelsPerCell);
    }
    public void clear() {
        image = new BufferedImage(pixelsPerSide, pixelsPerSide, BufferedImage.TYPE_4BYTE_ABGR); // TODO: can this be replaced with 3BYTE_BGR?
    }
    
    public void showVerb(UpdateEvent updateEvent) {
        return;
    }
}
