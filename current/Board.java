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
    public QuadTree quad = null;

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

    // board geometry methods
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

    // method to return "taxicab" length of a vector (no diagonals allowed)
    static long taxicabLength(Point p) {
	long x = Math.abs(p.x);
	long y = Math.abs(p.y);
	return x + y;
    }

    // method to return "Moore" length of a vector (diagonals allowed)
    static long mooreLength(Point p) {
	long x = Math.abs(p.x);
	long y = Math.abs(p.y);
	return Math.max(x,y);
    }

    // method to return direct length of a vector
    static double directLength(Point p) {
	return Math.sqrt(p.x*p.x+p.y*p.y);
    }

    // method to return angle between two vectors in units of Pi, as a real number from -1 to +1
    public final double angle (Point p, Point q) {
	double a = (Math.atan2(p.y,p.x) - Math.atan2(q.y,q.x)) / Math.PI;
	if (a <= -1)
	    a += 2;
	else if (a > 1)
	    a -= 2;
	return a;
    }

    // update methods
    // getRandomPair places coordinates of a random cell in p, weighted by its update rate
    public final void getRandomCell(Point p) {
	quad.sampleQuadLeaf(p,rnd);
    }

    // getRandomPair places coordinates of a random pair in (p,n) and returns direction from p to n
    public final int getRandomPair(Point p,Point n) {
	getRandomCell(p);
	int dir = readCell(p).sampleDir(rnd);
	getNeighbor(p,n,dir);
	return dir;
    }

    // net init methods
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
	return cell[p.x][p.y].incoming.get(bond);
    }

    public Point incomingCoord (Point p, String bond) {
	Point delta = incoming(p,bond);
	if (delta != null)
	    return p.add(delta);
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
	    RemoteCellCoord remoteCoords = remoteCell.get (targetCoords);
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
		Set<Particle> actives = oldSourceState.transform.get(dir).keySet();
		for (Iterator<Particle> a = actives.iterator(); a.hasNext(); )
		    System.err.println("_ " + a.next().name);
	    }

	    double energyBarrier = -bondEnergy(sourceCoords);  // activation energy for a cross-border move involves breaking all local bonds
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
	UpdateEvent proposedUpdate = oldSourceState.samplePair (dir, oldTargetState, rnd);
	UpdateEvent acceptedUpdate = null;

	// if move is non-null, bonds match and energy difference is acceptable, then write the update
	if (proposedUpdate != null)
	    if (proposedUpdate.bindBonds(sourceCoords,targetCoords,this))
		if (acceptUpdate(proposedUpdate,energyBarrier)) {  // must call bindBonds before acceptUpdate
		    proposedUpdate.write(this);
		    acceptedUpdate = proposedUpdate;
		}

	// return
	return acceptedUpdate;
    }

    // method to accept or reject a move based on the "Hastings ratio" for a given energy delta
    public final boolean acceptUpdate (UpdateEvent e, double energyBarrier) {
	double energyDelta = energyBarrier + e.energyDelta(this);
	boolean accept = energyDelta > 0 ? true : (rnd.nextDouble() < Math.pow(10,energyDelta));
	return accept;
    }

    // method to calculate the interaction energy of a cell (p) with its bond partners, as well as the self-energy of the particle.
    // if q != null, then q will be excluded from the set of partners.
    public final double bondEnergy (Point p, Point q, Particle pState, Map<String,Point> incoming, Map<String,Point> outgoing) {
	double E = pState.energy;
	// chain is m->n->p->r->s
	Point m = new Point(), n = new Point(), r = new Point(), s = new Point();
	Point m2n = new Point(), n2p = new Point();
	if (incoming != null)
	    for (Iterator<Map.Entry<String,Point>> iter = incoming.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		String bondName = kv.getKey();
		Point p2n = kv.getValue();
		p.add(p2n,n);
		p2n.multiply(-1,n2p);
		if (onBoard(n) && (q == null || !n.equals(q))) {
		    Point n2m = incoming(n,bondName);
		    if (n2m != null)
			n2m.multiply(-1,m2n);
		    E += patternSet.getEnergy(readCell(n).name,pState.name,bondName,n2p,n2m==null?null:m2n);
		}
	    }
	if (outgoing != null)
	    for (Iterator<Map.Entry<String,Point>> iter = outgoing.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		String bondName = kv.getKey();
		Point p2r = kv.getValue();
		p.add(p2r,r);
		if (onBoard(r) && (q == null || !r.equals(q))) {
		    Point p2n = null;
		    if (incoming != null) {
			p2n = incoming.get(bondName);
			if (p2n != null)
			    p2n.multiply(-1,n2p);
		    }
		    E += patternSet.getEnergy(pState.name,readCell(r).name,bondName,p2r,p2n==null?null:n2p);
		    Point r2s = outgoing(r,bondName);
		    if (r2s != null) {
			r.add(r2s,s);
			if (onBoard(s) && (q == null || !s.equals(q))) {
			    E += patternSet.getEnergy(readCell(r).name,readCell(s).name,bondName,r2s,p2r);
			}
		    }
		}
	    }
	return E;
    }

    // wrapper for bondEnergy with no excluded point
    public final double bondEnergy (Point p, Particle pState, Map<String,Point> incoming, Map<String,Point> outgoing) {
	return bondEnergy(p,null,pState,incoming,outgoing);
    }

    // method to calculate the bond energy of two cells with given states and bonds, as well as the self-energies of the two particles.
    public final double bondEnergy (Point p, Point q, Particle pState, Particle qState, Map<String,Point> pIn, Map<String,Point> pOut, Map<String,Point> qIn, Map<String,Point> qOut) {
	// chain is n->p->q->r
	double E = 0;
	Point n = new Point(), qMaybe = new Point(), r = new Point();
	Point n2p = new Point(), p2q = new Point();
	q.subtract(p,p2q);
	if (pOut != null)
	    for (Iterator<Map.Entry<String,Point>> iter = pOut.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Point> kv = iter.next();
		String bondName = kv.getKey();
		Point p2qMaybe = kv.getValue();
		p.add(p2qMaybe,qMaybe);
		if (qMaybe.equals(q)) {
		    Point p2n = null;
		    if (pIn != null) {
			p2n = pIn.get(bondName);
			if (p2n != null) {
			    p.add(p2n,n);
			    p2n.multiply(-1,n2p);
			}
		    }
		    E += patternSet.getEnergy(pState.name,qState.name,bondName,p2q,p2n==null?null:n2p);
		    if (qOut != null) {
			Point q2r = qOut.get(bondName);
			if (q2r != null) {
			    q.add(q2r,r);
			    if (onBoard(r))
				E += patternSet.getEnergy(qState.name,readCell(r).name,bondName,q2r,p2q);
			}
		    }
		}
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
	    sb.append(readCell(p).name);
	for (Iterator<Map.Entry<String,Point>> iter = incoming(p).entrySet().iterator(); iter.hasNext(); ) {
	    Map.Entry<String,Point> kv = iter.next();
	    sb.append(" <"+vectorString(kv.getValue())+":"+kv.getKey());
	}
	for (Iterator<Map.Entry<String,Point>> iter = outgoing(p).entrySet().iterator(); iter.hasNext(); ) {
	    Map.Entry<String,Point> kv = iter.next();
	    sb.append(" >"+vectorString(kv.getValue())+":"+kv.getKey());
	}
	return sb.toString();
    }

    // method returning a description of a two-cell neighborhood (including incoming & outgoing bonds) as a String
    public final String pairNeighborhoodDescription(Point p,Point q) {
	return singleNeighborhoodDescription(p,false) + " + " + singleNeighborhoodDescription(q,true);
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
	System.err.println("Connecting " + p + " to " + pRemote + " on " + remoteBoard);
	Point q = new Point(p);
	Point r = new Point(p);
	remoteCell.put (q, new RemoteCellCoord (remoteBoard, pRemote));
	/*
	    System.err.println(q+" "+q.hashCode());
	    System.err.println(r+" "+r.hashCode());
	    System.err.println(q.equals(r) ? "equal" : "inequal");
	    System.err.println(remoteCell.get(q)!=null ? "q stored" : "q lost");
	    System.err.println(remoteCell.get(r)!=null ? "r found" : "r not found");
	*/
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

