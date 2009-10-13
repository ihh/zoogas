import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Patterns are to be transmitted in a "Particle definition" packet with the following structure:
// ParticlePatterns (one per line, format "NAME R G B", describing appearances of Particles to which this definition packet applies)
// RulePatterns (one per line, format "A B C D P V")

public class PatternSet {
    // data
    RulePattern[] rulePattern = null;
    ParticlePattern[] particlePattern = null;
}
