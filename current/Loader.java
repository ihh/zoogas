import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.io.Reader;
import java.io.Writer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.net.SocketTimeoutException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * Multiplayer loading interface
 * Acts as a pretty screen that gets arguments for ZooGas's main method
 *
 * Add a scroll pane to view large maps?
 * Add an observer pane that draws boards
 */
public class Loader extends JFrame implements ItemListener, ActionListener {
    public Loader() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 1;
        c.gridwidth = 3;
        //c.anchor = c.CENTER;

        observerPanel = new JPanel();
        observerPanel.setPreferredSize(new Dimension(400, 400));
        add(observerPanel, c);
        observerConstraints = new GridBagConstraints();
        observerConstraints.gridx = 0;
        observerConstraints.gridy = 0;
        observerConstraints.gridheight = 1;
        observerConstraints.gridwidth = 1;
        observerPanel.setLayout(new GridBagLayout());
        observerPanel.setBackground(Color.BLACK);
        ++c.gridy;

        messages = new JTextArea();
        messages.setRows(1);
        messages.setBackground(Color.WHITE);
        messages.setFocusable(false);
        add(messages, c);
        ++c.gridy;
        c.gridwidth = 1;

        showWorldCheck = new JCheckBox("Display Detailed World");
        showWorldCheck.setMnemonic('d');
        add(showWorldCheck, c);
        c.gridx = 2;

        automaticallyRefresh = new JCheckBox("Automatically Refresh");
        automaticallyRefresh.setMnemonic('r');
        add(automaticallyRefresh, c);
        c.gridx = 0;
        ++c.gridy;

        forceReconnect = new JButton("Reconnect");
        forceReconnect.setActionCommand("Connect");
        forceReconnect.addActionListener(this);
        add(forceReconnect, c);
        ++c.gridx;

        launchMPButton = new JButton("Launch!");
        launchMPButton.setActionCommand("LaunchToWorld");
        launchMPButton.setEnabled(false);
        launchMPButton.setToolTipText("Select a location on the grid");
        launchMPButton.addActionListener(this);
        add(launchMPButton, c);
        ++c.gridx;

        launchSPButton = new JButton("Single Player");
        launchSPButton.setActionCommand("LaunchSP");
        launchSPButton.setToolTipText("Launch ZooGas in single player");
        launchSPButton.addActionListener(this);
        add(launchSPButton, c);
        ++c.gridy;
        c.gridwidth = 3;
        c.gridx = 0;

        JButton server = new JButton("Server") {};
        server.setActionCommand("TestServer");
        server.addActionListener(this);
        add(server, c);

        pack();

        setVisible(true);

        try {
            while (!readyToLaunch) {
                if(serverSocket != null && serverSocket.isConnected()) {
                    ByteBuffer bb = ByteBuffer.allocate(allocateBufferSize);
                    if(serverSocket.read(bb) != 0) {}
                }
                
                Thread.sleep(100);
            }

            dispose();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (AsynchronousCloseException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Connections
    SocketChannel serverSocket = null;
    Map<InetAddress, Socket> peerConnections;
    public final static int allocateBufferSize = 2048;

    // View
    JPanel observerPanel;
    GridBagConstraints observerConstraints;
    JTextArea messages;

    /**
     * When this box is checked, the Loader will attempt to draw the world as it is, rather than just displaying the grids that are occupied
     */
    private JCheckBox showWorldCheck;
    private JCheckBox automaticallyRefresh;
    private JComponent refreshRateField;
    private JButton launchSPButton;
    private JButton launchMPButton;
    private JButton forceReconnect;
    private Point currentSelection; // the currently selected space on the grid of all active boards
    private BoardRenderer world = null;
    private int updateTime = 5;

    // Launch
    private boolean readyToLaunch = false;
    private String[] launchArgs;

    public static WorldServer ws;
    public static void main(String[] args) {
        //WorldServer ws = new WorldServer();
        Loader load = new Loader();
        // waid for input

        //String[] loadArgs = load.getArgs();
        //load = null;
        //ZooGas.main(new String[] { });
    }

    public String[] getArgs() {
        return launchArgs;
    }

    public void itemStateChanged(ItemEvent e) {
        boolean selected = (e.getStateChange() == e.SELECTED);
        if (e.getItemSelectable() == showWorldCheck) {

        } else {
            System.out.println(e.getItemSelectable());
            return;
        }
    }

    public void actionPerformed(ActionEvent e) {
        if ("LaunchSP".equals(e.getActionCommand())) {
            launchArgs = new String[] { };
            readyToLaunch = true;
            return;
        } else if ("Connect".equals(e.getActionCommand())) {
            connectToWorld();
            return;
        } else if ("TestServer".equals(e.getActionCommand())) {
            forceReconnect.setEnabled(false);
            ws = new WorldServer();
            forceReconnect.setEnabled(true);
            return;
        }
        
    }

    public void connectToWorld() {
        InetAddress serverAddress;
        try {
            // Close current connection if any
            if(serverSocket != null && serverSocket.isOpen()) {
                serverSocket.close();
                serverSocket.socket().close();
                serverSocket = null;
            }
            
            // Connect to server
            serverAddress = InetAddress.getLocalHost(); // TODO: replace with real server
            messages.setText("Connecting to server at " + serverAddress + "...");
            serverSocket = SocketChannel.open();
            InetSocketAddress connectionToWorld = new InetSocketAddress(serverAddress, WorldServer.newConnectionPort); // must use address string version of constructor
            serverSocket.socket().setSoTimeout(10000);
            serverSocket.configureBlocking(true);
            serverSocket.connect(connectionToWorld);
            serverSocket.configureBlocking(false);

            // read out dedicated port
            ByteBuffer bb = ByteBuffer.allocate(4);
            messages.setText("Establishing connection to " + serverAddress);
            int response = serverSocket.read(bb);
            while(response != 0) {
                response = serverSocket.read(bb);
            }
            serverSocket.close();
            if(response == -1) {
                messages.setText("Server unavailable");
                return;
            }

            bb.flip();
            int newPort = bb.getInt();
            if (newPort == WorldServer.CONNECTIONS_FULL) {
                messages.setText("Connections to server are full");
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
                
                // Send a test packet
                bb = ByteBuffer.allocate(allocateBufferSize);
                WorldServer.writeStringToBuffer(bb, "this is a");
                WorldServer.writeStringToBuffer(bb, "test");
                bb.putInt(110011);
                System.out.println(" Sent: "+ bb);
                bb.flip();
                serverSocket.write(bb);
                
                messages.setText("Connected to " + serverAddress.getHostName() + ":" + newPort);
            }
        } catch (SocketTimeoutException ste) {
            messages.setText("Connection timed out");
            ste.printStackTrace();
        } catch (IOException ioe) {
            messages.setText("Connection failed");
            ioe.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("Client finished connecting");
        }
    }
}
