import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A set that contains an organized set of the rules (not necessarily parsed)
 */
public class RuleSet {
    public RuleSet() {
        ruleLines = new TreeSet<String>();
        wordSets = new HashMap<String, SortedSet<String>>();
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
                        ruleLines.add(s);
                        byteSize += 1 + s.getBytes().length;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
         
        for(String prefix : undeclaredWords) {
            System.err.println("RuleSet: undeclared Word: " + prefix);
        }
    }

    protected int byteSize = 0;
    private SortedSet<String> ruleLines = null; // set containing all rule lines
    private HashMap<String, SortedSet<String>> wordSets = null; // set of Words, and their rules
    private Set<String> undeclaredWords = new HashSet<String>();

    public boolean containsRules(String s) {
        return ruleLines.contains(s);
    }

    /**
     *Gets a copy of all unprocessed ruleLines as they are read from the ruleLines file
     * @return
     */
    public SortedSet<String> getAllRawRules() {
        return ruleLines;
    }

    /**
     *Gets the number of bytes required to verify these ruleLines
     * @return
     */
    public int getByteSize() {
        return byteSize;
    }

    /**
     *Returns true if a line is interpreted as a meaningful rule line.
     * @param str
     * @return
     */
    public static boolean isRule(String str) {
        final Pattern commentRegex = Pattern.compile(" *#.*");
        final Pattern whiteSpace = Pattern.compile("^ *$");
        if (commentRegex.matcher(str).matches() || whiteSpace.matcher(str).matches())
            return false;

        return true;
    }
    
    private SortedSet<String> getOrCreateWordSet(String prefix) {
        SortedSet<String> set;
        if(wordSets.containsKey(prefix)) {
            set = wordSets.get(prefix);
        }
        else {
            set = new TreeSet<String>();
            wordSets.put(prefix, set);
            undeclaredWords.add(prefix);
        }

        return set;
    }
    
    public boolean add(String str){
        final Pattern wordDeclRegex = Pattern.compile("WORD (.*)"); // handler for declaring a Word type
        final Pattern wordValueRegex = Pattern.compile(".*W=(\\S*/).*");
        if(!ruleLines.contains(str)) {
            Matcher matcher = wordValueRegex.matcher(str); // match Word value
            if(matcher.matches()) {
                String prefix = matcher.group(1);
                ruleLines.add(str);
                if(!getOrCreateWordSet(prefix).add(str))
                    System.err.println("PatternSet: duplicate line: " + str);

                return true;
            } 
            else { // match declaration of a Word
                matcher = wordValueRegex.matcher(str);
                if(matcher.matches()) {
                    String[] prefixes = matcher.group(1).split(" ");
                    for(String s : prefixes) {
                        if(!wordSets.containsKey(s)) {
                            wordSets.put(s, new TreeSet<String>());
                        }
                        
                        undeclaredWords.remove(s);
                    }
                    return true;
                }
            }
        }
        return true; // TODO: add other declaration types?
    }
}
