// probability distribution over Objects
import java.util.*;
import java.lang.*;


public class RandomVariable<V> {
    // private data
    private SortedMap<Double,V> cumprob2obj = new TreeMap<Double,V>();
    private Map<V,Double> obj2prob = new HashMap<V,Double>();
    private double totalWeight = 0;

    // private methods
    private final void rebuild() {
	cumprob2obj.clear();
	totalWeight = 0;
	Iterator<Map.Entry<V,Double>> iter = obj2prob.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry<V,Double> keyval = (Map.Entry<V,Double>) iter.next();
	    accumulate ((V) keyval.getKey(), ((Double) keyval.getValue()).doubleValue());
	}
    }

    private final void accumulate (V o, double p) {
	cumprob2obj.put (new Double(totalWeight), o);
	totalWeight += p;
    }

    // public methods
    public final int size() { return cumprob2obj.size(); }

    public final void add (V o, double p) {
	if (obj2prob.containsKey(o)) {
	    obj2prob.put (o, new Double(p));
	    rebuild();
	} else {
	    obj2prob.put (o, new Double(p));
	    accumulate (o, p);
	}
    }

    public final void close (V o) {
	if (totalWeight < 1 && size() > 0)
	    add (o, 1 - totalWeight);
	else if (totalWeight > 1)
	    System.err.println ("Warning: closing pattern with totalWeight " + totalWeight);
    }

    public final V sample (Random rnd) {
	if (size() > 0) {
	    double p = rnd.nextDouble() * totalWeight;
	    return cumprob2obj.get (cumprob2obj.headMap(new Double(p)).lastKey());
	}
	return null;
    }
};
