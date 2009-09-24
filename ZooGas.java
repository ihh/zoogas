import java.awt.Toolkit;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import javax.swing.JFrame;


import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;


import java.awt.Point;
import java.util.Vector;
import java.util.Random;
import java.awt.Color;

import java.awt.FontMetrics;

import java.lang.Math;
import java.lang.String;
import java.lang.RuntimeException;

import java.text.DecimalFormat;

public class ZooGas extends JFrame implements MouseListener, KeyListener {

    // simulation particle params
    int size;  // size of board in cells
    int species;  // number of species
    int aversion;  // number of species that species will consider too similar to prey on
    int omnivorousness;  // number of species that each species can prey on
    double forageEfficiency;  // probability that predation leads successfully to breeding
    double chokeRate;  // probability of dying due to overcrowding
    double birthRate;  // probability of breeding
    int mutateRange;  // mutation distance

    // tool particle params
    double buriedWallDecayRate, exposedWallDecayRate;  // probability of wall decay when buried/exposed
    double cementSetRate, cementStickRate;  // probability of cement setting into wall (or sticking to existing wall)
    double gasDispersalRate;  // probability that a gas particle will disappear
    double gasMultiplyRate;  // probability that a surviving fecundity gas particle will multiply
    double lavaSeedRate;  // probability that lava will stick to a wall particle (it always sticks to basalt)
    double lavaFlowRate;  // probability that lava will take a random step

    // initial conditions
    double initialDensity;  // initial density of species-containing cells
    int initialDiversity;  // initial number of species (can be increased with mutator gas)

    // view
    double refreshPeriod;  // probability of updating a cell before a refresh
    boolean uniformSpeed;  // aim for constant (slow) update speed
    int pixelsPerCell;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int popChartHeight, popBarHeight, entropyBarHeight, statusBarHeight;  // size in pixels of various parts of the status bar (below the board)
    int toolKeyWidth, toolReserveBarWidth, toolHeight, toolBarWidth;  // size in pixels of various parts of the tool bar (right of the board)
    Vector cellColorVec;

    // tools
    int sprayDiameter, sprayPower;  // diameter & power of spraypaint tool
    int[] sprayReserve, sprayMax;
    double[] sprayRefillRate;

    // cheat c0d3z
    String cheatString;
    int cheatStringPos;
    boolean cheating() { return cheatStringPos == cheatString.length(); }

    // underlying cellular automata model
    int cellTypes;  // 0 is assumed to represent inactive, empty space
    Vector pattern;  // probabilistic pattern replacement dictionary, indexed by source cellPairIndex

    // constant helper vars
    int wallParticle, cementParticle, acidParticle, fecundityParticle, mutatorParticle, lavaParticle, basaltParticle;
    int patternMatchesPerRefresh;

    // main board data
    int[][] cell;
    int[] cellCount;

    // helper objects
    Random rnd;
    BufferStrategy bf;
    Graphics bfGraphics;

    Point cursor;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown;  // true if mouse is currently down
    boolean randomPressed;  // true if 'randomize' button was pressed (randomize the model once only)
    boolean idealPressed;  // true if 'idealize' button was pressed (model as a perfectly-mixing ideal gas, using Gillespie algorithm)
    int sprayParticle;  // current spray particle

    int histXPos;  // x-pixel of histogram

    double entropy;  // current entropy score (defined in terms of *relative* species populations)
    double bestEntropy;  // best entropy so far
    double maxEntropy;  // max possible entropy
    double minEntropyOverCycle;  // lowest entropy over a complete "cycle" of the population histogram
    double bestMinEntropyOverCycle;  // best "lowest entropy" score

    // main()
    public static void main(String[] args) {
	new ZooGas();
    }
	

