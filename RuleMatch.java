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
// Similarly for $-2, $++3, etc.

// A matching rule should overwrite any previously matched rules, allowing us to create exceptions
// (e.g. "destroy any particle; DO NOT destroy basalt").
// UPDATE: this is now implicit in RandomVariable.add()

// Patterns such as this are to be transmitted in a "Particle definition" packet with the following structure:
// NAMES & COLORS (one per line, format "NAME R G B", describing appearances of Particles to which this definition packet applies)
// RULES (one per line, format "A B C D P V")

// The class (ParticleDefinition? ParticleTemplate?) encapsulating these definition data
// is a regexp-based generator for Particles and their production rules.

public class RuleMatch {
    // data
    RulePattern pattern = null;
    String A = null, B = null;
    Matcher am = null, bm = null;
    int dir = -1;
    ZooGas gas = null;  // just for dirString

    // static Patterns
    static Pattern dirPattern = Pattern.compile("\\$([FBLR])");
    static Pattern srcPattern = Pattern.compile("\\$([\\-]*|[\\+]*)([FBLRS]|[1-9][0-9]*)");
    static Pattern allPattern = Pattern.compile("\\$([\\-]*|[\\+]*)([FBLRST]|[1-9][0-9]*)");

    // constructors
    public RuleMatch(ZooGas gas) { this.gas = gas; }
    public RuleMatch(ZooGas gas,int dir) { this.gas = gas; bindDir(dir); }
    public RuleMatch(ZooGas gas,int dir,String a) { this.gas = gas; bindDir(dir); bindSource(a); }
    public RuleMatch(ZooGas gas,int dir,String a,String b) { this.gas = gas; bindDir(dir); bindSource(a); bindTarget(b); }

    // lhs methods
    void bindDir(int dir) {
	this.dir = dir;
    }

    void bindSource(String a) {
	A = a;
	am = pattern.A.matcher("^" + expandDir(a) + "$");
    }

    void bindTarget(String b) {
	B = b;
	bm = pattern.B.matcher("^" + expandDir(b) + "$");
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
    String C() { return matches() ? expandAll(pattern.C) : null; }
    String D() { return matches() ? expandAll(pattern.D) : null; }

    // expansion helpers
    protected String expandDir (String s) { return expand(dirPattern,s); }  // expand $F, $B, $L, $R
    protected String expandSrc (String s) { return expand(srcPattern,s); }  // expand $S (and $1,$2,... if matched by A)
    protected String expandAll (String s) { return expand(allPattern,s); }  // expand everything

    // main expand() method is just a placeholder for now (needs to expand $1, $2 etc)
    protected String expand (Pattern p, String s) {
	Matcher m = p.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String incdec = m.group(1), var = m.group(2);
	    boolean replaced = true;

	    // numeric value?
	    int n = 0;
	    try {
		n = new Integer(var).intValue();
	    } catch (NumberFormatException e) {
		n = 0;
	    }
	    
	    if (n >= 1 && n <= am.groupCount() + bm.groupCount()) {
		String nval = n <= am.groupCount() ? am.group(n) : bm.group(n-am.groupCount()+1);
		if (incdec.length() == 0)
		    m.appendReplacement(sb,nval);
		else {
		    // increment or decrement nval...
		    int nvalInt = Integer.parseInt(nval,36);
		    int amount = incdec.length();
		    if (incdec.charAt(0) == '+')
			m.appendReplacement(sb,Integer.toString(nvalInt+amount,36));
		    else if (nvalInt >= amount)
			m.appendReplacement(sb,Integer.toString(nvalInt-amount,36));
		    else
			replaced = false;
		}
	    } else if (incdec.length() == 0) {
		if (var.equals("F"))
		    m.appendReplacement(sb,gas.dirString(dir));
		else if (var.equals("B"))
		    m.appendReplacement(sb,gas.dirString((dir + 2) % 4));
		else if (var.equals("L"))
		    m.appendReplacement(sb,gas.dirString((dir + 1) % 4));
		else if (var.equals("R"))
		    m.appendReplacement(sb,gas.dirString((dir + 3) % 4));
		else if (var.equals("S"))
		    m.appendReplacement(sb,A);
		else if (var.equals("T"))
		    m.appendReplacement(sb,B);
		else
		    replaced = false;
	    } else
		replaced = false;

	    if (!replaced)
		m.appendReplacement(sb,m.group(0));
	}
	m.appendTail(sb);
	return sb.toString();
    }
}
