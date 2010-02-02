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
        int pixelsPerSide = getBoardSize(size);
        image = new BufferedImage(pixelsPerSide, pixelsPerSide, BufferedImage.TYPE_3BYTE_BGR);
        panel = new JPanel();
        panel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        Color c = new Color(100,100,150);
        /*Color c = new Color(100+(int)(Math.random() * 155),
                            100+(int)(Math.random() * 155),
                            100+(int)(Math.random() * 155));*/
        panel.setBackground(c);
    }

    JPanel panel;
    boolean hasPlayer = false;
        HashMap<Point, Color> particles;

    public JPanel getJPanel() {
        return panel;
    }

    public boolean hasPlayer(boolean b) {
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
    public void showVerb(Point p, Point n, Particle oldSource, Particle oldTarget, UpdateEvent newPair) {
        return;
    }
}
