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

    // constructors
    public RuleMatch() { }
    public RuleMatch(String a) { bindA(a); }
    public RuleMatch(String a,String b) { bindA(a); bindB(b); }

    // lhs methods
    void bindA(String a) {
	A = a;
	am = pattern.A.matcher(a);
    }

    void bindB(String b) {
	B = b;
	bm = pattern.B.matcher(b);
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
    // just placeholders for now (need to expand $1, $2 etc)
    String C() { return pattern.C; }
    String D() { return pattern.D; }
}
