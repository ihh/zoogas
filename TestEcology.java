import java.lang.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.io.*;
import java.awt.*;

public class TestEcology {
    // ecology params
    int species = 18;  // number of species
    int trophicSymmetry = 3;  // initial number of species (can be increased with mutator gas)
    double lifeRate = .03;  // probability of moving, preying, choking or spawning
    double forageEfficiency = .8;  // probability that predation leads successfully to breeding
    double chokeRate = .05;  // probability of dying due to overcrowding
    double birthRate = .02;  // probability of breeding
    double guestMoveRate = .01;  // move rate of zoo guests

    // tool particle params
    int wallDecayStates = 5;
    double playDecayRate = .001;  // speed of decay events that drive gameplay
    double buriedWallDecayRate = .18, exposedWallDecayRate = .22;  // probability of wall decay when buried/exposed
    double cementSetRate = .2, cementStickRate = .9;  // probability of cement setting into wall (or sticking to existing wall)
    double gasDispersalRate = .1;  // probability that a gas particle will disappear
    double gasMultiplyRate = .1;  // probability that an acid or mutator gas particle will survive for further catalysis after effecting their function
    double lavaSeedRate = .01;  // probability that lava will stick to a wall particle (it always sticks to basalt)
    double lavaFlowRate = .3;  // probability that lava will take a random step
    int mutateRange = 2;  // range of species change due to contact w/mutator gas

    // PatternSet
    PatternSet patternSet = new PatternSet();
    String patternSetFilename = "ECOLOGY.txt";

    // main()
    public static void main(String[] args) {
	// create pattern set
	TestEcology eco = new TestEcology();
	eco.addParticles();
	eco.addPatterns();

	// save pattern set
	eco.patternSet.toFile(eco.patternSetFilename);
    }

    // builder method for particles
    private void addParticles() {
	// init particles
	String sep = Particle.visibleSeparatorChar, spc = Particle.visibleSpaceChar;
	addParticle (spc, Color.black);  // empty space

	for (int s = 0; s < species; ++s)
	    addParticle ("critter" + sep + "s" + RuleMatch.int2string(s), Color.getHSBColor ((float) s / (float) (species+1), 1, 1));

	for (int w = 1; w <= wallDecayStates; ++w) {
	    float gray = (float) w / (float) (wallDecayStates + 1);
	    addParticle ("wall" + sep + RuleMatch.int2string(w), new Color (gray, gray, gray));  // walls (in various sequential states of decay)
	}
	addParticle ("cement", Color.white);  // cement (drifts; sets into wall)

	float gasHue = (float) species / (float) (species+1);
	addParticle ("acid", Color.darkGray);  // acid (destroys most things; dissolves basalt into lava)
	addParticle ("perfume", Color.getHSBColor (gasHue, (float) .5, (float) .5));  // fecundity gas (multiplies; makes animals breed)
	addParticle ("mutator", Color.getHSBColor (gasHue, (float) .5, (float) 1));  // mutator gas (converts animals into nearby species)
	addParticle ("lava", Color.lightGray);  // lava (drifts; sets into basalt)
	addParticle ("wall" + sep + "basalt", Color.orange);  // basalt
	addParticle (sep + "tripwire", new Color(1,1,1));  // tripwire (an invisible, static particle that animals will eat; use as a subtle test of whether animals have escaped)
	addParticle ("zoo_guest", new Color(254,254,254));  // guest (a visible, mobile particle that animals will eat; use as a test of whether animals have escaped)
    }

