import java.awt.Rectangle;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;

import java.nio.channels.SocketChannel;

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

    // Commands
    public static enum packetCommand {
        PING(0),
        SEND_SIZE(2, "ii", 4 + 4 + 4),
        CLAIMGRID(2, "ii", 4 + 4 + 4),
        OBSERVE(2,   "ii", 4 + 4 + 4);

        private packetCommand(int numArgs) {
            expectedCount = numArgs;
        }
        private packetCommand(int numArgs, String expectedArgs, int expectedBytes) {
            expectedCount = numArgs;
        }
        private int expectedCount = -1;
        private int expectedBytes = -1;
        private String expectedArgs = null;
        public boolean matchArgCount(int numArgs) {
            return numArgs == expectedCount || expectedCount == -1;
        }
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
            if (!usedPorts.containsKey(port)) {
                usedPorts.put(port, null); // TODO: use atomic test and set. for now, add as a null so that this port is marked as reserved
                return port;
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
        if(getNumPlayers() == 0)
            return new Rectangle(1, 1);

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
                        System.out.println(" Received: "+ bb);
                        bb.rewind(); // not flip!
                        System.out.println(" Read string \'" + getStringFromBuffer(bb) + "\'");
                        System.out.println(" Read string \'" + getStringFromBuffer(bb) + "\'");
                        System.out.println(" Read int \'" + bb.getInt() + "\'");
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
        
        // Observing methods
        private void sendSize() {
            
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
}
