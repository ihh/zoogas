import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;

public class Board extends MooreTopology {
    int size = 0;  // size of board in cells

    // main board data
    Cell[][] cell = null;
    HashMap<Point,RemoteCellCoord> remoteCell = null;  // map of connections from off-board Point's to RemoteCellCoord's

    // cellular automata rule/particle generator
    PatternSet patternSet = new PatternSet();

    // random number generator
    Random rnd = null;

    // name lookups
    protected Map<String,Particle> nameToParticle = new HashMap<String,Particle>();  // updated by Particle constructor

    // networking
    UpdateServer updateServer = null;  // UpdateServer fields UDP requests for cross-border interactions
    ConnectionServer connectServer = null;   // ConnectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    int boardServerPort = 4444;
    String localhost = null;

    // constructor
    public Board (int size) {
	this.size = size;
	rnd = new Random();
	cell = new Cell[size][size];
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		cell[x][y] = new Cell();

	// net init
	remoteCell = new HashMap<Point,RemoteCellCoord>();
	try {
	    localhost = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void initClient(int port,ZooGas gas) {

	this.boardServerPort = port;

	try {
	    updateServer = new UpdateServer (this, boardServerPort, gas);
	    updateServer.start();

	    connectServer = new ConnectionServer (this, boardServerPort);
	    connectServer.start();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public void initServer (InetSocketAddress remote) {
	connectBorder (new Point(0,0), new Point(-1,0), new Point(0,1), 128, new Point(-size,0), remote);  // west
	connectBorder (new Point(127,0), new Point(128,0), new Point(0,1), 128, new Point(+size,0), remote);  // east
	connectBorder (new Point(0,0), new Point(0,-1), new Point(1,0), 128, new Point(0,-size), remote);  // north
	connectBorder (new Point(0,127), new Point(0,128), new Point(1,0), 128, new Point(0,+size), remote);  // south
    }

    // read from image
    // TODO: eliminate the ZooGas references here, use a PatternSet instead
    protected void initFromImage (BufferedImage img, ParticleSet particleSet) {
	Set<Particle> ps = particleSet.getParticles(this);

	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y) {
		int c = img.getRGB(x,y);
		int red = (c & 0x00ff0000) >> 16;
		int green = (c & 0x0000ff00) >> 8;
		int blue = c & 0x000000ff;

		// find state with closest color
		int dmin = 0;
		Particle s = null;
		for (Iterator<Particle> e = ps.iterator(); e.hasNext() ;) {
		    Particle pt = e.next();
		    Color ct = pt.color;
		    int rdist = red - ct.getRed(), gdist = green - ct.getGreen(), bdist = blue - ct.getBlue();
		    int dist = rdist*rdist + gdist*gdist + bdist*bdist;
		    if (s == null || dist < dmin) {
			s = pt;
			dmin = dist;
			if (dist == 0)
			    break;
		    }
		}
		writeCell(new Point(x,y), s);
	    }
    }

    // read/write methods for cells
    public int getCellWriteCount (Point p) {
	return cell[p.x][p.y].writeCount;
    }

    public Particle readCell (Point p) {
	return cell[p.x][p.y].particle;
    }

    public void writeCell (Point p, Particle pc) {
	writeCell (p, pc, readCell(p));
    }

    private void writeCell (Point p, Particle pc, Particle old_pc) {
	if (old_pc != pc) {
	    cell[p.x][p.y].particle = pc;
	    ++cell[p.x][p.y].writeCount;
	    if (old_pc != null)
		old_pc.decReferenceCount();
	    pc.incReferenceCount();
	}
    }

    // fill/init method
    public void fill(Particle particle) {
	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		writeCell(p,particle);
    }

    // rendering methods
    public void drawCell (Point p, Graphics g, int pixelsPerCell) {
	g.setColor(cell[p.x][p.y].particle.color);
	Point q = new Point();
	getGraphicsCoords(p,q,pixelsPerCell);
	g.fillRect(q.x,q.y,pixelsPerCell,pixelsPerCell);
    }

    public void drawEverything(Graphics g, int pixelsPerCell) {
	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell (p,g,pixelsPerCell);
    }

    // method to sample a random cell
    private void getRandomPoint (Point p) {
	p.x = rnd.nextInt(size);
	p.y = rnd.nextInt(size);
    }

    // wrapper for topology method
    private int getRandomNeighbor (Point p, Point n) {
	return getNeighbor(p,n,rnd.nextInt(neighborhoodSize()));
    }

    // update methods
    // getRandomPair returns dir
    public int getRandomPair(Point p,Point n) {
	getRandomPoint(p);
	int dir = getRandomNeighbor(p,n);
	return dir;
    }

    public void update(Point p,Point n) {
	int dir = getRandomPair(p,n);
	evolvePair(p,n,dir);
    }

    public void update(int cycles) {
	Point p = new Point(), n = new Point();
	for (int u = 0; u < cycles; ++u)
	    update(p,n);
    }

    public void update(int cycles,Graphics g,int pixelsPerCell) {
	Point p = new Point(), n = new Point();
	for (int u = 0; u < cycles; ++u) {

	    int dir = getRandomPair(p,n);
	    Particle oldSource = readCell(p);
	    Particle oldTarget = onBoard(n) ? readCell(n) : null;
	    evolvePair(p,n,dir);
	    Particle newSource = readCell(p);
	    Particle newTarget = onBoard(n) ? readCell(n) : null;
	    
	    if (newSource != oldSource)
		drawCell(p,g,pixelsPerCell);
	    if (onBoard(n) && newTarget != oldTarget)
		drawCell(n,g,pixelsPerCell);
	}
    }

    // evolvePair(sourceCoords,targetCoords,dir) : delegate to appropriate evolve* method.
    // in what follows, one cell is designated the "source", and its neighbor is the "target".
    // "dir" is the direction from source to target.
    private void evolvePair (Point sourceCoords, Point targetCoords, int dir)
    {
	if (onBoard (targetCoords)) {
	    evolveLocalSourceAndLocalTarget (sourceCoords, targetCoords, dir);
	} else {
	    // request remote evolveLocalTargetForRemoteSource
	    RemoteCellCoord remoteCoords = (RemoteCellCoord) remoteCell.get (targetCoords);
	    if (remoteCoords != null)
		evolveLocalSourceAndRemoteTarget (sourceCoords, remoteCoords, dir);
	}
    }

    // evolveLocalSourceAndRemoteTarget: send an EVOLVE datagram to the network address of a remote cell.
    protected void evolveLocalSourceAndRemoteTarget (Point sourceCoords, RemoteCellCoord remoteCoords, int dir) {
	Particle oldSourceState = readCell(sourceCoords);
	if (oldSourceState.isActive(dir)) {

	    if (oldSourceState.name.equals("_")) {
		System.err.println("_ is active");
		Set<Particle> actives = oldSourceState.pattern[dir].keySet();
		for (Iterator<Particle> a = actives.iterator(); a.hasNext(); )
		    System.err.println("_ " + a.next().name);
	    }

	    double energyBarrier = -neighborhoodEnergy(sourceCoords);
	    BoardServer.sendEvolveDatagram (remoteCoords.addr, remoteCoords.port, remoteCoords.p, oldSourceState, sourceCoords, dir, energyBarrier, localhost, boardServerPort, getCellWriteCount(sourceCoords));
	}
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalSourceAndLocalTarget : handle entirely local updates. Strictly in the family, folks
    synchronized void evolveLocalSourceAndLocalTarget (Point sourceCoords, Point targetCoords, int dir)
    {
	writeCell (sourceCoords, evolveTargetForSource (sourceCoords, targetCoords, readCell(sourceCoords), dir, 0));
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalSourceAndLocalTarget : handle a remote request for update.
    // Return the new source state (the caller of this method will send this returned state back over the network as a RETURN datagram).
    synchronized Particle evolveLocalTargetForRemoteSource (Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	Particle p = evolveTargetForSource (null, targetCoords, oldSourceState, dir, energyBarrier);
	System.err.println("evolveLocalTargetForRemoteSource " + oldSourceState.name + " energyBarrier " + energyBarrier + " return " + p.name);
	return p;
	//	return evolveTargetForSource (null, targetCoords, oldSourceState, dir, energyBarrier);
    }

    // evolveTargetForSource : given a source state, and the co-ords of a target cell,
    // sample the new (source,target) state configuration, write the new target,
    // and return the new source state.
    // The source cell coords are provided, but may be null if the source cell is off-board.
    Particle evolveTargetForSource (Point sourceCoords, Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	// get old state-pair
	Particle oldTargetState = readCell (targetCoords);

	// sample new state-pair
	Particle newSourceState = oldSourceState;
	ParticlePair newCellPair = oldSourceState.samplePair (dir, oldTargetState, rnd, this);
	if (newCellPair != null) {
	    newSourceState = newCellPair.source;
	    Particle newTargetState = newCellPair.target;
	    // test for null
	    if (newSourceState == null || newTargetState == null) {
		throw new RuntimeException ("Null outcome of rule: " + oldSourceState.name + " " + oldTargetState.name + " -> " + (newSourceState == null ? "[null]" : newSourceState.name) + " " + (newTargetState == null ? "[null]" : newTargetState.name));
	    } else {
		// test energy difference and write, or reject
		if (energyDeltaAcceptable(sourceCoords,targetCoords,oldSourceState,oldTargetState,newSourceState,newTargetState,energyBarrier))
		    writeCell (targetCoords, newTargetState);
		else
		    newSourceState = oldSourceState;
	    }
	}

	// return
	return newSourceState;
    }

    // methods to test if a move is energetically acceptable
    boolean energyDeltaAcceptable (Point coords, Particle newState, double energyBarrier) {
	return energyDeltaAcceptable (null, coords, null, readCell(coords), null, newState, energyBarrier);
    }
    boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return energyDeltaAcceptable (sourceCoords, targetCoords, oldSourceState, oldTargetState, newSourceState, newTargetState, 0);
    }
    boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState, double energyBarrier) {
	double energyDelta = energyBarrier +
	    (sourceCoords == null
	     ? neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState)
	     : neighborhoodEnergyDelta(sourceCoords,targetCoords,oldSourceState,oldTargetState,newSourceState,newTargetState));

	return
	    energyDelta <= 0
	    ? true
	    : rnd.nextDouble() < Math.pow(10,-energyDelta);
    }

    // method to calculate the absolute interaction energy of a cell with its neighbors.
    double neighborhoodEnergy (Point p) {
	return neighborhoodEnergyDelta (p, null, readCell(p), null);
    }

    // methods to calculate the energy of a cell neighborhood, if the cell is in a particular state.
    // a single neighbor can optionally be excluded from the sum (this aids in pair-cell neighborhood calculations).
    double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState) {
	return neighborhoodEnergyDelta (p, oldState, newState, null);
    }
    double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState, Point exclude) {
	int N = neighborhoodSize();
	double delta = 0;
	Point q = new Point();
	for (int d = 0; d < N; ++d) {
	    getNeighbor(p,q,d);
	    if (q != exclude && onBoard(q)) {
		Particle nbrState = readCell(q);
		delta += newState.symmetricPairEnergy(nbrState);
		if (oldState != null)
		    delta -= oldState.symmetricPairEnergy(nbrState);
	    }
	}
	return delta;
    }

