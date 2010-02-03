import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleSet {
    public RuleSet() {
        rules = new TreeSet<String>();
    }
    public RuleSet(String filename) {
        this();

        try {
            FileInputStream fis = new FileInputStream(filename);
            InputStreamReader read = new InputStreamReader(fis);
            BufferedReader buff = new BufferedReader(read);
            try {
                while (buff.ready()) {
                    String s = buff.readLine();

                    if (isRule(s)) {
                        rules.add(s);
                        byteSize += 1 + s.getBytes().length;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected SortedSet<String> rules = null;
    protected int byteSize = 0;

    public boolean containsRules(String s) {
        return rules.contains(s);
    }

    /**
     *Gets a copy of all unprocessed rules as they are read from the rules file
     * @return
     */
    public SortedSet<String> getAllRawRules() {
        return rules;
    }

    /**
     *Gets the number of bytes required to verify these rules
     * @return
     */
    public int getByteSize() {
        return byteSize;
    }

    public static boolean isRule(String str) {
        final Pattern commentRegex = Pattern.compile(" *#.*");
        final Pattern whiteSpace = Pattern.compile("^ *$");
        if (commentRegex.matcher(str).matches() || whiteSpace.matcher(str).matches())
            return false;
        return true;
    }
}
