import java.io.IOException;

import java.util.*;
import java.util.regex.*;

// class to parse config rule lines (VERB, NOUN, BOND, TOOL)
// syntax of initializer string:
// RULETYPE <ArgumentExpression> <ArgumentExpression>  <ArgumentExpression> ...
// where RULETYPE is a rule identifier string (VERB, NOUN etc) and <ArgumentExpression> is as follows:
//  B=xxx  means "there is an optional argument B which, if omitted, has default value xxx"
//  B!     means "there is a mandatory argument B cannot be omitted"
//  B*     means "there is an argument B which can occur zero or more times"

// syntax of optional XML mapping string:
// <ArgumentMapping> <ArgumentMapping>  <ArgumentMapping> ...
// where <ArgumentMapping> is as follows:
//  B=xyz  means "argument B maps to XML tag xyz, i.e. <xyz>...</xyz>"
public class RuleSyntax {
    private String firstWord = null;
    private Map<String,String> argType = new HashMap<String,String>();   // argType values are "=", "!" or "*"
    private Map<String,String> defaultArg = new HashMap<String,String>();
    private Map<String,String> argXmlTag = new HashMap<String,String>();

    private boolean match = false;  // set to true after a successful match

    private static class ArgValPair {
	ArgValPair(String a,String v) { arg = a; val = v; }
	String arg, val;
    }
    private LinkedList<ArgValPair> parsedArgValPairs = new LinkedList<ArgValPair>();  // populated after a successful match
    private Map<String,String> parsedArg = new HashMap<String,String>();  // populated after a successful match

    // regexes
    static Pattern firstWordPattern = Pattern.compile("^(\\S+)");
    static Pattern defaultArgPattern = Pattern.compile("\\b(\\S)(=|!|\\*)(\\S*)");
    static Pattern parsedArgPattern = Pattern.compile("\\b(\\S)=(\\S+)");

    // constructors
    RuleSyntax (String init) {
	Matcher m = firstWordPattern.matcher(init);
	if (m.find()) {
	    firstWord = m.group(1);
	    m = defaultArgPattern.matcher(init);
	    while (m.find()) {
		String attr = m.group(1), type = m.group(2), val = m.group(3);
		argType.put(attr,type);
                if (type.equals("=")) {
		    defaultArg.put(attr,val);
                }
                else if (type.equals("!")) {
	            defaultArg.put(attr,null);
                }
	    }
	}
    }

    RuleSyntax (String init, String xmlMapping) {
	this(init);
	Matcher m = parsedArgPattern.matcher(xmlMapping);
	while (m.find()) {
	    String attr = m.group(1), tag = m.group(2);
	    argXmlTag.put(attr,tag);
	}
    }

    // parse method
    public boolean matches(String s) {
	match = false;
	parsedArg.clear();
	parsedArgValPairs.clear();
	Matcher m = firstWordPattern.matcher(s);
	if (m.find() && m.group(1).equals(firstWord)) {
	    match = true;
	    m = parsedArgPattern.matcher(s);
	    while (m.find()) {
		String arg = m.group(1);
		String val = m.group(2);
		parsedArgValPairs.add (new ArgValPair(arg, val));
		String type = argType.get(arg);
		if (type == null)
		    System.err.println("RuleSyntax: unrecognized argument "+arg+" in "+firstWord+" line");
		else {
		    String oldVal = parsedArg.get(arg);
		    if (oldVal != null) {
			if (type.equals("*"))
			    parsedArg.put(arg,oldVal+" "+val);
			else
			    System.err.println("RuleSyntax: duplicate argument "+arg+" in "+firstWord+" line");
                    } else {
			parsedArg.put(arg,val);
                    }
		}
	    }
	    for (Map.Entry<String,String> argVal : defaultArg.entrySet()) {
                String arg = argVal.getKey();
                String type = argType.get(arg);
                String val = parsedArg.get(arg);
                if (type.equals("!") && val == null) {
                    System.err.println("RuleSyntax: mandatory argument "+arg+" missing from "+firstWord+" line");
                    match = false;
                    break;
                }
            }
	}
	if (match) System.err.println(makeXML());
	return match;
    }

    // arg accessors
    boolean hasValue(String arg) {
	return parsedArg.containsKey(arg) || argType.get(arg).equals("=");
    }

    String getValue(String arg) {
	String val = null;
	if (parsedArg.containsKey(arg))
	    val = parsedArg.get(arg);
	if (val == null)
	    val = defaultArg.get(arg);
	if (val == null)
	    throw new RuntimeException("RuleSyntax: got null value for argument '"+arg+"' in "+firstWord+" line");
	return val;
    }

    // XML mapping
    // TODO: makeDTD() method?
    String makeXML() {
	String xml = null;
	if (match) {
	    xml = "<" + firstWord + ">";
	    for (ArgValPair argval : parsedArgValPairs) {
		String tag = argXmlTag.containsKey(argval.arg) ? argXmlTag.get(argval.arg) : argval.arg;
		xml = xml
		    + " <" + tag + ">"
		    + argval.val
		    + "</" + tag + ">";
	    }
	    xml = xml + " </" + firstWord + ">";
	}
	return xml;
    }
}
