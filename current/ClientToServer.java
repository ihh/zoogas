import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
        InetAddress serverAddress;
        try {
            // Close current connection if any
            if(serverSocket != null && serverSocket.isOpen()) {
                serverSocket.close();
                serverSocket = null;
            }

            // Connect to server
            serverAddress = InetAddress.getLocalHost(); // TODO: replace with real server
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

                loader.setMessage("Connected to " + serverAddress.getHostName() + ":" + newPort);
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
        while(bb.limit() != bb.position()) {
            packetCommand command = packetCommand.values()[bb.getInt()];
            //System.out.println("Client Received " + command + " " + bb);
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
                        System.err.println("Unknown parameter type " + c + ". Buffer discarded.");
                        return;
                }
            }

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
                default:
                    System.err.println("Unhandled command type " + command);
                    return;
            }
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
        verifyAndSend(bb, cmd, serverSocket);
    }
    
    public void sendParticles() {
        final packetCommand cmd = packetCommand.SEND_PARTICLES;

        HashMap<Particle, Integer> numParts = new HashMap<Particle, Integer>();
        int byteSize = 4;
        for(Particle p : gas.board.nameToParticle.values()) {
            int size = p.getOccupiedPoints().size();
            if(size > 0 && !"_".equals(p.name)) {
                byteSize += 1 + p.name.getBytes().length; // name
                byteSize += 4; // int storing the number of particles
                numParts.put(p, size);
                byteSize += (4 + 4) * size; // x,y coordinates
            }
        }
        
        ByteBuffer bb = prepareBuffer(cmd, byteSize);
        bb.putInt(numParts.size());
        for(Particle p : numParts.keySet()) {
            writeStringToBuffer(bb, p.name);
            bb.putInt(numParts.get(p));
            Set<Point> occupied = p.getOccupiedPoints();
            synchronized(occupied) {
                int count = 0;
                for(Point point : occupied) {
                    if(count >= numParts.get(p))
                        break;

                    bb.putInt(point.x);
                    bb.putInt(point.y);
                    ++count;
                }
            }
        }

        verifyAndSend(bb, cmd, serverSocket);
    }
    
    /**
     * Updates the currently observed board
     */
    public void sendRefreshObserved(Point obs) {
        final packetCommand cmd = packetCommand.REFRESH_OBSERVED;
        ByteBuffer bb = prepareBuffer(cmd);
        
        bb.putInt(obs.x);
        bb.putInt(obs.y);
        
        verifyAndSend(bb, cmd, serverSocket);
    }
    
    // Packet handlers
    private void handleSetSize(Object... args) {
        if(loader == null) {
            System.err.println("handleSetSize called when already in game");
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
}
