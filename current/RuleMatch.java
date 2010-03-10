import java.lang.*;

import java.util.*;
import java.util.regex.*;

import java.text.*;

import java.net.*;

import java.io.*;


// RuleMatch - a partially- or fully-bound RulePattern.
public class RuleMatch {
    // data
    protected RulePattern pattern = null;
    protected Topology topology = null;
    private int dir = -1;

    private Pattern aPattern = null, abPattern = null;
    protected String A = null, B = null;
    protected Matcher am = null, abm = null;
    private boolean aMatched = false, abMatched = false;

    // classes
    class AlreadyBoundException extends RuntimeException {
        AlreadyBoundException() {
            super("Attempt to bind already-bound rule");
        }
    }

    // constructors
    public RuleMatch(RulePattern p) {
        pattern = p;
    }
    public RuleMatch(RulePattern p, Topology topology, int dir) {
        this(p);
        bindDir(topology, dir);
    }

    // lhs methods
    public boolean bindDir(Topology t, int d) {
        if (!dirBound()) {
            topology = t;
            dir = d;
            aPattern = Pattern.compile(regexA());
            abPattern = Pattern.compile(regexAB());
            return true;
        }
        throw new AlreadyBoundException();
    }

    public final boolean bindSource(String a) {
        if (!sourceBound()) {
            A = a;
            am = aPattern.matcher(A);
            aMatched = am.matches();
            return matches();
        }
        throw new AlreadyBoundException();
    }

    public final boolean bindTarget(String b) {
        if (aMatched && !targetBound()) {
            B = b;
            abm = abPattern.matcher(A + ' ' + B);
            abMatched = abm.matches();
            return matches();
        }
        throw new AlreadyBoundException();
    }

    // unbinding
    public final void unbindTarget() {
        abm = null;
        B = null;
        abMatched = false;
    }

    public final void unbindSourceAndTarget() {
        unbindTarget();
        am = null;
        A = null;
        aMatched = false;
    }

    public void unbind() {
        unbindSourceAndTarget();
        aPattern = abPattern = null;
        dir = -1;
    }

    // matches() returns true if the rule has matched *so far*
    public boolean matches() {
        return am == null ? true : (aMatched && (abm == null ? true : abMatched));
    }

    // versions of matches() that bind temporarily, then unbind
    public final boolean matches(String a) {
        boolean m = bindSource(a);
        unbindSourceAndTarget();
        return m;
    }

    public final boolean matches(String a, String b) {
        boolean m = bindSource(a) && bindTarget(b);
        unbindSourceAndTarget();
        return m;
    }

    // methods to test if the rule is fully or partly bound
    public final boolean dirBound() {
        return dir >= 0;
    }
    public final boolean targetBound() {
        return B != null;
    }
    public final boolean sourceBound() {
        return A != null;
    }

    // expanded pattern methods
    public final String regexA() { return expandDir(pattern.getSourceName()); }
    public final String regexAB() { return expandDir(pattern.getSourceName() + ' ' + pattern.getTargetName()); }

    // main expand() methods
    protected final String expand(String s) {
        return expandTarget(expandMod(expandDec(expandInc(expandGroupOrSource(expandDir(s))))));
    }

