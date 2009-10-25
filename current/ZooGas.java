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

    // size of board in cells
    int size = 128;

    // board
    Board board = null;

    // pattern set
    String patternSetFilename = "ECOLOGY.txt";

    // initial conditions
    String initImageFilename = "TheZoo.bmp";  // if non-null, initialization loads a seed image from this filename
    String initParticleFilename = "TheZooParticles.txt";
    // String initImageFilename = null;

    // view
    int pixelsPerCell = 4;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int statusBarHeight;  // size in pixels of various parts of the status bar (below the board)
    int toolKeyWidth = 16, toolReserveBarWidth = 100, toolHeight = 30, toolBarWidth;  // size in pixels of various parts of the tool bar (right of the board)

    // tools
    String toolboxFilename = "TOOLS.txt";
    ToolBox toolBox = null;

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

	// create board
	board = new Board(size);

	// set helpers, etc.
	boardSize = board.getBoardSize(size,pixelsPerCell);
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
	}

	// init cell counts
	board.incCounts();

	// init spray tools
	initSprayTools();

	// init view
	statusBarHeight = 0;
	toolBarWidth = toolKeyWidth + toolReserveBarWidth;

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

        addMouseListener(this);
        addKeyListener(this);
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
	board.update(patternMatchesPerRefresh,bfGraphics,pixelsPerCell);
	++boardUpdateCount;
    }

    // init tools method
    private void initSprayTools() {
	toolBox = ToolBox.fromFile(toolboxFilename,board);
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
	boolean cursorOnBoard = getCursorPos();
	setCursor(cursorOnBoard ? boardCursor : normalCursor);

	// do spray
	if (mouseDown) {
	    if (cursorOnBoard)
		toolBox.currentTool.spray(cursorPos,board,spaceParticle);
	} else
	    toolBox.refill(1);
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

	// spray levels
	toolBox.plotReserves(bfGraphics,new Point(boardSize,0),toolHeight,toolReserveBarWidth,toolKeyWidth);

	// name of the game
	flashOrHide ("Z00 GAS", 8, true, 0, 400, true, Color.white);

	// networking
	flashOrHide ("Online", 10, board.boardServer != null, 0, -1, false, Color.blue);
	flashOrHide ("Connected", 11, board.remoteCell.size() > 0, 0, -1, false, Color.cyan);

	// identify particle that cursor is currently over
	boolean cursorOnBoard = getCursorPos();
	Particle cursorParticle = cursorOnBoard ? board.readCell(cursorPos) : null;
	printOrHide ("Current particle:", 13, cursorOnBoard, Color.white);
	printOrHide (cursorOnBoard ? cursorParticle.visibleName() : "", 14, cursorOnBoard, cursorOnBoard ? cursorParticle.color : Color.white);

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
	if (foundKey)
	    mouseDown = true;
    }

    public void keyReleased(KeyEvent e) {
	mouseDown = false;
    }
}
