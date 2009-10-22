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
}
