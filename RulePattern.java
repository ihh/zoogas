import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Syntax for regexp-based pattern generators:
//  A B C D P V
// where
//  A is a regexp that must globally match the old source state
//  B is a regexp that must globally match the old target state
//  C is a string that will expand to the new source state
//  D is a string that will expand to the new target state
//  P is a numeric constant that is the probability of the rule
//  V is a verb describing the action being carried out by the source when this rule fires (no whitespace)

public class RulePattern {
    // data
    Pattern[] A = null;
    String B = null, C = null, D = null, V = null;
    double P = 0;
    ZooGas gas = null;  // required for compass points
    
    // constructor
    public RulePattern (String a, String b, String c, String d, double p, String v, ZooGas g) {
	gas = g;
	A = new Pattern[gas.neighborhoodSize()];
	for (int dir = 0; dir < gas.neighborhoodSize(); ++dir)
	    A[dir] = Pattern.compile("^" + expandDir(a,dir) + "$");
	B = b;
	C = c;
	D = d;
	P = p;
	V = v;
    }

    // expandDir method
    static Pattern dirPattern = Pattern.compile("\\$([FBLR])");
    protected String expandDir (String s, int dir) {
	Matcher m = dirPattern.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find()) {
	    String var = m.group(1);
	    if (var.equals("F"))
		m.appendReplacement(sb,gas.dirString(dir));
	    else if (var.equals("B"))
		m.appendReplacement(sb,gas.dirString((dir + 2) % 4));
	    else if (var.equals("L"))
		m.appendReplacement(sb,gas.dirString((dir + 1) % 4));
	    else if (var.equals("R"))
		m.appendReplacement(sb,gas.dirString((dir + 3) % 4));
	}
	m.appendTail(sb);
	return sb.toString();
    }
}
