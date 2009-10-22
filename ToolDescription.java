public class ToolDescription {
    // data
    String particleName = null;
    double sprayDiameter = 0, maxReserve = 0, refillRate = 0, barWidth = 1;
    char hotKey = 0;

    // constructors
    ToolDescription (String particleName, char hotKey, double sprayDiameter, double maxReserve, double refillRate, double barWidth) {
	this.particleName = particleName;
	this.hotKey = hotKey;
	this.sprayDiameter = sprayDiameter;
	this.maxReserve = maxReserve;
	this.refillRate = refillRate;
	this.barWidth = barWidth;
    }
}
