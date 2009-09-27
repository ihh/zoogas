import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;
import javax.swing.JFrame;

public class ZooGas extends JFrame implements MouseListener, KeyListener {

    // simulation particle params
    int size = 128;  // size of board in cells
    int species = 18;  // number of species
    int aversion = 1;  // number of species that species will consider too similar to prey on
    int omnivorousness = 11;  // number of species that each species can prey on
    double forageEfficiency = .8;  // probability that predation leads successfully to breeding
    double chokeRate = .01;  // probability of dying due to overcrowding
    double birthRate = .02;  // probability of breeding
    int mutateRange = 2;  // mutation distance

    // tool particle params
    double buriedWallDecayRate = .00018, exposedWallDecayRate = .00022;  // probability of wall decay when buried/exposed
    double cementSetRate = .2, cementStickRate = .9;  // probability of cement setting into wall (or sticking to existing wall)
    double gasDispersalRate = .1;  // probability that a gas particle will disappear
    double gasMultiplyRate = .2;  // probability that a surviving fecundity gas particle will multiply (gives illusion of pressure)
    double lavaSeedRate = .01;  // probability that lava will stick to a wall particle (it always sticks to basalt)
    double lavaFlowRate = .3;  // probability that lava will take a random step

    // initial conditions
    double initialDensity = .1;  // initial density of species-containing cells
    int initialDiversity = 3;  // initial number of species (can be increased with mutator gas)

    // view
    int pixelsPerCell = 4;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int popChartHeight = 100, popBarHeight = 4, entropyBarHeight = 20, statusBarHeight;  // size in pixels of various parts of the status bar (below the board)
    int toolKeyWidth = 16, toolReserveBarWidth = 100, toolHeight = 30, toolBarWidth;  // size in pixels of various parts of the tool bar (right of the board)
    Vector cellColorVec;

    // tools
    int sprayDiameter, sprayPower;  // diameter & power of spraypaint tool
    int[] sprayReserve, sprayMax;
    double[] sprayRefillRate;

    // cheat c0d3z
    String cheatString = "boosh";
    int cheatStringPos = 0;
    boolean cheating() { return cheatStringPos == cheatString.length(); }

    // networking
    BoardServer boardServer = null;  // board servers field UDP requests for cross-border interactions
    ConnectionServer connectServer = null;   // connectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    int boardServerPort = 4444;
    String localhost = null;

    // underlying cellular automata model
    int cellTypes;  // 0 is assumed to represent inactive, empty space
    Vector pattern;  // probabilistic pattern replacement dictionary, indexed by source cellPairIndex

    // constant helper vars
    int wallParticle, cementParticle, acidParticle, fecundityParticle, mutatorParticle, lavaParticle, basaltParticle;
    int patternMatchesPerRefresh;

    // main board data
    int[][] cell, cellWriteCount;
    int[] cellCount;
    HashMap remoteCell;  // map of off-board Point's to RemoteCellCoord's

    // helper objects
    Random rnd;
    BufferStrategy bf;
    Graphics bfGraphics;

    Point cursor;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown;  // true if mouse is currently down
    boolean randomPressed;  // true if 'randomize' button was pressed (randomize the model once only)
    boolean idealPressed;  // true if 'idealize' button was pressed (model as a perfectly-mixing ideal gas, using Gillespie algorithm)
    int sprayParticle;  // current spray particle

    int histXPos = 0;  // x-pixel of histogram

    double entropy;  // current entropy score (defined in terms of *relative* species populations)
    double bestEntropy;  // best entropy so far
    double maxEntropy;  // max possible entropy
    double minEntropyOverCycle;  // lowest entropy over a complete "cycle" of the population histogram
    double bestMinEntropyOverCycle;  // best "lowest entropy" score

    // main()
    public static void main(String[] args) {
	// create ZooGas
	ZooGas gas = null;
	switch (args.length)
	    {
	    case 0:
		gas = new ZooGas();  // standalone
		break;
	    case 1:
		gas = new ZooGas(new Integer(args[0]).intValue());  // server on specified port
		break;
	    case 3:
		gas = new ZooGas(new Integer(args[0]).intValue(), new InetSocketAddress (args[1], new Integer(args[2]).intValue()));  // client, connecting to server at specified address/port
		break;
	    default:
		System.err.println ("Usage: <progname> [<port> [<remote address> <remote port>]]");
		break;
	    }

	// run it
	gas.gameLoop();
    }

    // networked constructor (client)
    public ZooGas (int port, InetSocketAddress remote) {
	this(port);

	connectBorder (new Point(0,0), new Point(-1,0), new Point(0,1), 128, new Point(-size,0), remote);  // west
	connectBorder (new Point(127,0), new Point(128,0), new Point(0,1), 128, new Point(+size,0), remote);  // east
	connectBorder (new Point(0,0), new Point(0,-1), new Point(1,0), 128, new Point(0,-size), remote);  // north
	connectBorder (new Point(0,127), new Point(0,128), new Point(1,0), 128, new Point(0,+size), remote);  // south
    }

