import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.Formatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.imageio.ImageIO;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import javax.swing.event.MouseInputAdapter;


public class ZooGas implements KeyListener {

    // command-line argument defaults
    static int defaultPort = 4444;
    static String defaultPatternSetFilename = "ECOLOGY.txt", defaultToolboxFilename = "TOOLS.txt";
    static int defaultBoardSize = 128;
    static int defaultTargetUpdateRate = 100;

    // size of board in cells
    int size = defaultBoardSize;

    // board
    Board board = null;

    // pattern set
    String patternSetFilename = defaultPatternSetFilename;

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
    String initParticlePrefix = "INIT";

    // tools and cheats
    String toolboxFilename = defaultToolboxFilename;
    ToolBox toolBox = null;
    final char cheatKey = '/'; // allows player to see the hidden parts of state names, i.e. the part behind the '/'
    final char stopKey = '.'; // stops the action on this board (does not block incoming network events)
    final char slowKey = ','; // allows player to see bonds

    // commentator code ("well done"-type messages)
    long boardUpdateCount = 0;
    long[] timeFirstTrue = new long[100]; // indexed by row: tracks the first time when various conditions are true, so that the messages flash at first

    Challenge objective;

    // constant helper vars
    final static String spaceParticleName = "_";
    Particle spaceParticle;
    double patternMatchesPerRefresh = 1;

    // Swing
    Cursor boardCursor, normalCursor;
    // Uncomment to use "helicopter.png" as a mouse cursor over the board:
    //    String boardCursorFilename = "helicopter.png";
    String boardCursorFilename = null;
    java.awt.Point boardCursorHotSpot = new java.awt.Point(50, 50); // ignored unless boardCursorFilename != null

    // view
    JFrame zooGasFrame;
    JPanel boardPanel;
    JPanel toolBoxPanel;
    JPanel statusPanel;
    BoardRenderer renderer;
    int boardSize; // width & height of board in pixels
    int belowBoardHeight = 0; // size in pixels of whatever appears below the board -- currently unused but left as a placeholder
    int toolBarWidth = 100, toolLabelWidth = 200, toolHeight = 30; // size in pixels of various parts of the tool bar (right of the board)
    int textBarWidth = 400, textHeight = 30;

    // verb history / subtitle track
    int verbHistoryLength = 10, verbHistoryPos = 0, verbHistoryRefreshPeriod = 20, verbHistoryRefreshCounter = 0, verbsSinceLastRefresh = 0;
    String[] verbHistory = new String[verbHistoryLength];
    Particle[] nounHistory = new Particle[verbHistoryLength];
    Point[] placeHistory = new Point[verbHistoryLength];
    int[] verbHistoryAge = new int[verbHistoryLength];

    Vector<String> hints = new Vector<String>();
    int currentHint = 0;
    double hintBrightness = 0, initialHintBrightness = 240, hintDecayRate = .2;
    private final int updatesRow = 0, titleRow = 4, networkRow = 5, hintRow = 6, objectiveRow = 8;

    // helper objects
    Point cursorPos = new Point(); // co-ordinates of cell beneath current mouse position
    boolean mouseDown = false; // true if mouse is currently down
    boolean cheatPressed = false; // true if cheatKey is pressed (allows player to see hidden parts of state names)
    boolean stopPressed = false; // true if stopKey is pressed (stops updates on this board)
    boolean slowPressed = false; // true if slowKey is pressed (slows updates on this board)
    int targetUpdateRate = 100;
    double updatesPerSecond = 0;
    long timeCheckPeriod = 20; // board refreshes between recalculations of updatesPerSecond
    String lastDumpStats = ""; // hacky way to avoid concurrency issues

    // connection
    protected ClientToServer toWorldServer = null;

