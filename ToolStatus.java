import java.awt.*;

public class ToolStatus {
    // data
    ToolDescription desc = null;
    double reserve = 0;
    Particle particle = null;

    // constructor
    ToolStatus (ToolDescription desc, Board board) {
	this.desc = desc;
	this.particle = board.getOrCreateParticle (desc.particleName);
    }

    // methods
    void refill(double rate) {
	double refillRate = desc.refillRate * rate;
	if (refillRate > 0. && reserve < desc.maxReserve)
	    reserve = Math.min (reserve + refillRate, desc.maxReserve);
    }

    void spray(Point cursorPos,Board board,Particle spaceParticle) {
	if (reserve > 1) {
	    Point sprayCell = new Point();

	    sprayCell.x = cursorPos.x + board.rnd.nextInt((int) desc.sprayDiameter) - (int) (desc.sprayDiameter / 2);
	    sprayCell.y = cursorPos.y + board.rnd.nextInt((int) desc.sprayDiameter) - (int) (desc.sprayDiameter / 2);

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
	char[] ca = new char[1];
	ca[0] = desc.hotKey;
	int xLeft = topLeft.x;
	int yMid = topLeft.y + toolHeight / 2;

	FontMetrics fm = g.getFontMetrics();
	int cw = fm.charWidth(desc.hotKey);
	int ch = fm.getHeight();

	g.setColor (particle.color);
	g.drawChars (ca, 0, 1, xLeft + toolReserveBarWidth + toolTextWidth/2 - cw/2, yMid + ch/2);

	int td = 4;
	int tw = toolReserveBarWidth - td;
	int w = (int) (desc.barWidth * (double) (tw * reserve / desc.maxReserve));
	if (w > toolReserveBarWidth)
	    w = toolReserveBarWidth;

	int bh = toolHeight * 3 / 4;
	g.fillRect (xLeft + toolReserveBarWidth - w, yMid - bh/2, w, bh);
	g.setColor (Color.black);
	g.fillRect (xLeft + td, yMid - bh/2, tw - w, bh);

	g.setColor (selected ? Color.white : Color.black);
	g.drawRect (xLeft + 2, yMid - toolHeight/2 + 2, toolBarWidth - 4, toolHeight - 4);
    }

}