    // constructor
    public ZooGas() {
	// set main parameters
	this.species = 18;
	this.omnivorousness = 11;
	this.aversion = 1;

	this.initialDensity = .1;
	this.initialDiversity = 3;

	this.size = 128;  // ideally a power of 2, for good random numbers
	this.pixelsPerCell = 4;

	double bdpScale = 1;  // scaling factor for birth/death/predation process
	this.forageEfficiency = .8 * bdpScale;
	this.birthRate = .02 * bdpScale;
	this.chokeRate = .01 * bdpScale;

	this.mutateRange = 2;

	this.refreshPeriod = 1;
	this.uniformSpeed = true;

	this.buriedWallDecayRate = .00018;
	this.exposedWallDecayRate = .00022;

	this.cementSetRate = .2;
	this.cementStickRate = .9;

	this.gasDispersalRate = .1;
	this.gasMultiplyRate = .2;

	this.lavaSeedRate = .01;
	this.lavaFlowRate = .3;

	// set helpers, etc.
	this.rnd = new Random();
	this.cell = new int[size][size];
	this.boardSize = size * pixelsPerCell;

	this.patternMatchesPerRefresh = (int) (refreshPeriod * size * size);

	this.wallParticle = species + 1;
	this.cementParticle = species + 2;
	this.acidParticle = species + 3;
	this.fecundityParticle = species + 4;
	this.mutatorParticle = species + 5;
	this.lavaParticle = species + 6;
	this.basaltParticle = species + 7;

	this.cellTypes = basaltParticle + 1;

	// init color vector
	this.cellColorVec = new Vector (cellTypes);
	cellColorVec.add (Color.black);  // empty space
	for (int s = 0; s < species; ++s)
	    cellColorVec.add (Color.getHSBColor ((float) s / (float) (species+1), 1, 1));
	float gasHue = (float) species / (float) (species+1);
	cellColorVec.add (Color.gray);  // walls
	cellColorVec.add (Color.white);  // cement
	cellColorVec.add (Color.darkGray);  // acids
	cellColorVec.add (Color.getHSBColor (gasHue, (float) .5, (float) .5));  // fecundity gas
	cellColorVec.add (Color.getHSBColor (gasHue, (float) .5, (float) 1));  // mutator gas
	cellColorVec.add (Color.lightGray);  // lava
	cellColorVec.add (Color.orange);  // basalt

	// init pattern-matching rule dictionary
	this.cellTypes = cellColorVec.size();
	this.pattern = new Vector (cellTypes * cellTypes);
	for (int p = 0; p < cellTypes * cellTypes; ++p)
	    pattern.add (new IntegerRandomVariable());

	// call method to add probabilistic pattern-matching replacement rules
	addPatterns();

	// pad any empty patterns, just to level out the speed
	if (uniformSpeed)
	    for (int p = 0; p < cellTypes * cellTypes; ++p) {
		IntegerRandomVariable d = (IntegerRandomVariable) pattern.get(p);
		if (d.size() == 0)
		    d.add (p, 1);
	    }

	// normalize probability distributions over matched-pattern replacements
	for (int p = 0; p < pattern.size(); ++p)
	    ((IntegerRandomVariable) pattern.get(p)).normalize();

	// init board summary counts
	cellCount = new int[cellTypes];
	for (int c = 0; c < cellTypes; ++c)
	    cellCount[c] = 0;

	// init board
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		if (rnd.nextDouble() < initialDensity) {
		    int s = rnd.nextInt(initialDiversity) * (species/initialDiversity) + 1;
		    this.cell[x][y] = s;
		    ++cellCount[s];
		} else
		    ++cellCount[0];

	// init spray tools
	initSprayTools();

	// init view
	this.popChartHeight = 100;
	this.popBarHeight = 4;
	this.entropyBarHeight = 20;
	this.statusBarHeight = popChartHeight + popBarHeight * (species + 1) + entropyBarHeight;

	this.toolKeyWidth = 16;
	this.toolReserveBarWidth = 100;
	this.toolHeight = 30;
	this.toolBarWidth = toolKeyWidth + toolReserveBarWidth;

	this.histXPos = 0;

	this.bestEntropy = 0;
	this.maxEntropy = log2(species);
	this.entropy = log2(initialDiversity);
	this.minEntropyOverCycle = entropy;
	this.bestMinEntropyOverCycle = entropy;

	// init Swing
	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	this.setUndecorated(true);
	this.setSize(boardSize + toolBarWidth,boardSize + statusBarHeight);
	this.setVisible(true);

	// double buffering
	this.createBufferStrategy(2);
	bf = this.getBufferStrategy();
	bfGraphics = bf.getDrawGraphics();

	// register for mouse & keyboard events
	this.cursor = new Point();
	this.mouseDown = false;
	this.randomPressed = false;
	this.idealPressed = false;

        addMouseListener(this);
        addKeyListener(this);

	// run game loop
	gameLoop();
    }

