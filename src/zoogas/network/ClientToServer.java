import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.awt.image.BufferedImage;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;

import java.util.ArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.SortedSet;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class ClientToServer extends NetworkThread {
    public ClientToServer (ZooGas gas) {
        super();
        setInterface(gas);
        start();
    }
    public ClientToServer (Loader loader) {
        super();
        setInterface(loader);
        start();
    }

    public void run() {
        try {
            while (true) {
                if(serverSocket != null && serverSocket.isConnected()) {
                    ByteBuffer bb = ByteBuffer.allocate(allocateBufferSize);
                    if(serverSocket.read(bb) != 0) {
                        processPacket(bb);
                    }
                }

                Thread.sleep(100);
            }
            // Warning: this loop will never exit if the server is in blocking mode

            //dispose();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (AsynchronousCloseException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    SocketChannel serverSocket = null;
    private ZooGas gas = null;
    private Loader loader = null;
    private ConnectionState state = ConnectionState.NOT_CONNECTED;

    private enum ConnectionState{
        NOT_CONNECTED,
        OBSERVING
    }
    
    /**
     *Returns true if the client is connected
     * @return
     */
    public boolean isConnected(){
        return state != ConnectionState.NOT_CONNECTED;
    }
    
    public void setInterface(Object o) {
        if(o instanceof ZooGas) {
            gas = (ZooGas)o;
            loader = null;
        }
        else if(o instanceof Loader) {
            loader = (Loader)o;
            gas = null;
        }
        else {
            System.err.println("Interface can only be ZooGas or Loader class");
            return;
        }
    }

    public void connectToWorld() {
        String serverAddress;
        try {
            // Close current connection if any
            if(serverSocket != null && serverSocket.isOpen()) {
                serverSocket.close();
                serverSocket = null;
            }

            // Connect to server
            serverAddress = InetAddress.getLocalHost().getHostAddress(); // TODO: replace with real server
            loader.setMessage("Connecting to server at " + serverAddress + "...");
            SocketChannel ssTemp = SocketChannel.open();
            InetSocketAddress connectionToWorld = new InetSocketAddress(serverAddress, WorldServer.newConnectionPort); // must use address string version of constructor
            ssTemp.socket().setSoTimeout(10000);
            ssTemp.configureBlocking(true);
            ssTemp.connect(connectionToWorld);
            ssTemp.configureBlocking(false);

            // read out dedicated port
            ByteBuffer bb = ByteBuffer.allocate(4);
            loader.setMessage("Establishing connection to " + serverAddress);
            int response = ssTemp.read(bb);
            while(response == 0) {
                response = ssTemp.read(bb);
            }
            ssTemp.close();
            if(response == -1) {
                loader.setMessage("Server unavailable");
                return;
            }

            bb.flip();
            int newPort = bb.getInt();
            if (newPort == CONNECTIONS_FULL) {
                loader.setMessage("Connections to server are full");
            }
            else {
                // connect on new port
                connectionToWorld = new InetSocketAddress(serverAddress, newPort);
                serverSocket = SocketChannel.open();
                serverSocket.socket().setSoTimeout(1000);
                serverSocket.configureBlocking(true);
                serverSocket.connect(connectionToWorld);
                while(!serverSocket.finishConnect()){
                    Thread.sleep(100);
                }
                serverSocket.configureBlocking(false);

                loader.setMessage("Connected to " + serverAddress + ":" + newPort);
                state = ConnectionState.OBSERVING;
                loader.setConnected(true);
            }
        } catch (SocketTimeoutException ste) {
            loader.setMessage("Connection timed out");
            ste.printStackTrace();
        } catch (IOException ioe) {
            loader.setMessage("Connection failed");
            ioe.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("Client finished connecting");
        }
    }

    void processPacket(ByteBuffer bb) {
        // first int is always the ordinal
        bb.rewind();

        // one buffer may contain more than one command!
        try {
            while(bb.limit() != bb.position()) {
                packetCommand command = packetCommand.values()[bb.getInt()];

                //System.out.println("Client Received " + command + " " + bb);
                ArrayList<Object> parameters = collectParameters(command, bb);

                switch(command) {
                    case PING: // End of buffer is read as a ping. No harm done
                        return;
                    case SEND_SIZE:
                        handleSetSize(parameters.toArray());
                        break;
                    case LAUNCH:
                        handleLaunch();
                        break;
                    case CURRENT_CLIENTS:
                        handleGetPlayerLocs(bb, parameters.toArray());
                        break;
                    case REQUEST_PARTICLES:
                        handleSendParticles();
                        break;
                    case SEND_PARTICLES:
                        handleClientParticles(bb, parameters.toArray());
                        break;
                    case CONNECT_PEER:
                        handleConnectPeer(bb, parameters.toArray());
                        break;
                    default:
                        System.err.println("Client: Unhandled command type " + command);
                        return;
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    // Packet senders
    public boolean sendJoinLocation(Point p) {
        final packetCommand cmd = packetCommand.CLAIM_GRID;

        ByteBuffer bb = prepareBuffer(cmd);
        bb.putInt(p.x);
        bb.putInt(p.y);
        return verifyAndSend(bb, cmd, serverSocket);
    }
    
    public void sendAllClientRules(RuleSet ruleSets, int byteSize) {
        final packetCommand cmd = packetCommand.CHECKIN_ALL_RULES;
        SortedSet<String> rules = ruleSets.getAllRawRules();
        ByteBuffer bb = prepareBuffer(cmd, byteSize + 4);
        bb.putInt(rules.size());
        for(String s : rules) {
            writeStringToBuffer(bb, s);
        }
        verifyAndSend(bb, cmd, serverSocket);
    }
    
    public void sendRuleSet(RuleSet ruleSets, String prefix) {
        final packetCommand cmd = packetCommand.CHECKIN_RULESET;
        RuleSet.PrefixSet ruleSet = ruleSets.getPrefixSet(prefix);
        
        if(ruleSet == null) {
            System.err.println("sendRuleSet did not find prefix " + prefix );
            return;
        }
        
        ByteBuffer bb = prepareBuffer(cmd, prefix.getBytes().length + 1 + ruleSet.getByteSize());
        for(String rule : ruleSet) {
            writeStringToBuffer(bb, rule);
        }
        verifyAndSend(bb, cmd, serverSocket);
    }
    
    public void sendParticles() {
        final packetCommand cmd = packetCommand.SEND_PARTICLES;

        /*HashMap<Particle, Integer> numParts = new HashMap<Particle, Integer>();
        int byteSize = 4;
        for(Particle p : gas.board.nameToParticle.values()) {
            int size = p.getOccupiedPoints().size();
            if(size > 0 && !"_".equals(p.name)) {
                //byteSize += 1 + p.name.getBytes().length; // name
                byteSize += 3; // color
                byteSize += 4; // int storing the number of particles
                numParts.put(p, size);
                byteSize += (4 + 4) * size; // x,y coordinates
            }
        }*/
        
        HashMap<Integer, Integer> numParts = new HashMap<Integer, Integer>();
        HashMap<Integer, List<Particle>> colors = new HashMap<Integer, List<Particle>>();
        int byteSize = 4;
        for(Particle p : gas.board.nameToParticle.values()) {
            int size = p.getOccupiedPoints().size();
            if(size > 0 && !"_".equals(p.name)) {
                //byteSize += 1 + p.name.getBytes().length; // name
                
                int c = p.color.getRGB();
                if(!colors.containsKey(c)) {
                    byteSize += 4; // color, but only if this color is not already present
                    colors.put(c, new ArrayList<Particle>());
                    numParts.put(c, 0);
                }
                colors.get(c).add(p);
                
                numParts.put(c, numParts.get(c) + size);

                byteSize += 4; // int storing the number of particles
                byteSize += (4 + 4) * size; // x,y coordinates
            }
        }
        
        ByteBuffer bb = prepareBuffer(cmd, byteSize);
        bb.putInt(numParts.size());
        for(Integer c : colors.keySet()) {
            //writeStringToBuffer(bb, p.name);

            //bb.put((byte)(c & 0xFF));
            //bb.put((byte)((c & 0xFF00) >> 8));
            //bb.put((byte)((c & 0xFF0000) >> 16));
            bb.putInt(c);

            bb.putInt(numParts.get(c));
            for(Particle p : colors.get(c)) {
            Set<Point> occupied = p.getOccupiedPoints();
                synchronized(occupied) {
                    int count = 0;
                    for(Point point : occupied) {
                        if(count >= numParts.get(c))
                            break;
    
                        bb.putInt(point.x);
                        bb.putInt(point.y);
                        ++count;
                    }
                    //System.err.println(count + " of " +  numParts.get(p) + " " + p.name + " sent");
                }
            }
        }

        verifyAndSend(bb, cmd, serverSocket);
    }
    
    /**
     * Updates the currently observed board at Point obs
     */
    public void sendRefreshObserved(Point obs) {
        final packetCommand cmd = packetCommand.REFRESH_OBSERVED;
        ByteBuffer bb = prepareBuffer(cmd);        
        bb.putInt(obs.x);
        bb.putInt(obs.y);
        System.out.println("Asking world server for " + obs);
        verifyAndSend(bb, cmd, serverSocket, true);
    }
    public void sendRefreshObserved() {
        for(Point p : loader.observerMap.keySet()) {
            ObserverRenderer obsRenderer = loader.observerMap.get(p);
            if(obsRenderer.hasPlayer) {
                sendRefreshObserved(p);
                try {
                    Thread.currentThread().sleep(500);
                } catch (Exception e) {
                }
            }
        }
    }
    
    // Packet handlers
    private void handleSetSize(Object... args) {
        if(loader == null) {
            System.err.println("handleSetSize called when already in game");
            System.err.println(serverSocket.socket().getRemoteSocketAddress());
            return;
        }

        int width = (Integer)args[0];
        int height = (Integer)args[1];

        loader.setGridSize(width, height);
    }

    private void handleLaunch() {
        if(loader == null) {
            System.err.println("handleLaunch called when already in game");
            return;
        }

        loader.launch(serverSocket.socket().getPort());
    }

    private void handleGetPlayerLocs(ByteBuffer bb, Object... args) {
        if(loader == null) {
            System.err.println("handleGetPlayerLocs called when already in game");
            return;
        }

        int numClients = (Integer)args[0];

        Set<Point> set = new HashSet<Point>();
        for(int i = 0; i < numClients; ++i) {
            int x = bb.getInt();
            int y = bb.getInt();
            Point p = new Point(x, y);
            set.add(p);
        }

        loader.initPlayerLocs(set);
    }
    private void handleSendParticles() {
        if(gas == null) {
            System.err.println("handleSendParticles called when not in game");
            return;
        }
        
        sendParticles();
    }
    private void handleClientParticles(ByteBuffer bb, Object... args) {
        int particles = (Integer)args[0];
        int x0 = bb.getInt();
        int y0 = bb.getInt();
        System.out.println("Received " + x0 + " " + y0);
        ObserverRenderer renderer = loader.observerMap.get(new Point(x0, y0));
        if(renderer == null) {
            System.err.println("Renderer not found");
            return;
        }
        renderer.clear();

        for(int i = 0; i < particles; ++i) {
            //String name = getStringFromBuffer(bb);
            //Byte b = bb.get();
            //Byte g = bb.get();
            //Byte r = bb.get();
            int rgb = bb.getInt();

            ArrayList<Point> list = new ArrayList<Point>();
            int numPoints = bb.getInt();
            for(int j = 0; j < numPoints; ++j) {
                int x = bb.getInt();
                int y = bb.getInt();
                renderer.drawCell(new Point(x, y), new Color(rgb)); //new Color(r, g, b)
            }
        }
        
        renderer.getJPanel().repaint();
    }
    private void handleConnectPeer(ByteBuffer bb, Object... args){
        String address = (String)args[0];
        int port = (Integer)args[1];
        int direction = (Integer)args[2];
        while(gas == null) {
            try {
                Thread.currentThread().sleep(500);
            } catch (InterruptedException e) {
            }
        }
        gas.board.connectBorderInDirection(direction, new InetSocketAddress(address, port));
    }
}
