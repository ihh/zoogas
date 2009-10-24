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

    // hardwired stuff copied from TestEcology. Should eliminate references to these, or read them from a file
    int species = 18;  // number of species
    int trophicSymmetry = 3;  // initial number of species (can be increased with mutator gas)
    int wallDecayStates = 5;
    String patternSetFilename = "ECOLOGY.txt";

    // board
    Board board = null;

    // initial conditions
    String initImageFilename = "TheZoo.bmp";  // if non-null, initialization loads a seed image from this filename
    // String initImageFilename = null;
    double initialDensity = .1;  // initial density of species-containing cells (used only if initImageFilename == null)

    // view
    int pixelsPerCell = 4;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int popChartHeight = 100, popBarHeight = 4, entropyBarHeight = 20, statusBarHeight;  // size in pixels of various parts of the status bar (below the board)
    int toolKeyWidth = 16, toolReserveBarWidth = 100, toolHeight = 30, toolBarWidth;  // size in pixels of various parts of the tool bar (right of the board)

    // tools
    String toolboxFilename = "TOOLS.txt";
    ToolBox toolBox = null;

    // cheat c0d3z
    String cheatString = "boosh";
    int cheatStringPos = 0;
    boolean cheating() { return cheatStringPos == cheatString.length(); }

    // commentator code ("well done"-type messages)
    int boardUpdateCount = 0;
    int[] timeFirstTrue = new int[100];   // indexed by row: tracks the first time when various conditions are true, so that the messages flash at first

    // cellular automata state list
    private Vector<Particle> particleVec = new Vector<Particle>();  // internal to this class

    // constant helper vars
    Particle spaceParticle, cementParticle, acidParticle, fecundityParticle, mutatorParticle, lavaParticle, basaltParticle, tripwireParticle, guestParticle;
    Particle[] wallParticle, speciesParticle;
    int patternMatchesPerRefresh;

    // Swing
    Insets insets;
    BufferStrategy bufferStrategy;
    Graphics bfGraphics;
    Cursor boardCursor, normalCursor;
    // Uncomment to use "helicopter.png" as a mouse cursor over the board:
    //    String boardCursorFilename = "helicopter.png";
    String boardCursorFilename = null;
    Point boardCursorHotSpot = new Point(50,50);  // ignored unless boardCursorFilename != null

    // helper objects
    Point cursorPos;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown;  // true if mouse is currently down
    boolean randomPressed;  // true if 'randomize' button was pressed (randomize the model once only)
    boolean meanFieldPressed;  // true if 'meanField' button was pressed (model using mean field, with no spatial autocorrelation)

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
	board.initServer(remote);
    }

    // networked constructor (server)
    public ZooGas (int port) {
	this();
	board.initClient(port);
    }

    // default constructor
    public ZooGas() {

	// set helpers, etc.
	board = new Board(size);
	boardSize = board.getBoardSize(size,pixelsPerCell);

	patternMatchesPerRefresh = (int) (size * size);

	// load patternSet
	board.loadPatternSetFromFile(patternSetFilename);

	// init particles
	// we should not really need to do this, since we have already read the particle defs from a rule file.
	// however, for the moment, the game interface itself (tools, population charts, etc) is not read from a file, so we need some actual Particle objects.
	// another niggling little issue is that ParticlePattern doesn't contain actual particle names (only regexes)
	// so we currently have no way to map colors->names when initing from an image.
	String sep = Particle.visibleSeparatorChar, spc = Particle.visibleSpaceChar;
	spaceParticle = newParticle (spc, Color.black);  // empty space
	speciesParticle = new Particle[species];
	for (int s = 0; s < species; ++s)
	    speciesParticle[s] = newParticle ("critter" + sep + "s" + RuleMatch.int2string(s), Color.getHSBColor ((float) s / (float) (species+1), 1, 1));

	wallParticle = new Particle[wallDecayStates];
	for (int w = 1; w <= wallDecayStates; ++w) {
	    float gray = (float) w / (float) (wallDecayStates + 1);
	    wallParticle[w-1] = newParticle ("wall" + sep + RuleMatch.int2string(w), new Color (gray, gray, gray));  // walls (in various sequential states of decay)
	}
	cementParticle = newParticle ("cement", Color.white);  // cement (drifts; sets into wall)

	float gasHue = (float) species / (float) (species+1);
	acidParticle = newParticle ("acid", Color.darkGray);  // acid (destroys most things; dissolves basalt into lava)
	fecundityParticle = newParticle ("perfume", Color.getHSBColor (gasHue, (float) .5, (float) .5));  // fecundity gas (multiplies; makes animals breed)
	mutatorParticle = newParticle ("mutator", Color.getHSBColor (gasHue, (float) .5, (float) 1));  // mutator gas (converts animals into nearby species)
	lavaParticle = newParticle ("lava", Color.lightGray);  // lava (drifts; sets into basalt)
	basaltParticle = newParticle ("wall" + sep + "basalt", Color.orange);  // basalt
	tripwireParticle = newParticle (sep + "tripwire", new Color(1,1,1));  // tripwire (an invisible, static particle that animals will eat; use as a subtle test of whether animals have escaped)
	guestParticle = newParticle ("zoo_guest", new Color(254,254,254));  // guest (a visible, mobile particle that animals will eat; use as a test of whether animals have escaped)

	// init board
	boolean boardInitialized = false;
	if (initImageFilename != null) {

	    try {
		BufferedImage img = ImageIO.read(new File(initImageFilename));
		board.initFromImage(img,this);
		boardInitialized = true;

	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	if (!boardInitialized)  // fallback: randomize board
	    for (int x = 0; x < size; ++x)
		for (int y = 0; y < size; ++y)
		    if (board.rnd.nextDouble() < initialDensity) {
			int s = board.rnd.nextInt(trophicSymmetry) * (species/trophicSymmetry);
			Particle p = speciesParticle[s];
			board.cell[x][y].particle = p;
		    } else
			board.cell[x][y].particle = spaceParticle;

	// init cell counts
	board.incCounts();

	// init spray tools
	initSprayTools();

	// init view
	statusBarHeight = popChartHeight + popBarHeight * (species + 1) + entropyBarHeight;
	toolBarWidth = toolKeyWidth + toolReserveBarWidth;

	entropy = log2(trophicSymmetry);
	bestEntropy = minEntropyOverCycle = bestMinEntropyOverCycle = entropy;
	maxEntropy = log2(species);

	// init JFrame
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	setResizable(false);
	setVisible(true);

	// set size
	insets = getInsets();
	setSize(boardSize + toolBarWidth + insets.left + insets.right,boardSize + statusBarHeight + insets.top + insets.bottom);

	// init double buffering
	createBufferStrategy(2);
	bufferStrategy = getBufferStrategy();
	bfGraphics = bufferStrategy.getDrawGraphics();
	bfGraphics.translate (insets.left, insets.top);

	// create cursors
	boardCursor = new Cursor(Cursor.HAND_CURSOR);
	normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);

	if (boardCursorFilename != null) {
	    //Get the default toolkit  
	    Toolkit toolkit = Toolkit.getDefaultToolkit();  
  
	    //Load an image for the cursor  
	    Image image = toolkit.getImage(boardCursorFilename);
	    boardCursor = toolkit.createCustomCursor(image, boardCursorHotSpot, "ZooGasHelicopter");  
	}

	// register for mouse & keyboard events
	cursorPos = new Point();
	mouseDown = false;
	randomPressed = false;
	meanFieldPressed = false;

        addMouseListener(this);
        addKeyListener(this);
    }

    // builder method for cell types
    private Particle newParticle (String name, Color color) {
	Particle p = new Particle (name, color, board, board.patternSet);
	particleVec.add (p);
	return p;
    }

    // TODO: eliminate deprecated member particleVec and deprecated methods getParticleByNumber, particleTypes
    protected Particle getParticleByNumber (int n) {
	return (Particle) particleVec.get (n);
    }

    protected int particleTypes() {
	return particleVec.size();
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


    // main evolution loop
    private void evolveStuff() {
	if (meanFieldPressed)
	    randomizeBoard();
	board.update(patternMatchesPerRefresh,bfGraphics,pixelsPerCell);
	++boardUpdateCount;
    }

    // log2
    private static double log2(double x) { return Math.log(x) / Math.log(2); }

    // method to shuffle the board
    private void randomizeBoard() {
	// randomize board without changing total cell counts
	// uses a Fisher-Yates shuffle

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		board.cell[p.x][p.y].particle = spaceParticle;

	int i = 0;
	for (int c = 1; c < particleTypes(); ++c)
	    for (int k = 0; k < getParticleByNumber(c).count; ++k)
		{
		    board.cell[i % size][i / size].particle = getParticleByNumber(c);
		    ++i;
		}

	for (int k = 0; k < i; ++k)
	    {
		int kSwap = k + board.rnd.nextInt (size*size - k);
		Particle tmp = board.cell[kSwap % size][kSwap / size].particle;
		board.cell[kSwap % size][kSwap / size].particle = board.cell[k % size][k / size].particle;
		board.cell[k % size][k / size].particle = tmp;
	    }
    }



    // init tools method
    private void initSprayTools() {
	toolBox = ToolBox.fromFile(toolboxFilename,board);
	cheatStringPos = 0;
    }

    // getCursorPos() returns true if cursor is over board, and places cell coords in cursorPos
    private boolean getCursorPos() {
	Point mousePos = getMousePosition();
	if (mousePos != null) {
	    mousePos.translate(-insets.left,-insets.top);
	    board.getCellCoords(mousePos,cursorPos,pixelsPerCell);
	    return board.onBoard(cursorPos);
	}
	return false;
    }

    private void useTools() {
	// randomize
	if (randomPressed) {
	    randomizeBoard();
	    redrawBoard();
	    refreshBuffer();
	    randomPressed = false;
	}

	boolean cursorOnBoard = getCursorPos();
	setCursor(cursorOnBoard ? boardCursor : normalCursor);

	// do spray
	if (mouseDown) {
	    if (cursorOnBoard)
		toolBox.currentTool.spray(cursorPos,board,spaceParticle);
	} else
	    toolBox.refill ((entropy + 1) / (maxEntropy + 1));
    }


    // rendering methods
    private void drawCell (Point p) {
	board.drawCell(p,bfGraphics,pixelsPerCell);
    }

    private void drawEverything() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize+toolBarWidth,boardSize+statusBarHeight);

	board.drawEverything(bfGraphics,pixelsPerCell);

	refreshBuffer();
    }

    protected void redrawBoard() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize,boardSize);

	board.drawEverything(bfGraphics,pixelsPerCell);
    }

    protected void refreshBuffer() {
	// draw border around board
	bfGraphics.setColor(Color.white);
	bfGraphics.drawLine(0,0,boardSize,0);
	bfGraphics.drawLine(0,0,0,boardSize);
	bfGraphics.drawLine(0,boardSize,boardSize,boardSize);
	bfGraphics.drawLine(boardSize,0,boardSize,boardSize);

	// update buffer
	bufferStrategy.show();
	Toolkit.getDefaultToolkit().sync();	
    }

    // status & tool bars
    protected void plotCounts() {
	int h = 0;
	int cellsOnBoard = size * size;
	entropy = 0;

	int maxCount = 0, totalCount = 0;
	for (int c = 0; c < species; ++c) {
	    int cc = speciesParticle[c].count;
	    if (cc > maxCount)
		maxCount = cc;
	    totalCount += cc;
	}

	if (maxCount > 0)
	    for (int c = 0; c < species; ++c) {
		int cc = speciesParticle[c].count;
		double p = ((double) cc) / (double) totalCount;
		if (p > 0 && p <= 1)
		    entropy -= p * log2(p);

		bfGraphics.setColor(speciesParticle[c].color);
		int b = popChartHeight * cc / totalCount;
		int w = boardSize * cc / maxCount;
		if (b < 1 && cc > 0)
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
	    throw new RuntimeException ("Entropy outside permitted range");
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
	toolBox.plotReserves(bfGraphics,new Point(boardSize,0),toolHeight,toolReserveBarWidth,toolKeyWidth);

	// name of the game
	flashOrHide ("Z00 GAS", 5, true, 0, 400, true, Color.white);

	// cheat mode
	printOrHide (cheatString, 6, cheating(), Color.getHSBColor ((float) board.rnd.nextDouble(), 1, 1));

	// entropy: positive feedback
	double dScore = Math.pow(2,entropy);
	flashOrHide ("Nice balance!", 7, dScore > 3.6, 100, -1, true, Color.pink);
	flashOrHide ("AWESOME ZOO!", 8, dScore > 5, 100, -1, true, Color.yellow);

	// entropy: negative feedback
	flashOrHide ("Uh-oh, imbalance", 9, dScore < 2.5 && dScore > 1.8, 10, 400, false, Color.red);
	flashOrHide ("Diversity low", 10, dScore < 2, 20, 500, false, Color.red);

	// number of species
	int liveSpecies = 0;
	for (int s = 0; s < species; ++s)
	    if (speciesParticle[s].count > 0)
		++liveSpecies;

	Color darkRed = Color.getHSBColor(0,(float).5,(float).5);
	flashOrHide ("EXTINCTION", 11, liveSpecies < 3, 25, 600, true, darkRed);
	flashOrHide ("ECO CRASH", 12, liveSpecies < 2, 30, -1, true, Color.white);

	flashOrHide (liveSpecies + " species!", 13, liveSpecies > 3, 100, -1, false, Color.orange);
	flashOrHide ("GR00VY", 14, liveSpecies > 5, 500, -1, false, Color.orange);

	flashOrHide ("V0ID space", 16, liveSpecies < 1, 0, 1000, false, Color.cyan);

	flashOrHide ("Guests eaten", 15, guestParticle.count==0, 0, -1, false, Color.red);
	flashOrHide ("ZOO BREAK!", 17, tripwireParticle.count==0, 0, -1, false, Color.red);


	// networking
	flashOrHide ("Online", 18, board.boardServer != null, 0, -1, false, Color.blue);
	flashOrHide ("Connected", 19, board.remoteCell.size() > 0, 0, -1, false, Color.cyan);

	// identify particle that cursor is currently over
	boolean cursorOnBoard = getCursorPos();
	Particle cursorParticle = cursorOnBoard ? board.readCell(cursorPos) : null;
	printOrHide (cursorOnBoard ? cursorParticle.visibleName() : "", 20, cursorOnBoard, cursorOnBoard ? cursorParticle.color : Color.white);

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


    // UI methods
    // mouse events
    public void mousePressed(MouseEvent e) {
	mouseDown = true;

	Point mousePos = getMousePosition();
	mousePos.x -= insets.left;
	mousePos.y -= insets.top;
	if (mousePos.x >= boardSize && mousePos.y < toolHeight * toolBox.tool.size()) {
	    int row = mousePos.y / toolHeight;
	    toolBox.currentTool = toolBox.tool.elementAt(row);
	}
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
	boolean foundKey = toolBox.hotKeyPressed(c);
	if (foundKey) {
	    mouseDown = true;
	} else if (cheating()) {
	    // cheats
	    if (c == 'k' || c == 'K') {
		randomPressed = true;
	    } else if (c == 'l' || c == 'L') {
		meanFieldPressed = true;
	    } else if (c == '0') {
		initSprayTools();
	    } else if (c == '8') {
		toolBox.currentTool.reserve = toolBox.currentTool.maxReserve = 123456789;
	    } else if (c == '7') {
		toolBox.currentTool.sprayDiameter *= 1.5;
		toolBox.currentTool.sprayPower *= 2.25;
	    } else if (c == '6') {
		toolBox.currentTool.sprayPower *= 1.5;
	    }
	}
    }

    public void keyReleased(KeyEvent e) {
	char c = e.getKeyChar();
	if (cheatStringPos < cheatString.length())
	    {
		if (c == cheatString.charAt (cheatStringPos))
		    ++cheatStringPos;
		if (cheating())
		    toolBox.revealHiddenTools();
	    }
	else {  // cheating() == true
	    if (c == 'l' || c == 'L')
		randomPressed = true;  // force one last randomization and redraw
	}
	mouseDown = false;
	meanFieldPressed = false;
    }
}
