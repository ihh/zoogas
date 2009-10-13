import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Here is a hypothetical syntax for regexp-based pattern generators:
//  A B C D P V
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)

// The following "special variables" will be expanded in {A,B,C,D} as appropriate:
//  $1,$2,$3... => groups in A and B regexps (c.f. Perl)
//  $S,$T => full names for old source,target states
//  $F,$L,$R,$B => directions relative to neighbor direction ($F=forward, $L=left, $R=right, $B=back)
//  $-1 => numerically one less than $1, interpreted as an alphadecimal number (i.e. base 36)
//  $--1 => numerically two less than $1 (and $---1 is three less, etc.); negative numbers evaluate to the empty string
//  $+1 => numerically one greater than $1
// (similarly for $-2, $++3, etc.)
//  $%3++1 => ($1 + 2) mod 3
//  $%M+{k}N => ($N + k) mod M   ...where +{k} denotes a run of k plus(+) characters

// A matching rule should overwrite any previously matched rules, allowing us to create exceptions
// (e.g. "destroy any particle; DO NOT destroy basalt").
// UPDATE: this is now implicit in RandomVariable.add()

// Patterns such as this are to be transmitted in a "Particle definition" packet with the following structure:
// NAMES & COLORS (one per line, format "NAME R G B", describing appearances of Particles to which this definition packet applies)
// RULES (one per line, format "A B C D P V")

public class RuleMatch {
    // data
    RulePattern pattern = null;
    String A = null, B = null;
    Matcher am = null, bm = null;
    int dir = -1;

    // constructors
    public RuleMatch(RulePattern p) { pattern = p; }
    public RuleMatch(RulePattern p,int dir) { this(p); bindDir(dir); }
    public RuleMatch(RulePattern p,int dir,String a) { this(p,dir); bindSource(a); }
    public RuleMatch(RulePattern p,int dir,String a,String b) { this(p,dir,a); bindTarget(b); }

    // lhs methods
    void bindDir(int d) {
	dir = d;
    }

    void bindSource(String a) {
	A = a;
	am = pattern.A[dir].matcher(a);
    }

    void bindTarget(String b) {
	B = b;
	bm = pattern.B[dir].matcher(b);
    }

    boolean matches() {
	return
	    am == null
	    ? true
	    : (am.matches() && (bm == null
				? true
				: bm.matches()));
    }

    // rhs methods
    String C() { return matches() ? expandVars(pattern.C) : null; }
    String D() { return matches() ? expandVars(pattern.D) : null; }

    // main expand() methods
    protected String expandVars (String s) {
	return expandMod(expandDec(expandInc(expandVar(s))));
    }

    String getVar(String var) {
	String val = null;
	try {
	    int n = new Integer(var).intValue();
	    val = n <= am.groupCount() ? am.group(n) : bm.group(n-am.groupCount()+1);
	} catch (NumberFormatException e) { }
	return val;
    }

    static Pattern varPattern = Pattern.compile("\\$([ST]|[1-9][0-9]*)");
    protected String expandVar (String s) {
	Matcher m = varPattern.matcher(pattern.expandDir(s,dir));
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String g = m.group(1);
	    if (g.equals("S"))
		m.appendReplacement(sb,A);
	    else if (g.equals("T"))
		m.appendReplacement(sb,B);
	    else
		m.appendReplacement(sb,getVar(g));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    static Pattern incVarPattern = Pattern.compile("\\$([\\+]+)([1-9][0-9]*)");
    protected String expandInc (String s) {
	Matcher m = incVarPattern.matcher(pattern.expandDir(s,dir));
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String inc = m.group(1), g = m.group(2);
	    int n = Integer.parseInt(getVar(g),36);
	    int delta = inc.length();
	    m.appendReplacement(sb,Integer.toString(n+delta,36));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    static Pattern decVarPattern = Pattern.compile("\\$([\\-]+)([1-9][0-9]*)");
    protected String expandDec (String s) {
	Matcher m = decVarPattern.matcher(pattern.expandDir(s,dir));
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String dec = m.group(1), g = m.group(2);
	    int n = Integer.parseInt(getVar(g),36);
	    int delta = dec.length();
	    if (n >= delta)
		m.appendReplacement(sb,Integer.toString(n-delta,36));
	}
	m.appendTail(sb);
	return sb.toString();
    }

    static Pattern modVarPattern = Pattern.compile("\\$%([1-9][0-9]*)([\\+]+)([1-9][0-9]*)");
    protected String expandMod (String s) {
	Matcher m = modVarPattern.matcher(pattern.expandDir(s,dir));
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String mod = m.group(1), inc = m.group(2), g = m.group(3);
	    int n = Integer.parseInt(getVar(g),36);
	    int M = Integer.parseInt(mod);
	    int delta = inc.length();
	    m.appendReplacement(sb,Integer.toString((n+delta)%M,36));
	}
	m.appendTail(sb);
	return sb.toString();
    }
}
