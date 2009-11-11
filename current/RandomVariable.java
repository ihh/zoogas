// probability distribution over Objects
import java.util.*;
import java.lang.*;


public class RandomVariable<V> {
    // private data
    private SortedMap<Double,V> cumprob2obj = new TreeMap<Double,V>();
    private Map<V,Double> obj2prob = new HashMap<V,Double>();
    private double totalWeight = 0, closedWeight = 0;

    // private methods
    private final void rebuild() {
	cumprob2obj.clear();
	totalWeight = 0;
	for(Map.Entry<V,Double> keyval : obj2prob.entrySet()) {
	    accumulate(keyval.getKey(), keyval.getValue());
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
	    if (p > 0)
		obj2prob.put (o, new Double(p));
	    else
		obj2prob.remove (o);
	    rebuild();
	} else if (p > 0) {
	    obj2prob.put (o, new Double(p));
	    accumulate (o, p);
	}
    }

    public final void close() {
	closedWeight = totalWeight < 1 ? 1 : totalWeight;
    }

    public final V sample () {
	if (size() > 0) {
	    double p = Math.random() * closedWeight;
	    if (p <= totalWeight)
		return cumprob2obj.get (cumprob2obj.headMap(new Double(p)).lastKey());
	}
	return null;
    }
};
