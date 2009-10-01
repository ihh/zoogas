import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;
import javax.swing.JFrame;
import javax.imageio.ImageIO;

public class ZooGas extends JFrame implements MouseListener, KeyListener {

    // simulation particle params
    int size = 128;  // size of board in cells
    int species = 18;  // number of species
    int aversion = 1;  // number of species that species will consider too similar to prey on
    int omnivorousness = 11;  // number of species that each species can prey on
    double lifeRate = .1;  // probability of moving, preying, choking or spawning
    double forageEfficiency = .8;  // probability that predation leads successfully to breeding
    double chokeRate = .01;  // probability of dying due to overcrowding
    double birthRate = .02;  // probability of breeding

    // tool particle params
    int wallDecayStates = 5;
    double playDecayRate = .006;  // speed of decay events that drive gameplay
    double buriedWallDecayRate = .18, exposedWallDecayRate = .22;  // probability of wall decay when buried/exposed
    double cementSetRate = .2, cementStickRate = .9;  // probability of cement setting into wall (or sticking to existing wall)
    double gasDispersalRate = .1;  // probability that a gas particle will disappear
    double gasMultiplyRate = .2;  // probability that a surviving fecundity gas particle will multiply (gives illusion of pressure)
    double lavaSeedRate = .01;  // probability that lava will stick to a wall particle (it always sticks to basalt)
    double lavaFlowRate = .3;  // probability that lava will take a random step
    int mutateRange = 2;  // range of species change due to contact w/mutator gas

    // initial conditions
    String initImageFilename = "TheZoo.bmp";  // if non-null, initialization loads a seed image from this filename
    // String initImageFilename = null;
    double initialDensity = .1;  // initial density of species-containing cells
    int initialDiversity = 3;  // initial number of species (can be increased with mutator gas)

    // view
    int pixelsPerCell = 4;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int popChartHeight = 100, popBarHeight = 4, entropyBarHeight = 20, statusBarHeight;  // size in pixels of various parts of the status bar (below the board)
    int toolKeyWidth = 16, toolReserveBarWidth = 100, toolHeight = 30, toolBarWidth;  // size in pixels of various parts of the tool bar (right of the board)

    // tools
    int sprayDiameter, sprayPower;  // diameter & power of spraypaint tool
    int[] sprayReserve, sprayMax;
    double[] sprayRefillRate;

    // cheat c0d3z
    String cheatString = "boosh";
    int cheatStringPos = 0;
    boolean cheating() { return cheatStringPos == cheatString.length(); }

    // commentator code ("well done"-type messages)
    int boardUpdateCount = 0;
    int[] timeFirstTrue = new int[100];   // indexed by row: tracks the first time when various conditions are true, so that the messages flash at first

    // networking
    BoardServer boardServer = null;  // board servers field UDP requests for cross-border interactions
    ConnectionServer connectServer = null;   // connectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    int boardServerPort = 4444;
    String localhost = null;

    // underlying cellular automata model
    int cellTypes = 0;
    Vector cellColorVec = new Vector(), cellName = new Vector();
    Vector pattern;  // probabilistic pattern replacement dictionary, indexed by source cellPairIndex

    // constant helper vars
    int wallParticle, cementParticle, acidParticle, fecundityParticle, mutatorParticle, lavaParticle, basaltParticle;
    int patternMatchesPerRefresh;

    // main board data
    int[][] cell, cellWriteCount;
    int[] cellCount;
    HashMap remoteCell;  // map of off-board Point's to RemoteCellCoord's

    // random number generator
    Random rnd;

    // Swing
    BufferStrategy bf;
    Graphics bfGraphics;
    Cursor boardCursor, normalCursor;

    // helper objects
    Point cursorPos;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown;  // true if mouse is currently down
    boolean randomPressed;  // true if 'randomize' button was pressed (randomize the model once only)
    boolean mixPressed;  // true if 'mix' button was pressed (model as a perfectly-mixed gas, i.e. with no spatial fluctuations, using Gillespie algorithm)
    int sprayParticle;  // current spray particle

    int histXPos = 0;  // current x-position of sweeping population graph

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

