import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Particle class, encapsulating the behavior, appearance & summary statistics of a given CA state
public class Particle {
    public String name = "Particle";
    public Color color = null;
    protected IdentityHashMap pattern = new IdentityHashMap();  // production rules, indexed by neighbor Particle
    protected int count = 0;  // how many on the board

    // constructor
    public Particle (String name, Color color) {
	this.name = name;
	this.color = color;
    }

    // methods
    String visibleName() {
	String[] partsOfName = name.split ("/", 2);
	return partsOfName[0].replaceAll("_"," ");
    }
}
