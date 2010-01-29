public class ObserverRenderer extends BoardRenderer {
    public ObserverRenderer() {
        super();
        pixelsPerCell = 1;
    }
    public void drawCell(Point p) {
    }
    public void showVerb(Point p, Point n, Particle oldSource, Particle oldTarget, UpdateEvent newPair) {
        return;
    }
}
