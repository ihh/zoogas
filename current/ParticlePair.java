

public class ParticlePair {
    // data
    Particle source = null, target = null;

    // methods
    public ParticlePair (Particle s, Particle t) {
	source = s;
	target = t;
    }

    public boolean equals (Object obj) {
	if (obj.getClass().equals (getClass())) {
	    ParticlePair pp = (ParticlePair) obj;
	    return (source == null ? pp.source == null : source.equals(pp.source))
		&& (target == null ? pp.target == null : target.equals(pp.target));
	}
	return false;
    }

    public int hashCode() {
	int s = source == null ? 0 : source.hashCode();
	int t = target == null ? 0 : target.hashCode();
	return s ^ t;
    }
};
