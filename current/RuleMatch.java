import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// RuleMatch - a partially- or fully-bound RulePattern.
// For minilanguage syntax see subclasses TransformRuleMatch, EnergyRuleMatch
public class RuleMatch {
    // data
    protected RulePattern pattern = null;
    private Pattern aPattern = null, bPattern = null;
    private String A = null, B = null;
    private Matcher am = null, bm = null;
    private boolean aMatched = false, bMatched = false;

    // constructors
    public RuleMatch(RulePattern p) { pattern = p; }

    // lhs methods
    public final boolean bindSource(String a) {
	if (!sourceBound()) {
	    A = a;
	    if (aPattern == null)
		aPattern = Pattern.compile(regexA());
	    am = aPattern.matcher(a);
	    aMatched = am.matches();
	    return aMatched;
	}
	// throw AlreadyBoundException
	return false;
    }

    public final boolean bindTarget(String b) {
	if (aMatched && !targetBound()) {
	    B = b;
	    if (bPattern == null)
		bPattern = Pattern.compile(regexB());
	    bm = bPattern.matcher(b);
	    bMatched = bm.matches();
	    return bMatched;
	}
	// throw AlreadyBoundException
	return false;
    }

    // unbinding
    public final void unbindTarget() {
	bm = null;
	B = null;
	bMatched = false;
    }

    public final void unbindSource() {
	unbindTarget();
	am = null;
	A = null;
	bPattern = null;
	aMatched = false;
    }

    public void unbind() {
	unbindSource();
	aPattern = null;
    }

    // matches() returns true if the rule has matched *so far*
    public final boolean matches() {
	return
	    am == null
	    ? true
	    : (aMatched && (bm == null
				? true
				: bMatched));
    }

    // methods to test if the rule is fully or partly bound
    public final boolean targetBound() { return B != null; }
    public final boolean sourceBound() { return A != null; }

    // expanded pattern methods
    public String regexA() { return pattern.A; }
    public String regexB() { return expandLHS(pattern.B); }
    public final String A() { return A; }
    public final String B() { return B; }

    // main expand() methods
    // expansion of B
    protected final String expandLHS (String s) {
	return expandMod(expandDec(expandInc(expandGroupOrSource(s))));
    }

    // expansion of $F, $B, $L, $R
    // (overridden in TransformRuleMatch)
    protected String expandDir (String s) {
	return s;
    }

    // expansion of $1, $2, ... and $S
    static Pattern groupPattern = Pattern.compile("\\$(S|[1-9]\\d*)");
    protected final String expandGroupOrSource (String s) {
	Matcher m = groupPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String g = m.group(1);
	    if (g.equals("S"))
		m.appendReplacement(sb,A);
	    else
		m.appendReplacement(sb,getGroup(g));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    // expansion of $T
    static Pattern targetPattern = Pattern.compile("\\$T");
    protected final String expandTarget (String s) {
	Matcher m = targetPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find())
	    m.appendReplacement(sb,B);
	m.appendTail(sb);
	return sb.toString();
    }

    // expansion of $+1.n
    static Pattern incGroupPattern = Pattern.compile("\\$\\+(\\d*)\\.?([1-9]\\d*)");
    protected final String expandInc (String s) {
	Matcher m = incGroupPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String inc = m.group(1), g = m.group(2);
	    int n = string2int(getGroup(g));
	    int delta = inc.length()>0 ? string2int(inc) : 1;
	    m.appendReplacement(sb,int2string(n+delta));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    // expansion of $-1.n
    static Pattern decGroupPattern = Pattern.compile("\\$\\-(\\d*)\\.?([1-9]\\d*)");
    protected final String expandDec (String s) {
	Matcher m = decGroupPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String dec = m.group(1), g = m.group(2);
	    int n = string2int(getGroup(g));
	    int delta = dec.length()>0 ? string2int(dec) : 1;
	    if (n >= delta)
		m.appendReplacement(sb,int2string(n-delta));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    // expansion of $%3+1.n
    static Pattern modGroupPattern = Pattern.compile("\\$%([1-9]\\d*)\\+(\\d*)\\.?([1-9]\\d*)");
    protected final String expandMod (String s) {
	Matcher m = modGroupPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String mod = m.group(1), inc = m.group(2), g = m.group(3);
	    int n = string2int(getGroup(g));
	    int M = string2int(mod);
	    int delta = inc.length()>0 ? string2int(inc) : 1;
	    m.appendReplacement(sb,int2string((n+delta)%M));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    // helper method to get a group ($1,$2,...) from AB
    String getGroup(String group) {
	String val = "";
	try {
	    int n = new Integer(group).intValue();
	    if (n <= am.groupCount())
		val = am.group(n);
	    else if (bm != null) {
		n -= am.groupCount();
		if (n <= bm.groupCount())
		    val = bm.group(n);
	    }
	} catch (NumberFormatException e) { }
	return val;
    }

    // helper methods to encode/decode decimal numbers
    static private int base = 10;
    static String int2string(int n) { return Integer.toString(n,base); }
    static int string2int(String s) { return Integer.parseInt(s,base); }
}
