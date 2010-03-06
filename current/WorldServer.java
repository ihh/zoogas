import java.awt.Rectangle;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;

import java.nio.channels.SocketChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * The main server that enables connections between players on a grid
 */
public class WorldServer extends Thread {
    public WorldServer() {
        usedPorts = new HashMap<Integer, ServerToClient>();
        pointToClient = new HashMap<Point, ServerToClient>();
        clientLocation = new HashMap<ServerToClient, Point>();
        
        clientParticles = new HashMap<ServerToClient, HashMap<Integer, List<Point>>>();
        try {
            incomingClientsSSC = ServerSocketChannel.open();
            incomingClientsSSC.socket().bind(new InetSocketAddress(newConnectionPort));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        ruleset = new RuleSet(ZooGas.defaultPatternSetFilename);

        start();
    }

    // Ports
    private ServerSocketChannel incomingClientsSSC;
    public final static int newConnectionPort = 4440;
    public final int minPort = newConnectionPort + 1;
    public final int maxPort = 4450;
    private Map<Integer, ServerToClient> usedPorts;

    // ZooGas connectivity
    private Map<Point, ServerToClient> pointToClient;
    private Map<ServerToClient, Point> clientLocation;
    
    // Observations
    private Map<ServerToClient, HashMap<Integer, List<Point>>> clientParticles;

    // Validation
    RuleSet ruleset = null;
    // RSA check?
    // rules check (rules for rules?)
    
    private static enum ThreadStatus{
        LOADING,
        CHECKING_IN,
        READY
    }

    public void run() {
        SocketChannel sc;
        while (true) {
            try {
                sc = incomingClientsSSC.accept();
                if (sc != null) {
                    System.gc();
                    int newPort = getFirstUnusedPort();
                    ByteBuffer bb = ByteBuffer.allocate(4);
                    bb.putInt(newPort);
                    bb.flip();
                    sc.configureBlocking(true);
                    sc.write(bb);

                    InetAddress newClientAdd = sc.socket().getInetAddress();
                    sc.close();

                    if (newPort != ServerToClient.CONNECTIONS_FULL) {
                        System.out.println("new Client on " + newPort);
                        new ServerToClient(new InetSocketAddress(newClientAdd.getHostName(), newPort), newPort);
                    }
                } else {
                    sleep(50);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
            }
        }
    }

    public int getFirstUnusedPort() {
        for (int port = minPort; port <= maxPort; ++port) {
            synchronized(usedPorts) {
                if (!usedPorts.containsKey(port)) {
                    usedPorts.put(port, null);
                    return port;
                }
            }
        }

        return ServerToClient.CONNECTIONS_FULL;
    }

    public void deregisterClient(ServerToClient ct) {
        usedPorts.remove(ct.port); // allow this key to be reused now
        pointToClient.remove(ct.getLocation());
    }

    public int getNumPlayers() {
        return pointToClient.size();
    }

    /**
     * Gets the current dimensions of the world board
     * @return
     */
    public Rectangle getDimensions() {
        if(getNumPlayers() < 10)
            return new Rectangle(3, 3);

        return new Rectangle(1, 1);
    }
    
    /**
     *After a client has joined, connect the borders of neighbors if any
     * @param newClientLoc
     */
    public void addPeerToPeerConnections(ServerToClient client, Point newClientLoc) {
        Map<Integer, ServerToClient> neighbors = new HashMap<Integer, ServerToClient>();

        for(int dir = 0; dir < 4; ++dir) {
            Point q = new Point(newClientLoc);
            switch(dir)
            {
                case 0:
                    q.y++;
                    break;
                case 1:
                    q.x++;
                    break;
                case 2:
                    q.y--;
                    break;
                case 3:
                    q.x--;
                    break;
            }
            if(pointToClient.containsKey(q))
                neighbors.put(dir, pointToClient.get(q));
        }
        
        for(int dir : neighbors.keySet()) {
            // Tell peers how to connect borders
            ServerToClient peer = neighbors.get(dir);
            client.sendConnectPeer(peer, dir);
            peer.sendConnectPeer(client, (dir+2)%4);
        }
    }
    
    /**
     *Disconnect peer connections after a client has disconnected from this WorldServer
     * @param newClientLoc
     */
    public void dropPeerToPeerConnections(Point oldClientLoc) {
        
    }


    private class ServerToClient extends NetworkThread {
        ServerToClient(InetSocketAddress clientAddress, int port) {
            this.port = port;

            try {
                serverSocketChannel = ServerSocketChannel.open();
                System.out.println("Binding to " + clientAddress);
                serverSocketChannel.socket().setSoTimeout(1000);
                serverSocketChannel.configureBlocking(true);
                serverSocketChannel.socket().bind(clientAddress);

                System.out.println(" waiting for connection..." + port);
                while(socketChannel == null) {
                    socketChannel = serverSocketChannel.accept();
                }

                System.out.println(" received connection... "+ port);
                isConnected = true;
                usedPorts.put(port, this);
            } catch (IOException e) {
                e.printStackTrace();
            }

            start();

            sendSize();
            sendCurrentPlayerLocs();
        }

        private int port;
        ServerSocketChannel serverSocketChannel;
        SocketChannel socketChannel;
        boolean isConnected = false;
        //Point location;
        ThreadStatus status = ThreadStatus.READY;

        public void run() {
            try {
                socketChannel.configureBlocking(false);
                while (socketChannel.isConnected()) {
                    ByteBuffer bb = ByteBuffer.allocate(allocateBufferSize);
                    if(socketChannel.read(bb) > 0) {
                        processPacket(bb);
                    }
                    else {
                        sleep(1000);
                        if(getLocation() != null)
                            sendRequestCurrentParticles();
                        //socketChannel.socket().getOutputStream().write(0); // Keep alive
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                if (!e.getLocalizedMessage().startsWith("An established connection was aborted"))
                    e.printStackTrace();
            } finally {
                try {
                    serverSocketChannel.close();
                    serverSocketChannel = null;
                    isConnected = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            usedPorts.put(port, null); // keep this key "reserved" while it is still in listening mode
        }

        protected void finalize() throws Throwable {
            super.finalize();
            if(serverSocketChannel != null) {
                serverSocketChannel.close(); // TODO: close should be handled in run()
                isConnected = false;
                System.err.println("Warning: socket is being closed in WorldServer.finalize(), should be closed after run()");
            }

            deregisterClient(this); // callback to deregister this thread (unreserve this port, etc.)
            System.out.println("Finalized thread listening on port " + port);
        }
        
        /**
         *Returns the location of this client, or null if this client is not placed on the grid currently or is observing
         * @return
         */
        Point getLocation() {
            if(!clientLocation.containsKey(this))
                return null;
            return clientLocation.get(this);
        }

        void processPacket(ByteBuffer bb) {
            // first int is always the ordinal
            bb.rewind();
            packetCommand command;
            try {
                command = packetCommand.values()[bb.getInt()];
            }
            catch(ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
                System.err.println(bb);
                return;
            }

            //System.out.println("Server Received " + command + " " + bb);            
            ArrayList<Object> parameters = collectParameters(command, bb);

            switch(command) {
                case OBSERVE:
                    return;
                case CLAIM_GRID:
                    handleSetPlayerLoc(parameters.toArray());
                    return;
                case CHECKIN_ALL_RULES:
                    handleCheckRules(bb, parameters.toArray());
                    return;
                case SEND_PARTICLES:
                    handleClientParticles(bb, parameters.toArray());
                    return;
                case REFRESH_OBSERVED:
                    handleRefreshObserved(parameters.toArray());
                    return;
                default:
                    System.err.println("Server: Unhandled command type " + command);
                    return;
            }
        }

        // Packet senders
        private void sendLaunch() {
            ByteBuffer bb = prepareBuffer(packetCommand.LAUNCH);
            verifyAndSend(bb, packetCommand.LAUNCH, socketChannel);
        }

        private void sendSize() {
            ByteBuffer bb = prepareBuffer(packetCommand.SEND_SIZE);
            Rectangle rect = getDimensions();
            bb.putInt(rect.width);
            bb.putInt(rect.height);
            System.out.println("sending size to " + this.port);
            verifyAndSend(bb, packetCommand.SEND_SIZE, socketChannel);
        }

        private void sendCurrentPlayerLocs() {
            // send an x, y coordinate for every client:
            ByteBuffer bb = prepareBuffer(packetCommand.CURRENT_CLIENTS, 4 + (4 + 4) * pointToClient.size());
            bb.putInt(pointToClient.size());
            for(Point p : pointToClient.keySet()) {
                bb.putInt(p.x);
                bb.putInt(p.y);
            }
            verifyAndSend(bb, packetCommand.CURRENT_CLIENTS, socketChannel);
        }
        
        private void sendRequestCurrentParticles() {
            ByteBuffer bb = prepareBuffer(packetCommand.REQUEST_PARTICLES);
            verifyAndSend(bb, packetCommand.REQUEST_PARTICLES, socketChannel);
        }
        private void sendClientParticles(Point p) {
            int byteSize = 12;
            HashMap<Integer, List<Point>> sentParticles = new HashMap<Integer, List<Point>>();
            ServerToClient selectedClient = pointToClient.get(p);
            HashMap<Integer, List<Point>> particles = clientParticles.get(selectedClient);
            for(Integer part : particles.keySet()) {
                ArrayList<Point> list = new ArrayList<Point>(particles.get(part));
                //byteSize += part.getBytes().length + 1; // name
                byteSize += 4; // color
                byteSize += 4; // number of particles
                byteSize += particles.get(part).size() * (4 + 4); // x,y coordinates
                sentParticles.put(part, list);
            }
            
            ByteBuffer bb = prepareBuffer(packetCommand.SEND_PARTICLES, byteSize);
            bb.putInt(sentParticles.size());
            //System.err.println("Observer is paging " + p);
            bb.putInt(p.x);
            bb.putInt(p.y);
            for(Integer c : sentParticles.keySet()) {
                //writeStringToBuffer(bb, part);
                //bb.put((byte)(c & 0xFF));
                //bb.put((byte)((c & 0xFF00) >> 8));
                //bb.put((byte)((c & 0xFF0000) >> 16));
                
                bb.putInt(c);
                
                List<Point> list = sentParticles.get(c);
                bb.putInt(list.size());
                for(Point q : list) {
                    bb.putInt(q.x);
                    bb.putInt(q.y);
                }
            }
            verifyAndSend(bb, packetCommand.SEND_PARTICLES, socketChannel);
        }
        
        private void sendConnectPeer(ServerToClient neighbor, int dir) {
            ServerSocket socket = neighbor.serverSocketChannel.socket();
            String address = socket.getInetAddress().getHostAddress();
            int port = neighbor.port;

            ByteBuffer bb = prepareBuffer(packetCommand.CONNECT_PEER, address.getBytes().length + 1 + 4 + 4);
            writeStringToBuffer(bb, address);
            bb.putInt(port);
            bb.putInt(dir);
            verifyAndSend(bb, packetCommand.CONNECT_PEER, socketChannel);
        }

        // Packet handlers
        /**
         *Tells the client its location on the World grid
         * @param args
         */
        private void handleSetPlayerLoc(Object... args) {
            final Point requestedPoint = new Point((Integer)args[0], (Integer)args[1]);
            // atomic test and set block
            synchronized(pointToClient) {
                // Another client is already there, do nothing
                if(pointToClient.containsKey(requestedPoint))
                    return;

                pointToClient.put(requestedPoint, this);
            }

            // Give the position to the client
            clientLocation.put(this, requestedPoint);
            sendLaunch();

            // tell other observers a new client has connected
            for(ServerToClient ct : usedPorts.values()){
                if(ct != this && !clientLocation.containsKey(ct))
                    ct.sendCurrentPlayerLocs();
            }
            
            // TODO: send player locations
            addPeerToPeerConnections(this, requestedPoint);
        }


        // In-game methods
        private void handleCheckRules(ByteBuffer bb, Object... args) {
            int numRules = (Integer)args[0];
            for(int i = 0; i < numRules; ++i) {
                String s = getStringFromBuffer(bb);
                if(!ruleset.containsRules(s)) {
                    System.err.println("Unknown rule " + s);
                }
            }
        }


        private void handleClientParticles(ByteBuffer bb, Object... args) {
            int color = (Integer)args[0];
            //HashMap<String, List<Point>> particleMap = new HashMap<String, List<Point>>();
            HashMap<Integer, List<Point>> particleMap = new HashMap<Integer, List<Point>>();
            for(int i = 0; i < color; ++i) {
                //String name = getStringFromBuffer(bb);
                
                //int c = (bb.get() << 16) | (bb.get() << 8) | bb.get();
                int c = bb.getInt();
                
                ArrayList<Point> list = new ArrayList<Point>();
                int numPoints = bb.getInt();
                for(int j = 0; j < numPoints; ++j) {
                    int x = bb.getInt();
                    int y = bb.getInt();
                    list.add(new Point(x, y));
                }
                //particleMap.put(name, list);
                particleMap.put(c, list);
            }
            clientParticles.put(this, particleMap);
        }
        private void handleRefreshObserved(Object... args) {
            Point p = new Point((Integer)args[0], (Integer)args[1]);
            if(!pointToClient.containsKey(p)) {
                System.err.println("Observer is paging for a client that does not exist");
                return;
            }
            
            sendClientParticles(p);
        }
    }
}