    // expansion of $F, $B, $L, $R
    private static Pattern dirPattern = Pattern.compile("\\$(F|B|L|R|\\+L|\\+\\+L|\\+R|\\+\\+R)");
    protected String expandDir(String s) {
        Matcher m = dirPattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String var = m.group(1);
            int nbrs = topology.neighborhoodSize();
            if (var.equals("F"))
                m.appendReplacement(sb, topology.dirString(dir));
            else if (var.equals("B"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs / 2) % nbrs));
            else if (var.equals("L"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs - 1) % nbrs));
            else if (var.equals("+L"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs - 2) % nbrs));
            else if (var.equals("++L"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs - 3) % nbrs));
            else if (var.equals("R"))
                m.appendReplacement(sb, topology.dirString((dir + 1) % nbrs));
            else if (var.equals("+R"))
                m.appendReplacement(sb, topology.dirString((dir + 2) % nbrs));
            else if (var.equals("++R"))
                m.appendReplacement(sb, topology.dirString((dir + 3) % nbrs));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // expansion of $1, $2, ... and $S
    static Pattern groupPattern = Pattern.compile("\\$(S|[1-9]\\d*)");
    protected final String expandGroupOrSource(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            Matcher m = groupPattern.matcher(s);
            while (m.find()) {
                String g = m.group(1);
                if (g.equals("S"))
                    m.appendReplacement(sb, A);
                else
                    m.appendReplacement(sb, getGroup(g));
            }
            m.appendTail(sb);
        } catch (Exception e) {
            System.err.println("While expanding " + s);
            e.printStackTrace();
        }
        return sb.toString();
    }

    // expansion of $T
    static Pattern targetPattern = Pattern.compile("\\$T");
    protected final String expandTarget(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            Matcher m = targetPattern.matcher(s);
            while (m.find())
                m.appendReplacement(sb, B);
            m.appendTail(sb);
        } catch (Exception e) {
            System.err.println("While expanding " + s);
            e.printStackTrace();
        }
        return sb.toString();
    }

    // expansion of $+1.n
    static Pattern incGroupPattern = Pattern.compile("\\$\\+(\\d*)\\.?([1-9]\\d*)");
    protected final String expandInc(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            Matcher m = incGroupPattern.matcher(s);
            while (m.find()) {
                String inc = m.group(1), g = m.group(2);
                int n = string2int(getGroup(g));
                int delta = inc.length() > 0 ? string2int(inc) : 1;
                m.appendReplacement(sb, int2string(n + delta));
            }
            m.appendTail(sb);
        } catch (Exception e) {
            System.err.println("While expanding " + s);
            e.printStackTrace();
        }
        return sb.toString();
    }

    // expansion of $-1.n
    static Pattern decGroupPattern = Pattern.compile("\\$\\-(\\d*)\\.?([1-9]\\d*)");
    protected final String expandDec(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            Matcher m = decGroupPattern.matcher(s);
            while (m.find()) {
                String dec = m.group(1), g = m.group(2);
                int n = string2int(getGroup(g));
                int delta = dec.length() > 0 ? string2int(dec) : 1;
                if (n >= delta)
                    m.appendReplacement(sb, int2string(n - delta));
            }
            m.appendTail(sb);
        } catch (Exception e) {
            System.err.println("While expanding " + s);
            e.printStackTrace();
        }
        return sb.toString();
    }

    // expansion of $%3+1.n
    static Pattern modGroupPattern = Pattern.compile("\\$%([1-9]\\d*)\\+(\\d*)\\.?([1-9]\\d*)");
    protected final String expandMod(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            Matcher m = modGroupPattern.matcher(s);
            while (m.find()) {
                String mod = m.group(1), inc = m.group(2), g = m.group(3);
                int n = string2int(getGroup(g));
                int M = string2int(mod);
                int delta = inc.length() > 0 ? string2int(inc) : 1;
                m.appendReplacement(sb, int2string((n + delta) % M));
            }
            m.appendTail(sb);
        } catch (Exception e) {
            System.err.println("While expanding " + s);
            e.printStackTrace();
        }
        return sb.toString();
    }

    // helper method to get a group ($1,$2,...) from AB
    String getGroup(String group) {
        String val = "";
        try {
            int n = new Integer(group).intValue();
            if (n <= abm.groupCount())
                val = abm.group(n);
        } catch (Exception e) {
            System.err.println("While trying to get group $" + group + " matching " + A + " " + B + " to " + abPattern.pattern());
            e.printStackTrace();
        }
        return val;
    }

    // helper methods to encode/decode decimal numbers
    static private int base = 10;
    static String int2string(int n) {
        return Integer.toString(n, base);
    }
    static int string2int(String s) {
        return Integer.parseInt(s, base);
    }
}