    // builder method for patterns
    private void addPatterns() {
	// the cyclic ecology
	for (int s = 1; s <= species; ++s)
	    {
		// adjacent to emptiness
		addPattern (s, 0, s, s, birthRate);  // spontaneous birth
		addPattern (s, 0, 0, s, 1 - birthRate);  // no birth, so take a random walk step

		// adjacent to self
		addPattern (s, s, 0, s, chokeRate);  // spontaneous death due to overcrowding
		addPattern (s, s, s, s, 1 - chokeRate);  // no overcrowding, we're good pals here

		// adjacent to wall
		addPattern (s, wallParticle, 0, wallParticle, chokeRate);  // spontaneous death due to being muthafuckin BURIED ALIVE or CRUSHED AGAINST A BRICK WALL
		addPattern (s, wallParticle, s, wallParticle, 1 - chokeRate);  // no overcrowding, take a deep breath

		// adjacent to prey
		for (int t = aversion; t < aversion + omnivorousness; ++t) {
		    int prey = ((s - 1 + t) % species) + 1;
		    addPattern (s, prey, s, s, forageEfficiency);  // eat + breed (i.e. convert)
		    addPattern (s, prey, s, 0, 1. - forageEfficiency);  // eat + don't breed
		}

		// adjacent to other
		for (int t = 0; t < species; ++t)
		    if (t < aversion || t >= aversion + omnivorousness) {
			int other = ((s - 1 + t) % species) + 1;
			addPattern (s, other, 0, other, chokeRate);  // spontaneous death due to overcrowding
			addPattern (s, other, s, other, 1 - chokeRate);  // no overcrowding, we're good pals here
		    }
	    }

	// decaying walls
	for (int c = 0; c < cellTypes; ++c)
	    {
		double decayRate = (c == wallParticle ? buriedWallDecayRate : (c == acidParticle ? 1 : exposedWallDecayRate));
		addPattern (wallParticle, c, 0, c, decayRate);  // wall decays
		addPattern (wallParticle, c, wallParticle, c, 1 - decayRate);  // wall lives
	    }

	// drifting & setting cement
	for (int c = 1; c < cellTypes; ++c) {
	    double setRate = (c == wallParticle ? cementStickRate : cementSetRate);
	    addPattern (cementParticle, c, wallParticle, c, setRate);  // cement sets into wall
	    addPattern (cementParticle, c, cementParticle, c, 1 - setRate);  // cement stays liquid
	}
	addPattern (cementParticle, 0, 0, cementParticle, 1);  // liquid cement always does random walk step

	// death gas
	for (int c = 1; c < cellTypes; ++c) {
	    addPattern (acidParticle, c, acidParticle, 0, gasMultiplyRate);  // acid lives
	    addPattern (acidParticle, c, 0, 0, 1 - gasMultiplyRate);  // acid dies
	}
	addPattern (acidParticle, 0, 0, acidParticle, 1);  // acid always does random walk step, doesn't disperse

	// fecundity gas
	for (int c = 1; c <= species; ++c) {
	    addPattern (fecundityParticle, c, fecundityParticle, 0, 1);  // fecundity particle makes species BREED
	    addPattern (c, fecundityParticle, c, c, 1);
	}
	addPattern (fecundityParticle, 0, fecundityParticle, fecundityParticle, gasMultiplyRate);  // gas breeds (!? gives illusion of pressure, I guess)
	addPattern (fecundityParticle, 0, 0, fecundityParticle, 1 - gasMultiplyRate);  // gas does random walk step

	// mutator gas
	for (int c = 1; c <= species; ++c)
	    for (int t = -mutateRange; t <= mutateRange; ++t)
		if (t != 0)
		    {
			int mutant = ((c - 1 + t + species) % species) + 1;
			double mutProb = Math.pow (gasMultiplyRate, Math.abs(t) - 1);
			addPattern (mutatorParticle, c, 0, mutant, mutProb);  // fecundity particle makes species mutate into random other species
			addPattern (mutatorParticle, c, mutatorParticle, c, 1 - mutProb);  // fecundity particle makes species mutate into random other species
		    }
	addPattern (mutatorParticle, 0, 0, 0, gasDispersalRate);  // gas disperses
	addPattern (mutatorParticle, 0, 0, mutatorParticle, 1 - gasDispersalRate);  // gas does random walk step
	addPattern (mutatorParticle, fecundityParticle, mutatorParticle, mutatorParticle, 1);  // fecundity gas reacts with mutator to produce MORE mutator

	// flowing & setting lava
	for (int c = 1; c < cellTypes; ++c) {
	    if (c == basaltParticle || c == wallParticle) {
		double setRate = (c == basaltParticle ? 1 : lavaSeedRate);
		addPattern (lavaParticle, c, basaltParticle, c, setRate);  // lava sets into basalt
		addPattern (lavaParticle, c, lavaParticle, c, 1 - setRate);  // lava stays liquid
	    }
	}
	addPattern (lavaParticle, 0, 0, lavaParticle, lavaFlowRate);  // lava does random walk step
	addPattern (lavaParticle, 0, 0, lavaParticle, 1 - lavaFlowRate);  // lava stays put

	addPattern (basaltParticle, fecundityParticle, lavaParticle, 0, 1);  // this just for fun: perfume melts basalt
    }

