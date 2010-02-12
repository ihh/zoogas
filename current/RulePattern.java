import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;
import java.io.*;

public class RulePattern {
    // data
    String prefix = null, A = null, B = null;
    
    // constructors
    public RulePattern (String a, String b) {
	prefix = "";  // TODO: initialize this from the new (mandatory) "w=..." field of the VERB line. Throw an error if it does not match the pattern ^[^/]+/$
	A = a;
	B = b;
    }
}
