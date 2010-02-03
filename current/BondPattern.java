import java.util.regex.*;

public class BondPattern {
    // data
    String beginPointLabel = null, endPointLabel = null, bondName = null;

    // constructor
    BondPattern(String b,String e,String n) {
	beginPointLabel = b;
	endPointLabel = e;
	bondName = n;
    }

    // static fromString constructor
    final static Pattern bondPatternRegex = Pattern.compile("\\((\\S+),(\\S+),(\\S+)\\)");
    final static BondPattern fromString(String s) {
	Matcher m = bondPatternRegex.matcher(s);
	if (m.matches()) {
	    return new BondPattern(m.group(1),m.group(3),m.group(2));
	} else {
	    throw new RuntimeException("Could not parse bond expression '"+s+"'");
	}
    }
}