    // helper to add a pattern
    private void addPattern (int pc_old, int nc_old, int pc_new, int nc_new, double prob) {
	if (pc_old == 0)
	    throw new RuntimeException (new String ("Can't trigger a rule on empty space"));
	((IntegerRandomVariable) pattern.get (makeCellPairIndex(pc_old,nc_old))).add (makeCellPairIndex(pc_new,nc_new), prob);
    }

    // init tools method
    private void initSprayTools() {
	this.sprayDiameter = 6;
	this.sprayPower = 45;

	this.sprayReserve = new int[cellTypes];
	this.sprayMax = new int[cellTypes];
	this.sprayRefillRate = new double[cellTypes];

	for (int c = 0; c < cellTypes; ++c) {
	    sprayReserve[c] = sprayMax[c] = 0;
	    sprayRefillRate[c] = 0.;
	}

	double baseRefillRate = 0.25 * (double) sprayPower;

	sprayRefillRate[cementParticle] = .75 * baseRefillRate;
	sprayMax[cementParticle] = 1000;

	sprayRefillRate[acidParticle] = .75 * baseRefillRate;
	sprayMax[acidParticle] = 800;

	sprayRefillRate[fecundityParticle] = .5 * baseRefillRate;
	sprayMax[fecundityParticle] = 100;

	sprayRefillRate[mutatorParticle] = .03 * baseRefillRate;
	sprayMax[mutatorParticle] = 100;

	sprayRefillRate[lavaParticle] = .5 * baseRefillRate;
	sprayMax[lavaParticle] = 400;

	this.sprayParticle = cementParticle;

	this.cheatString = new String ("boosh");
	this.cheatStringPos = 0;
    }

