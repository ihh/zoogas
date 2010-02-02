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

import java.util.HashSet;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class ClientToServer extends NetworkThread {
    public ClientToServer (ZooGas gas) {
        super();
        this.gas = gas;
        start();
    }
    public ClientToServer (Loader loader) {
        super();
        this.loader = loader;
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
    ZooGas gas = null;
    Loader loader = null;

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
            System.out.println(bb);
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
            System.out.println("Received " + command + " " + bb);
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
                default:
                    System.err.println("Unhandled command type " + command);
                    return;
            }
        }
    }

    // Packet senders
    public boolean sendJoinLocation(Point p) {
        packetCommand cmd = packetCommand.CLAIM_GRID;

        ByteBuffer bb = prepareBuffer(cmd);
        bb.putInt(p.x);
        bb.putInt(p.y);
        return verifyAndSend(bb, cmd, serverSocket);
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
}
