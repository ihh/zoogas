package zoogas.core.rules;

import java.io.*;

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
    public RuleSyntax (String init) {
    initialize(init);
    makeDTDElements();
    }

    public RuleSyntax (String init, String xmlMapping) {
    initialize(init);
    initializeXmlMapping(xmlMapping);
    makeDTDElements();
    }

    // static constants
    static String documentType = "ZOOGAS";  // DTD Document Type

    // private
    private String firstWord = null;
    private Map<String,String> argType = new HashMap<String,String>();   // argType values are "=", "!" or "*"
    private Map<String,String> defaultArg = new HashMap<String,String>();
    private Map<String,String> argXmlTag = new HashMap<String,String>();
    private Map<String,String> tagArg = new HashMap<String,String>();
    private LinkedList<String> argOrder = new LinkedList<String>();

    private boolean match = false;  // set to true after a successful match

    private LinkedList<String> suppliedArgOrder = new LinkedList<String>();  // populated after a successful match
    private Map<String,String> parsedArg = new HashMap<String,String>();  // populated after a successful match

    // regexes
    static Pattern firstWordPattern = Pattern.compile("^(\\S+)");
    static Pattern defaultArgPattern = Pattern.compile("\\b(\\S)(=|!|\\*)(\\S*)");
    static Pattern parsedArgPattern = Pattern.compile("\\b(\\S)=(\\S+)");

    // debug logging method
    static public PrintStream debugXMLdumpStream = null;  // set this to System.err if you want to print XML to standard error (ZooGas.java also dumps DTD to this if non-null)

    // init method
    private void initialize (String init) {
	Matcher m = firstWordPattern.matcher(init);
	if (m.find()) {
	    firstWord = m.group(1);
	    m = defaultArgPattern.matcher(init);
	    while (m.find()) {
		String attr = m.group(1), type = m.group(2), val = m.group(3);
		argOrder.addLast(attr);
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

    private void initializeXmlMapping(String xmlMapping) {
	Matcher m = parsedArgPattern.matcher(xmlMapping);
	while (m.find()) {
	    String attr = m.group(1), tag = m.group(2);
	    argXmlTag.put(attr,tag);
	    tagArg.put(tag,attr);
	}
    }

    // parse method
    public boolean matches(String s) {
	match = false;
	parsedArg.clear();
	suppliedArgOrder.clear();
	Matcher m = firstWordPattern.matcher(s);
	if (m.find() && m.group(1).equals(firstWord)) {
	    match = true;
	    m = parsedArgPattern.matcher(s);
	    while (m.find()) {
		String arg = m.group(1);
		String val = m.group(2);
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
			suppliedArgOrder.addLast(arg);
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
	if (match && debugXMLdumpStream != null)
	    debugXMLdumpStream.println(makeXML());
	return match;
    }

    // arg accessors
    public boolean hasXmlTagValue(String tag) {
	String arg = tagArg.get(tag);
	if (arg == null)
	    throw new RuntimeException("Tag " + tag + " not found");
	return hasValue(arg);
    }

    private boolean hasValue(String arg) {
	return parsedArg.containsKey(arg) || argType.get(arg).equals("=");
    }

    public String getXmlTagValue(String tag) {
	String arg = tagArg.get(tag);
	if (arg == null)
	    throw new RuntimeException("Tag " + tag + " not found");
	return getValue(arg);
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

    // mapping of a RuleSyntax to a set of XML DTD elements
    private static Vector<String> elements = new Vector<String>();
    private static Set<String> tagsSeen = new TreeSet<String>();
    private void makeDTDElements() {
	String element = "<!ELEMENT " + firstWord + " (";
	Vector<String> attributes = new Vector<String>();
	Vector<String> subElements = new Vector<String>();
	int mySubs = 0;
	for (String arg : argOrder) {
	    String tag = argXmlTag.containsKey(arg) ? argXmlTag.get(arg) : arg;
	    element = element + (mySubs++ > 0 ? ", " : "") + tag;
	    String attribute = "", defaultValueComment = "";
	    String type = argType.get(arg);
	    if (type.equals("!")) {
		// Uncomment to make mandatory arguments into REQUIRED attributes.
		// The current behavior is that they are required sub-elements, and may not be specified as attributes.
		//		element = "";
		//		attribute = "#REQUIRED";
	    } else if (type.equals("=")) {
		element = element + "?";
		String defaultVal = defaultArg.get(arg);
		attribute = '"' + defaultVal + '"';
		defaultValueComment = "    <!-- Default value of " + firstWord + "." + tag + " is \"" + defaultVal + "\" -->";
	    } else if (type.equals("*")) {
		element = element + "*";
		attribute = '"' + defaultArg.get(arg) + '"';
	    }

	    boolean seenTag = tagsSeen.contains(tag);
	    tagsSeen.add(tag);

	    if (attribute.length() > 0)
		attributes.addElement("  <!ATTLIST " + firstWord + " " + tag + " CDATA " + attribute + ">");

	    if (element.length() > 0) {
		if (seenTag)
		    subElements.addElement("  <!-- Element " + tag + " already defined -->" + defaultValueComment);
		else
		    subElements.addElement("  <!ELEMENT " + tag + " (#PCDATA)>" + defaultValueComment);
	    }
	}
	element = element + ")>";

	elements.addElement(element);
	elements.addAll(subElements);
	elements.add("<!-- Optional child elements may, alternatively, be specified as attributes -->");
	elements.addAll(attributes);
	elements.add("<!-- End of top-level element " + firstWord + " -->\n");
    }

    @Deprecated
    public static String makeDTD() {
	String dtd = "<!DOCTYPE " + documentType + " [\n";
	String tab = "    ";
	for (String elem : elements)
	    dtd = dtd + tab + elem + "\n";
	dtd = dtd + "]>\n";
	return dtd;
    }

    // XML encoding of a particular match
    String makeXML() {
	String xml = null;
	if (match) {
	    xml = "<" + firstWord + ">";
	    for (String arg : suppliedArgOrder) {
		String tag = argXmlTag.containsKey(arg) ? argXmlTag.get(arg) : arg;
		xml = xml
		    + " <" + tag + ">"
		    + parsedArg.get(arg)
		    + "</" + tag + ">";
	    }
	    xml = xml + " </" + firstWord + ">";
	}
	return xml;
    }
}
