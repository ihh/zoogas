import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.imageio.ImageIO;

public class ZooGas extends JFrame implements BoardRenderer, MouseListener, KeyListener {

    // size of board in cells
    int size = 128;

    // board
    Board board = null;

    // pattern set
    String patternSetFilename = "ECOLOGY.txt";

    // Initial conditions; or, How To Build a Zoo.
    // if initImageFilename is not null, then the image will be read from a file,
    // and converted to the Particles in initParticleFilename.
    // if initImageFilename is null, then it is assumed that a miniprogram to build a zoo
    // is contained in the initially-loaded pattern set.
    // in this case, the entire zoo will be initialized to particle "spaceParticleName",
    // and one initialization particle "INIT/radius" (where "radius" is half the size of the zoo) will be placed at coords (radius,radius).
    String initImageFilename = null;
    //    String initImageFilename = "TheZoo.bmp";  // if non-null, initialization loads a seed image from this filename
    String initParticleFilename = "TheZooParticles.txt";
    String initParticlePrefix = "/INIT.";

    // view
    int pixelsPerCell = 4;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int belowBoardHeight = 0;  // size in pixels of whatever appears below the board -- currently unused but left as a placeholder
    int toolBarWidth = 100, toolLabelWidth = 200, toolHeight = 30;  // size in pixels of various parts of the tool bar (right of the board)
    int textBarWidth = 400, textHeight = 30;
    int totalWidth, totalHeight;
    int verbHistoryLength = 10, verbHistoryPos = 0, verbHistoryRefreshPeriod = 100, verbHistoryRefreshCounter = 0, verbsSinceLastRefresh = 0;
    String[] verbHistory = new String[verbHistoryLength];
    Particle[] nounHistory = new Particle[verbHistoryLength];
    int updatesRow = 0, titleRow = 4, networkRow = 5, nounRow = 8, verbHistoryRow = 12;

    // tools and cheats
    String toolboxFilename = "TOOLS.txt";
    ToolBox toolBox = null;
    char cheatKey = '/';  // allows player to see the hidden parts of state names, i.e. the part behind the '/'
    char stopKey = '.';  // stops the action on this board (does not block incoming network events)
    char slowKey = ',';  // slows the action on this board (does not block incoming network events)
    int slowFactor = 40;  // rate at which the board slows down when slowKey is pressed (actually the rate between buffer refreshes)

    // cellular automata state list
    private Vector<Particle> particleVec = new Vector<Particle>();  // internal to this class

    // commentator code ("well done"-type messages)
    int boardUpdateCount = 0;
    int[] timeFirstTrue = new int[100];   // indexed by row: tracks the first time when various conditions are true, so that the messages flash at first

    // constant helper vars
    static String spaceParticleName = "_";
    Particle spaceParticle;
    int patternMatchesPerRefresh;

    // Swing
    Graphics bfGraphics;
    BufferedImage bfImage;
    Cursor boardCursor, normalCursor;
    // Uncomment to use "helicopter.png" as a mouse cursor over the board:
    //    String boardCursorFilename = "helicopter.png";
    String boardCursorFilename = null;
    Point boardCursorHotSpot = new Point(50,50);  // ignored unless boardCursorFilename != null

