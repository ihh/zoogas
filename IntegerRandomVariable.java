// probability distribution over integers
import java.util.Vector;
import java.util.Random;
import java.lang.Integer;
import java.lang.Double;


public class IntegerRandomVariable {
    private Vector value = new Vector();
    private Vector weight = new Vector();
    private double totalWeight = 0;

    // methods
    public final int size() { return value.size(); }

    public final void add (int o, double p) {
	value.add (new Integer (o));
	weight.add (new Double (p));
	totalWeight += p;
    }

    public int sample (Random rnd) {
	double p = rnd.nextDouble() * totalWeight;

	int v = 0;
	while (v < value.size() - 1) {
	    p -= ((Double) weight.get(v)).doubleValue();
	    if (p <= 0)
		break;
	    ++v;
	}

	return ((Integer) value.get(v)).intValue();
    }
};