	// init particles
	newCellType ("_", Color.black);  // empty space
	for (int s = 1; s <= species; ++s)
	    newCellType ("s" + s, Color.getHSBColor ((float) (s-1) / (float) (species+1), 1, 1));

	int[] wallParticles = new int[wallDecayStates];
	for (int w = 1; w <= wallDecayStates; ++w) {
	    float gray = (float) w / (float) (wallDecayStates + 1);
	    wallParticles[w-1] = newCellType ("w" + w, new Color (gray, gray, gray));  // walls
	}
	wallParticle = wallParticles[0];
	cementParticle = newCellType ("ts", Color.white);  // cement

	float gasHue = (float) species / (float) (species+1);
	acidParticle = newCellType ("td", Color.darkGray);  // acids
	fecundityParticle = newCellType ("tf", Color.getHSBColor (gasHue, (float) .5, (float) .5));  // fecundity gas
	mutatorParticle = newCellType ("tg", Color.getHSBColor (gasHue, (float) .5, (float) 1));  // mutator gas
	lavaParticle = newCellType ("tb", Color.lightGray);  // lava
	basaltParticle = newCellType ("wb", Color.orange);  // basalt

	// init pattern-matching rule dictionary
	pattern = new Vector (cellTypes * cellTypes);
	for (int p = 0; p < cellTypes * cellTypes; ++p)
	    pattern.add (new IntegerRandomVariable());

