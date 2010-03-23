import java.lang.*;

import java.util.*;

import java.text.*;

import java.awt.Color;
import java.awt.image.*;

import java.net.*;

import java.io.*;

import java.util.concurrent.ConcurrentHashMap;

public class Board extends MooreTopology {
    public int size = 0; // size of board in cells

    // main board data
    private Cell[][] cell = null;

    // cellular automata rule/particle generator
    private PatternSet patternSet = null;

    // particle name registry
    protected Map<String, Particle> nameToParticle = new ConcurrentHashMap<String, Particle>(); // updated by Particle constructor
    protected Map<String, SortedSet<Particle>> prefixToParticles = new ConcurrentHashMap<String, SortedSet<Particle>>(); // updated by Particle constructor

    // off-board connections
    private HashMap<Point, RemoteCellCoord> remoteCell = null; // map of connections from off-board Point's to RemoteCellCoord's

    // networking
    private UpdateServer updateServer = null; // UpdateServer fields UDP requests for cross-border interactions
    private ConnectionServer connectServer =
        null; // ConnectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    private int boardServerPort = 4444;
    private String localhost = null;
    private ClientToServer toWorldServer;

    // fast quad tree
    public QuadTree quad = null;

    // constructor
    public Board(int size) {
        this.size = size;
        cell = new Cell[size][size];
        for (int x = 0; x < size; ++x)
            for (int y = 0; y < size; ++y)
                cell[x][y] = new Cell();

        // quad tree
        quad = new QuadTree(size);

        // net init
        remoteCell = new HashMap<Point, RemoteCellCoord>();
        try {
            localhost = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // board geometry methods
    // helper to test if a cell is on board
    public final boolean onBoard(Point p) {
        return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size;
    }

    // helper to get direction (quick implementation; reimplement in superclass for performance optimization)
    public int getNeighborDirection(Point p, Point q) {
        Point n = new Point();
        int ns = neighborhoodSize();
        for (int dir = 0; dir < ns; ++dir) {
            getNeighbor(p, n, dir);
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
        else if (delta.y == 0) // delta.x == 0 && delta.y == 0
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
        return Math.max(x, y);
    }

    // scheduling methods
    // gotUpdates() is true if total board update rate is >0
    public final boolean gotUpdates() {
	return quad.topQuadRate() > 0;
    }

    // getWaitTime: returns wait time to next event
    public final double getWaitTime() {
	return -Math.log(Math.random()) / quad.topQuadRate();
    }

    // getRandomPair places coordinates of a random cell in p, sampled proportionally to its update rate
    public final void getRandomCell(Point p) {
        quad.sampleQuadLeaf(p);
    }

    // getRandomPair places coordinates of a random pair in (p,n) and returns direction from p to n
    public final int getRandomPair(Point p, Point n) {
        getRandomCell(p);
        int dir = readCell(p).sampleDir();
        getNeighbor(p, n, dir);
        return dir;
    }

    // net init methods
    public final void initServer(int port, ZooGas gas) {
        this.boardServerPort = port;
        toWorldServer = gas.getWorldServerThread();

        try {
            updateServer = new UpdateServer(this, boardServerPort, gas.renderer);
            updateServer.start();

            connectServer = new ConnectionServer(this, boardServerPort);
            connectServer.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final void initClient(InetSocketAddress remote) {
        connectBorderInDirection(0, remote);
        connectBorderInDirection(1, remote);
        connectBorderInDirection(2, remote);
        connectBorderInDirection(3, remote);
    }

    // read/write methods for cells
    public final int getCellWriteCount(Point p) {
        return cell[p.x][p.y].writeCount;
    }

    public final Particle readCell(Point p) {
        return cell[p.x][p.y].particle;
    }

    public final void writeCell(Point p, Particle pc) {
        writeCell(p, pc, readCell(p));
    }

    private final void writeCell(Point p, Particle pc, Particle old_pc) {
        if (old_pc != pc) {
            cell[p.x][p.y].particle = pc;
            ++cell[p.x][p.y].writeCount;
            if (old_pc != null)
                old_pc.removeReference(new Point(p));
            pc.addReference(new Point(p)); // Note: must be new point!
            quad.updateQuadTree(new Point(p), pc.normalizedTotalTransformRate());
        }
    }

    // bond accessors
    public Map<String, Point> incoming(Point p) {
        return cell[p.x][p.y].incoming;
    }

    public Map<String, Point> outgoing(Point p) {
        return cell[p.x][p.y].outgoing;
    }

    public Point incoming(Point p, String bond) {
        return cell[p.x][p.y].incoming.get(bond);
    }

    public Point incomingCoord(Point p, String bond) {
        Point delta = incoming(p, bond);
        if (delta != null)
            return p.add(delta);
        return null;
    }

    public Point outgoing(Point p, String bond) {
        if (cell[p.x][p.y].outgoing.containsKey(bond)) {
            Point delta = cell[p.x][p.y].outgoing.get(bond);
            Point q = p.add(delta);
            return q;
        }
        return null;
    }

    public void removeBonds(Point p) {
        Map<String, Point> in = incoming(p), out = outgoing(p);
        if (in.size() > 0 || out.size() > 0) {
            Point q;
            for (Map.Entry<String, Point> kv : in.entrySet()) {
                q = p.add(kv.getValue());
                if (onBoard(q))
                    outgoing(q).remove(kv.getKey());
                //		System.err.println("Removing bond "+kv.getKey()+" from "+q+" to "+p);
            }
            in.clear();
            for (Map.Entry<String, Point> kv : out.entrySet()) {
                q = p.add(kv.getValue());
                if (onBoard(q))
                    incoming(q).remove(kv.getKey());
                //		System.err.println("Removing bond "+kv.getKey()+" from "+p+" to "+q);
            }
            out.clear();
        }
    }

    public void addBond(Point p, Point q, String bond) {
        int ns = neighborhoodSize();
        int dir = getNeighborDirection(p, q);
        int rev = reverseDir(dir);
        outgoing(p).put(bond, q.subtract(p));
        incoming(q).put(bond, p.subtract(q));
        //	System.err.println("Adding bond "+bond+" from "+p+" to "+q);
    }

    public void addIncoming(Point p, Map<String, Point> bondDir) {
        if (bondDir != null && bondDir.size() > 0) {
            for (Map.Entry<String, Point> kv : bondDir.entrySet()) {
                Point q = p.add(kv.getValue());
                if (onBoard(q))
                    addBond(q, p, kv.getKey());
            }
        }
    }

    public void addOutgoing(Point p, Map<String, Point> bondDir) {
        if (bondDir != null && bondDir.size() > 0) {
            for (Map.Entry<String, Point> kv : bondDir.entrySet()) {
                Point q = p.add(kv.getValue());
                if (onBoard(q))
                    addBond(p, q, kv.getKey());
            }
        }
    }

    // fill/init method
    public final void fill(Particle particle) {
        for (int x = 0; x < size; ++x) {
            for (int y = 0; y < size; ++y) {
                Point p = new Point(x, y);
                writeCell(p, particle);
            }
        }
    }

    // update()
    public final void update(double maxTime, BoardRenderer renderer) {
	double t = 0;
	while (gotUpdates()) {
	    t += getWaitTime();
	    if (t >= maxTime)
		break;

            Point p = new Point(), n = new Point(); // Must stay inside the loop; Points are stored (as Particles)
            int dir = getRandomPair(p, n);
            Particle oldSource = readCell(p);
            Particle oldTarget = onBoard(n) ? readCell(n) : null;
            UpdateEvent newPair = evolvePair(p, n, dir);
            if (newPair != null) {
                Particle newSource = newPair.source;
                Particle newTarget = newPair.target;

                if (newSource != oldSource)
                    renderer.drawCell(p);

                if (onBoard(n) && newTarget != oldTarget)
                    renderer.drawCell(n);

                if (newPair.verb != null)
                    renderer.showVerb(newPair);
            }
        }
    }

    // evolvePair(sourceCoords,targetCoords,dir) : delegate to appropriate evolve* method.
    // in what follows, one cell is designated the "source", and its neighbor is the "target".
    // "dir" is the direction from source to target.
    // returns a UpdateEvent describing the new state and verb (may be null).
    private final UpdateEvent evolvePair(Point sourceCoords, Point targetCoords, int dir) {
        UpdateEvent pp = null;
        if (onBoard(targetCoords)) {
            pp = evolveLocalSourceAndLocalTarget(sourceCoords, targetCoords, dir);
        } else {
            // request remote evolveLocalTargetForRemoteSource
            RemoteCellCoord remoteCoords = remoteCell.get(targetCoords);
            if (remoteCoords != null)
                evolveLocalSourceAndRemoteTarget(sourceCoords, remoteCoords, dir);
        }
        return pp;
    }

    // evolveLocalSourceAndRemoteTarget: send an EVOLVE datagram to the network address of a remote cell.
    protected final void evolveLocalSourceAndRemoteTarget(Point sourceCoords, RemoteCellCoord remoteCoords, int dir) {
        Particle oldSourceState = readCell(sourceCoords);

        if (oldSourceState.isActive(dir)) {

            if (oldSourceState.name.equals("_")) {
                System.err.println("Oops, this can't be good: empty space (_) is active. Rules:");
                Set<Particle> actives = oldSourceState.transform.get(dir).keySet();
                for (Particle a : actives)
                    System.err.println("_ " + a.name);
            }

            double energyBarrier = -bondEnergy(sourceCoords); // activation energy for a cross-border move involves breaking all local bonds
            connectServer.sendEvolveDatagram(remoteCoords.addr, remoteCoords.port, remoteCoords.p, oldSourceState, sourceCoords, dir, energyBarrier, localhost,
                                             boardServerPort, getCellWriteCount(sourceCoords));
        }
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalSourceAndLocalTarget : handle entirely local updates. Strictly in the family, folks
    // returns a UpdateEvent
    synchronized public final UpdateEvent evolveLocalSourceAndLocalTarget(Point sourceCoords, Point targetCoords, int dir) {
        return evolveTargetForSource(sourceCoords, targetCoords, readCell(sourceCoords), dir, 0);
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalTargetForRemoteSource : handle a remote request for update.
    // Return the new source state (the caller of this method will send this returned state back over the network as a RETURN datagram).
    synchronized public final Particle evolveLocalTargetForRemoteSource(Point targetCoords, Particle oldSourceState, int dir, double energyBarrier) {
        UpdateEvent pp = evolveTargetForSource(null, targetCoords, oldSourceState, dir, energyBarrier);
        return pp == null ? oldSourceState : pp.source;
    }

    // evolveTargetForSource : given a source state, and the co-ords of a target cell,
    // sample the new (source,target) state configuration,
    // write the updated target, and return the updated (source,target) pair.
    // The source cell coords are provided, but may be null if the source cell is off-board.
    public final UpdateEvent evolveTargetForSource(Point sourceCoords, Point targetCoords, Particle oldSourceState, int dir, double energyBarrier) {
        // get old state-pair
        Particle oldTargetState = readCell(targetCoords);

        // sample new state-pair
        UpdateEvent proposedUpdate = oldSourceState.samplePair(dir, oldTargetState);
        UpdateEvent acceptedUpdate = null;

        // if move is non-null, bonds match and energy difference is acceptable, then write the update
        if (proposedUpdate != null)
            if (proposedUpdate.bindBonds(sourceCoords, targetCoords, this))
                if (acceptUpdate(proposedUpdate, energyBarrier)) { // must call bindBonds before acceptUpdate
                    proposedUpdate.write(this);
                    acceptedUpdate = proposedUpdate;
                }

        // return
        return acceptedUpdate;
    }

    // method to accept or reject a move based on the "Hastings ratio" for a given energy delta
    public final boolean acceptUpdate(UpdateEvent e, double energyBarrier) {
        double energyDelta = energyBarrier + e.energyDelta(this);
        boolean accept = energyDelta > 0 ? true : (Math.random() < Math.pow(10, energyDelta));
        return accept;
    }

    // method to calculate the interaction energy of a cell (p) with its bond partners, as well as the self-energy of the particle.
    // if q != null, then q will be excluded from the set of partners.
    public final double bondEnergy(Point p, Point q, Particle pState, Map<String, Point> incoming, Map<String, Point> outgoing) {
        double E = pState.energy;
        // chain is m->n->p->r->s
        Point m = new Point();
        Point n2p = null;
        if (incoming != null)
            for (Map.Entry<String, Point> kv : incoming.entrySet()) {
                String bondName = kv.getKey();
                Point p2n = kv.getValue();
                Point n = p.add(p2n);
                n2p = p2n.multiply(-1);
                if (onBoard(n) && (q == null || !n.equals(q))) {
                    Point n2m = incoming(n, bondName);
                    Point m2n = null;
                    if (n2m != null)
                        m2n = n2m.multiply(-1);
                    E += patternSet.getEnergy(readCell(n).name, pState.name, bondName, n2p, n2m == null ? null : m2n);
                }
            }
        if (outgoing != null)
            for (Map.Entry<String, Point> kv : outgoing.entrySet()) {
                String bondName = kv.getKey();
                Point p2r = kv.getValue();
                Point r = p.add(p2r);
                if (onBoard(r) && (q == null || !r.equals(q))) {
                    Point p2n = null;
                    if (incoming != null) {
                        p2n = incoming.get(bondName);
                        if (p2n != null)
                            n2p = p2n.multiply(-1);
                    }
                    E += patternSet.getEnergy(pState.name, readCell(r).name, bondName, p2r, p2n == null ? null : n2p);
                    Point r2s = outgoing(r, bondName);
                    if (r2s != null) {
                        Point s = r.add(r2s);
                        if (onBoard(s) && (q == null || !s.equals(q))) {
                            E += patternSet.getEnergy(readCell(r).name, readCell(s).name, bondName, r2s, p2r);
                        }
                    }
                }
            }
        return E;
    }

    // wrapper for bondEnergy with no excluded point
    public final double bondEnergy(Point p, Particle pState, Map<String, Point> incoming, Map<String, Point> outgoing) {
        return bondEnergy(p, null, pState, incoming, outgoing);
    }

    // method to calculate the bond energy of two cells with given states and bonds, as well as the self-energies of the two particles.
    public final double bondEnergy(Point p, Point q, Particle pState, Particle qState, Map<String, Point> pIn, Map<String, Point> pOut, Map<String, Point> qIn,
                                   Map<String, Point> qOut) {
        // chain is n->p->q->r
        double E = 0;
        Point p2q = q.subtract(p);
        if (pOut != null)
            for (Map.Entry<String, Point> kv : pOut.entrySet()) {
                String bondName = kv.getKey();
                Point p2qMaybe = kv.getValue();
                Point qMaybe = p.add(p2qMaybe);
                if (qMaybe.equals(q)) {
                    Point p2n = null;
                    Point n2p = null;
                    if (pIn != null) {
                        p2n = pIn.get(bondName);
                        if (p2n != null) {
                            Point n = p.add(p2n);
                            n2p = p2n.multiply(-1);
                        }
                    }
                    E += patternSet.getEnergy(pState.name, qState.name, bondName, p2q, p2n == null ? null : n2p);
                    if (qOut != null) {
                        Point q2r = qOut.get(bondName);
                        if (q2r != null) {
                            Point r = q.add(q2r);
                            if (onBoard(r))
                                E += patternSet.getEnergy(qState.name, readCell(r).name, bondName, q2r, p2q);
                        }
                    }
                }
            }
        return E + bondEnergy(p, q, pState, pIn, pOut) + bondEnergy(q, p, qState, qIn, qOut);
    }

    // bondEnergy wrappers that read incoming & outgoing bond sets from the Board
    public final double bondEnergy(Point p) {
        return bondEnergy(p, null, readCell(p), incoming(p), outgoing(p));
    }

    public final double bondEnergy(Point p, Point q) {
        return bondEnergy(p, q, readCell(p), readCell(q), incoming(p), outgoing(p), incoming(q), outgoing(q));
    }

    // method returning a description of a cell neighborhood (including incoming & outgoing bonds) as a String
    protected final String singleNeighborhoodDescription(Point p, boolean includeSelf) {
        StringBuffer sb = new StringBuffer();
        if (includeSelf)
            sb.append(readCell(p).name);
        for (Map.Entry<String, Point> kv : incoming(p).entrySet()) {
            sb.append(" <" + vectorString(kv.getValue()) + ":" + kv.getKey());
        }
        for (Map.Entry<String, Point> kv : outgoing(p).entrySet()) {
            sb.append(" >" + vectorString(kv.getValue()) + ":" + kv.getKey());
        }
        return sb.toString();
    }

    // method returning a description of a two-cell neighborhood (including incoming & outgoing bonds) as a String
    public final String pairNeighborhoodDescription(Point p, Point q) {
        return singleNeighborhoodDescription(p, false) + " + " + singleNeighborhoodDescription(q, true);
    }

    // method to send requests to establish two-way network connections between cells
    // (called in the client during initialization)
    protected final void connectBorder(Point sourceStart, Point targetStart, Point lineVector, int lineLength, Point remoteOrigin,
                                     InetSocketAddress remoteBoard) {
        String[] connectRequests = new String[lineLength];
        Point source = new Point(sourceStart);
        Point target = new Point(targetStart);
        for (int i = 0; i < lineLength; ++i) {
            Point remoteSource = new Point(source.x - remoteOrigin.x, source.y - remoteOrigin.y);
            Point remoteTarget = new Point(target.x - remoteOrigin.x, target.y - remoteOrigin.y);

            addRemoteCellCoord(target, remoteBoard, remoteTarget);
            connectRequests[i] = BoardServer.connectString(remoteSource, source, localhost, boardServerPort);

            source.x += lineVector.x;
            source.y += lineVector.y;

            target.x += lineVector.x;
            target.y += lineVector.y;
        }

        BoardServer.sendTCPPacket(remoteBoard.getAddress(), remoteBoard.getPort(), connectRequests);
    }
    
    protected final void connectBorderInDirection(int dir, InetSocketAddress remote) {
        switch(dir) {
            case 0:
                connectBorder(new Point(0, 127), new Point(0, 128), new Point(1, 0), 128, new Point(0, +size), remote); // north
                break;
            case 1:
                connectBorder(new Point(127, 0), new Point(128, 0), new Point(0, 1), 128, new Point(+size, 0), remote); // east
                break;
            case 2:
                connectBorder(new Point(0, 0), new Point(0, -1), new Point(1, 0), 128, new Point(0, -size), remote); // south
                break;
            case 3:
                connectBorder(new Point(0, 0), new Point(-1, 0), new Point(0, 1), 128, new Point(-size, 0), remote); // west
                break;
        }
    }

    protected final void addRemoteCellCoord(Point p, InetSocketAddress remoteBoard, Point pRemote) {
        Point q = new Point(p);
        Point r = new Point(p);
        remoteCell.put(q, new RemoteCellCoord(remoteBoard, pRemote));
        /*
	    System.err.println(q+" "+q.hashCode());
	    System.err.println(r+" "+r.hashCode());
	    System.err.println(q.equals(r) ? "equal" : "inequal");
	    System.err.println(remoteCell.get(q)!=null ? "q stored" : "q lost");
	    System.err.println(remoteCell.get(r)!=null ? "r found" : "r not found");
	*/
    }

    // Particle name-indexing methods
    protected final void registerParticle(Particle p) {
        nameToParticle.put(p.name, p);
        SortedSet<Particle> particles;
        if(!prefixToParticles.containsKey(p.prefix)) {
            particles = new TreeSet<Particle>();
            prefixToParticles.put(p.prefix, particles);
        }
        else{
            particles = prefixToParticles.get(p.prefix);
        }

        particles.add(p);
    }

    protected final void deregisterParticle(Particle p) {
        nameToParticle.remove(p.name);
        SortedSet<Particle> prefixSet = prefixToParticles.get(p.prefix);
        if(prefixSet != null) {
            prefixSet.remove(p);
            if(prefixSet.size() != 0)
                prefixToParticles.remove(p.prefix);
        }
        else {
            System.err.println("Warning: found no prefix set for " + p.name);
        }
        System.err.println("Deregistering " + p.name);
    }

    public final Particle getParticleByName(String name) {
        return nameToParticle.get(name);
    }

    public final boolean gotPrefix(String prefix) {
	return prefixToParticles.containsKey(prefix);
    }

    public final Set<Particle> getParticlesByPrefix(String prefix) {
        if(prefixToParticles.containsKey(prefix))
            return prefixToParticles.get(prefix);
        System.err.println(prefix+" particles are not defined!");
        return new HashSet<Particle>();
    }

    protected final Particle getOrCreateParticle(String name) {
        return patternSet.getOrCreateParticle(name, this);
    }

    protected Collection<Particle> knownParticles() {
        return nameToParticle.values();
    }

    // flush particle cache, and flush all particles' transformation rule & energy caches
    public void flushCaches() {
        for (Particle p : knownParticles()) {
            if (p.getReferenceCount() <= 0)
                deregisterParticle(p);
        }
    }

    // method to init PatternSet from file
    public final void loadPatternSetFromFile(String filename) {
        patternSet = PatternSet.fromFile(filename, this);

        if (toWorldServer != null)
            toWorldServer.sendAllClientRules(patternSet, patternSet.getByteSize());
    }

    // network helpers
    public final boolean online() {
        return updateServer != null;
    }
    public final boolean connected() {
        return remoteCell.size() > 0;
    }

    // read from image
    protected final void initFromImage(BufferedImage img, ParticleSet particleSet) {
        Set<Particle> ps = particleSet.getParticles(this);

        for (int x = 0; x < size; ++x)
            for (int y = 0; y < size; ++y) {
                int c = img.getRGB(x, y);
                int red = (c & 0x00ff0000) >> 16;
                int green = (c & 0x0000ff00) >> 8;
                int blue = c & 0x000000ff;

                // find state with closest color
                int dmin = 0;
                Particle s = null;
                for (Particle pt : ps) {
                    Color ct = pt.color;
                    int rdist = red - ct.getRed(), gdist = green - ct.getGreen(), bdist = blue - ct.getBlue();
                    int dist = rdist * rdist + gdist * gdist + bdist * bdist;
                    if (s == null || dist < dmin) {
                        s = pt;
                        dmin = dist;
                        if (dist == 0)
                            break;
                    }
                }
                writeCell(new Point(x, y), s);
            }
    }

    // debug
    String debugDumpStats() {
        int transRules = 0, outcomes = 0;
        for (Particle p : nameToParticle.values()) {
            transRules += p.transformationRules();
            outcomes += p.outcomes();
        }
        return nameToParticle.size() + " states, " + transRules + " rules, " + outcomes + " outcomes";
    }
}

