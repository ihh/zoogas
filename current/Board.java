import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.Color;
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
    QuadTree quad = null;

    // constructor
    public Board (int size) {
	this.size = size;
	rnd = new Random();
	cell = new Cell[size][size];
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		cell[x][y] = new Cell();

	// quad tree
	quad = new QuadTree(size);

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
    }

    private final void writeCell (Point p, Particle pc, Particle old_pc) {
	if (old_pc != pc) {
	    cell[p.x][p.y].particle = pc;
	    ++cell[p.x][p.y].writeCount;
	    if (old_pc != null)
		old_pc.decReferenceCount();
	    pc.incReferenceCount();
	    quad.updateQuadTree (p, pc.normalizedTotalTransformRate());
	}
    }

    // bond accessors
    public Map<String,Point> incoming (Point p) {
	return cell[p.x][p.y].incoming;
    }

    public Map<String,Point> outgoing (Point p) {
	return cell[p.x][p.y].outgoing;
    }

    public Point incoming (Point p, String bond) {
	if (cell[p.x][p.y].incoming.containsKey(bond)) {
	    Point delta = cell[p.x][p.y].incoming.get(bond);
	    Point q = p.add(delta);
	    return q;
	}
	return null;
    }

    public Point outgoing (Point p, String bond) {
	if (cell[p.x][p.y].outgoing.containsKey(bond)) {
	    Point delta = cell[p.x][p.y].outgoing.get(bond);
	    Point q = p.add(delta);
	    return q;
	}
	return null;
    }

    public void removeBonds (Point p) {
	Map<String,Point> in = incoming(p), out = outgoing(p);
	if (in.size() > 0 || out.size() > 0) {
	    Point q = new Point();
	    for (Iterator<Map.Entry<String,Point>> iter = in.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		p.add(kv.getValue(),q);
		if (onBoard(q))
		    outgoing(q).remove(kv.getKey());
		//		System.err.println("Removing bond "+kv.getKey()+" from "+q+" to "+p);
	    }
	    in.clear();
	    for (Iterator<Map.Entry<String,Point>> iter = out.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		p.add(kv.getValue(),q);
		if (onBoard(q))
		    incoming(q).remove(kv.getKey());
		//		System.err.println("Removing bond "+kv.getKey()+" from "+p+" to "+q);
	    }
	    out.clear();
	}
    }

    public void addBond (Point p, Point q, String bond) {
	int ns = neighborhoodSize();
	int dir = getNeighborDirection(p,q);
	int rev = reverseDir(dir);
	outgoing(p).put(bond,q.subtract(p));
	incoming(q).put(bond,p.subtract(q));
	//	System.err.println("Adding bond "+bond+" from "+p+" to "+q);
    }

    public void addIncoming (Point p, Map<String,Point> bondDir) {
	if (bondDir != null && bondDir.size() > 0) {
	    Point q = new Point();
	    for (Iterator<Map.Entry<String,Point>> iter = bondDir.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		p.add(kv.getValue(),q);
		if (onBoard(q))
		    addBond(q,p,kv.getKey());
	    }
	}
    }

    public void addOutgoing (Point p, Map<String,Point> bondDir) {
	if (bondDir != null && bondDir.size() > 0) {
	    Point q = new Point();
	    for (Iterator<Map.Entry<String,Point>> iter = bondDir.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		p.add(kv.getValue(),q);
		if (onBoard(q))
		    addBond(p,q,kv.getKey());
	    }
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

    // helper to get direction (quick implementation; reimplement in superclass for performance optimization)
    public int getNeighborDirection(Point p,Point q) {
	Point n = new Point();
	int ns = neighborhoodSize();
	for (int dir = 0; dir < ns; ++dir) {
	    getNeighbor(p,n,dir);
	    if (n.x == q.x && n.y == q.y)
		return dir;
	}
	return -1;
    }

    // helper to reverse direction
    public int reverseDir(int dir) {
	int ns = neighborhoodSize();
	return (dir + (ns >> 1)) % ns;
    }

    // helper to turn a vector into a string
    public String vectorString(Point delta) {
	StringBuffer sb = new StringBuffer();
	if (delta.y > 0)
	    sb.append("s" + (delta.y > 1 ? delta.y : ""));
	else if (delta.y < 0)
	    sb.append("n" + (delta.y < -1 ? -delta.y : ""));
	if (delta.x > 0)
	    sb.append("e" + (delta.x > 1 ? delta.x : ""));
	else if (delta.x < 0)
	    sb.append("w" + (delta.x < -1 ? -delta.x : ""));
	else if (delta.y == 0)  // delta.x == 0 && delta.y == 0
	    sb.append("0");
	return sb.toString();
    }

    // update methods
    // getRandomPair places coordinates of a random pair in (p,n) and returns direction from p to n
    public final int getRandomPair(Point p,Point n) {
	quad.sampleQuadLeaf(p,rnd);
	int dir = readCell(p).sampleDir(rnd);
	getNeighbor(p,n,dir);
	return dir;
    }

    // update()
    public final void update(double boardUpdates,BoardRenderer renderer) {
	int updatedCells = 0;
	Point p = new Point(), n = new Point();
	double maxUpdates = boardUpdates * quad.topQuadRate();
	for (; updatedCells < maxUpdates; ++updatedCells) {

	    int dir = getRandomPair(p,n);
	    Particle oldSource = readCell(p);
	    Particle oldTarget = onBoard(n) ? readCell(n) : null;
	    UpdateEvent newPair = evolvePair(p,n,dir);
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
    }

    // evolvePair(sourceCoords,targetCoords,dir) : delegate to appropriate evolve* method.
    // in what follows, one cell is designated the "source", and its neighbor is the "target".
    // "dir" is the direction from source to target.
    // returns a UpdateEvent describing the new state and verb (may be null).
    private final UpdateEvent evolvePair (Point sourceCoords, Point targetCoords, int dir)
    {
	UpdateEvent pp = null;
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
		System.err.println("Oops, this can't be good: empty space (_) is active. Rules:");
		Set<String> actives = oldSourceState.transform.get(dir).keySet();
		for (Iterator<String> a = actives.iterator(); a.hasNext(); )
		    System.err.println("_ " + a.next());
	    }

	    double energyBarrier = -bondEnergy(sourceCoords);
	    BoardServer.sendEvolveDatagram (remoteCoords.addr, remoteCoords.port, remoteCoords.p, oldSourceState, sourceCoords, dir, energyBarrier, localhost, boardServerPort, getCellWriteCount(sourceCoords));
	}
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalSourceAndLocalTarget : handle entirely local updates. Strictly in the family, folks
    // returns a UpdateEvent
    synchronized public final UpdateEvent evolveLocalSourceAndLocalTarget (Point sourceCoords, Point targetCoords, int dir)
    {
	return evolveTargetForSource(sourceCoords,targetCoords,readCell(sourceCoords),dir,0);
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalTargetForRemoteSource : handle a remote request for update.
    // Return the new source state (the caller of this method will send this returned state back over the network as a RETURN datagram).
    synchronized public final Particle evolveLocalTargetForRemoteSource (Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	UpdateEvent pp = evolveTargetForSource(null,targetCoords,oldSourceState,dir,energyBarrier);
	return pp == null ? oldSourceState : pp.source;
    }

    // evolveTargetForSource : given a source state, and the co-ords of a target cell,
    // sample the new (source,target) state configuration,
    // write the updated target, and return the updated (source,target) pair.
    // The source cell coords are provided, but may be null if the source cell is off-board.
    public final UpdateEvent evolveTargetForSource (Point sourceCoords, Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	// get old state-pair
	Particle oldTargetState = readCell (targetCoords);

	// sample new state-pair
	UpdateEvent newCellPair = oldSourceState.samplePair (dir, oldTargetState, rnd, sourceCoords, targetCoords);

	if (newCellPair != null) {
	    Particle newSourceState = newCellPair.source;
	    Particle newTargetState = newCellPair.target;
	    // test for null
	    if (newSourceState == null || newTargetState == null) {
		throw new RuntimeException ("Null outcome of rule: " + oldSourceState.name + " " + oldTargetState.name + " -> " + (newSourceState == null ? "[null]" : newSourceState.name) + " " + (newTargetState == null ? "[null]" : newTargetState.name));
	    } else {
		// update particles and bonds
		//		System.err.println("Firing rule "+newCellPair.verb+" from "+sourceCoords+" to "+targetCoords);
		removeBonds(targetCoords);
		if (onBoard(sourceCoords)) {
		    removeBonds(sourceCoords);
		    writeCell (sourceCoords, newSourceState, oldSourceState);
		    addIncoming(sourceCoords,newCellPair.sIncoming);
		    addOutgoing(sourceCoords,newCellPair.sOutgoing);
		}
		writeCell (targetCoords, newTargetState, oldTargetState);
		addIncoming(targetCoords,newCellPair.tIncoming);
		addOutgoing(targetCoords,newCellPair.tOutgoing);
	    }
	}

	// return
	return newCellPair;
    }

    // methods to return the "Hastings ratio" for a given energy delta
    public final double hastingsRatio (double energyDelta) {
	return
	    energyDelta > 0
	    ? 1
	    : Math.pow(10,energyDelta);
    }

    // method to calculate the interaction energy of a cell with its bond partners.
    // if q != null, then q will be excluded from the set of partners.
    public final double bondEnergy (Point p, Point q, Particle pState, Map<String,Point> incoming, Map<String,Point> outgoing) {
	double E = 0;
	Point n = new Point();
	if (incoming != null)
	    for (Iterator<Map.Entry<String,Point>> iter = incoming.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		Point delta = kv.getValue();
		p.add(delta,n);
		if (onBoard(n) && (q == null || !n.equals(q)))
		    E += patternSet.getEnergy(readCell(n).name,pState.name,kv.getKey(),delta);
	    }
	if (outgoing != null)
	    for (Iterator<Map.Entry<String,Point>> iter = outgoing.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		Point delta = kv.getValue();
		p.add(delta,n);
		if (onBoard(n) && (q == null || !n.equals(q)))
		    E += patternSet.getEnergy(pState.name,readCell(n).name,kv.getKey(),delta);
	    }
	return E;
    }

    // method to calculate the bond energy of two cells.
    public final double bondEnergy (Point p, Point q, Particle pState, Particle qState, Map<String,Point> pIn, Map<String,Point> pOut, Map<String,Point> qIn, Map<String,Point> qOut) {
	double E = 0;
	Point n = new Point();
	if (pOut != null)
	    for (Iterator<Map.Entry<String,Point>> iter = pOut.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		Point delta = kv.getValue();
		p.add(delta,n);
		if (n.equals(q))
		    E += patternSet.getEnergy(pState.name,qState.name,kv.getKey(),delta);
	    }
	return E + bondEnergy(p,q,pState,pIn,pOut) + bondEnergy(q,p,qState,qIn,qOut);
    }

    // bondEnergy wrappers that read incoming & outgoing bond sets from the Board
    public final double bondEnergy (Point p) {
	return bondEnergy (p, null, readCell(p), incoming(p), outgoing(p));
    }

    public final double bondEnergy (Point p, Point q) {
	return bondEnergy (p, q, readCell(p), readCell(q), incoming(p), outgoing(p), incoming(q), outgoing(q));
    }

    // method returning a description of a cell neighborhood (including incoming & outgoing bonds) as a String
    protected final String singleNeighborhoodDescription(Point p,boolean includeSelf) {
	StringBuffer sb = new StringBuffer();
	if (includeSelf)
	    sb.append(readCell(p).name+" ");
	for (Iterator<Map.Entry<String,Point>> iter = incoming(p).entrySet().iterator(); iter.hasNext(); ) {
	    Map.Entry<String,Point> kv = iter.next();
	    sb.append("<"+vectorString(kv.getValue())+":"+kv.getKey());
	}
	for (Iterator<Map.Entry<String,Point>> iter = outgoing(p).entrySet().iterator(); iter.hasNext(); ) {
	    Map.Entry<String,Point> kv = iter.next();
	    sb.append(">"+vectorString(kv.getValue())+":"+kv.getKey());
	}
	return sb.toString();
    }

    // method returning a description of a two-cell neighborhood (including incoming & outgoing bonds) as a String
    public final String pairNeighborhoodDescription(Point p,Point q) {
	return singleNeighborhoodDescription(p,false) + "+" + singleNeighborhoodDescription(q,true);
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
	int transRules = 0, outcomes = 0;
	for (Iterator<Particle> iter = nameToParticle.values().iterator(); iter.hasNext(); ) {
	    Particle p = iter.next();
	    transRules += p.transformationRules();
	    outcomes += p.outcomes();
	}
	return nameToParticle.size() + " states, " + transRules + " rules, " + outcomes + " outcomes";
    }
}

