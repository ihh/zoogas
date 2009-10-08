import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;

public class Particle {
    public String name = null;
    public Color color = null;
    protected IdentityHashMap pattern = new IdentityHashMap();  // production rules, indexed by neighbor Particle
    protected int count = 0;  // how many on the board

    // methods
    public Particle (String name, Color color) {
	this.name = name;
	this.color = color;
    }
}