    // main()
    public static void main(String[] args) {
        main(args, null);
    }
    public static void main(String[] args, ClientToServer toWorldServer) {
        // create ZooGas object
        ZooGas gas = new ZooGas();
        if(toWorldServer != null) {
            gas.toWorldServer = toWorldServer;
            toWorldServer.setInterface(gas);
        }

        // Process options and args before initializing ZooGas
        boolean isServer = false;
        boolean isClient = false;
        int port = defaultPort;
        String socketAddress = null;

        for (int i = 0; i < args.length; ++i) {
            if ("-s".equals(args[i]) || "--server".equals(args[i])) {
                isServer = true;
            } else if ("-c".equals(args[i]) || "--client".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: not enough parameters given");
                    System.err.println("-c/--client usage: [-c|--client] <remote address>[:<remote port>]");
                    System.exit(0);
                    return;
                }
                socketAddress = args[++i];
                isServer = isClient = true;
            } else if ("-p".equals(args[i]) || "--port".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: not enough parameters given");
                    System.err.println("-p/--port usage: [-p|--port] <port>");
                    System.exit(0);
                    return;
                }
                port = (new Integer(args[++i]));
                isServer = true;
            } else if ("-t".equals(args[i]) || "--tools".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: no tools file specified");
                    System.err.println("-t/--tools usage: [-t|--tools] <tools file>");
                    System.exit(0);
                    return;
                }
                gas.toolboxFilename = args[++i];
            } else if ("-r".equals(args[i]) || "--rules".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: no rules file specified");
                    System.err.println("-r/--rules usage: [-r|--rules] <rules file>");
                    System.exit(0);
                    return;
                }
                gas.patternSetFilename = args[++i];
            } else if ("-u".equals(args[i]) || "--updates".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: no update rate parameter specified");
                    System.err.println("-u/--updates usage: [-u|--updates] <target update rate>");
                    System.exit(0);
                    return;
                }
                gas.targetUpdateRate = Integer.parseInt(args[++i]);
            } else if ("-?".equals(args[i]) || "-h".equals(args[i]) || "--help".equals(args[i])) {
                System.err.println("Usage: <progname> [<option> [<args>]]");
                System.err.println("Valid options:");
                System.err.println("\t[-c|--client <remote address>[:<remote port>]]");
                System.err.println("\t                     - Start ZooGas in client mode");
                System.err.println("\t[-s|--server]        - Start ZooGas in server mode");
                System.err.println("\t[-p|--p <port>]      - Use <port> as the server port (default " + defaultPort + ")");
                System.err.println("\t[-t|--tools <file>]  - Load tools from specified file (default \"" + defaultToolboxFilename + "\")");
                System.err.println("\t[-r|--rules <file>]  - Load rules from specified file (default \"" + defaultPatternSetFilename + "\")");
                System.err.println("\t[-u|--updates <n>]   - Specify desired updates per second (default " + defaultTargetUpdateRate + ")");
                System.err.println("\t[-?|-h|--help]       - Display this very useful help message");
                System.exit(0);
                return;
            } else {
                // Unknown option
                System.err.println("Error: Unknown option: " + args[i]);
                System.exit(0);
                return;
            }
        }

        // initialize after options have been considered
        gas.renderer = new PlayerRenderer(gas, gas.board, gas.size);
        if (isServer) // start as server
            gas.board.initServer(port, gas);

	InetSocketAddress serverAddr = null;
	if (isClient) // start as client (and server)
	{
	    String[] address = socketAddress.split(":");
	    if(address.length > 1) {
	        serverAddr = new InetSocketAddress(address[0], new Integer(address[1]));
	    }
	    else {
	        serverAddr = new InetSocketAddress(address[0], defaultPort);
	    }
	}
        
        gas.board.loadPatternSetFromFile(gas.patternSetFilename);
        gas.start(serverAddr);
    }

    public ZooGas() {
        // create board (needed from some options), then wait for start to be called
        board = new Board(size);
    }

    public void start(InetSocketAddress serverAddr)
    {
	// set helpers, etc.
	boardSize = board.getBoardSize(size,renderer.pixelsPerCell);

        spaceParticle = board.getOrCreateParticle(spaceParticleName);

        // init board
        if (initImageFilename != null) {
            try {
                BufferedImage img = ImageIO.read(new File(initImageFilename));
                ParticleSet imgParticle = ParticleSet.fromFile(initParticleFilename);
                board.initFromImage(img, imgParticle);

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            board.fill(spaceParticle);
            String initParticleName = initParticlePrefix + '/' + RuleMatch.int2string(size / 2);
            Particle initParticle = board.getOrCreateParticle(initParticleName);
            if (initParticle == null)
                throw new RuntimeException("Initialization particle " + initParticleName + " not found");
            Point initPoint = new Point(size / 2, size / 2);
            board.writeCell(initPoint, initParticle);
        }

        // init spray tools
        initSprayTools();

        // init objective

	// TODO: create the following challenge sequence:
	// - place 5 animals
	// - place 5 guests
	// - create an enclosure
	// - while keeping at least 5 guests alive, do each of the following:
	//  - (once only) get animal population over 100
	//  - (for at least 30 seconds) maintain species diversity at ~2.9 species or better, i.e. keep entropy of species distribution above log(2.9)
	//  - (for at least 30 seconds) maintain species diversity at ~3.9 species or better, i.e. keep entropy of species distribution above log(3.9)
	// - keep at least 5 guests alive, and species diversity at ~3.9 or better, while the computer makes your life hell by...
	//  - spraying a random burst of animals around a random location in the zoo every 10 seconds
	//  - spraying a low-intensity acid storm all over the zoo
	//  - a volcano erupts at a random location in the zoo, pouring lava everywhere
	//  - one of your zoo guests starts spraying perfume everywhere
	//  - one of your zoo guests turns into a terrorist, throwing bombs all over the place


	// TODO: challenges should be able provide challenge-specific scores, feedback and rewards;
	// e.g. (at a minimum) the diversity scores and particle counts that are currently displayed.


        // hackish test cases (kept here for reference)
        // place 5 guests anywhere
        // objective = new Challenge(board, new Challenge.EncloseParticles(5, "zoo_guest", board));
        // create 4 separated enclosures
        // objective = new Challenge(board, new Challenge.EnclosuresCondition(board, null, null, 4));
        // create 3 separated enclosures with 4 zoo_guests in each
        //objective = new Challenge(board, new Challenge.EnclosuresCondition(board, null, new Challenge.EncloseParticles(4, "zoo_guest", board), 3));
        // place a zoo_guest, then wait 50 updates
        //objective = new Challenge(board, new Challenge.SucceedNTimes(null, new Challenge.EncloseParticles(1, "zoo_guest", board), 50));
        // place 5 animals anywhere
        // objective = new Challenge(board, new Challenge.EncloseParticles(5, "critter/.*", board));

	// init hints
	String specialKeys = "Special keys: "+cheatKey+" (reveal state) "+slowKey+" (reveal bonds) "+stopKey+" (freeze)";
	hints.add ("Deputy Head Zookeeper, Celia O'Tuamata.");
	hints.add ("Make a zoo using the tools in your Toolbox (left).");
	hints.add ("Select a tool by clicking, or press its hot-key.");
	hints.add ("Try building a cage.");
	if (toolBox.tool.size() > 0)
	    hints.add ("Press \"" + toolBox.tool.get(0).hotKey + "\" to select " + toolBox.tool.get(0).particleName + "; etc.");
	hints.add ("Click on the board to use the currently selected tool.");
	hints.add ("Hold down the tool hotkey with the mouse over the board.");
	if (toolBox.tool.size() > 0)
	    hints.add ("Mouseover the board & hold \"" + toolBox.tool.get(0).hotKey + "\" to spray " + toolBox.tool.get(0).particleName + " pixels; etc.");
	hints.add ("Use cage-builders to get your zoo started.");
	hints.add ("Next to each tool is a bar showing the reserve.");
	hints.add ("If you mouseover a pixel on the board, its name appears.");
	hints.add ("When you build a cage, it contains a few animals.");
	hints.add (specialKeys);
	hints.add ("The \""+cheatKey+"\" key reveals the hidden state of a pixel.");
	hints.add (specialKeys);
	hints.add ("The \""+cheatKey+"\" key reveals outgoing(>) and incoming(<) bonds.");
	hints.add ("The \""+stopKey+"\" key stops all action on the board.");
	hints.add ("Keep cage walls in good repair, or animals will escape.");
	hints.add (specialKeys);
	hints.add ("The \""+cheatKey+"\" key reveals the number of pixels in existence.");
	hints.add ("The \""+slowKey+"\" key draws bonds on the board.");

        // init JFrame
        zooGasFrame = new JFrame("ZooGas");
        JPanel contentPane = (JPanel)zooGasFrame.getContentPane();
        zooGasFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        zooGasFrame.setResizable(false);

        boardPanel = new JPanel() {
                public void paintComponent(Graphics g) {
                    //super.paintComponent(g);
                    g.drawImage(renderer.image, 0, 0, null);
                    if (slowPressed) {
                        drawBonds(g);
                        drawEnclosures(g);
                    }
		    drawVerbs(g);
		    drawCursorNoun(g);
                }
            };
        toolBoxPanel = new JPanel() {
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    drawToolbox(g);
                }
            };
        statusPanel = new JPanel() {
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    drawStatus(g);
                }
            };

        boardPanel.setDoubleBuffered(false);
        toolBoxPanel.setDoubleBuffered(false);
        statusPanel.setDoubleBuffered(false);

        boardPanel.setBackground(Color.BLACK);
        toolBoxPanel.setBackground(Color.BLACK);
        statusPanel.setBackground(Color.BLACK);

        // set size
        boardPanel.setPreferredSize(new Dimension(boardSize, boardSize));
        toolBoxPanel.setPreferredSize(new Dimension(toolBarWidth + toolLabelWidth, boardSize));
        statusPanel.setPreferredSize(new Dimension(textBarWidth, boardSize));

        boardPanel.setBorder(new LineBorder(Color.white, 1));

        // add to content pane using layout
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        contentPane.add(boardPanel, c);
        ++c.gridx;
        contentPane.add(toolBoxPanel, c);
        ++c.gridx;
        contentPane.add(statusPanel, c);

        zooGasFrame.pack();
        zooGasFrame.setVisible(true);

        // create cursors
        boardCursor = new Cursor(Cursor.HAND_CURSOR);
        normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);
        
        // register for mouse & keyboard events
        BoardMouseAdapter boardMouse = new BoardMouseAdapter();
        boardPanel.addMouseListener(boardMouse);
        boardPanel.addMouseMotionListener(boardMouse);

        MouseListener toolsMouse = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                toolBox.clickSelect(e.getPoint().y);
            }
        };
        toolBoxPanel.addMouseListener(toolsMouse);
         
        MouseListener statusMouse = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                hintBrightness = initialHintBrightness;
                currentHint = (currentHint + 1) % hints.size();
            }
        };
        statusPanel.addMouseListener(statusMouse);
        zooGasFrame.addKeyListener(this);

	// connect to server
	if (serverAddr != null)
	    board.initClient(serverAddr);

	// run
	gameLoop();
    }

    // main game loop
    private void gameLoop() {
        // Game logic goes here
        zooGasFrame.repaint();

        Runtime runtime = Runtime.getRuntime();
        long lastTimeCheck = System.currentTimeMillis();
        long updateStartTime = System.currentTimeMillis();
        long targetTimePerUpdate = 1000 / targetUpdateRate;
        long timeDiff;

        try {
            while (true) {
                updateStartTime = System.currentTimeMillis();

                if (!stopPressed)
                    evolveStuff();
                useTools();

                if (boardUpdateCount % timeCheckPeriod == 0) {
                    double heapFraction = ((double)(runtime.totalMemory() - runtime.freeMemory())) / (double)runtime.maxMemory();
                    if (heapFraction > .5)
                        board.flushCaches();

                    lastDumpStats = board.debugDumpStats();
                    long currentTimeCheck = System.currentTimeMillis();
                    updatesPerSecond = ((double)1000 * timeCheckPeriod) / ((double)(currentTimeCheck - lastTimeCheck));
                    lastTimeCheck = currentTimeCheck;

                    if (objective != null)
                        objective.check();
                }
                zooGasFrame.repaint();

                timeDiff = System.currentTimeMillis() - updateStartTime;
                if (timeDiff < targetTimePerUpdate) {
                    Thread.currentThread().sleep(targetTimePerUpdate - timeDiff);
                }
            }
        } catch (InterruptedException e) {
        }
    }

    // main evolution loop
    private void evolveStuff() {
        board.update(patternMatchesPerRefresh, renderer);
        ++boardUpdateCount;
    }

    // init tools method
    private void initSprayTools() {
        toolBox = ToolBox.fromFile(toolboxFilename, board);
        toolBox.toolHeight = toolHeight;
        toolBox.toolReserveBarWidth = toolBarWidth;
        toolBox.toolTextWidth = toolLabelWidth;
    }

    private void useTools() {
        if (board.onBoard(cursorPos)) {
            zooGasFrame.setCursor(boardCursor);

            // do spray
            if (mouseDown && toolBox.currentTool != null) {
                toolBox.currentTool.spray(cursorPos, board, renderer, spaceParticle);
                return;
            }
        } else
            zooGasFrame.setCursor(normalCursor);

        toolBox.refill(1);
    }
    
    public ClientToServer getWorldServerThread() {
        return toWorldServer;
    }
    
    /**
     *Refreshes the buffer of the ZooGas frame
     */
    protected void refreshBuffer() {
        // update buffer
        zooGasFrame.getContentPane().repaint();
    }

    /**
     *Draws all active bonds between particles using random colors
     * @param g
     */
    protected void drawBonds(Graphics g) {
        Point p = new Point();
        for (p.x = 0; p.x < board.size; ++p.x) {
            for (p.y = 0; p.y < board.size; ++p.y) {
                for (Map.Entry<String, Point> kv : board.incoming(p).entrySet()) {
                    Point delta = kv.getValue();
                    if (delta != null) {
                        Point q = p.add(kv.getValue());
                        if (board.onBoard(q))
                            drawBond(g, p, q);
                        /* TODO: consider using new Points in this loop
			 */
                    }
                }
            }
        }
    }
    private void drawBond(Graphics g, Point p, Point q) {
        g.setColor(new Color((float)Math.random(), (float)Math.random(), (float)Math.random()));
        Point pg = renderer.getGraphicsCoords(p);
        Point qg = renderer.getGraphicsCoords(q);
        int k = renderer.pixelsPerCell >> 1;
        g.drawLine(pg.x + k, pg.y + k, qg.x + k, qg.y + k);
    }

    // highlight enclosures
    protected void drawEnclosures(Graphics g) {
        Image image = new BufferedImage(boardSize, boardSize, BufferedImage.TYPE_INT_ARGB);
        Graphics ig = image.getGraphics();
        for (Set<Point> enclosure : Challenge.getEnclosures(board)) {
            //ig.setColor(new Color ((float)Math.random(), (float)Math.random(), (float)Math.random()));
            ig.setColor(new Color(100, 0, 0, 150));
            for (Point p : enclosure) {
                Point q = renderer.getGraphicsCoords(p);
                ig.fillRect(q.x, q.y, renderer.pixelsPerCell, renderer.pixelsPerCell);
            }
        }

        g.drawImage(image, 0, 0, null);
    }

    /**
     *Draws the status panel
     * @param g
     */
    protected void drawStatus(Graphics g) {
        // name of the game
        flashOrHide(g, "Z00 GAS", titleRow, true, 0, 400, true, Color.white);

        // networking
        flashOrHide(g, "Online", networkRow, board.online(), 0, -1, false, Color.blue);
        flashOrHide(g, "Connected", networkRow + 1, board.connected(), 0, -1, false, Color.cyan);

        // current objective
        if (objective != null)
            printOrHide(g, "Goal: " + objective.getDescription(), objectiveRow, true, Color.white);
	else {
	    // until we get challenges working properly, just display an auto-rotating hint and a few feedback scores

	    // current hint
	    hintBrightness -= hintDecayRate;
	    if (hintBrightness < 0) {
		hintBrightness = initialHintBrightness;
		currentHint = (currentHint + 1) % hints.size();
	    }
	    
	    Color hintColor = new Color ((int) hintBrightness, (int) hintBrightness, 0);
	    printOrHide(g, hints.elementAt(currentHint), hintRow, true, hintColor);

	    // quick, hacky feedback scores on population stats - to be replaced by more generic challenges (which may incorporate these scores)
	    String guestName = "zoo_guest";
	    String critterPrefix = "critter";
	    int targetGuests = 10;
	    int targetCritters = 100;
	    double targetDiversity = 3.5;

	    Particle guestParticle = board.getParticleByName(guestName);
	    int totalGuests = guestParticle==null ? 0 : guestParticle.getReferenceCount();
	    String guestString = "Guests: " + String.format("% 2d", totalGuests) + (totalGuests < targetGuests
							     ? (" (goal: " + targetGuests + ")")
							     : "");

	    int totalCritters = 0;
	    double diversityScore = 0;
	    if (board.gotPrefix(critterPrefix)) {
		Set<Particle> critters = board.getParticlesByPrefix(critterPrefix);
		if (critters != null) {
		    for (Particle critter : critters)
			totalCritters += critter.getReferenceCount();
		    double entropy = 0;
		    for (Particle critter : critters) {
			double p = ((double) critter.getReferenceCount()) / (double) totalCritters;
			if (p > 0)
			    entropy -= p * Math.log(p);
		    }
		    diversityScore = Math.exp(entropy);
		}
	    }

	    String popString = "Animals: " + String.format("% 3d", totalCritters) + (totalCritters < targetCritters
										     ? (" (goal: " + targetCritters + ")")
										     : "");

	    String divString = totalCritters < targetCritters
		? ""
		: (", diversity: " + String.format("%.2f", diversityScore) + (diversityScore < targetDiversity
									      ? (" (goal: " + targetDiversity + ")")
									      : ""));
            printOrHide(g, guestString, objectiveRow, true, Color.green);
            printOrHide(g, popString + divString, objectiveRow + 1, true, Color.green);
	}

        // update rate and other stats
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb, Locale.US);
        Runtime runtime = Runtime.getRuntime();
        printOrHide(g, lastDumpStats, updatesRow, true, new Color(48, 48, 0));
        printOrHide(g, "Heap: current " + kmg(runtime.totalMemory()) + ", max " + kmg(runtime.maxMemory()) + ", free " + kmg(runtime.freeMemory()),
                    updatesRow + 1, true, new Color(48, 48, 0));
        printOrHide(g, formatter.format("Updates/sec: %.2f", updatesPerSecond).toString(), updatesRow + 2, true, new Color(64, 64, 0));
    }

    protected void drawVerbs(Graphics g) {
	// display params
	int maxAge = 100;
	boolean writeNouns = false;
	int verbBalloonBorder = 2;
	int bubbleLines = writeNouns ? 2 : 1;

	// font
        FontMetrics fm = g.getFontMetrics();

	// loop over verb history
        for (int vpos = 0; vpos < verbHistoryLength; ++vpos) {
            int v = (verbHistoryPos + verbHistoryLength - vpos) % verbHistoryLength;

            if (verbHistory[v] != null) {
		if (verbHistoryAge[v]++ >= maxAge)
		    verbHistory[v] = null;
		else {
		    String nounText = cheatPressed ? nounHistory[v].name : nounHistory[v].visibleName();
		    String verbText = cheatPressed ? verbHistory[v] : Particle.visibleText(verbHistory[v]);
		    Color verbColor = nounHistory[v].color;

		    String[] text = new String[bubbleLines];
		    Color[] textColor = new Color[bubbleLines];

		    text[0] = verbText;
		    textColor[0] = verbColor;

		    if (writeNouns) {
			text[1] = nounText;
			textColor[1] = verbColor;
		    }

		    drawSpeechBalloon (g, placeHistory[v], 0., -1., verbBalloonBorder, text, textColor, verbColor, Color.black);
		}
	    }
	}

	if (++verbHistoryRefreshCounter >= verbHistoryRefreshPeriod)
	    verbsSinceLastRefresh = verbHistoryRefreshCounter = 0;
    }

    protected void drawCursorNoun(Graphics g) {
	int nounBalloonBorder = 2;
        if (board.onBoard(cursorPos)) {
            Particle cursorParticle = board.readCell(cursorPos);
            boolean isSpace = cursorParticle == spaceParticle;

            String nameToShow =
                cheatPressed ? cursorParticle.name + " (" + cursorParticle.getReferenceCount() + ")" + board.singleNeighborhoodDescription(cursorPos, false) :
                cursorParticle.visibleName();

	    if (nameToShow.length() > 0) {
		Color fgCurs = cursorParticle == null ? Color.white : cursorParticle.color;
		Color bgCurs = cheatPressed ? new Color(255 - fgCurs.getRed(), 255 - fgCurs.getGreen(), 255 - fgCurs.getBlue()) : Color.black;

		String[] text = new String[1];
		Color[] textColor = new Color[1];

		text[0] = nameToShow;
		textColor[0] = bgCurs;

		drawSpeechBalloon (g, cursorPos, 0., +3., nounBalloonBorder, text, textColor, null, fgCurs);
	    }
	}
    }

    // TODO: drawSpeechBalloon should detect cases where the speech balloon is out of the Panel's paintable area, and adjust its position accordingly
    protected void drawSpeechBalloon (Graphics g, Point cell, double xOffset, double yOffset, int balloonBorder, String[] text, Color[] textColor, Color balloonColor, Color bgColor) {
        FontMetrics fm = g.getFontMetrics();

	int xSize = 0,
	    ySize = fm.getHeight();

	for (int n = 0; n < text.length; ++n) {
	    xSize = Math.max (xSize, fm.stringWidth(text[n]));
	}

	java.awt.Point cellGraphicsCoords = renderer.getGraphicsCoords(cell);

	int xPos = cellGraphicsCoords.x + (int) (xSize * (xOffset - 0.5)),
	    yPos = cellGraphicsCoords.y + (int) (ySize * yOffset);

	// draw speech balloon
	int yTextSize = ySize * text.length;

	if (balloonColor != null) {
	    g.setColor(balloonColor);
	    g.drawLine(xPos, yPos, cellGraphicsCoords.x, cellGraphicsCoords.y);
	}

	g.setColor(bgColor);
	g.fillRect(xPos - balloonBorder,
		   yPos - yTextSize - balloonBorder,
		   xSize + 2*balloonBorder,
		   yTextSize + 2*balloonBorder);

	for (int n = 0; n < text.length; ++n) {
	    g.setColor(textColor[n]);
	    g.drawString(text[n], xPos, yPos - ySize*n);
	}

	if (balloonColor != null) {
	    g.setColor(balloonColor);
	    g.drawRect(xPos - balloonBorder,
		       yPos - yTextSize - balloonBorder,
		       xSize + 2*balloonBorder,
		       yTextSize + 2*balloonBorder);
	}

    }

    // tool bars
    protected void drawToolbox(Graphics g) {
        // spray levels
        toolBox.plotReserves(g);
    }

    static String kmg(long bytes) {
        return bytes < 1024 ? (bytes + "B") :
               (bytes < 1048576 ? (bytes / 1024 + "K") : (bytes < 1073741824 ? (bytes / 1048576 + "M") : bytes / 1073741824 + "G"));
    }

    private void flashOrHide(Graphics g, String text, int row, boolean show, int minTime, int maxTime, boolean onceOnly, Color color) {
        int flashPeriod = 10, flashes = 10;
        boolean reallyShow = false;
        boolean currentlyShown = timeFirstTrue[row] > 0;
        if (show) {
            if (!currentlyShown)
                timeFirstTrue[row] = boardUpdateCount;
            else {
                long timeSinceFirstTrue = boardUpdateCount - timeFirstTrue[row];
                long flashesSinceFirstTrue = (timeSinceFirstTrue - minTime) / flashPeriod;
                reallyShow =
                        timeSinceFirstTrue >= minTime && (maxTime < 0 || timeSinceFirstTrue <= maxTime) && ((flashesSinceFirstTrue > 2 * flashes) || (flashesSinceFirstTrue %
                                                                                                                                                      2 == 0));
            }
        } else if (!onceOnly)
            timeFirstTrue[row] = 0;

        if (reallyShow || currentlyShown)
            printOrHide(g, text, row, reallyShow, color);
    }

    private void printOrHide(Graphics g, String text, int row, boolean show, Color color) {
        FontMetrics fm = g.getFontMetrics();

        int ch = fm.getHeight(), bleed = 6, yPos = row * (ch + bleed);
        if (show && text != null) {
            int xSize = fm.stringWidth(text), xPos = textBarWidth - xSize;
            g.setColor(color);
            g.drawString(text, xPos, yPos + ch);
        }
    }

    private void printOrHide(Graphics g, String text, int row, boolean show, Color color, Color bgColor) {
        FontMetrics fm = g.getFontMetrics();
        int ch = fm.getHeight(), bleed = 6, yPos = row * (ch + bleed);
        g.setColor(bgColor);
        g.fillRect(0, yPos, textBarWidth, ch + bleed);

        printOrHide(g, text, row, show, color);
    }


    // UI methods
    // mouse events
    private class BoardMouseAdapter extends MouseInputAdapter {
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
            cursorPos.x = -1;
            cursorPos.y = -1;
            mouseDown = false;
        }

        public void mouseClicked(MouseEvent e) {
            mouseDown = false;
        }

        public void mouseMoved(MouseEvent e) {
            cursorPos = renderer.getCellCoords(e.getPoint());
        }

        public void mouseDragged(MouseEvent e) {
            cursorPos = renderer.getCellCoords(e.getPoint());
        }
    }

    // key events
    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
        char c = e.getKeyChar();
        if (toolBox.hotKeyPressed(c))
            mouseDown = true;
        else {
            switch (c) {
                case cheatKey:
                    cheatPressed = true;
                    break;
                case stopKey:
                    stopPressed = true;
                    break;
                case slowKey:
                    slowPressed = true;
                    break;
                case 'm':
                    for (String ss : board.nameToParticle.keySet())
                        System.err.println(ss);
                    break;
                case '`':
                    if(statusPanel.isVisible()) {
                        statusPanel.setVisible(false);
                        toolBoxPanel.setVisible(false);
                    }
                    else {
                        statusPanel.setVisible(true);
                        toolBoxPanel.setVisible(true);
                    }
                    zooGasFrame.pack();
                    break;
            }
        }
    }

    public void keyReleased(KeyEvent e) {
        mouseDown = false;
        switch (e.getKeyChar()) {
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
