import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;

public class ParticlePattern {
    // data
    Pattern namePattern = null;
    Color color = null;
    
    // constructors
    static Pattern catchAllPattern = Pattern.compile(".*");
    public ParticlePattern (String n, Color c) {
	try {
	    namePattern = Pattern.compile(n);
	} catch (Exception e) {
	    e.printStackTrace();
	    namePattern = catchAllPattern;
	}
	color = c;
    }

    public ParticlePattern (String n, String colorString) {
	this(n,new Color(Integer.parseInt(colorString,16)));
    }

    // method to match a name and return a Particle, or null if match fails
    Particle makeParticle (String name, Board board, PatternSet ps) {
	//	System.err.println("Matching "+name+" to "+namePattern.pattern());
	Particle p = null;
	Matcher m = namePattern.matcher(name);
	if (m.matches())
	    p = new Particle (name, color, board, ps);
	return p;
    }

    // toString method
    public String toString() {
	return namePattern + " " + color.getRed() + " " + color.getGreen() + " " + color.getBlue();
    }
}
