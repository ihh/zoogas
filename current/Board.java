import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;

public class Board extends MooreTopology {
    public int size = 0;  // size of board in cells

    // main board data
    private Cell[][] cell = null;

    // cellular automata rule/particle generator
    private PatternSet patternSet = new PatternSet(this);

    // random number generator
    private Random rnd = null;

    // particle name registry
    protected Map<String,Particle> nameToParticle = new HashMap<String,Particle>();  // updated by Particle constructor

    // off-board connections
    private HashMap<Point,RemoteCellCoord> remoteCell = null;  // map of connections from off-board Point's to RemoteCellCoord's

    // networking
    private UpdateServer updateServer = null;  // UpdateServer fields UDP requests for cross-border interactions
    private ConnectionServer connectServer = null;   // ConnectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    private int boardServerPort = 4444;
    private String localhost = null;

    // fast quad tree
    private int K = 0;  // K = log_2(size)
    private double[] quadRate = null;

    // constructor
    public Board (int size) {
	this.size = size;
	rnd = new Random();
	cell = new Cell[size][size];
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		cell[x][y] = new Cell();

	// quad tree
	int tmp = size;
	for (K = 0; tmp > 1; ) {
	    if ((tmp & 1) != 0)
		throw new RuntimeException("While building quad tree: board size is not a power of 2");
	    tmp = tmp >> 1;
	    ++K;
	}
	int totalNodes = (4*size*size - 1) / 3;
	quadRate = new double[totalNodes];  // initialized to zero

	// net init
	remoteCell = new HashMap<Point,RemoteCellCoord>();
	try {
	    localhost = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public final void initClient(int port,ZooGas gas) {

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

    public final void initServer (InetSocketAddress remote) {
	connectBorder (new Point(0,0), new Point(-1,0), new Point(0,1), 128, new Point(-size,0), remote);  // west
	connectBorder (new Point(127,0), new Point(128,0), new Point(0,1), 128, new Point(+size,0), remote);  // east
	connectBorder (new Point(0,0), new Point(0,-1), new Point(1,0), 128, new Point(0,-size), remote);  // north
	connectBorder (new Point(0,127), new Point(0,128), new Point(1,0), 128, new Point(0,+size), remote);  // south
    }

    // read/write methods for cells
    public final int getCellWriteCount (Point p) {
	return cell[p.x][p.y].writeCount;
    }

    public final Particle readCell (Point p) {
	return cell[p.x][p.y].particle;
    }

    public final void writeCell (Point p, Particle pc) {
	writeCell (p, pc, readCell(p));
	updateQuadTree (p, Math.min (pc.totalTransformRate, 1));
    }

    private final void writeCell (Point p, Particle pc, Particle old_pc) {
	if (old_pc != pc) {
	    cell[p.x][p.y].particle = pc;
	    ++cell[p.x][p.y].writeCount;
	    if (old_pc != null)
		old_pc.decReferenceCount();
	    pc.incReferenceCount();
	}
    }

    // fill/init method
    public final void fill(Particle particle) {
	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		writeCell(p,particle);
    }

    // helper to test if a cell is on board
    public final boolean onBoard (Point p) { return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size; }

    // quad-tree indexing
    private int quadNodeIndex(Point p,int level) {
	int nodesBeforeLevel = ((1 << (level << 1)) - 1) / 3;
	int msbY = p.y >> (K - level);
	int msbX = p.x >> (K - level);
	return msbX + (msbY << level) + nodesBeforeLevel;
    }

    private int quadChildIndex(int parentIndex,int parentLevel,int whichChild) {
	int childLevel = parentLevel + 1;
	int nodesBeforeParent = ((1 << (parentLevel << 1)) - 1) / 3;
	int nodesBeforeChild = ((1 << (childLevel << 1)) - 1) / 3;
	int parentOffset = parentIndex - nodesBeforeParent;
	int msbParentY = parentOffset >> parentLevel;
	int msbParentX = parentOffset - (msbParentY << parentLevel);
	int msbChildY = (msbParentY << 1) | (whichChild >> 1);
	int msbChildX = (msbParentX << 1) | (whichChild & 1);
	return msbChildX + (msbChildY << childLevel) + nodesBeforeChild;
    }

    private void updateQuadTree(Point p,double val) {
	double oldVal = quadRate[quadNodeIndex(p,K)];
	double diff = val - oldVal;
	for (int lev = 0; lev <= K; ++lev) {
	    int n = quadNodeIndex(p,lev);
	    quadRate[n] = Math.max (quadRate[n] + diff, 0);
	}
    }

    private void sampleQuadLeaf(Point p) {
	int node = 0;
	p.x = p.y = 0;
	for (int lev = 0; lev < K; ++lev) {
	    double prob = rnd.nextDouble() * quadRate[node];
	    int whichChild = 0, childNode = -1;
	    while (true) {
		childNode = quadChildIndex(node,lev,whichChild);
		prob -= quadRate[childNode];
		if (prob < 0 || whichChild == 3)
		    break;
		++whichChild;
	    }
	    node = childNode;
	    p.y = (p.y << 1) | (whichChild >> 1);
	    p.x = (p.x << 1) | (whichChild & 1);
	}
    }

    private double topQuadRate() { return quadRate[0]; }

    // update methods
    // getRandomPair returns dir
    public final int getRandomPair(Point p,Point n) {
	sampleQuadLeaf(p);
	int dir = readCell(p).sampleDir(rnd);
	getNeighbor(p,n,dir);
	return dir;
    }

    // update() returns number of updated cells
    public final int update(double boardUpdates,BoardRenderer renderer) {
	int updatedCells = 0;
	Point p = new Point(), n = new Point();
	double maxUpdates = boardUpdates * topQuadRate();
	for (; updatedCells < maxUpdates; ++updatedCells) {

	    int dir = getRandomPair(p,n);
	    Particle oldSource = readCell(p);
	    Particle oldTarget = onBoard(n) ? readCell(n) : null;
	    ParticlePair newPair = evolvePair(p,n,dir);
	    if (newPair != null) {
		Particle newSource = newPair.source;
		Particle newTarget = newPair.target;
	    
		if (newSource != oldSource)
		    renderer.drawCell(p);

		if (onBoard(n) && newTarget != oldTarget)
		    renderer.drawCell(n);

		if (newPair.verb != null)
		    renderer.showVerb(p,n,oldSource,oldTarget,newPair);
	    }
	}
	return updatedCells;
    }

    // evolvePair(sourceCoords,targetCoords,dir) : delegate to appropriate evolve* method.
    // in what follows, one cell is designated the "source", and its neighbor is the "target".
    // "dir" is the direction from source to target.
    // returns a ParticlePair describing the new state and verb (may be null).
    private final ParticlePair evolvePair (Point sourceCoords, Point targetCoords, int dir)
    {
	ParticlePair pp = null;
	if (onBoard (targetCoords)) {
	    pp = evolveLocalSourceAndLocalTarget (sourceCoords, targetCoords, dir);
	} else {
	    // request remote evolveLocalTargetForRemoteSource
	    RemoteCellCoord remoteCoords = (RemoteCellCoord) remoteCell.get (targetCoords);
	    if (remoteCoords != null)
		evolveLocalSourceAndRemoteTarget (sourceCoords, remoteCoords, dir);
	}
	return pp;
    }

    // evolveLocalSourceAndRemoteTarget: send an EVOLVE datagram to the network address of a remote cell.
    protected final void evolveLocalSourceAndRemoteTarget (Point sourceCoords, RemoteCellCoord remoteCoords, int dir) {
	Particle oldSourceState = readCell(sourceCoords);
	if (oldSourceState.isActive(dir)) {

	    if (oldSourceState.name.equals("_")) {
		System.err.println("_ is active");
		Set<Particle> actives = oldSourceState.pattern.get(dir).keySet();
		for (Iterator<Particle> a = actives.iterator(); a.hasNext(); )
		    System.err.println("_ " + a.next().name);
	    }

	    double energyBarrier = -neighborhoodEnergy(sourceCoords);
	    BoardServer.sendEvolveDatagram (remoteCoords.addr, remoteCoords.port, remoteCoords.p, oldSourceState, sourceCoords, dir, energyBarrier, localhost, boardServerPort, getCellWriteCount(sourceCoords));
	}
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalSourceAndLocalTarget : handle entirely local updates. Strictly in the family, folks
    // returns a ParticlePair
    synchronized public final ParticlePair evolveLocalSourceAndLocalTarget (Point sourceCoords, Point targetCoords, int dir)
    {
	ParticlePair newCellPair = evolveTargetForSource(sourceCoords,targetCoords,readCell(sourceCoords),dir,0);
	if (newCellPair != null)
	    writeCell (sourceCoords, newCellPair.source);
	return newCellPair;
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalTargetForRemoteSource : handle a remote request for update.
    // Return the new source state (the caller of this method will send this returned state back over the network as a RETURN datagram).
    synchronized public final Particle evolveLocalTargetForRemoteSource (Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	ParticlePair pp = evolveTargetForSource(null,targetCoords,oldSourceState,dir,energyBarrier);
	return pp == null ? oldSourceState : pp.source;
    }

    // evolveTargetForSource : given a source state, and the co-ords of a target cell,
    // sample the new (source,target) state configuration, accept/reject based on energy difference,
    // write the updated target, and return the updated (source,target) pair.
    // The source cell coords are provided, but may be null if the source cell is off-board.
    public final ParticlePair evolveTargetForSource (Point sourceCoords, Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	// get old state-pair
	Particle oldTargetState = readCell (targetCoords);

	// sample new state-pair
	ParticlePair newCellPair = oldSourceState.samplePair (dir, oldTargetState, rnd, this);

	if (newCellPair != null) {
	    Particle newSourceState = newCellPair.source;
	    Particle newTargetState = newCellPair.target;
	    // test for null
	    if (newSourceState == null || newTargetState == null) {
		throw new RuntimeException ("Null outcome of rule: " + oldSourceState.name + " " + oldTargetState.name + " -> " + (newSourceState == null ? "[null]" : newSourceState.name) + " " + (newTargetState == null ? "[null]" : newTargetState.name));
	    } else {
		// test energy difference and write, or reject
		if (energyDeltaAcceptable(sourceCoords,targetCoords,dir,oldSourceState,oldTargetState,newSourceState,newTargetState,energyBarrier)) {
		    //		    System.err.println ("Firing rule: " + oldSourceState.name + " " + oldTargetState.name + " -> " + newSourceState.name + " " + newTargetState.name + " " + newCellPair.verb);
		    writeCell (targetCoords, newTargetState);
		} else {
		    newCellPair = null;
		}
	    }
	}

	// return
	return newCellPair;
    }

    // methods to test if a move is energetically acceptable
    public final boolean energyDeltaAcceptable (Point coords, Particle newState, double energyBarrier) {
	return energyDeltaAcceptable (null, coords, -1, null, readCell(coords), null, newState, energyBarrier);
    }
    public final boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, int dir, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return energyDeltaAcceptable (sourceCoords, targetCoords, dir, oldSourceState, oldTargetState, newSourceState, newTargetState, 0);
    }
    public final boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, int dir, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState, double energyBarrier) {

	double energyDelta = energyBarrier +
	    (sourceCoords == null
	     ? neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState)
	     : neighborhoodEnergyDelta(sourceCoords,targetCoords,dir,oldSourceState,oldTargetState,newSourceState,newTargetState));

	//	if (energyDelta < 0)
	//	    System.err.println("Gain in energy (" + -energyDelta + "): " + oldSourceState.name + " " + oldTargetState.name + " -> " + newSourceState.name + " " + newTargetState.name);

	return
	    energyDelta <= 0
	    ? true
	    : rnd.nextDouble() < Math.pow(10,-energyDelta);
    }

    // method to calculate the absolute interaction energy of a cell with its neighbors.
    public final double neighborhoodEnergy (Point p) {
	return neighborhoodEnergyDelta (p, null, readCell(p), null);
    }

    // methods to calculate the energy of a cell neighborhood, if the cell is in a particular state.
    // a single neighbor can optionally be excluded from the sum (this aids in pair-cell neighborhood calculations).
    public final double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState) {
	return oldState == newState ? 0 : neighborhoodEnergyDelta (p, oldState, newState, null);
    }
    public final double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState, Point exclude) {
	double delta = 0;
	if (oldState != newState) {
	    int N = neighborhoodSize();
	    Point q = new Point();
	    for (int d = 0; d < N; ++d) {
		getNeighbor(p,q,d);
		if (q != exclude && onBoard(q)) {
		    Particle nbrState = readCell(q);
		    delta += newState.symmetricPairEnergy(nbrState,d);
		    if (oldState != null)
			delta -= oldState.symmetricPairEnergy(nbrState,d);
		}
	    }
	}
	return delta;
    }