    // method to calculate the change in energy of a joint neighborhood around a given pair of cells in a particular pair-state.
    // ("joint neighborhood" means the union of the neighborhoods of the two cells.)
    double neighborhoodEnergyDelta (Point sourceCoords, Point targetCoords, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return
	    neighborhoodEnergyDelta(sourceCoords,oldSourceState,newSourceState,targetCoords)
	    + neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState,sourceCoords)
	    + newSourceState.symmetricPairEnergy(newTargetState) - oldSourceState.symmetricPairEnergy(oldTargetState);
    }

    // method to send requests to establish two-way network connections between cells
    // (called in the client during initialization)
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

    public boolean onBoard (Point p) { return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size; }


    // Particle name-indexing methods
    protected void registerParticle (Particle p) {
	nameToParticle.put (p.name, p);
    }

    protected void deregisterParticle (Particle p) {
	nameToParticle.remove (p.name);
	//	System.err.println("Deregistering " + p.name);
    }

    public Particle getParticleByName (String name) {
	return (Particle) nameToParticle.get (name);
    }

    protected Particle getOrCreateParticle (String name) {
	return patternSet.getOrCreateParticle (name, this);
    }

    void loadPatternSetFromFile(String filename) {
	patternSet = PatternSet.fromFile(filename);
    }
}

