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
        usedPorts = new HashMap<Integer, ClientThread>();
        clientLocations = new HashMap<Point, ClientThread>();
        try {
            incomingClientsSSC = ServerSocketChannel.open();
            incomingClientsSSC.socket().bind(new InetSocketAddress(newConnectionPort));
        } catch (IOException e) {
            e.printStackTrace();
        }

        start();
    }

    // Defines
    public final static int CONNECTIONS_FULL = -1;
    public final static int allocateBufferSize = 2048;

    // Ports
    private ServerSocketChannel incomingClientsSSC;
    public final static int newConnectionPort = 4440;
    public final int minPort = newConnectionPort + 1;
    public final int maxPort = 4450;
    private Map<Integer, ClientThread> usedPorts;
    
    // ZooGas connectivity
    private Map<Point, ClientThread> clientLocations;

    // Validation
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

                    if (newPort != CONNECTIONS_FULL) {
                        new ClientThread(new InetSocketAddress(newClientAdd.getHostName(), newPort), newPort);
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

        return CONNECTIONS_FULL;
    }
    
    public void deregisterClient(ClientThread ct) {
        usedPorts.remove(ct.port); // allow this key to be reused now
        clientLocations.remove(ct.location);
    }
   
    public int getNumPlayers() {
        return clientLocations.size();
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


    private class ClientThread extends Thread {
        ClientThread(InetSocketAddress clientAddress, int port) {
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
        
        // members for after the client has started playing
        // TODO: maybe use a different class for this, or extend ClientThread?
        Point location;

        public void run() {
            try {
                while (socketChannel.isConnected()) {
                    ByteBuffer bb = ByteBuffer.allocate(allocateBufferSize);
                    int temp;
                    if(socketChannel.read(bb) > 0) {
                        processPacket(bb);
                    }
                    else {
                        sleep(100);
                        socketChannel.socket().getOutputStream().write(0); // Keep alive
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

        private void processPacket(ByteBuffer bb) {
            // first int is always the ordinal
            bb.rewind();
            packetCommand command = packetCommand.values()[bb.getInt()];
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
            ByteBuffer bb = prepareBuffer(packetCommand.CURRENT_CLIENTS, 4 + (4 + 4) * clientLocations.size());
            bb.putInt(clientLocations.size());
            for(Point p : clientLocations.keySet()) {
                bb.putInt(p.x);
                bb.putInt(p.y);
            }
            verifyAndSend(bb, packetCommand.CURRENT_CLIENTS, socketChannel);
            System.out.println("sending something");
        }

        // Packet handlers
        private void handleSetPlayerLoc(Object... args) {
            final Point requestedPoint = new Point((Integer)args[0], (Integer)args[1]);
            // atomic test and set block
            synchronized(clientLocations) {
                // Another client is already there, do nothing
                if(clientLocations.containsKey(requestedPoint))
                    return;
                
                clientLocations.put(requestedPoint, this);
            }

            // Give the position to the client
            location = requestedPoint;
            
            sendLaunch();
            
            // tell other observers a new client has connected
            for(ClientThread ct : usedPorts.values()){
                if(ct != this)
                    ct.sendCurrentPlayerLocs();
            }
        }
        
        
        // In-game methods
    }
    
    // TODO: Move this to common "network" based abstract class
    public static String getStringFromBuffer(ByteBuffer bb) {
        StringBuilder sb = new StringBuilder();
        byte c = bb.get();
        while(c != '\0') {
            sb.append((char)c); // TODO: check non-utf-8
            c = bb.get();
        }
        return sb.toString();
    }
    public static ByteBuffer writeStringToBuffer(ByteBuffer bb, String s) {
        s += '\0';
        byte[] stringBytes = s.getBytes();
        bb.put(stringBytes);
        return bb;
    }
    
    // Commands
    public static enum packetCommand {
        PING             (0),
        SEND_SIZE        (2, "ii", 4 + 4),
        CLAIM_GRID       (2, "ii", 4 + 4),
        LAUNCH           (0),
        OBSERVE          (2, "ii", 4 + 4),
        DISCONNECT       (0),
        //CURRENT_CLIENTS  (2, "i(ii)*", 0); // ideally, should be something regex-like
        CURRENT_CLIENTS  (1, "i", 4 + 4 + 4); // variadic

        private packetCommand(int numArgs) {
            expectedCount = numArgs;
        }
        private packetCommand(int numArgs, String str, int bytes) {
            expectedCount = numArgs;
            expectedArgs = str;
            expectedBytes = bytes;
        }
        private int expectedCount = 0;
        private int expectedBytes = 0; // expected number of bytes, not including the enum itself
        private String expectedArgs = "";
        public int getExpectedCount() {
            return expectedCount;
        }
        public int getNumBytes() {
            return expectedBytes;
        }
        public String getExpectedArgs() {
            return expectedArgs;
        }
        public boolean matchArgCount(int numArgs) {
            return numArgs == expectedCount;
        }
    }
    
    /**
     *Prepares a buffer for <i>sending</i> a packet
     * @param cmd
     * @return
     */
    public static ByteBuffer prepareBuffer(packetCommand cmd){
        return prepareBuffer(cmd, cmd.expectedBytes);
    }
    public static ByteBuffer prepareBuffer(packetCommand cmd, int byteCount){
        ByteBuffer bb = ByteBuffer.allocate(4 + byteCount);
        bb.putInt(cmd.ordinal());
        return bb;
    }
    public static boolean verifyAndSend(ByteBuffer bb, packetCommand cmd, SocketChannel sc, boolean requiresBlocking) {
        bb.flip();
        try {
            if(requiresBlocking)
                sc.configureBlocking(true);
            sc.write(bb);
            if(requiresBlocking)
                sc.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public static boolean verifyAndSend(ByteBuffer bb, packetCommand cmd, SocketChannel sc) {
        return verifyAndSend(bb, cmd, sc, false);
    }
}