	// call method to add probabilistic pattern-matching replacement rules
	System.err.println ("Adding production rules");
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
	boolean boardInitialized = false;
	if (initImageFilename != null) {

	    // uncomment to get rgb values of colors for designing image...
	    /*
	    for (int t = 1; t < cellTypes; ++t) {
		Color testColor = (Color) cellColorVec.get(t);
		System.out.println(t + " " + testColor);
	    }
	    */

	    try {
		BufferedImage img = ImageIO.read(new File(initImageFilename));
		for (int x = 0; x < size; ++x)
		    for (int y = 0; y < size; ++y) {
			int c = img.getRGB(x,y);
			int red = (c & 0x00ff0000) >> 16;
			int green = (c & 0x0000ff00) >> 8;
			int blue = c & 0x000000ff;

			// find state with closest color
			if (red!=0 || green!=0 || blue!=0) {
			    int s = 0, dmin = 0;
			    for (int t = 1; t < cellTypes; ++t) {
				Color ct = (Color) cellColorVec.get(t);
				int rdist = red - ct.getRed(), gdist = green - ct.getGreen(), bdist = blue - ct.getBlue();
				int dist = rdist*rdist + gdist*gdist + bdist*bdist;
				if (s == 0 || dist < dmin) {
				    s = t;
				    dmin = dist;
				}
			    }
			    if (s == acidParticle)
				System.err.println ("Warning: dark grey pixel in image at (" + x + "," + y + "), RGB value (" + red + "," + green + "," + blue + ") is being read as acid");
			    cell[x][y] = s;
			}
		    }

		boardInitialized = true;

	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	if (!boardInitialized)  // fallback: randomize board
	    for (int x = 0; x < size; ++x)
		for (int y = 0; y < size; ++y)
		    if (rnd.nextDouble() < initialDensity) {
			int s = rnd.nextInt(initialDiversity) * (species/initialDiversity) + 1;
			cell[x][y] = s;
		    }

	// init cell counts
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y) {
		cellWriteCount[x][y] = 0;
		++cellCount[cell[x][y]];
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

	boardCursor = new Cursor(Cursor.HAND_CURSOR);
	normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);

	// double buffering
	createBufferStrategy(2);
	bf = getBufferStrategy();
	bfGraphics = bf.getDrawGraphics();

	// register for mouse & keyboard events
	cursorPos = new Point();
	mouseDown = false;
	randomPressed = false;
	mixPressed = false;

        addMouseListener(this);
        addKeyListener(this);

	// net init
	try {
	    localhost = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    // builder method for cell types
    private int newCellType (String name, Color color) {
	cellColorVec.add (color);
	cellName.add (name);
	++cellTypes;
	return cellTypes - 1;
    }

    private String stateName (int s) {
	return (String) cellName.get(s);
    }

    // builder method for patterns
    private void addPatterns() {
	// the cyclic ecology
	for (int s = 1; s <= species; ++s)
	    {
		// adjacent to emptiness
		addPattern (s, 0, s, s, lifeRate*birthRate);  // spontaneous birth
		addPattern (s, 0, 0, s, lifeRate*(1-birthRate));  // no birth, so take a random walk step
		addPattern (s, 0, s, 0, 1 - lifeRate);  // do nothing

		// adjacent to self
		addPattern (s, s, 0, s, lifeRate*chokeRate);  // spontaneous death due to overcrowding
		addPattern (s, s, s, s, 1 - lifeRate*chokeRate);  // no overcrowding, we're good pals here

		// adjacent to wall
		for (int w = wallParticle; w < wallParticle + wallDecayStates; ++w) {
		    addPattern (s, w, 0, w, lifeRate*chokeRate);  // spontaneous death due to being muthafuckin BURIED ALIVE or CRUSHED AGAINST A BRICK WALL
		    addPattern (s, w, s, w, 1 - lifeRate*chokeRate);  // no overcrowding, take a deep breath
		}

		// adjacent to prey
		for (int t = aversion; t < aversion + omnivorousness; ++t) {
		    int prey = ((s - 1 + t) % species) + 1;
		    addPattern (s, prey, s, s, lifeRate*forageEfficiency);  // eat + breed (i.e. convert)
		    addPattern (s, prey, s, 0, lifeRate*(1 - forageEfficiency));  // eat + don't breed
		    addPattern (s, prey, s, prey, 1 - lifeRate);  // don't eat or breed
		}

		// adjacent to other
		for (int t = 0; t < species; ++t)
		    if (t < aversion || t >= aversion + omnivorousness) {
			int other = ((s - 1 + t) % species) + 1;
			addPattern (s, other, 0, other, lifeRate*chokeRate);  // spontaneous death due to overcrowding
			addPattern (s, other, s, other, 1 - lifeRate*chokeRate);  // no overcrowding, we're good pals here
		    }
	    }

	// decaying walls
	for (int w = wallParticle; w < wallParticle + wallDecayStates; ++w)
	    for (int c = 0; c < cellTypes; ++c)
		{
		    boolean isWall = c >= wallParticle && c < wallParticle + wallDecayStates;
		    double decayRate = playDecayRate * (isWall ? buriedWallDecayRate : (c == acidParticle ? 1 : exposedWallDecayRate));
		    addPattern (w, c, (w == wallParticle) ? 0 : (w-1), c, decayRate);  // wall decays
		    addPattern (w, c, w, c, 1 - decayRate);  // wall lives
		}

	// drifting & setting cement
	for (int c = 1; c < cellTypes; ++c) {
	    boolean isWall = c >= wallParticle && c < wallParticle + wallDecayStates;
	    double setRate = (isWall ? cementStickRate : cementSetRate);
	    addPattern (cementParticle, c, wallParticle + wallDecayStates - 1, c, setRate);  // cement sets into wall
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
	    addPattern (fecundityParticle, c, c, c, 1);  // fecundity particle makes species BREED
	    addPattern (c, fecundityParticle, c, c, 1);
	}
	addPattern (fecundityParticle, 0, fecundityParticle, fecundityParticle, lifeRate*gasMultiplyRate);  // gas breeds (!? gives illusion of pressure, I guess)
	addPattern (fecundityParticle, 0, 0, fecundityParticle, gasDispersalRate * (1 - lifeRate*gasMultiplyRate));  // gas disperses
	addPattern (fecundityParticle, 0, 0, fecundityParticle, (1 - gasDispersalRate) * (1 - lifeRate*gasMultiplyRate));  // gas does random walk step

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
	    boolean isWall = c >= wallParticle && c < wallParticle + wallDecayStates;
	    if (c == basaltParticle || isWall) {
		double setRate = (c == basaltParticle ? 1 : lavaSeedRate);
		addPattern (lavaParticle, c, basaltParticle, c, setRate);  // lava sets into basalt
		addPattern (lavaParticle, c, lavaParticle, c, 1 - setRate);  // lava stays liquid
	    }
	}
	addPattern (lavaParticle, 0, 0, lavaParticle, lavaFlowRate);  // lava does random walk step
	addPattern (lavaParticle, 0, 0, lavaParticle, 1 - lavaFlowRate);  // lava stays put

	addPattern (basaltParticle, fecundityParticle, lavaParticle, 0, lavaSeedRate);  // this just for fun: perfume melts basalt
	addPattern (basaltParticle, fecundityParticle, lavaParticle, 0, 1 - lavaSeedRate);
    }

    // helper to add a pattern
    private void addPattern (int pc_old, int nc_old, int pc_new, int nc_new, double prob) {
	if (pc_old == 0)
	    throw new RuntimeException (new String ("Can't trigger a rule on empty space: " + pc_old + "(" + stateName(pc_old) + ") " + nc_old + "(" + stateName(nc_old) + ") " + pc_new + "(" + stateName(pc_new) + ") " + nc_new + "(" + stateName(nc_new) + ")"));
	((IntegerRandomVariable) pattern.get (makeCellPairIndex(pc_old,nc_old))).add (makeCellPairIndex(pc_new,nc_new), prob);
	// print the pattern to stderr
	System.err.println ("P(" + stateName(pc_old) + " " + stateName(nc_old) + " -> " + stateName(pc_new) + " " + stateName(nc_new) + ") = " + prob);
    }

    // init tools method
    private void initSprayTools() {
	sprayDiameter = 2;
	sprayPower = 15;

	sprayReserve = new int[cellTypes];
	sprayMax = new int[cellTypes];
	sprayRefillRate = new double[cellTypes];

	for (int c = 0; c < cellTypes; ++c) {
	    sprayReserve[c] = sprayMax[c] = 0;
	    sprayRefillRate[c] = 0.;
	}

	double baseRefillRate = 0.25 * (double) sprayPower;

	sprayRefillRate[cementParticle] = .35 * baseRefillRate;
	sprayMax[cementParticle] = 300;

	sprayRefillRate[acidParticle] = .75 * baseRefillRate;
	sprayMax[acidParticle] = 200;

	sprayRefillRate[fecundityParticle] = .5 * baseRefillRate;
	sprayMax[fecundityParticle] = 80;

	sprayRefillRate[mutatorParticle] = .03 * baseRefillRate;
	sprayMax[mutatorParticle] = 40;

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
	// randomize
	if (randomPressed) {
	    randomizeBoard();
	    redrawBoard();
	    refreshBuffer();
	    randomPressed = false;
	}

	Point mousePos = getMousePosition();
	if (mousePos != null) {
	    cursorPos.x = (int) (mousePos.x / pixelsPerCell);
	    cursorPos.y = (int) (mousePos.y / pixelsPerCell);

	    if (onBoard(cursorPos))
		setCursor(boardCursor);
	    else
		setCursor(normalCursor);
	}

	// do spray
	if (mouseDown) {
	    if (mousePos != null) {

		if (onBoard(cursorPos))
		    for (int i = 0; i < sprayPower; ++i) {
			if (sprayReserve[sprayParticle] > 0) {

			    Point sprayCell = new Point();

			    sprayCell.x = cursorPos.x + rnd.nextInt(sprayDiameter) - sprayDiameter / 2;
			    sprayCell.y = cursorPos.y + rnd.nextInt(sprayDiameter) - sprayDiameter / 2;

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
	// draw border around board
	bfGraphics.setColor(Color.white);
	bfGraphics.drawLine(0,0,boardSize,0);
	bfGraphics.drawLine(0,0,0,boardSize);
	bfGraphics.drawLine(0,boardSize,boardSize,boardSize);
	bfGraphics.drawLine(boardSize,0,boardSize,boardSize);

	// update buffer
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
	++boardUpdateCount;
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
	
	int newTargetState = getTargetState (newCellPairIndex);
	int newSourceState = getSourceState (newCellPairIndex);
	
	writeCell (targetCoords, newTargetState);
	return newSourceState;
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
	if (mixPressed) {
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
	    if (!mixPressed) {
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

	// spray levels
	int m = 0;
	for (int c = 0; c < cellTypes; ++c)
	    if (sprayMax[c] > m)
		m = sprayMax[c];

	plotReserve ('S', 0, cementParticle, 1);
	plotReserve ('D', 1, acidParticle, .8);
	plotReserve ('F', 2, fecundityParticle, .5);
	plotReserve ('G', 3, mutatorParticle, .2);

	// cheat mode
	plotOrHide ('B', 4, lavaParticle, .4, cheating());

	// name of the game
	flashOrHide ("Z00 GAS", 5, true, 0, 400, true, Color.white);

	// lava spray (only available in cheat mode)
	printOrHide (cheatString, 6, cheating(), Color.getHSBColor ((float) rnd.nextDouble(), 1, 1));

	// entropy: positive feedback
	double dScore = Math.pow(2,entropy);
	flashOrHide ("Nice balance!", 7, dScore > 3.6, 100, -1, true, Color.pink);
	flashOrHide ("AWESOME ZOO!", 8, dScore > 5, 100, -1, true, Color.yellow);

	// entropy: negative feedback
	flashOrHide ("Uh-oh", 9, dScore < 2.5 && dScore > 1.8, 10, 400, false, Color.red);
	flashOrHide ("Diversity low", 10, dScore < 2, 20, 500, false, Color.red);

	// number of species
	int liveSpecies = 0;
	for (int s = 1; s <= species; ++s)
	    if (cellCount[s] > 0)
		++liveSpecies;

	Color darkRed = Color.getHSBColor(0,(float).5,(float).5);
	flashOrHide ("EXTINCTION", 11, liveSpecies < 3, 25, 600, true, darkRed);
	flashOrHide ("ECO CRASH", 12, liveSpecies < 2, 30, -1, true, Color.white);

	flashOrHide (liveSpecies + " species!", 13, liveSpecies > 3, 100, -1, false, Color.orange);
	flashOrHide ("GR00VY", 14, liveSpecies > 5, 500, -1, false, Color.orange);

	flashOrHide ("V0ID space", 16, liveSpecies < 1, 0, 1000, false, Color.cyan);

	// networking
	flashOrHide ("Online", 18, boardServer != null, 0, -1, false, Color.blue);
	flashOrHide ("Connected", 19, remoteCell.size() > 0, 0, -1, false, Color.cyan);


    }

    private int toolYCenter (int row) { return (2 * row + 1) * toolHeight / 2; }

    private void flashOrHide (String text, int row, boolean show, int minTime, int maxTime, boolean onceOnly, Color color) {
	int flashPeriod = 10, flashes = 10;
	boolean reallyShow = false;
	boolean currentlyShown = timeFirstTrue[row] > 0;
	if (show) {
	    if (!currentlyShown)
		timeFirstTrue[row] = boardUpdateCount;
	    else {
		int timeSinceFirstTrue = boardUpdateCount - timeFirstTrue[row];
		int flashesSinceFirstTrue = (timeSinceFirstTrue - minTime) / flashPeriod;
		reallyShow = timeSinceFirstTrue >= minTime && (maxTime < 0 || timeSinceFirstTrue <= maxTime) && ((flashesSinceFirstTrue > 2*flashes) || (flashesSinceFirstTrue % 2 == 0));
	    }
	} else if (!onceOnly)
	    timeFirstTrue[row] = 0;

	if (reallyShow || currentlyShown)
	    printOrHide (text, row, reallyShow, color);
    }

    private void printOrHide (String text, int row, boolean show, Color color) {
	FontMetrics fm = bfGraphics.getFontMetrics();
	int xSize = fm.stringWidth(text), xPos = boardSize + toolBarWidth - xSize;
	int ch = fm.getHeight(), yPos = toolYCenter(row) + ch / 2;

	bfGraphics.setColor (Color.black);
	bfGraphics.fillRect (boardSize, yPos - ch + 1, toolBarWidth, ch + 2);

	if (show) {
	    bfGraphics.setColor (color);
	    bfGraphics.drawString (text, xPos, yPos);
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
		    mixPressed = true;
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
	mixPressed = false;
    }
}


