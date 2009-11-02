import java.util.*;

public class UpdateEvent {
    // data
    Particle source = null, target = null;
    HashMap<String,Point> sIncoming = null, sOutgoing = null, tIncoming = null, tOutgoing = null;
    // everything below here ignored by equals() and hashCode() methods
    String verb = null;
    double energyDelta = 0;
    
    // methods
    // constructor
    public UpdateEvent (Particle s, Particle t, String v) {
	source = s;
	target = t;
	verb = v;
    }

    // helpers
    public boolean makesBonds() { return sIncoming != null || sOutgoing != null || tIncoming != null || tOutgoing != null; }

    // equals, hashCode
    public boolean equals (Object obj) {
	if (obj.getClass().equals (getClass())) {
	    UpdateEvent pp = (UpdateEvent) obj;
	    return (source == pp.source || source.equals(pp.source))
		&& (target == pp.target || target.equals(pp.target))
		&& (sIncoming == pp.sIncoming || sIncoming.equals(pp.sIncoming))  // the == catches the null pointer case
		&& (sOutgoing == pp.sOutgoing || sOutgoing.equals(pp.sOutgoing))  // the == catches the null pointer case
		&& (tIncoming == pp.tIncoming || tIncoming.equals(pp.tIncoming))  // the == catches the null pointer case
		&& (tOutgoing == pp.tOutgoing || tOutgoing.equals(pp.tOutgoing));  // the == catches the null pointer case
	}
	return false;
    }

    public int hashCode() {
	int s = source == null ? 0 : source.hashCode();
	int t = target == null ? 0 : target.hashCode();
	int si = sIncoming == null ? 0 : sIncoming.hashCode();
	int so = sOutgoing == null ? 0 : sOutgoing.hashCode();
	int ti = tIncoming == null ? 0 : tIncoming.hashCode();
	int to = tOutgoing == null ? 0 : tOutgoing.hashCode();
	return s ^ t ^ si ^ so ^ ti ^ to;
    }

    // part of verb visible to player
    public final String visibleVerb() {
	return Particle.visibleText(verb);
    }
};
