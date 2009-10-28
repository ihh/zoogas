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
    private HashMap<Point,RemoteCellCoord> remoteCell = null;  // map of connections from off-board Point's to RemoteCellCoord's

    // cellular automata rule/particle generator
    private PatternSet patternSet = new PatternSet();

    // random number generator
    private Random rnd = null;

    // name lookups
    protected Map<String,Particle> nameToParticle = new HashMap<String,Particle>();  // updated by Particle constructor

    // networking
    private UpdateServer updateServer = null;  // UpdateServer fields UDP requests for cross-border interactions
    private ConnectionServer connectServer = null;   // ConnectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    private int boardServerPort = 4444;
    private String localhost = null;

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

    // read/write methods for cells
    public final int getCellWriteCount (Point p) {
	return cell[p.x][p.y].writeCount;
    }

    public final Particle readCell (Point p) {
	return cell[p.x][p.y].particle;
    }

    public final void writeCell (Point p, Particle pc) {
	writeCell (p, pc, readCell(p));
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

    // method to sample a random cell
    private final  void getRandomPoint (Point p) {
	p.x = rnd.nextInt(size);
	p.y = rnd.nextInt(size);
    }

    // wrapper for topology method
    private final  int getRandomNeighbor (Point p, Point n) {
	return getNeighbor(p,n,rnd.nextInt(neighborhoodSize()));
    }

    // update methods
    // getRandomPair returns dir
    public final int getRandomPair(Point p,Point n) {
	getRandomPoint(p);
	int dir = getRandomNeighbor(p,n);
	return dir;
    }

    public final void update(Point p,Point n) {
	int dir = getRandomPair(p,n);
	evolvePair(p,n,dir);
    }

    public final void update(int cycles) {
	Point p = new Point(), n = new Point();
	for (int u = 0; u < cycles; ++u)
	    update(p,n);
    }

    public final void update(int cycles,BoardRenderer renderer) {
	Point p = new Point(), n = new Point();
	for (int u = 0; u < cycles; ++u) {

	    long startTime = System.currentTimeMillis();

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

	    long timeLapsed = System.currentTimeMillis() - startTime;
	    if (timeLapsed > 20) {  // if a rule takes >20ms to complete, something's badly wrong
		System.err.println("Rule took " + timeLapsed + "ms to complete: " + oldSource.name + " " + oldTarget.name + " -> " + (newPair == null ? "[null]" : (newPair.source.name + " " + newPair.target.name)));
		//		System.err.println("Source neighborhood: " + neighborhoodDescription(p));
		//		System.err.println("Target neighborhood: " + neighborhoodDescription(n));
		//		System.err.println(debugDumpStats());
	    }
	}
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
		if (energyDeltaAcceptable(sourceCoords,targetCoords,oldSourceState,oldTargetState,newSourceState,newTargetState,energyBarrier))
		    writeCell (targetCoords, newTargetState);
		else
		    newCellPair = null;
	    }
	}

	// return
	return newCellPair;
    }

    // methods to test if a move is energetically acceptable
    public final boolean energyDeltaAcceptable (Point coords, Particle newState, double energyBarrier) {
	return energyDeltaAcceptable (null, coords, null, readCell(coords), null, newState, energyBarrier);
    }
    public final boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return energyDeltaAcceptable (sourceCoords, targetCoords, oldSourceState, oldTargetState, newSourceState, newTargetState, 0);
    }
    public final boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState, double energyBarrier) {

	double energyDelta = energyBarrier +
	    (sourceCoords == null
	     ? neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState)
	     : neighborhoodEnergyDelta(sourceCoords,targetCoords,oldSourceState,oldTargetState,newSourceState,newTargetState));

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
	return neighborhoodEnergyDelta (p, oldState, newState, null);
    }
    public final double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState, Point exclude) {
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
    public final double neighborhoodEnergyDelta (Point sourceCoords, Point targetCoords, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return
	    neighborhoodEnergyDelta(sourceCoords,oldSourceState,newSourceState,targetCoords)
	    + neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState,sourceCoords)
	    + newSourceState.symmetricPairEnergy(newTargetState) - oldSourceState.symmetricPairEnergy(oldTargetState);
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

    public final boolean onBoard (Point p) { return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size; }


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

    public final void loadPatternSetFromFile(String filename) {
	patternSet = PatternSet.fromFile(filename);
    }

    // network helpers
    public final boolean online() { return updateServer != null; }
    public final boolean connected() { return remoteCell.size() > 0; }

    // debug
    String debugDumpStats() {
	int interactions = 0, energyRules = 0, transRules = 0, outcomes = 0;
	for (Iterator<Particle> iter = nameToParticle.values().iterator(); iter.hasNext(); ) {
	    Particle p = iter.next();
	    interactions += p.interactions();
	    energyRules += p.energyRules();
	    transRules += p.transformationRules();
	    outcomes += p.outcomes();
	}
	return nameToParticle.size() + " states, " + interactions + " pairs, " + energyRules + " energies, " + transRules + " rules, " + outcomes + " outcomes";
    }
}