    // main game loop
    private void gameLoop() {
	// Your game logic goes here.

	drawEverything();

	Point mousePos = new Point(), sprayCell = new Point();
	while (true)
	    {
		evolveStuff();

		plotCounts();

		refreshBuffer();

		// randomize
		if (randomPressed) {
		    randomizeBoard();
		    redrawBoard();
		    randomPressed = false;
		}

		// do spray
		if (mouseDown) {
		    mousePos = getMousePosition();
		    if (mousePos != null) {
			cursor.x = (int) (mousePos.x / pixelsPerCell);
			cursor.y = (int) (mousePos.y / pixelsPerCell);

			if (cursor.x < size && cursor.y < size)
			    for (int i = 0; i < sprayPower; ++i) {
				if (sprayReserve[sprayParticle] > 0) {

				    sprayCell.x = (cursor.x + rnd.nextInt(sprayDiameter) + size - sprayDiameter / 2) % size;
				    sprayCell.y = (cursor.y + rnd.nextInt(sprayDiameter) + size - sprayDiameter / 2) % size;

				    int oldCell = readCell (sprayCell);
				    if (oldCell == 0)
					writeCell (sprayCell, sprayParticle, oldCell);

				    // unless this cell was already full of spray, or a wall, deplete the reserve
				    //				    if (oldCell != sprayParticle && oldCell != wallParticle)
				    if (oldCell == 0)
					--sprayReserve[sprayParticle];
				}
			    }
		    }

		} else {  // if (mouseDown) ...

		    // not spraying, so refresh spray reserves
		    for (int c = 0; c < cellTypes; ++c) {
			double refillRate = sprayRefillRate[c] * (entropy + 1) / (maxEntropy + 1);

			if (refillRate > 0. && sprayReserve[c] < sprayMax[c])
			    {
				if (refillRate > 1.)
				    sprayReserve[c] += (int) (refillRate + .5);
				else if (rnd.nextDouble() < refillRate)
				    ++sprayReserve[c];
			    }
		    }
		}
	    }
    }

    private void refreshBuffer() {
	bf.show();
	Toolkit.getDefaultToolkit().sync();	
    }

    private void evolveStuff() {
	Point p = new Point(), n = new Point();
	int pc, nc;
	for (int u = 0; u < patternMatchesPerRefresh; ++u)
	    {
		getRandomPoint(p);
		pc = readCell(p);
		if (uniformSpeed || pc != 0) {
		    getRandomNeighbor(p,n);
		    nc = readCell (n);
		    int cellPairIndex = makeCellPairIndex (pc, nc);
		    IntegerRandomVariable pd = (IntegerRandomVariable) pattern.get(cellPairIndex);
		    if (uniformSpeed || pd.size() > 0)
			writeCellPair (p, n, pd.sample (rnd), cellPairIndex);
		}
	    }
    }

    private void getRandomPoint (Point p) {
	p.x = rnd.nextInt(size);
	p.y = rnd.nextInt(size);
    }