    // method to calculate the change in energy of a joint neighborhood around a given pair of cells in a particular pair-state.
    // ("joint neighborhood" means the union of the neighborhoods of the two cells.)
    public final double neighborhoodEnergyDelta (Point sourceCoords, Point targetCoords, int dir, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return
	    neighborhoodEnergyDelta(sourceCoords,oldSourceState,newSourceState,targetCoords)
	    + neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState,sourceCoords)
	    + newSourceState.symmetricPairEnergy(newTargetState,dir) - oldSourceState.symmetricPairEnergy(oldTargetState,dir);
    }

    // debug method (or not...)
    // returns a description of the neighborhood as a String
    String neighborhoodDescription(Point p) {
	StringBuffer sb = new StringBuffer();
	Point n = new Point();
	for (int d = 0; d < neighborhoodSize(); ++d) {
	    getNeighbor(p,n,d);
	    if (d > 0)
		sb.append(' ');
	    sb.append(readCell(n).name);
	}
	return sb.toString();
    }

    // method to send requests to establish two-way network connections between cells
    // (called in the client during initialization)
    private final void connectBorder (Point sourceStart, Point targetStart, Point lineVector, int lineLength, Point remoteOrigin, InetSocketAddress remoteBoard) {
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

    protected final void addRemoteCellCoord (Point p, InetSocketAddress remoteBoard, Point pRemote) {
	System.err.println("Connecting (" + p.x + "," + p.y + ") to (" + pRemote.x + "," + pRemote.y + ") on " + remoteBoard);
	remoteCell.put (new Point(p), new RemoteCellCoord (remoteBoard, pRemote));
    }


    // Particle name-indexing methods
    protected final void registerParticle (Particle p) {
	nameToParticle.put (p.name, p);
    }

    protected final void deregisterParticle (Particle p) {
	nameToParticle.remove (p.name);
	//	System.err.println("Deregistering " + p.name);
    }

    public final Particle getParticleByName (String name) {
	return (Particle) nameToParticle.get (name);
    }

    protected final Particle getOrCreateParticle (String name) {
	return patternSet.getOrCreateParticle (name, this);
    }

    protected Collection<Particle> knownParticles() {
	return nameToParticle.values();
    }

    // flush particle cache, and flush all particles' transformation rule & energy caches
    public void flushCaches() {
	Collection<Particle> particles = knownParticles();
	LinkedList<Particle> particlesToForget = new LinkedList<Particle>();
	for (Iterator<Particle> iter = particles.iterator(); iter.hasNext(); ) {
	    Particle p = iter.next();
	    p.flushCaches();
	    if (p.getReferenceCount() <= 0)
		particlesToForget.add(p);
	}
	for (Iterator<Particle> iter = particlesToForget.iterator(); iter.hasNext(); )
	    deregisterParticle(iter.next());
    }

    // method to init PatternSet from file
    public final void loadPatternSetFromFile(String filename) {
	patternSet = PatternSet.fromFile(filename,this);
    }

    // network helpers
    public final boolean online() { return updateServer != null; }
    public final boolean connected() { return remoteCell.size() > 0; }

    // read from image
    protected final void initFromImage (BufferedImage img, ParticleSet particleSet) {
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

    // debug
    String debugDumpStats() {
	int energyRules = 0, transRules = 0, outcomes = 0;
	for (Iterator<Particle> iter = nameToParticle.values().iterator(); iter.hasNext(); ) {
	    Particle p = iter.next();
	    energyRules += p.energyRules();
	    transRules += p.transformationRules();
	    outcomes += p.outcomes();
	}
	return nameToParticle.size() + " states, " + energyRules + " energies, " + transRules + " rules, " + outcomes + " outcomes";
    }
}

