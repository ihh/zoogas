import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.awt.Color;
import java.text.*;
import java.net.*;
import java.io.*;

public class ParticlePattern {
    // data
    Pattern namePattern = null;
    Color color = null;
    double energy = 0;
    String prefix = null; // The set of rules this pattern belongs to
    
    // constructors
    static Pattern catchAllPattern = Pattern.compile(".*");
    public ParticlePattern (String w, String n, Color c, double en) {
	try {
	    namePattern = Pattern.compile(n);
	} catch (Exception e) {
	    e.printStackTrace();
	    namePattern = catchAllPattern;
	}
	color = c;
	energy = en;
        prefix = w;
    }

    public ParticlePattern (String w, String n, String colorString, String energyString) {
	this(w, n, new Color(Integer.parseInt(colorString,16)),Double.parseDouble(energyString));
    }

    // method to match a name and return a Particle, or null if match fails
    Particle makeParticle(String name, Board board, PatternSet ps) {
	//	System.err.println("Matching "+name+" to "+namePattern.pattern());
	Particle p = null;
	Matcher m = namePattern.matcher(name);
	if (m.matches())
	    p = new Particle(name, prefix, color, energy, board, ps);
	return p;
    }

    // toString method
    public String toString() {
	return namePattern + " " + color.getRed() + " " + color.getGreen() + " " + color.getBlue();
    }
}