    // helper objects
    Point cursorPos = null;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown = false;  // true if mouse is currently down
    boolean cheatPressed = false;  // true if cheatKey is pressed (allows player to see hidden parts of state names)
    boolean stopPressed = false;  // true if stopKey is pressed (stops updates on this board)
    boolean slowPressed = false;  // true if slowKey is pressed (slows updates on this board)
    double updatesPerSecond = 0;

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
	board.initClient(port,this);
    }

    // default constructor
    public ZooGas() {

	// create board
	board = new Board(size);

	// set helpers, etc.
	boardSize = board.getBoardSize(size,pixelsPerCell);
	totalWidth = boardSize + toolBarWidth + toolLabelWidth + textBarWidth;
	totalHeight = boardSize + belowBoardHeight;

	patternMatchesPerRefresh = (int) (size * size);

	// load patternSet
	board.loadPatternSetFromFile(patternSetFilename);
	spaceParticle = board.getOrCreateParticle(spaceParticleName);

	// init board
	if (initImageFilename != null) {

	    try {
		BufferedImage img = ImageIO.read(new File(initImageFilename));
		ParticleSet imgParticle = ParticleSet.fromFile(initParticleFilename);
		board.initFromImage(img,imgParticle);

	    } catch (IOException e) {
		e.printStackTrace();
	    }
	} else {
	    board.fill(spaceParticle);
	    String initParticleName = initParticlePrefix + RuleMatch.int2string(size/2);
	    Particle initParticle = board.getOrCreateParticle(initParticleName);
	    if (initParticle == null)
		throw new RuntimeException("Initialization particle " + initParticleName + " not found");
	    Point initPoint = new Point(size/2,size/2);
	    board.writeCell(initPoint,initParticle);
	}

	// init spray tools
	initSprayTools();

	// init JFrame
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	setResizable(false);
	bfImage = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_3BYTE_BGR);
	bfGraphics = bfImage.getGraphics();
	setContentPane(new JPanel() {
				protected void paintComponent(Graphics g)
				{
					super.paintChildren(g);
					g.drawImage(bfImage, 0, 0, null);
				}});

	// set size
	getContentPane().setPreferredSize(new Dimension(totalWidth,totalHeight));
	pack();
	setVisible(true);

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

        addMouseListener(this);
        addKeyListener(this);
    }

    // main game loop
    private void gameLoop() {
	// Game logic goes here.

	drawEverything();

	long lastTimeCheck = System.currentTimeMillis();
	int timeCheckPeriod = 10;
	while (true)
	    {
		if (!stopPressed)
		    evolveStuff();
		useTools();

		if (boardUpdateCount % timeCheckPeriod == 0) {
		    long currentTimeCheck = System.currentTimeMillis();
		    updatesPerSecond = ((double) 1000 * timeCheckPeriod) / ((double) (currentTimeCheck - lastTimeCheck));
		    if (slowPressed)
			updatesPerSecond /= slowFactor;  // this is a bit hacky
		    lastTimeCheck = currentTimeCheck;
		}

		plotCounts();
		refreshBuffer();
	    }
    }


    // main evolution loop
    private void evolveStuff() {
	board.update(slowPressed ? (patternMatchesPerRefresh/slowFactor) : patternMatchesPerRefresh,this);
	++boardUpdateCount;
    }

    // init tools method
    private void initSprayTools() {
	toolBox = ToolBox.fromFile(toolboxFilename,board);
	toolBox.toolHeight = toolHeight;
	toolBox.toolReserveBarWidth = toolBarWidth;
	toolBox.toolTextWidth = toolLabelWidth;
    }

    // getCursorPos() returns true if cursor is over board, and places cell coords in cursorPos
    private boolean getCursorPos() {
	Point mousePos = getContentPane().getMousePosition();
	if (mousePos != null) {
	    board.getCellCoords(mousePos,cursorPos,pixelsPerCell);
	    return board.onBoard(cursorPos);
	}
	return false;
    }

    private void useTools() {
	boolean cursorOnBoard = getCursorPos();
	setCursor(cursorOnBoard ? boardCursor : normalCursor);

	// do spray
	if (mouseDown) {
	    if (cursorOnBoard)
		toolBox.currentTool.spray(cursorPos,board,this,spaceParticle);
	} else
	    toolBox.refill(1);
    }


    // BoardRenderer methods
    public void drawCell (Point p) {
	bfGraphics.setColor(board.readCell(p).color);
	Point q = new Point();
	board.getGraphicsCoords(p,q,pixelsPerCell);
	bfGraphics.fillRect(q.x,q.y,pixelsPerCell,pixelsPerCell);
    }

    public void showVerb (Point p,Point n,Particle oldSource,Particle oldTarget,ParticlePair newPair) {
	if (verbsSinceLastRefresh == 0)
	    if (cheatPressed || newPair.visibleVerb().length() > 0) {
		// check for duplicates
		boolean foundDuplicate = false;
		for (int v = 0; v < verbHistoryLength; ++v)
		    if (newPair.verb.equals(verbHistory[v]) && oldSource.color.equals(nounHistory[v].color)) {
			foundDuplicate = true;
			break;
		    }
		if (!foundDuplicate) {
		    verbHistoryPos = (verbHistoryPos + 1) % verbHistoryLength;
		    verbHistory[verbHistoryPos] = newPair.verb;
		    nounHistory[verbHistoryPos] = oldSource;
		    ++verbsSinceLastRefresh;
		}
	    }
    }

    // other rendering methods
    private void drawEverything() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,totalWidth,totalHeight);

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell(p);

	refreshBuffer();
    }

    protected void redrawBoard() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize,boardSize);

	drawEverything();
    }

    protected void refreshBuffer() {
	// draw border around board
	bfGraphics.setColor(Color.white);
	bfGraphics.drawRect(0,0,boardSize-1,boardSize-1);

	// update buffer
	repaint();
	Toolkit.getDefaultToolkit().sync();	
    }

    // status & tool bars
    protected void plotCounts() {

	// spray levels
	toolBox.plotReserves(bfGraphics,new Point(boardSize,0));

	// name of the game
	flashOrHide ("Z00 GAS", titleRow, true, 0, 400, true, Color.white);

	// networking
	flashOrHide ("Online", networkRow, board.online(), 0, -1, false, Color.blue);
	flashOrHide ("Connected", networkRow+1, board.connected(), 0, -1, false, Color.cyan);

	// identify particle that cursor is currently over
	boolean cursorOnBoard = getCursorPos();
	Particle cursorParticle = cursorOnBoard ? board.readCell(cursorPos) : null;
	boolean isSpace = cursorParticle == spaceParticle;
	printOrHide (cursorParticle == null
		     ? "Mouseover board to identify pixels"
		     : "Under cursor:", nounRow, true, Color.white);
	printOrHide (cursorOnBoard ? (cheatPressed ? cursorParticle.name : cursorParticle.visibleName()) : "", nounRow+1, cursorOnBoard, cursorOnBoard ? cursorParticle.color : Color.white);

	// update rate and other stats
	StringBuilder sb = new StringBuilder();
	Formatter formatter = new Formatter(sb, Locale.US);
	Runtime runtime = Runtime.getRuntime();
	printOrHide (board.debugDumpStats(), updatesRow, true, new Color(48,48,0));
	printOrHide ("Heap: current " + kmg(runtime.totalMemory()) + ", max " + kmg(runtime.maxMemory()) + ", free " + kmg(runtime.freeMemory()), updatesRow+1, true, new Color(48,48,0));
	printOrHide (formatter.format("Updates/sec: %.2f",updatesPerSecond).toString(), updatesRow+2, true, new Color(64,64,0));

	// recent verbs
	printOrHide ("Recent events:", verbHistoryRow, true, Color.white);
	for (int vpos = 0; vpos < verbHistoryLength; ++vpos) {
	    int v = (verbHistoryPos + verbHistoryLength - vpos) % verbHistoryLength;
	    String verbText = null;
	    Color verbColor = null;
	    if (verbHistory[v] != null) {
		String noun = cheatPressed ? nounHistory[v].name : nounHistory[v].visibleName();
		String nounInBrackets = noun.length() > 0 ? (" (" + noun + ")") : "";
		// uncomment to always print noun:
		//		verbText = (cheatPressed ? verbHistory[v] : Particle.visibleText(verbHistory[v])) + nounInBrackets;
		verbText = cheatPressed ? (verbHistory[v] + nounInBrackets) : Particle.visibleText(verbHistory[v]);
		verbColor = nounHistory[v].color;
	    }
	    printOrHide (verbText, verbHistoryRow + vpos + 1, true, verbColor);
	    if (++verbHistoryRefreshCounter >= verbHistoryRefreshPeriod)
		verbsSinceLastRefresh = verbHistoryRefreshCounter = 0;
	}
    }

    static String kmg(long bytes) {
	return bytes < 1024 ? (bytes + "B") : (bytes < 1048576 ? (bytes/1024 + "K") : (bytes < 1073741824 ? (bytes/1048576 + "M") : bytes/1073741824 + "G"));
    }

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
	int ch = fm.getHeight(), bleed = 6, yPos = row * (ch + bleed);

	bfGraphics.setColor (Color.black);
	bfGraphics.fillRect (boardSize + toolBarWidth + toolLabelWidth, yPos, textBarWidth, ch + bleed);

	if (show && text != null) {
	    int xSize = fm.stringWidth(text), xPos = boardSize + toolBarWidth + toolLabelWidth + textBarWidth - xSize;
	    bfGraphics.setColor (color);
	    bfGraphics.drawString (text, xPos, yPos + ch);
	}
    }


    // UI methods
    // mouse events
    public void mousePressed(MouseEvent e) {
	mouseDown = true;

	Point mousePos = getContentPane().getMousePosition();
	if (mousePos.x >= boardSize && mousePos.x < boardSize + toolBarWidth + toolLabelWidth)
	    toolBox.clickSelect(mousePos.y);
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
	if (foundKey)
	    mouseDown = true;
	else if (c == cheatKey)
	    cheatPressed = true;
	else if (c == stopKey)
	    stopPressed = true;
	else if (c == slowKey)
	    slowPressed = true;
    }

    public void keyReleased(KeyEvent e) {
	mouseDown = false;
	slowPressed = stopPressed = cheatPressed = false;
    }
}
