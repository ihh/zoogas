import java.awt.Point;

interface BoardRenderer {
    public void drawCell (Point p);
    public void showVerb (Point p,Point n,Particle oldSource,Particle oldTarget,ParticlePair newPair);
}