    // networked constructor (server)
    public ZooGas (int port) {
	this();
	this.boardServerPort = port;

	try {
	    boardServer = new BoardServer (this, boardServerPort);
	    boardServer.start();

	    connectServer = new ConnectionServer (this, boardServerPort);
	    connectServer.start();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    // default constructor
    public ZooGas() {

	// set helpers, etc.
	rnd = new Random();
	cell = new int[size][size];
	cellWriteCount = new int[size][size];
	remoteCell = new HashMap();
	boardSize = size * pixelsPerCell;

	patternMatchesPerRefresh = (int) (size * size);

	wallParticle = species + 1;
	cementParticle = species + 2;
	acidParticle = species + 3;
	fecundityParticle = species + 4;
	mutatorParticle = species + 5;
	lavaParticle = species + 6;
	basaltParticle = species + 7;

	cellTypes = basaltParticle + 1;

	// init color vector
	cellColorVec = new Vector (cellTypes);
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
	cellTypes = cellColorVec.size();
	pattern = new Vector (cellTypes * cellTypes);
	for (int p = 0; p < cellTypes * cellTypes; ++p)
	    pattern.add (new IntegerRandomVariable());

	// call method to add probabilistic pattern-matching replacement rules
	addPatterns();

	// pad any empty patterns, just to level out the speed
	for (int p = 0; p < cellTypes * cellTypes; ++p) {
	    IntegerRandomVariable d = (IntegerRandomVariable) pattern.get(p);
	    if (d.size() == 0)
		d.add (p, 1);
	}

	// init board summary counts
	cellCount = new int[cellTypes];
	for (int c = 0; c < cellTypes; ++c)
	    cellCount[c] = 0;

	// init board
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y) {
		cellWriteCount[x][y] = 0;
		if (rnd.nextDouble() < initialDensity) {
		    int s = rnd.nextInt(initialDiversity) * (species/initialDiversity) + 1;
		    cell[x][y] = s;
		    ++cellCount[s];
		} else
		    ++cellCount[0];
	    }

	// init spray tools
	initSprayTools();

	// init view
	statusBarHeight = popChartHeight + popBarHeight * (species + 1) + entropyBarHeight;
	toolBarWidth = toolKeyWidth + toolReserveBarWidth;

	entropy = log2(initialDiversity);
	bestEntropy = minEntropyOverCycle = bestMinEntropyOverCycle = entropy;
	maxEntropy = log2(species);

	// init Swing
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	setUndecorated(true);
	setSize(boardSize + toolBarWidth,boardSize + statusBarHeight);
	setVisible(true);

	// double buffering
	createBufferStrategy(2);
	bf = getBufferStrategy();
	bfGraphics = bf.getDrawGraphics();

	// register for mouse & keyboard events
	cursor = new Point();
	mouseDown = false;
	randomPressed = false;
	idealPressed = false;

        addMouseListener(this);
        addKeyListener(this);

	// net init
	try {
	    localhost = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    e.printStackTrace();
	}
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
	sprayDiameter = 6;
	sprayPower = 45;

	sprayReserve = new int[cellTypes];
	sprayMax = new int[cellTypes];
	sprayRefillRate = new double[cellTypes];

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

	sprayParticle = cementParticle;

	cheatStringPos = 0;
    }

    // main game loop
    private void gameLoop() {
	// Game logic goes here.

	drawEverything();

	while (true)
	    {
		evolveStuff();
		useTools();

		plotCounts();
		refreshBuffer();
	    }
    }

    private void useTools() {
	Point mousePos, sprayCell = new Point();

	// randomize
	if (randomPressed) {
	    randomizeBoard();
	    redrawBoard();
	    refreshBuffer();
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

			    sprayCell.x = cursor.x + rnd.nextInt(sprayDiameter) - sprayDiameter / 2;
			    sprayCell.y = cursor.y + rnd.nextInt(sprayDiameter) - sprayDiameter / 2;

			    if (onBoard(sprayCell)) {
				int oldCell = readCell (sprayCell);
				if (oldCell == 0) {
				    writeCell (sprayCell, sprayParticle, oldCell);
				    --sprayReserve[sprayParticle];
				}
			    }
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

    protected void refreshBuffer() {
	bf.show();
	Toolkit.getDefaultToolkit().sync();	
    }

    private void evolveStuff() {
	Point p = new Point(), n = new Point();
	for (int u = 0; u < patternMatchesPerRefresh; ++u)
	    {
		getRandomPoint(p);
		getRandomNeighbor(p,n);
		evolvePair(p,n);
	    }
    }

    private boolean onBoard (Point p) { return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size; }

    private void evolvePair (Point sourceCoords, Point targetCoords)
    {
	if (onBoard (targetCoords)) {
	    evolveLocalSourceAndLocalTarget (sourceCoords, targetCoords);
	} else {
	    // request remote evolveLocalTargetForRemoteSource
	    RemoteCellCoord remoteCoords = (RemoteCellCoord) remoteCell.get (targetCoords);
	    if (remoteCoords != null)
		evolveLocalSourceAndRemoteTarget (sourceCoords, remoteCoords);
	}
    }

    protected void evolveLocalSourceAndRemoteTarget (Point sourceCoords, RemoteCellCoord remoteCoords) {
	BoardServer.sendEvolveDatagram (remoteCoords.addr, remoteCoords.port, remoteCoords.p, readCell(sourceCoords), sourceCoords, localhost, boardServerPort, getCellWriteCount(sourceCoords));
    }

    synchronized void evolveLocalSourceAndLocalTarget (Point sourceCoords, Point targetCoords)
    {
	writeCell (sourceCoords, evolveTargetForSource (targetCoords, readCell(sourceCoords)));
    }

    synchronized int evolveLocalTargetForRemoteSource (Point targetCoords, int oldSourceState)
    {
	return evolveTargetForSource (targetCoords, oldSourceState);
    }

    int evolveTargetForSource (Point targetCoords, int oldSourceState)
    {
	int oldTargetState = readCell (targetCoords);

	int oldCellPairIndex = makeCellPairIndex (oldSourceState, oldTargetState);
	int newCellPairIndex = ((IntegerRandomVariable) pattern.get(oldCellPairIndex)).sample(rnd);

	writeCell (targetCoords, getTargetState (newCellPairIndex));
	return getSourceState (newCellPairIndex);
    }


    private void connectBorder (Point sourceStart, Point targetStart, Point lineVector, int lineLength, Point remoteOrigin, InetSocketAddress remoteBoard) {
	String[] connectRequests = new String [lineLength];
	Point source = new Point (sourceStart);
	Point target = new Point (targetStart);
	for (int i = 0; i < lineLength; ++i) {
	    Point remoteSource = new Point (source.x - remoteOrigin.x, source.y - remoteOrigin.y);
	    Point remoteTarget = new Point (target.x - remoteOrigin.x, target.y - remoteOrigin.y);

	    addRemoteCellCoord (target, remoteBoard, remoteTarget);
	    connectRequests[i] = BoardServer.connectString (remoteSource, source, localhost, boardServerPort);

	    source.x += lineVector.x;
	    source.y += lineVector.y;

	    target.x += lineVector.x;
	    target.y += lineVector.y;
	}

	BoardServer.sendTCPPacket (remoteBoard.getAddress(), remoteBoard.getPort(), connectRequests);
    }

    protected void addRemoteCellCoord (Point p, InetSocketAddress remoteBoard, Point pRemote) {
	System.err.println("Connecting (" + p.x + "," + p.y + ") to (" + pRemote.x + "," + pRemote.y + ") on " + remoteBoard);
	remoteCell.put (new Point(p), new RemoteCellCoord (remoteBoard, pRemote));
    }

    private void getRandomPoint (Point p) {
	p.x = rnd.nextInt(size);
	p.y = rnd.nextInt(size);
    }

    private void getRandomNeighbor (Point p, Point n) {
	int ni = rnd.nextInt(4);
	n.x = p.x;
	n.y = p.y;
	int delta = (ni & 1) == 0 ? -1 : +1;
	if ((ni & 2) == 0) { n.x += delta; } else { n.y += delta; }
	// Replace previous two lines with the following for periodic boundary conditions:
	/*
	  int delta = (ni & 1) == 0 ? size-1 : +1;
	  if ((ni & 2) == 0) { n.x = (n.x + delta) % size; } else { n.y = (n.y + delta) % size; }
	*/
    }
    private int neighborhoodSize() { return 4; }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }


    private int makeCellPairIndex (int sourceState, int targetState) {
	return targetState + cellTypes * sourceState;
    }

    private int getSourceState (int cellPairIndex) {
	return cellPairIndex / cellTypes;
    }

    private int getTargetState (int cellPairIndex) {
	return cellPairIndex % cellTypes;
    }

    protected int getCellWriteCount (Point p) {
	return cellWriteCount[p.x][p.y];
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
		    System.err.println (k + " " + cellCount[k]);
		System.err.println ("Sampled: " + rv + " " + cellCount[rv]);
		throw new RuntimeException (new String ("Returned a zero-probability cell type"));
	    }
	    return rv;
	}
	return cell[p.x][p.y];
    }

    protected void writeCell (Point p, int pc) {
	writeCell (p, pc, readCell(p));
    }

    private void writeCell (Point p, int pc, int old_pc) {
	if (old_pc != pc) {
	    if (!idealPressed) {
		cell[p.x][p.y] = pc;
		++cellWriteCount[p.x][p.y];
		drawCell(p);
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

    protected void redrawBoard() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize,boardSize);

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell (p);
    }

    // status & tool bars
    protected void plotCounts() {
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

	if (maxCount > 0)
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
	    System.err.println ("entropyBarLevel: " + entropyBarLevel);
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
	    bfGraphics.setColor (Color.getHSBColor ((float) rnd.nextDouble(), 1, 1));
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


