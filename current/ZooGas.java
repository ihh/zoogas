import java.lang.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
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

    // tools and cheats
    String toolboxFilename = "TOOLS.txt";
    ToolBox toolBox = null;
    final char cheatKey = '/';  // allows player to see the hidden parts of state names, i.e. the part behind the '/'
    final char stopKey = '.';  // stops the action on this board (does not block incoming network events)
    final char slowKey = ',';  // allows player to see bonds

    // cellular automata state list
    private Vector<Particle> particleVec = new Vector<Particle>();  // internal to this class

    // commentator code ("well done"-type messages)
    long boardUpdateCount = 0;
    long[] timeFirstTrue = new long[100];   // indexed by row: tracks the first time when various conditions are true, so that the messages flash at first

    // constant helper vars
    static String spaceParticleName = "_";
    Particle spaceParticle;
    double patternMatchesPerRefresh = 1;

    // Swing
    Graphics2D bfGraphics;
    BufferedImage bfImage;
    Cursor boardCursor, normalCursor;
    // Uncomment to use "helicopter.png" as a mouse cursor over the board:
    //    String boardCursorFilename = "helicopter.png";
    String boardCursorFilename = null;
    java.awt.Point boardCursorHotSpot = new java.awt.Point(50,50);  // ignored unless boardCursorFilename != null

    // view
    final int pixelsPerCell = 4;  // width & height of each cell in pixels
    JPanel boardPanel;
    int boardSize;  // width & height of board in pixels
    int belowBoardHeight = 0;  // size in pixels of whatever appears below the board -- currently unused but left as a placeholder
    JPanel toolBoxPanel;
    JPanel statusPanel;
    int toolBarWidth = 100, toolLabelWidth = 200, toolHeight = 30;  // size in pixels of various parts of the tool bar (right of the board)
    int textBarWidth = 400, textHeight = 30;
    int verbHistoryLength = 10, verbHistoryPos = 0, verbHistoryRefreshPeriod = 20, verbHistoryRefreshCounter = 0, verbsSinceLastRefresh = 0;
    String[] verbHistory = new String[verbHistoryLength];
    Particle[] nounHistory = new Particle[verbHistoryLength];
    Vector<String> hints = new Vector<String>();
    int currentHint = 0;
    double hintBrightness = 0;
    int updatesRow = 0, titleRow = 4, networkRow = 5, hintRow = 7, nounRow = 8, verbHistoryRow = 12;

    // helper objects
    Point cursorPos = null;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown = false;  // true if mouse is currently down
    boolean cheatPressed = false;  // true if cheatKey is pressed (allows player to see hidden parts of state names)
    boolean stopPressed = false;  // true if stopKey is pressed (stops updates on this board)
    boolean slowPressed = false;  // true if slowKey is pressed (slows updates on this board)
    double updatesPerSecond = 0;
    String lastDumpStats = ""; // hacky way to avoid concurrency issues

    // main()
    public static void main(String[] args) {
	// create ZooGas object
	ZooGas gas = new ZooGas();

	// Process options and args before initializing ZooGas
	int port = 0;
	String socketAddress = null;

	for(int i = 0; i < args.length; ++i) {
	    if("-s".equals(args[i]) || "--server".equals(args[i])) {
		if(port == 0)
		    port = 4444;
	    }
	    else if("-c".equals(args[i]) || "--client".equals(args[i])) {
		if(i+1 >= args.length) {
		    System.err.println("Error: not enough parameters given");
		    System.err.println("-c/--client usage: [-c|--client] <remote address>[:<remote port>]");
		    System.exit(0);
		    return;
		}
		socketAddress = args[++i];
	    }
	    else if("-p".equals(args[i]) || "--port".equals(args[i])) {
		if(i+1 >= args.length) {
		    System.err.println("Error: not enough parameters given");
		    System.err.println("-p/--port usage: [-p|--port] <port>");
		    System.exit(0);
		    return;
		}
		port = (new Integer(args[++i]));
	    }
	    else if("-t".equals(args[i]) || "--tools".equals(args[i])) {
		if(i+1 >= args.length) {
		    System.err.println("Error: no tools file specified");
		    System.err.println("-t/--tools usage: [-t|--tools] <tools file>");
		    System.exit(0);
		    return;
		}
		gas.toolboxFilename = args[++i];
	    }
	    else if("-r".equals(args[i]) || "--rules".equals(args[i])) {
		if(i+1 >= args.length) {
		    System.err.println("Error: no rules file specified");
		    System.err.println("-r/--rules usage: [-r|--rules] <rules file>");
		    System.exit(0);
		    return;
		}
		gas.patternSetFilename = args[++i];
	    }
	    else if("-?".equals(args[i]) || "-h".equals(args[i]) || "--help".equals(args[i])) {
		System.err.println("Usage: <progname> [<option> [<args>]]");
		System.err.println("Valid options:");
		System.err.println("\t[-c|--client] <port> <address>[:<remote port>]  - Start ZooGas in client mode");
		System.err.println("\t[-s|--server]  - Start ZooGas in server mode");
	        System.err.println("\t[-p|--p <port>]  - Use <port> as the server port");
		System.err.println("\t[-t|--tools <file>]  - Load tools from specified file (default \"TOOLS.txt\")");
		System.err.println("\t[-r|--rules <file>]  - Load rules from specified file (default \"ECOLOGY.txt\")");
		System.err.println("\t[-?|-h|--help]  - Displays this very useful help message");
		System.exit(0);
		return;
	    }
	    else {
		// Unknown option
		System.err.println("Error: Unknown option: " +  args[i]);
		System.exit(0);
		return;
	    }
	}

	// initialize after options have been considered
	if (socketAddress != null) // start as client (and server)
	{
	    if(port == 0)
	        port = 4444;

	    gas.board.initClient(port, gas); // init the server stuff first, then connect
	    
	    String[] address = socketAddress.split(":");
	    if(address.length > 1) {
	        gas.board.initServer(new InetSocketAddress(address[0], new Integer(address[1])));
	    }
	    else {
	        gas.board.initServer(new InetSocketAddress(address[0], 4444));
	    }
	}
	else if(port != 0) // start as server
	{
	    gas.board.initClient(port, gas);
	}

	gas.start();
    }

    public ZooGas() {
	// create board (needed from some options), then wait for start to be called
	board = new Board(size);
    }

    public void start()
    {
	// set helpers, etc.
	boardSize = board.getBoardSize(size,pixelsPerCell);

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

	// init hints
	String specialKeys = "Special keys: "+cheatKey+" (reveal state) "+slowKey+" (reveal bonds) "+stopKey+" (freeze)";
	hints.add ("Hi, welcome to Zoo Gas!");
	hints.add ("I am the Deputy Head Zookeeper, Celia O'Tuamata.");
	hints.add ("I'll be walking you through your first day.");
	hints.add ("This job is pretty open-ended. Just make a zoo.");
	hints.add ("You have a bunch of tools to do this, in your Toolbox.");
	hints.add ("The Toolbox is the list to the right of the board...");
	hints.add ("... i.e. just to the left of this message.");
	hints.add ("Select a tool by clicking, or press its hot-key.");
	if (toolBox.tool.size() > 0)
	    hints.add ("For example, press \"" + toolBox.tool.get(0).hotKey + "\" to select " + toolBox.tool.get(0).particleName + ".");
	hints.add ("Click on the board to use the currently selected tool...");
	hints.add ("...or hold down the tool hotkey with the mouse over the board.");
	if (toolBox.tool.size() > 0)
	    hints.add ("Like, mouseover the board & hold \"" + toolBox.tool.get(0).hotKey + "\" to spray " + toolBox.tool.get(0).particleName + " pixels.");
	hints.add ("Next to each tool there is a bar...");
	hints.add ("...this shows how many pixels you have in reserve.");
	hints.add ("The bars recharge - gradually! We're not made of money.");
	hints.add ("The board itself is on the far left, within the white square.");
	hints.add ("Actually I'm assuming you already figured that out.");
	hints.add ("This area to the right contains feedback messages...");
	hints.add ("...such as these helpful hints (click to hurry'em along, btw).");
	hints.add ("If you mouseover a pixel on the board, a message appears...");
	hints.add ("...telling you the name of that pixel.");
	hints.add ("At bottom right, you can also see a list of recent events.");
	hints.add ("Now you can experiment with the pixels a bit...");
	hints.add ("...they interact in a lot of different ways.");
	hints.add ("Meanwhile I'm gonna tell you some stuff I probably shouldn't.");
	hints.add ("It's not cheating, exactly, but it's sort of bending the rules.");
	hints.add ("Some of these hacks kinda mess reality up a bit, y'know?");
	hints.add ("You might find some cracks in the Matrix.");
	hints.add ("Anyways... here are the special keys. Don't say I didn't warn ya.");
	hints.add (specialKeys);
	hints.add ("The \""+cheatKey+"\" key reveals the hidden state of a pixel...");
	hints.add ("...that is, when you mouseover that pixel.");
	hints.add ("It also reveals the pixel's outgoing(>) and incoming(<) bonds,");
	hints.add ("along with the number of pixels of this type in existence.");
	hints.add (specialKeys);
	hints.add ("The \""+stopKey+"\" key stops all action on the board.");
	hints.add ("Try it now. Add pixels, press \""+stopKey+"\" and Feel the Power!");
	hints.add (specialKeys);
	hints.add ("The \""+slowKey+"\" key draws bonds on the board.");
	hints.add ("This also slows things down a bit.");
	hints.add ("You won't see anything unless you have some bonded pixels.");
	hints.add ("I'll leave you to find out what these are.");
	hints.add (specialKeys);
	hints.add ("OK, that's pretty much all I got for ya...");
	hints.add ("...I'm going to loop now, because I'm an NPC and we do that.");
	hints.add ("Plus, I'm kinda forgetful: every five minutes is like a new day.");
	hints.add ("I think it might be a side effect of the mutator gas.");
	hints.add ("I've kind of lost my train of thought.... what was I saying...");
	hints.add ("oh yeah...");

	// init JFrame
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	setResizable(false);
	bfImage = new BufferedImage(boardSize, boardSize, BufferedImage.TYPE_3BYTE_BGR);
	bfGraphics = bfImage.createGraphics();
	drawBorder(bfGraphics);

	boardPanel = new JPanel() {
	    public void paintComponent(Graphics g)
	    {
	        //super.paintComponent(g);
	        g.drawImage(bfImage, 0, 0, null);
		drawBorder(g);
	    }
	};
	toolBoxPanel = new JPanel(){
	    public void paintComponent(Graphics g)
	    {
	        super.paintComponent(g);
	        drawToolbox(g);
	    }
	};
	statusPanel = new JPanel(){
	    public void paintComponent(Graphics g)
	    {
		super.paintComponent(g);
		drawStatus(g);
	    }
	};

	boardPanel.setBackground(Color.BLACK);
	toolBoxPanel.setBackground(Color.BLACK);
	statusPanel.setBackground(Color.BLACK);

	// set size
	boardPanel.setPreferredSize(new Dimension(boardSize, boardSize));
	toolBoxPanel.setPreferredSize(new Dimension(toolBarWidth + toolLabelWidth, boardSize));
	statusPanel.setPreferredSize(new Dimension(textBarWidth, boardSize));

	// add to content pane using layout
	getContentPane().setLayout(new GridBagLayout());
	GridBagConstraints c = new GridBagConstraints();
	c.gridx = 0;
	getContentPane().add(boardPanel, c);
	++c.gridx;
	getContentPane().add(toolBoxPanel, c);
	++c.gridx;
	getContentPane().add(statusPanel, c);

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

        boardPanel.addMouseListener(this);
        addKeyListener(this);

        gameLoop();
    }

    // main game loop
    private void gameLoop() {
	// Game logic goes here
	drawEverything(false);

	long lastTimeCheck = System.currentTimeMillis();
	long timeCheckPeriod = 10;
	while (true)
	{
	    Runtime runtime = Runtime.getRuntime();
	    double heapFraction = ((double) (runtime.totalMemory() - runtime.freeMemory())) / (double) runtime.maxMemory();
	    if (heapFraction > .5)
		board.flushCaches();

	    if (!stopPressed)
		evolveStuff();
	    useTools();

	    if (boardUpdateCount % timeCheckPeriod == 0) {
		lastDumpStats = board.debugDumpStats();
		long currentTimeCheck = System.currentTimeMillis();
		updatesPerSecond = ((double) 1000 * timeCheckPeriod) / ((double) (currentTimeCheck - lastTimeCheck));
		lastTimeCheck = currentTimeCheck;
	    }

	    drawEverything(slowPressed);
	}
    }


    // main evolution loop
    private void evolveStuff() {
	board.update(patternMatchesPerRefresh,this);
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
	Point mousePos = new Point(getContentPane().getMousePosition());
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

    // bond-rendering method
    Random rnd = new Random();
    public void drawBond (Point p, Point q) {
	bfGraphics.setColor(new Color (rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()));
	Point pg = new Point();
	Point qg = new Point();
	board.getGraphicsCoords(p,pg,pixelsPerCell);
	board.getGraphicsCoords(q,qg,pixelsPerCell);
	int k = pixelsPerCell>>1;
	bfGraphics.drawLine(pg.x+k,pg.y+k,qg.x+k,qg.y+k);
    }

    // BoardRenderer methods
    public void drawCell (Point p) {
	bfGraphics.setColor(board.readCell(p).color);
	Point q = new Point();
	board.getGraphicsCoords(p,q,pixelsPerCell);
	bfGraphics.fillRect(q.x,q.y,pixelsPerCell,pixelsPerCell);
    }

    public void showVerb (Point p,Point n,Particle oldSource,Particle oldTarget,UpdateEvent newPair) {
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
    private void drawEverything(boolean drawBonds) {
	if(drawBonds)
            drawBonds();
	refreshBuffer();
    }

    // draw border around board
    protected void drawBorder(Graphics g) {
	g.setColor(Color.white);
	g.drawRect(0,0,boardSize-1,boardSize-1);
    }

    // draw some random bonds
    protected void drawBonds() {
	Point p = new Point();
	Point q = new Point();
	for (p.x = 0; p.x < board.size; ++p.x)
	    for (p.y = 0; p.y < board.size; ++p.y) {
		for (Map.Entry<String,Point> kv : board.incoming(p).entrySet()) {
		    p.add(kv.getValue(),q);
		    if (board.onBoard(q))
			drawBond(p,q);
		}
	    }
    }

    // do a refresh
    protected void refreshBuffer() {
	// update buffer
	getContentPane().repaint();
    }

    // status
    protected void drawStatus(Graphics g) {
	// name of the game
	flashOrHide (g, "Z00 GAS", titleRow, true, 0, 400, true, Color.white);

	// networking
	flashOrHide (g, "Online", networkRow, board.online(), 0, -1, false, Color.blue);
	flashOrHide (g, "Connected", networkRow+1, board.connected(), 0, -1, false, Color.cyan);

	// hint
	int fg = (int) (hintBrightness>255 ? (511-hintBrightness) : hintBrightness);
	int bg = 16;
	Color hintForeground = new Color(bg,Math.max(fg,bg),0);
	Color hintBackground = new Color(bg,bg,0);
	printOrHide (g, hints.get(currentHint), hintRow, true, hintForeground, hintBackground);
	if ((hintBrightness += .5) >= 512) {
	    hintBrightness = 0;
	    currentHint = (currentHint + 1) % hints.size();
	}

	// identify particle that cursor is currently over
	boolean cursorOnBoard = getCursorPos();
	Particle cursorParticle = cursorOnBoard ? board.readCell(cursorPos) : null;
	boolean isSpace = cursorParticle == spaceParticle;
	printOrHide (g, cursorParticle == null
		     ? "Mouseover board to identify pixels"
		     : "Under cursor:", nounRow, true, Color.white);
	String nameToShow = "";
	if (cursorOnBoard)
	    nameToShow = cheatPressed
		? cursorParticle.name + " (" + cursorParticle.count + ")" + board.singleNeighborhoodDescription(cursorPos,false)
		: cursorParticle.visibleName();
	printOrHide (g, nameToShow, nounRow+1, cursorOnBoard, cursorOnBoard ? cursorParticle.color : Color.white);

	// update rate and other stats
	StringBuilder sb = new StringBuilder();
	Formatter formatter = new Formatter(sb, Locale.US);
	Runtime runtime = Runtime.getRuntime();
	printOrHide (g, lastDumpStats, updatesRow, true, new Color(48,48,0));
	printOrHide (g, "Heap: current " + kmg(runtime.totalMemory()) + ", max " + kmg(runtime.maxMemory()) + ", free " + kmg(runtime.freeMemory()), updatesRow+1, true, new Color(48,48,0));
	printOrHide (g, formatter.format("Updates/sec: %.2f",updatesPerSecond).toString(), updatesRow+2, true, new Color(64,64,0));

	// recent verbs
	printOrHide (g, "Recent events:", verbHistoryRow, true, Color.white);
	for (int vpos = 0; vpos < verbHistoryLength; ++vpos) {
	    int v = (verbHistoryPos + verbHistoryLength - vpos) % verbHistoryLength;
	    String verbText = null;
	    Color verbColor = null;
	    if (verbHistory[v] != null) {
		String noun = cheatPressed ? nounHistory[v].name : nounHistory[v].visibleName();
		String nounInBrackets = noun.length() > 0 ? (" (" + noun + ")") : "";
		// uncomment to always print noun:
		//              verbText = (cheatPressed ? verbHistory[v] : Particle.visibleText(verbHistory[v])) + nounInBrackets;
		verbText = cheatPressed ? (verbHistory[v] + nounInBrackets) : Particle.visibleText(verbHistory[v]);
		verbColor = nounHistory[v].color;
	    }
	    printOrHide (g, verbText, verbHistoryRow + vpos + 1, true, verbColor);
	    if (++verbHistoryRefreshCounter >= verbHistoryRefreshPeriod)
		verbsSinceLastRefresh = verbHistoryRefreshCounter = 0;
	}
    }

    // tool bars
    protected void drawToolbox(Graphics g) {
	// spray levels
	toolBox.plotReserves(g);
    }

    static String kmg(long bytes) {
	return bytes < 1024 ? (bytes + "B") : (bytes < 1048576 ? (bytes/1024 + "K") : (bytes < 1073741824 ? (bytes/1048576 + "M") : bytes/1073741824 + "G"));
    }

    private void flashOrHide (Graphics g, String text, int row, boolean show, int minTime, int maxTime, boolean onceOnly, Color color) {
	int flashPeriod = 10, flashes = 10;
	boolean reallyShow = false;
	boolean currentlyShown = timeFirstTrue[row] > 0;
	if (show) {
	    if (!currentlyShown)
		timeFirstTrue[row] = boardUpdateCount;
	    else {
		long timeSinceFirstTrue = boardUpdateCount - timeFirstTrue[row];
		long flashesSinceFirstTrue = (timeSinceFirstTrue - minTime) / flashPeriod;
		reallyShow = timeSinceFirstTrue >= minTime && (maxTime < 0 || timeSinceFirstTrue <= maxTime) && ((flashesSinceFirstTrue > 2*flashes) || (flashesSinceFirstTrue % 2 == 0));
	    }
	} else if (!onceOnly)
	    timeFirstTrue[row] = 0;

	if (reallyShow || currentlyShown)
	    printOrHide (g, text, row, reallyShow, color);
    }

    private void printOrHide (Graphics g, String text, int row, boolean show, Color color) {
	FontMetrics fm = g.getFontMetrics();
	
	int ch = fm.getHeight(), bleed = 6, yPos = row * (ch + bleed);
	if (show && text != null) {
	    int xSize = fm.stringWidth(text), xPos = textBarWidth - xSize;
	    g.setColor (color);
	    g.drawString (text, xPos, yPos + ch);
	}
    }

    private void printOrHide (Graphics g, String text, int row, boolean show, Color color, Color bgColor) {
	FontMetrics fm = g.getFontMetrics();
	int ch = fm.getHeight(), bleed = 6, yPos = row * (ch + bleed);
	g.setColor (bgColor);
	g.fillRect (0, yPos, textBarWidth, ch + bleed);
	
	printOrHide(g, text, row, show, color);
    }


    // UI methods
    // mouse events
    public void mousePressed(MouseEvent e) {
	mouseDown = true;

	Point mousePos = new Point(e.getPoint());
	if (mousePos.x >= boardSize && mousePos.x < boardSize + toolBarWidth + toolLabelWidth)
	    toolBox.clickSelect(mousePos.y);
	else if (mousePos.x >= boardSize + toolBarWidth + toolLabelWidth) {
	    hintBrightness = 240;
	    currentHint = (currentHint + 1) % hints.size();
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
	if (toolBox.hotKeyPressed(c))
	    mouseDown = true;
	else
	{
	    switch(c){
	        case cheatKey: 
	            cheatPressed = true;
		    break;
	        case stopKey:
	            stopPressed = true;
		    break;
	        case slowKey:
	            slowPressed = true;
	            break;
	    }
	}
    }

    public void keyReleased(KeyEvent e) {
	mouseDown = false;
	switch(e.getKeyChar()){
	    case cheatKey: 
	        cheatPressed = false;
	        break;
	    case stopKey:
	        stopPressed = false;
		break;
	    case slowKey:
	        slowPressed = false;
	        break;
        }
    }
}
