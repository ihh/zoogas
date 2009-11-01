import java.util.*;
import java.util.regex.*;

// class to parse config rule lines (VERB, NOUN, BOND, TOOL)
public class RuleSyntax {
    private String firstWord = null;
    private Map<String,String> defaultArg = new HashMap<String,String>();
    private Map<String,String> parsedArg = new HashMap<String,String>();

    // regexes
    static Pattern firstWordPattern = Pattern.compile("^(\\S+)");
    static Pattern defaultArgPattern = Pattern.compile("\\b(\\S)(=(\\S*)|)\\b");
    static Pattern parsedArgPattern = Pattern.compile("\\b(\\S)=(\\S*)");
    
    // constructor
    RuleSyntax (String init) {
	Matcher m = firstWordPattern.matcher(init);
	if (m.find()) {
	    firstWord = m.group(1);
	    m = defaultArgPattern.matcher(init);
	    while (m.find()) {
		String attr = m.group(1), eq = m.group(2), val = m.group(3);
		defaultArg.put(attr,eq.length()>0 ? val : null);
	    }
	}
    }

    // parse method
    public boolean matches(String s) {
	boolean match = false;
	parsedArg.clear();
	Matcher m = firstWordPattern.matcher(s);
	if (m.find() && m.group(1).equals(firstWord)) {
	    m = parsedArgPattern.matcher(s);
	    while (m.find()) {
		String arg = m.group(1), val = m.group(2);
		if (!defaultArg.containsKey(arg))
		    System.err.println("RuleSyntax: unrecognized argument "+arg+" in "+firstWord+" line");
		parsedArg.put(arg,val);
	    }
	    match = true;
	    for (Iterator<Map.Entry<String,String>> iter = defaultArg.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,String> argVal = iter.next();
		String arg = argVal.getKey();
		String defaultVal = argVal.getValue();
		String parsedVal = parsedArg.get(arg);
		if ((parsedVal == null || parsedVal.length() == 0) && defaultVal != null && defaultVal.length() == 0) {
		    System.err.println("RuleSyntax: mandatory argument "+arg+" missing from "+firstWord+" line");
		    match = false;
		    break;
		}
	    }
	}
	return match;
    }

    // arg accessor
    String getValue(String arg) {
	String val = null;
	if (parsedArg.containsKey(arg))
	    val = parsedArg.get(arg);
	if (val == null || val.length() == 0)
	    val = defaultArg.get(arg);
	if (val == null)
	    throw new RuntimeException("RuleSyntax: got null value for argument '"+arg+"' in "+firstWord+" line");
	return val;
    }
}