    // builder method for patterns
    private void addPatterns() {

	// Uncomment this for a super-fast game :-)
	/*
	  playDecayRate *= 10;
	  lifeRate *= 10;
	*/

	// the cyclic ecology
	int types = species / trophicSymmetry;
	for (int type = 0; type < types; ++type) {

	    // make some species a bit faster at moving, and some a bit faster at eating/breeding
	    double mul = 1;
	    double moveRate = lifeRate, myRate = lifeRate;
	    switch (type) {
	    case 0: break;
	    case 1: mul = 1.2; break;
	    case 2: mul = 1.5; break;
	    case 3: mul = 1.3; break;
	    case 4: mul /= 1.2; break;
	    case 5: mul /= 1.5; break;
	    case 6: mul /= 1.3; break;
	    case 7: moveRate *= 1.1; myRate *= 1.2; break;
	    case 8: moveRate *= 1.2; myRate *= 1.1; break;
	    default: break;
	    }
	    moveRate *= mul;
	    myRate /= Math.sqrt(mul);

	    // do species-specific ecology rules and build a general regex for this type
	    StringBuffer typeRegex = new StringBuffer (".*/s([");
	    for (int cyc = 0; cyc < trophicSymmetry; ++cyc) {
		int s = cyc * types + type;
		String sid = RuleMatch.int2string(s);
		typeRegex.append(sid);

		// predation
		StringBuffer preyRegex = new StringBuffer ("");
		StringBuffer predRegex = new StringBuffer ("");
		for (int t = 1; t <= (int) species/2; ++t)
		    preyRegex.append(RuleMatch.int2string((s+t)%species));
		for (int t = ((int) species/2) + 1; t < species; ++t)
		    predRegex.append(RuleMatch.int2string((s+t)%species));

		addPattern (".*/s" + sid + " .*/s[" + predRegex + sid + "] _ $T " + myRate*chokeRate + " choke");
		addPattern (".*/s" + sid + " .*/s[" + preyRegex + "] $S $S " + myRate*forageEfficiency + " eat");
		addPattern (".*/s" + sid + " .*/s[" + preyRegex + "] $S _ " + myRate*(1-forageEfficiency) + " kill");
	    }
	    typeRegex.append ("])");

	    // adjacent to emptiness
	    addPattern (typeRegex + " _ $S $S " + myRate*birthRate + " birth");  // spontaneous birth
	    addPattern (typeRegex + " _ $T $S " + moveRate*(1-myRate*birthRate) + " step");  // no birth, so take a random walk step

	    // adjacent to self
	    addPattern (typeRegex + " $S _ $T " + myRate*chokeRate + " choke");  // spontaneous death due to overcrowding

	    // adjacent to wall
	    addPattern (typeRegex + " wall.* _ $T " + myRate*chokeRate + " choke");
	}

	// adjacent to guest or tripwire: eat'em!
	addPattern (".*s/ /tripwire|zoo_guest _ $S 1 eat");

	// decaying walls
	addPattern ("wall/([2-9a-z]) .* wall/$-1 $T " + playDecayRate * exposedWallDecayRate + " decay");
	addPattern ("wall/([2-9a-z]) wall.* wall/$-1 $T " + playDecayRate * buriedWallDecayRate + " decay");
	addPattern ("wall/([2-9a-z]) acid wall/$-1 $T " + playDecayRate + " decay");

	addPattern ("wall/1 .* _ $T " + playDecayRate * exposedWallDecayRate + " decay");
	addPattern ("wall/1 wall.* _ $T " + playDecayRate * buriedWallDecayRate + " decay");
	addPattern ("wall/1 acid _ $T " + playDecayRate + " decay");

	// drifting & setting cement
	String newWall = "wall/" + RuleMatch.int2string(wallDecayStates-1);
	addPattern ("cement [^_].* " + newWall + " $T " + cementSetRate + " set");
	addPattern ("cement wall.* " + newWall + " $T " + cementStickRate + " stick");
	addPattern ("cement _ $T $S 1 drift");

	// death gas
	addPattern("acid .* $S _ " + gasMultiplyRate + " dissolve");
	addPattern("acid .* _ _ " + (1 - gasMultiplyRate) + " dissolve");
	addPattern("acid /tripwire|wall/basalt|lava|_ $S _ 0 dissolve");
	addPattern("acid /tripwire|wall/basalt|lava|_ _ _ 0 dissolve");
	addPattern("acid _ $T $S 1 drift");

	// fecundity gas
	addPattern("perfume .*/s.* $T $T 1 hornify");
	addPattern(".*/s.* perfume $S $S 1 breed");

	addPattern("perfume _ _ $T " + gasDispersalRate + " disperse");
	addPattern("perfume _ $S $S " + (1-gasDispersalRate)*gasDispersalRate + " billow");
	addPattern("perfume _ $T $S " + (1-gasDispersalRate)*(1-gasDispersalRate) + " drift");

	// mutator gas
	StringBuffer mutateFwd = new StringBuffer("$1$%" + RuleMatch.int2string(species));
	StringBuffer mutateBack = new StringBuffer("$1$%" + RuleMatch.int2string(species));
	for (int t = 0; t < species; ++t)
	    mutateBack.append("+");
	for (int t = 1; t <= mutateRange; ++t) {
	    double mutProb = Math.pow (gasMultiplyRate, t-1);
	    mutateFwd.append("+");
	    mutateBack.deleteCharAt(mutateBack.length()-1);
	    addPattern("mutator (.*/s)([0-9a-z]) _ " + mutateFwd + "2 " + mutProb + " mutate");
	    addPattern("mutator (.*/s)([0-9a-z]) _ " + mutateBack + "2 " + mutProb + " mutate");
	}
	addPattern("mutator _ _ _ " + gasDispersalRate + " disperse");
	addPattern("mutator _ $T $S " + (1-gasDispersalRate) + " drift");
	addPattern("mutator perfume $T $T " + 1 + " react");

	// flowing & setting lava
	addPattern("lava wall/. wall/basalt $T " + lavaSeedRate + " set");
	addPattern("lava wall/basalt $T $T 1 set");
	addPattern("lava _ $T $S " + lavaFlowRate + " flow");

	// basalt
	addPattern("wall/basalt acid _ lava " + lavaFlowRate + " dissolve");

	// guests
	addPattern("zoo_guest _ $T $S " + guestMoveRate + " perambulate");
    }

    // helpers to add particles and patterns
    private void addParticle (String name, Color color) {
	patternSet.addParticlePattern (name, color);
    }

    private void addPattern (String abcdpv) {
	patternSet.addRulePattern (abcdpv);
	// uncomment to print the production rule to stderr
	//	System.err.println (abcdpv);
    }
}
