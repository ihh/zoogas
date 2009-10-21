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
    public ParticlePattern (String n, Color c) {
	namePattern = Pattern.compile(n);
	color = c;
    }

    public ParticlePattern (String nc) {
	String[] args = nc.split(" ");
	if (args.length >= 4) {
	    namePattern = Pattern.compile (args[0]);
	    color = new Color (Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
	}
    }

    // method to match a name and return a Particle, or null if match fails
    Particle makeParticle (String name, Board board, PatternSet ps) {
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
