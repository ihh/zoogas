import java.awt.*;
import java.util.*;

public class SprayTool {
    // data
    protected String particleName = null;
    protected double sprayDiameter = 0, sprayPower = 0, reserve = 0, maxReserve = 0, refillRate = 0, barWidth = 1;
    protected char hotKey = 0;
    protected Particle particle = null;

    // constructors
    public SprayTool (Board board, String pn, double sd, double sp, double rmax, double fill) {
	this.particleName = pn;
	this.sprayDiameter = sd;
	this.sprayPower = sp;
	this.reserve = 0;
	this.maxReserve = rmax;
	this.refillRate = fill;
	this.barWidth = 0;
        this.particle = board.getOrCreateParticle(particleName);
    }

    static RuleSyntax toolSyntax = new RuleSyntax
	("TOOL n! k= d=1 p=1 r=1 f=1 w=1", "n=Particle k=Key d=Diameter p=Power r=Reserve f=RefillRate w=DisplayWidth");
    static SprayTool fromString (String toolString, Board board) {
	SprayTool stat = null;
        if(RuleSet.isRule(toolString)) {
            if (toolSyntax.matches(toolString)) {
                stat = new SprayTool(board,
				     toolSyntax.getXmlTagValue("Particle"),
				     Double.parseDouble(toolSyntax.getXmlTagValue("Diameter")),
				     Double.parseDouble(toolSyntax.getXmlTagValue("Power")),
				     Double.parseDouble(toolSyntax.getXmlTagValue("Reserve")),
				     Double.parseDouble(toolSyntax.getXmlTagValue("RefillRate")));
                stat.barWidth = Double.parseDouble(toolSyntax.getXmlTagValue("DisplayWidth"));
                stat.hotKey = toolSyntax.getXmlTagValue("Key").charAt(0);
            }
            else {
                System.err.println("Wrong no. of args in toolString '" + toolString + "'");
                return null;
            }
        }
        else {
            return null;
        }
        stat.particle = board.getOrCreateParticle(stat.particleName); // initializes the tool in the board
        return stat;
    }

    // methods
    void refill() { refill(1); }
    void refill(double scale) {
	if(reserve < maxReserve)
	{
	    double refillAmount = refillRate * scale;
	    if (refillAmount > 0.)
		reserve = Math.min(reserve + refillAmount, maxReserve);
	}
    }

    // spray(...) returns true if at least one particle was sprayed. (This is used by Challenge.SprayCondition)
    boolean spray(Point cursorPos,Board board,BoardRenderer renderer,String spacePrefix) {
	boolean succeeded = false;
	Point sprayCell = new Point();
	for (int n = 0; reserve >= 1 && n < sprayPower; ++n) {

	    sprayCell.x = cursorPos.x + (int)(Math.random()*(int)(sprayDiameter)) - (int)(sprayDiameter / 2);
	    sprayCell.y = cursorPos.y + (int)(Math.random()*(int)(sprayDiameter)) - (int)(sprayDiameter / 2);

	    if (board.onBoard(sprayCell)) {
		Particle oldCell = board.readCell (sprayCell);
		if (spacePrefix == null || oldCell.prefix.equals(spacePrefix)) {
		    board.writeCell (sprayCell, particle);
		    reserve -= 1;
		    renderer.drawCell (sprayCell);
		    succeeded = true;
		}
	    }
	}
	return succeeded;
    }

    void plotReserve (Graphics g, int yTop, int toolHeight, int toolReserveBarWidth, int toolTextWidth, boolean selected) {
	int toolBarWidth = toolReserveBarWidth + toolTextWidth;

	int yMid =  yTop + toolHeight / 2;

	FontMetrics fm = g.getFontMetrics();
	int cw = fm.charWidth(hotKey);
	int ch = fm.getHeight();

	g.setColor (particle.color);
	g.drawString (hotKey + ": " + particle.visibleName(), toolReserveBarWidth, yMid + ch/2);

	int td = 4;
	int bh = toolHeight - td;

	int tw = toolReserveBarWidth - td;
	int w = (int) Math.min (tw, barWidth * bh * reserve / maxReserve);

	// draw bar in spray color
	int barXpos = toolReserveBarWidth - w,
	    barYpos = yMid - bh/2;
	g.fillRect (barXpos, barYpos, w, bh);

	// if there's an icon, hollow out the bar
	if (particle.icon != null)
	    if (particle.icon.size > 0) {
		int iconSize = particle.icon.size;
		int pixelsPerIconPixel = Math.max (1, bh / iconSize);
		int columns = (int) ((w - 2) / pixelsPerIconPixel);
		int columnsWhenFull = (int) ((tw - 2) / pixelsPerIconPixel);
		int xBleed = (tw - columnsWhenFull * pixelsPerIconPixel) / 2,
		    yBleed = (bh - iconSize * pixelsPerIconPixel) / 2;
		g.setColor(Color.black);
		for (int row = 0; row < iconSize; ++row)
		    for (int col = 0; col < columns; ++col)
			if (!particle.icon.mask[row % iconSize][col % iconSize])
			    g.fillRect (barXpos + xBleed + col * pixelsPerIconPixel,
					barYpos + yBleed + row * pixelsPerIconPixel,
					pixelsPerIconPixel, pixelsPerIconPixel);
	    }

	// draw border if selected
	if(selected) {
	    g.setColor (Color.white);
	    g.drawRect (2, yMid - toolHeight/2, toolBarWidth - 4, toolHeight - 1);
	}
    }

    // isHotKey helper
    boolean isHotKey(char c) { return Character.toUpperCase(hotKey) == Character.toUpperCase(c); }
}
