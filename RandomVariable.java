// probability distribution over Objects
import java.util.*;
import java.lang.*;


public class RandomVariable {
    // private data
    private SortedMap cumprob2obj = new TreeMap();
    private Map obj2prob = new HashMap();
    private double totalWeight = 0;

    // private methods
    private final void rebuild() {
	cumprob2obj.clear();
	totalWeight = 0;
	Iterator iter = obj2prob.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry keyval = (Map.Entry)iter.next();
	    accumulate (keyval.getKey(), ((Double) keyval.getValue()).doubleValue());
	}
    }

    private final void accumulate (Object o, double p) {
	cumprob2obj.put (new Double(totalWeight), o);
	totalWeight += p;
    }

    // public methods
    public final int size() { return cumprob2obj.size(); }

    public final void add (Object o, double p) {
	if (obj2prob.containsKey(o)) {
	    obj2prob.put (o, new Double(p));
	    rebuild();
	} else {
	    obj2prob.put (o, new Double(p));
	    accumulate (o, p);
	}
    }

    public final void close (Object o) {
	if (totalWeight < 1 && size() > 0)
	    add (o, 1 - totalWeight);
	else if (totalWeight > 1)
	    System.err.println ("Warning: closing pattern with totalWeight " + totalWeight);
    }

    public final Object sample (Random rnd) {
	if (size() > 0) {
	    double p = rnd.nextDouble() * totalWeight;
	    return cumprob2obj.get (cumprob2obj.headMap(new Double(p)).lastKey());
	}
	return null;
    }
};
