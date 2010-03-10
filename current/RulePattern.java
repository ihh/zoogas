import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;
import java.io.*;

public class RulePattern {
    // data
    private String prefix = null;
    protected String A = null;
    private String B = null;
    
    // constructors
    public RulePattern (String w, String a, String b) {
	prefix = w;
	A = a;
	B = b;
    }

    public final String getSourceName() {
        return A;
    }
    
    public final String getTargetName() {
        return B;
    }
}