    private void getRandomNeighbor (Point p, Point n) {
	int ni = rnd.nextInt(4);
	n.x = p.x;
	n.y = p.y;
	int delta = (ni & 1) == 0 ? size-1 : +1;
	if ((ni & 2) == 0) { n.x = (n.x + delta) % size; } else { n.y = (n.y + delta) % size; }
    }
    private int neighborhoodSize() { return 4; }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }

    private void writeCellPair (Point p, Point n, int newCellPairIndex) {
	writeCellPair (p, n, newCellPairIndex, makeCellPairIndex (readCell(p), readCell(n)));
    }

    private void writeCellPair (Point p, Point n, int newCellPairIndex, int oldCellPairIndex) {
	writeCell (p, (int) (newCellPairIndex / cellTypes), (int) (oldCellPairIndex / cellTypes));
	writeCell (n, newCellPairIndex % cellTypes, oldCellPairIndex % cellTypes);
    }

    private int makeCellPairIndex (int pc, int nc) {
	return nc + cellTypes * pc;
    }

    private int readCell (Point p) {
	if (idealPressed) {
	    int x = rnd.nextInt (size * size);
	    int rv = 0;
	    while (rv < cellTypes - 1) {
		x -= cellCount[rv];
		if (x <= 0)
		    break;
		++rv;
	    }
	    if (cellCount[rv] == 0) {
		for (int k = 0; k < cellTypes; ++k)
		    System.out.println (k + " " + cellCount[k]);
		System.out.println ("Sampled: " + rv + " " + cellCount[rv]);
		throw new RuntimeException (new String ("Returned a zero-probability cell type"));
	    }
	    return rv;
	}
	return cell[p.x][p.y];
    }

    private void writeCell (Point p, int pc) {
	writeCell (p, pc, readCell(p));
    }

    private void writeCell (Point p, int pc, int old_pc) {
	if (old_pc != pc) {
	    if (!idealPressed) {
		cell[p.x][p.y] = pc;
		drawCell (p);
	    }
	    --cellCount[old_pc];
	    ++cellCount[pc];
	}
    }

    private void randomizeBoard() {
	// randomize board without changing total cell counts
	// uses a Fisher-Yates shuffle

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		cell[p.x][p.y] = 0;

	int i = 0;
	for (int c = 1; c < cellTypes; ++c)
	    for (int k = 0; k < cellCount[c]; ++k)
		{
		    cell[i % size][i / size] = c;
		    ++i;
		}

	for (int k = 0; k < i; ++k)
	    {
		int kSwap = k + rnd.nextInt (size*size - k);
		int tmp = cell[kSwap % size][kSwap / size];
		cell[kSwap % size][kSwap / size] = cell[k % size][k / size];
		cell[k % size][k / size] = tmp;
	    }
    }

    private void drawCell (Point p) {
	bfGraphics.setColor(cellColor(cell[p.x][p.y]));
	bfGraphics.fillRect(p.x*pixelsPerCell,p.y*pixelsPerCell,pixelsPerCell,pixelsPerCell);
    }

    public Color cellColor (int cval) {
	Color c = null;
	if (cval >= 0 && cval < cellTypes)
	    c = (Color) cellColorVec.get (cval);
	return c;
    }

    private void drawEverything() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize+toolBarWidth,boardSize+statusBarHeight);

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell (p);

	refreshBuffer();
    }

    private void redrawBoard() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize,boardSize);

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell (p);

	refreshBuffer();
    }

    // status & tool bars
    private void plotCounts() {
	int h = 0;
	int cellsOnBoard = size * size;
	double[] p = new double[species + 1];
	entropy = 0;

	int maxCount = 0, totalCount = 0;
	for (int c = 1; c <= species; ++c) {
	    if (cellCount[c] > maxCount)
		maxCount = cellCount[c];
	    totalCount += cellCount[c];
	}

	for (int c = 1; c <= species; ++c) {
	    p[c] = ((double) cellCount[c]) / (double) totalCount;
	    if (p[c] > 0 && p[c] <= 1)
		entropy -= p[c] * log2(p[c]);

	    bfGraphics.setColor(cellColor(c));
	    int b = popChartHeight * cellCount[c] / totalCount;
	    int w = boardSize * cellCount[c] / maxCount;
	    if (b < 1 && cellCount[c] > 0)
		b = 1;
	    if (b + h > popChartHeight)
		b = popChartHeight - h;
	    bfGraphics.fillRect(histXPos,boardSize + popChartHeight - h - b,1,b);
	    h += b;

	    bfGraphics.fillRect(0,boardSize + popChartHeight + popBarHeight * c,w,popBarHeight);
	    bfGraphics.setColor(Color.black);
	    bfGraphics.fillRect(w+1,boardSize + popChartHeight + popBarHeight * c,boardSize-w-1,popBarHeight);
	}
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(histXPos,boardSize,1,popChartHeight - h);

	histXPos = (histXPos + 1) % (boardSize);

	bfGraphics.setColor(Color.white);
	bfGraphics.fillRect(histXPos,boardSize,1,popChartHeight);

	// calculate entropy scores
	if (histXPos == 0) {  // just completed a cycle?

	    if (minEntropyOverCycle > bestMinEntropyOverCycle)
		bestMinEntropyOverCycle = minEntropyOverCycle;

	    minEntropyOverCycle = entropy;
	}

	if (entropy < minEntropyOverCycle)
	    minEntropyOverCycle = entropy;

	if (entropy > bestEntropy)
	    bestEntropy = entropy;

	// entropy bar
	int entropyBarBase = boardSize + popChartHeight + popBarHeight * (species + 1);
	float entropyBarLevel = (float) (entropy / maxEntropy);
	if (entropyBarLevel < 0 || entropyBarLevel > 1) {
	    System.out.println ("entropyBarLevel: " + entropyBarLevel);
	    throw new RuntimeException (new String ("Entropy outside permitted range"));
	}
	int entropyBarWidth = (int) (entropyBarLevel * (float) boardSize);

	double minGray = .1, redThreshold = 2*minGray;
	float entropyGrayLevel = (float) Math.max (entropyBarLevel, minGray);
	float entropyRedLevel = (float) Math.max (entropyGrayLevel, (redThreshold - entropyGrayLevel) / redThreshold);
	Color entropyColor = new Color (entropyRedLevel, entropyGrayLevel, entropyGrayLevel);
	bfGraphics.setColor(entropyColor);
	bfGraphics.fillRect(0,entropyBarBase,entropyBarWidth,entropyBarHeight);

	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(entropyBarWidth,entropyBarBase,boardSize - entropyBarWidth,entropyBarHeight);

	bfGraphics.setColor(Color.pink);
	bfGraphics.fillRect((int) ((bestMinEntropyOverCycle / maxEntropy) * (double) boardSize),entropyBarBase,1,entropyBarHeight);

	bfGraphics.setColor(Color.white);
	bfGraphics.fillRect((int) ((bestEntropy / maxEntropy) * (double) boardSize),entropyBarBase,1,entropyBarHeight);

	// entropy scores
	DecimalFormat myFormatter = new DecimalFormat("###.000");
	String entropyString = myFormatter.format(Math.pow(2,entropy));
	String bestEntropyString = myFormatter.format(Math.pow(2,bestEntropy));
	String minEntropyString = myFormatter.format(Math.pow(2,minEntropyOverCycle));
	String bestMinEntropyString = myFormatter.format(Math.pow(2,bestMinEntropyOverCycle));

	FontMetrics fm = bfGraphics.getFontMetrics();
	int ch = fm.getHeight();
	int sw = fm.stringWidth(entropyString);
	int bsw = fm.stringWidth(bestEntropyString);

	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(boardSize,boardSize + statusBarHeight - 5*ch,toolBarWidth,5*ch);

	bfGraphics.setColor(entropyColor);
	bfGraphics.drawString (entropyString, boardSize + toolBarWidth - sw, boardSize + statusBarHeight);

	bfGraphics.setColor(Color.white);
	bfGraphics.drawString (bestEntropyString, boardSize + toolBarWidth - bsw, boardSize + statusBarHeight - ch);

	// toolbar
	int m = 0;
	for (int c = 0; c < cellTypes; ++c)
	    if (sprayMax[c] > m)
		m = sprayMax[c];

	plotReserve ('S', 0, cementParticle, 1);
	plotReserve ('D', 1, acidParticle, .8);
	plotReserve ('F', 2, fecundityParticle, .5);
	plotReserve ('G', 3, mutatorParticle, .2);

	plotOrHide ('B', 4, lavaParticle, .4, cheating());

	printOrHide (cheatString, 6, cheating());
    }

    private int toolYCenter (int row) { return (2 * row + 1) * toolHeight / 2; }

    private void printOrHide (String text, int row, boolean show) {
	FontMetrics fm = bfGraphics.getFontMetrics();
	int xSize = fm.stringWidth(text), xPos = boardSize + toolBarWidth - xSize;
	int ch = fm.getHeight(), yPos = toolYCenter(row) + ch / 2;
	if (show) {
	    bfGraphics.setColor (Color.white);
	    bfGraphics.drawString (text, xPos, yPos);
	} else {
	    bfGraphics.setColor (Color.black);
	    bfGraphics.fillRect (xPos, yPos - ch + 1, xSize, ch);
	}
    }

    private void plotOrHide (char c, int row, int particle, double scale, boolean show) {
	if (show)
	    plotReserve (c, row, particle, scale);
	else
	    {
		bfGraphics.setColor (Color.black);
		bfGraphics.fillRect (boardSize, toolYCenter(row) - toolHeight/2, toolBarWidth, toolHeight);
	    }
    }

    private void plotReserve (char c, int row, int particle, double scale) {
	char[] ca = new char[1];
	ca[0] = c;
	int center = toolYCenter(row);
	int count = sprayReserve[particle];

	FontMetrics fm = bfGraphics.getFontMetrics();
	int cw = fm.charWidth(c);
	int ch = fm.getHeight();

	bfGraphics.setColor (cellColor(particle));
	bfGraphics.drawChars (ca, 0, 1, boardSize + toolReserveBarWidth + toolKeyWidth/2 - cw/2, center + ch/2);

	int w = (int) (scale * (double) (toolReserveBarWidth * count / sprayMax[particle]));
	if (w > toolReserveBarWidth)
	    w = toolReserveBarWidth;

	int bh = toolHeight * 3 / 4;
	bfGraphics.fillRect (boardSize + toolReserveBarWidth - w, center - bh/2, w, bh);
	bfGraphics.setColor (Color.black);
	bfGraphics.fillRect (boardSize, center - bh/2, toolReserveBarWidth - w, bh);
    }

    // mouse events
    public void mousePressed(MouseEvent e) {
	mouseDown = true;
    }

    public void mouseReleased(MouseEvent e) {
	mouseDown = false;
    }

    public void mouseEntered(MouseEvent e) {
	mouseDown = false;
    }

    public void mouseExited(MouseEvent e) {
	mouseDown = false;
    }

    public void mouseClicked(MouseEvent e) {
	mouseDown = false;
    }

    // key events
    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
	char c = e.getKeyChar();
	if (c == 's' || c == 'S') {
	    mouseDown = true;
	    sprayParticle = cementParticle;
	} else if (c == 'd' || c == 'D') {
	    mouseDown = true;
	    sprayParticle = acidParticle;
	} else if (c == 'f' || c == 'F') {
	    mouseDown = true;
	    sprayParticle = fecundityParticle;
	} else if (c == 'g' || c == 'G') {
	    mouseDown = true;
	    sprayParticle = mutatorParticle;
	} else if (cheating())
	    {
		// cheats
		if (c == 'k' || c == 'K') {
		    randomPressed = true;
		} else if (c == 'l' || c == 'L') {
		    idealPressed = true;
		} else if (c == '0') {
		    initSprayTools();
		} else if (c == '9') {
		    mouseDown = true;
		    if (sprayParticle >= species)
			sprayParticle = 1;
		    else
			++sprayParticle;
		    sprayReserve[sprayParticle] = 123456789;
		} else if (c == '8') {
		    sprayReserve[sprayParticle] = 123456789;
		} else if (c == '7') {
		    sprayDiameter *= 1.5;
		    sprayPower *= 2.25;
		} else if (c == '6') {
		    sprayPower *= 1.5;
		} else if (c == 'b' || c == 'B') {
		    mouseDown = true;
		    sprayParticle = lavaParticle;
		}
	    }
    }

    public void keyReleased(KeyEvent e) {
	char c = e.getKeyChar();
	if (cheatStringPos < cheatString.length())
	    {
		if (c == cheatString.charAt (cheatStringPos))
		    ++cheatStringPos;
	    }
	else {  // cheating() == true
	    if (c == 'l' || c == 'L')
		randomPressed = true;
	}
	mouseDown = false;
	idealPressed = false;
    }
}


