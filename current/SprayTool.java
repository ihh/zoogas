import java.awt.*;

public class SprayTool {
    // data
    String particleName = null;
    double sprayDiameter = 0, sprayPower = 0, reserve = 0, maxReserve = 0, refillRate = 0, barWidth = 1;
    char hotKey = 0;
    Particle particle = null;

    // constructor
    static SprayTool fromString (String toolString, Board board) {
	SprayTool stat = null;
	String[] toolArgs = toolString.split(" ");
	if (toolArgs.length == 7) {
	    stat = new SprayTool();
	    stat.particleName = toolArgs[0];
	    stat.hotKey = toolArgs[1].charAt(0);
	    stat.sprayDiameter = new Double(toolArgs[2]).doubleValue();
	    stat.sprayPower = new Double(toolArgs[3]).doubleValue();
	    stat.maxReserve = new Double(toolArgs[4]).doubleValue();
	    stat.refillRate = new Double(toolArgs[5]).doubleValue();
	    stat.barWidth = new Double(toolArgs[6]).doubleValue();
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

    void spray(Point cursorPos,Board board,Particle spaceParticle) {
	Point sprayCell = new Point();
	for (int n = 0; reserve >= 1 && n < sprayPower; ++n) {

	    sprayCell.x = cursorPos.x + board.rnd.nextInt((int) sprayDiameter) - (int) (sprayDiameter / 2);
	    sprayCell.y = cursorPos.y + board.rnd.nextInt((int) sprayDiameter) - (int) (sprayDiameter / 2);

	    if (board.onBoard(sprayCell)) {
		Particle oldCell = board.readCell (sprayCell);
		if (spaceParticle == null || oldCell == spaceParticle) {
		    board.writeCell (sprayCell, particle);
		    reserve -= 1;
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

}
