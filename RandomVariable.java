// probability distribution over Objects
import java.util.*;
import java.lang.*;


public class RandomVariable {
    // private data
    private SortedMap rv2obj = new TreeMap();
    private double totalWeight = 0;

    // public methods
    public final int size() { return rv2obj.size(); }

    public final void add (Object o, double p) {
	rv2obj.put (new Double (totalWeight), o);
	totalWeight += p;
    }

    public final void close (Object o) {
	if (totalWeight < 1 && size() > 0)
	    add (o, 1 - totalWeight);
    }

    public final Object sample (Random rnd) {
	if (size() > 0) {
	    double p = rnd.nextDouble() * totalWeight;
	    return rv2obj.get (rv2obj.headMap(new Double(p)).lastKey());
	}
	return null;
    }
};
