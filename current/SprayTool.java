import java.awt.*;
import java.util.*;

public class SprayTool {
    // data
    private String particleName = null;
    private double sprayDiameter = 0, sprayPower = 0, reserve = 0, maxReserve = 0, refillRate = 0, barWidth = 1;
    private char hotKey = 0;
    private Particle particle = null;

    // random number generator
    private Random rnd = new Random();

    // constructor
    static RuleSyntax toolSyntax = new RuleSyntax("TOOL n= k= d=1 p=1 r=1 f=1 w=.1");
    static SprayTool fromString (String toolString, Board board) {
	SprayTool stat = null;
	if (toolSyntax.matches(toolString)) {
	    stat = new SprayTool();
	    stat.particleName = toolSyntax.getValue("n");
	    stat.hotKey = toolSyntax.getValue("k").charAt(0);
	    stat.sprayDiameter = Double.parseDouble(toolSyntax.getValue("d"));
	    stat.sprayPower = Double.parseDouble(toolSyntax.getValue("p"));
	    stat.maxReserve = Double.parseDouble(toolSyntax.getValue("r"));
	    stat.refillRate = Double.parseDouble(toolSyntax.getValue("f"));
	    stat.barWidth = Double.parseDouble(toolSyntax.getValue("w"));
	} else
	    System.err.println("Wrong no. of args in toolString '" + toolString + "'");
	stat.particle = board.getOrCreateParticle (stat.particleName);
	return stat;
    }

    // methods
    void refill(double scale) {
	double refillAmount = refillRate * scale;
	if (refillAmount > 0. && reserve < maxReserve)
	    reserve = Math.min (reserve + refillAmount, maxReserve);
    }

    void spray(Point cursorPos,Board board,BoardRenderer renderer,Particle spaceParticle) {
	Point sprayCell = new Point();
	for (int n = 0; reserve >= 1 && n < sprayPower; ++n) {

	    sprayCell.x = cursorPos.x + rnd.nextInt((int) sprayDiameter) - (int) (sprayDiameter / 2);
	    sprayCell.y = cursorPos.y + rnd.nextInt((int) sprayDiameter) - (int) (sprayDiameter / 2);

	    if (board.onBoard(sprayCell)) {
		Particle oldCell = board.readCell (sprayCell);
		if (spaceParticle == null || oldCell == spaceParticle) {
		    board.writeCell (sprayCell, particle);
		    reserve -= 1;
		    renderer.drawCell (sprayCell);
		}
	    }
	}
    }

    void plotReserve (Graphics g, Point topLeft, int toolHeight, int toolReserveBarWidth, int toolTextWidth, boolean selected) {
	int toolBarWidth = toolReserveBarWidth + toolTextWidth;
	g.setColor (Color.black);
	g.fillRect (topLeft.x, topLeft.y, toolBarWidth, toolHeight);

	int xLeft = topLeft.x;
	int yMid = topLeft.y + toolHeight / 2;

	FontMetrics fm = g.getFontMetrics();
	int cw = fm.charWidth(hotKey);
	int ch = fm.getHeight();

	g.setColor (particle.color);
	g.drawString (hotKey + ": " + particle.visibleName(), xLeft + toolReserveBarWidth, yMid + ch/2);

	int td = 4;
	int tw = toolReserveBarWidth - td;
	int w = (int) (barWidth * (double) (tw * reserve / maxReserve));
	if (w > toolReserveBarWidth)
	    w = toolReserveBarWidth;

	int bh = toolHeight * 3 / 4;
	g.fillRect (xLeft + toolReserveBarWidth - w, yMid - bh/2, w, bh);

	g.setColor (selected ? Color.white : Color.black);
	g.drawRect (xLeft + 2, yMid - toolHeight/2 + 2, toolBarWidth - 4, toolHeight - 4);
    }

    // isHotKey helper
    boolean isHotKey(char c) { return Character.toUpperCase(hotKey) == Character.toUpperCase(c); }
}
