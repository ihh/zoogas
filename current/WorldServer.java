import java.awt.Rectangle;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;

import java.nio.channels.SocketChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    // Validation
    RuleSet ruleset = null;
    // RSA check
    // rules check (rules for rules?)

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
        pointToClient.remove(ct.location);
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
        Point location;

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
                        if(location != null)
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
                ArrayList<Object> parameters = new ArrayList<Object>();
                for(int i = 0; i < command.getExpectedCount(); ++i) {
                    char c = command.getExpectedArgs().charAt(i);
                    switch(c) {
                        case 'i':
                            parameters.add(bb.getInt());
                            break;
                        case 's':
                            parameters.add(getStringFromBuffer(bb));
                            break;
                        default:
                            System.err.println("Unknown parameter type " + c);
                            return;
                    }
                }

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
                    System.err.println("Unhandled command type " + command);
                    return;
            }
        }

        // Packet senders
        private void sendLaunch() {
            //verifyAndSend(prepareBuffer(packetCommand.LAUNCH), packetCommand.SEND_SIZE, socketChannel);
            ByteBuffer bb = prepareBuffer(packetCommand.LAUNCH);
            verifyAndSend(bb, packetCommand.LAUNCH, socketChannel);
        }

        private void sendSize() {
            ByteBuffer bb = prepareBuffer(packetCommand.SEND_SIZE);
            Rectangle rect = getDimensions();
            System.out.println(rect);
            bb.putInt(rect.width);
            bb.putInt(rect.height);
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

        // Packet handlers
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
            location = requestedPoint;

            sendLaunch();

            // tell other observers a new client has connected
            for(ServerToClient ct : usedPorts.values()){
                if(ct != this)
                    ct.sendCurrentPlayerLocs();
            }
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
            int particles = (Integer)args[0];
            //bb.duplicate();
            for(int i = 0; i < particles; ++i) {
                String name = getStringFromBuffer(bb);
                int numPoints = bb.getInt();
                for(int j = 0; j < numPoints; ++j) {
                    int x = bb.getInt();
                    int y = bb.getInt();
                }
            }
        }
        private void handleRefreshObserved(Object... args) {
            Point p = new Point((Integer)args[0], (Integer)args[1]);
            if(!pointToClient.containsKey(p))
                return;
        }
    }

    // TODO: Move this to common "network" based abstract class
}
