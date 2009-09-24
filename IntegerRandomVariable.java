// probability distribution over integers
import java.util.Vector;
import java.util.Random;
import java.lang.Integer;
import java.lang.Double;


public class IntegerRandomVariable {
    private Vector o_vec, p_vec;

    // call normalize() after [multiple calls to] add(), and before calling sample(), to set up these helper vars
    private int outcome[];
    private double probability[];

    // methods
    public IntegerRandomVariable() {
	o_vec = new Vector();
	p_vec = new Vector();
    }

    public int size() { return p_vec.size(); }

    public void add (int o, double p) {
	o_vec.add (new Integer (o));
	p_vec.add (new Double (p));
    }

    public void normalize() {
	outcome = new int[o_vec.size()];
	probability = new double[p_vec.size()];

	double norm = 0;
	for (int i = 0; i < p_vec.size(); ++i)
	    norm += ((Double) p_vec.get(i)).doubleValue();

	for (int i = 0; i < p_vec.size(); ++i)
	    {
		outcome[i] = ((Integer) o_vec.get(i)).intValue();
		probability[i] = ((Double) p_vec.get(i)).doubleValue() / norm;
	    }
    }

    public int sample (Random rnd) {
	double p = rnd.nextDouble();

	int o = 0;
	while (o < outcome.length - 1) {
	    p -= probability[o];
	    if (p <= 0)
		break;
	    ++o;
	}

	return outcome[o];
    }
};
