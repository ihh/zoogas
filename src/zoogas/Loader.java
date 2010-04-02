import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.io.Reader;
import java.io.Writer;

import java.net.BindException;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;

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
        observerMap = new HashMap<Point, ObserverRenderer>();
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

        automaticallyRefresh = new JCheckBox("Automatically Refresh") {
            Thread refresher = null;
            Loader loader = null;
            
            public void fireActionPerformed(ActionEvent e){
                if(loader == null) {
                    if(getActionListeners().length > 0)
                        loader = (Loader)this.getActionListeners()[0];
                    else {
                        return;
                    }
                }
                if(isSelected() && loader.toWorldServer.isConnected()) {
                    refresher = new Thread() {
                        public void run() {
                            while(isSelected()) {
                                if(loader != null) {
                                    loader.refreshObserved();
                                }
                                try {
                                    Thread.currentThread().sleep(5000);
                                }
                                catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    };
                    refresher.start();
                }
                else {
                    refresher = null;
                }
                super.fireActionPerformed(e);
            }
        };
        automaticallyRefresh.setActionCommand("AutoRefresh");
        automaticallyRefresh.setMnemonic('r');
        automaticallyRefresh.addActionListener(this);
        add(automaticallyRefresh, c);
        c.gridx = 0;
        ++c.gridy;

        forceReconnect = new JButton("Connect");
        forceReconnect.setActionCommand("Connect");
        forceReconnect.setMnemonic('c');
        forceReconnect.addActionListener(this);
        add(forceReconnect, c);
        ++c.gridx;

        launchMPButton = new JButton("Launch!");
        launchMPButton.setActionCommand("LaunchMP");
        launchMPButton.setMnemonic('l');
        launchMPButton.setEnabled(false);
        launchMPButton.setToolTipText("Select a location on the grid");
        launchMPButton.addActionListener(this);
        add(launchMPButton, c);
        ++c.gridx;

        launchSPButton = new JButton("Single Player");
        launchSPButton.setActionCommand("LaunchSP");
        launchSPButton.setMnemonic('s');
        launchSPButton.setToolTipText("Launch ZooGas in single player");
        launchSPButton.addActionListener(this);
        add(launchSPButton, c);
        ++c.gridy;
        c.gridwidth = 2;
        c.gridx = 0;

        server = new JButton("Start Debug Server");
        server.setActionCommand("testServer");
        server.setMnemonic('d');
        server.addActionListener(this);
        add(server, c);
        c.gridwidth = 1;
        c.gridx = 2;
        
        manualRefresh = new JButton("Refresh");
        manualRefresh.setActionCommand("manualRefresh");
        manualRefresh.addActionListener(this);
        add(manualRefresh, c);
        
        // Menu items
        JMenuBar menubar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.setMnemonic('f');
        JMenuItem openRulesFile = new JMenuItem("Open rules");
        openRulesFile.setMnemonic('r');
        openRulesFile.setActionCommand("openrules");
        openRulesFile.addActionListener(this);
        file.add(openRulesFile);

        JMenu options = new JMenu("Options");
        options.setMnemonic('o');
        menubar.add(file);
        menubar.add(options);
        
        setJMenuBar(menubar);

        pack();
        setResizable(false);
        setVisible(true);

        toWorldServer = new ClientToServer(this);
    }

    // Connections
    //Map<InetAddress, Socket> peerConnections;
    ClientToServer toWorldServer;

    // View
    JPanel observerPanel;
    GridBagConstraints observerConstraints;
    HashMap<Point, ObserverRenderer> observerMap;
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
    private JButton server;
    private JButton manualRefresh;
    public Point currentSelection; // the currently selected space on the grid of all active boards
    private BoardRenderer world = null;
    private int updateTime = 5;

    // Launch
    private boolean readyToLaunch = false;
    private String[] launchArgs;

    public WorldServer ws;

    public static void main(String[] args) {
        Loader load = new Loader();
        while(!load.readyToLaunch) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        ZooGas.main(load.getArgs(), load.toWorldServer);
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
            launch();
            return;
        } else if ("Connect".equals(e.getActionCommand())) {
            toWorldServer.connectToWorld();
            forceReconnect.setText("Reconnect");
            return;
        } else if ("LaunchMP".equals(e.getActionCommand())) {
            (observerMap.get(currentSelection)).getJPanel().setBorder(new LineBorder(Color.YELLOW, 3));
            toWorldServer.sendJoinLocation(currentSelection);
            return;
        } else if ("testServer".equals(e.getActionCommand())) {
            forceReconnect.setEnabled(false);
            try {
                ws = new WorldServer();
            } catch (BindException f) {
                setMessage("Could not bind WorldServer's address. WorldServer already started?");
            }
            forceReconnect.setEnabled(true);
            return;
        } else if ("manualRefresh".equals(e.getActionCommand())) {
            refreshObserved();
            return;
        } else if ("AutoRefresh".equals(e.getActionCommand())){
            // See constructor of automaticallyRefresh
            return;
        } else if ("openrules".equals(e.getActionCommand())) {
            // TODO: add real implementation
            ZooGas.defaultPatternSetFilename = "ECOLOGY2.txt";
            return;
        } else {
            System.err.println("Handler for action " + e.getActionCommand() + " not found");
        }
    }
    
    private void refreshObserved(){
        toWorldServer.sendRefreshObserved();
    }

    public void setConnected(boolean connected) {
        if(connected == true && automaticallyRefresh.isSelected()) {
            automaticallyRefresh.setSelected(false);
            automaticallyRefresh.doClick();
        }
    }

    public void setMessage(String str) {
        messages.setText(str);
    }

    public void launch() {
        //launchArgs = new String[] {"-s", "-p", String.valueOf(6112)}; // TODO remove
        readyToLaunch = true;
        dispose();
    }
    public void launch(int listeningPort) {
        //(observerMap.get(currentSelection)).getJPanel().setBorder(new LineBorder(Color.GREEN, 3));
        launchArgs = new String[] {"-s", "-p", String.valueOf(listeningPort)};
        readyToLaunch = true;
        dispose();
    }

    public void setGridSize(int width, int height) {
        observerPanel.removeAll();
        for(int x = 0; x < width; ++x) {
            for(int y = 0; y < height; ++y) {
                observerConstraints.gridx = x;
                observerConstraints.gridy = y;
                ObserverRenderer obs = new ObserverRenderer(ZooGas.defaultBoardSize);

                JPanel pane = obs.getJPanel();
                final Point p = new Point(x, y);
                observerMap.put(p, obs);
                pane.addMouseListener(
                    new MouseAdapter() {
                        public void mousePressed(MouseEvent e) {
                            // double click
                            if(readyToLaunch) {
                                return;
                            }

                            if(p.equals(currentSelection)) {
                                if(e.getClickCount() == 2) {
                                    (observerMap.get(currentSelection)).getJPanel().setBorder(new LineBorder(Color.YELLOW, 3));
                                    toWorldServer.sendJoinLocation(p);
                                }
                                return;
                            }

                            if(currentSelection != null)
                                (observerMap.get(currentSelection)).getJPanel().setBorder(null);

                            if(!observerMap.get(p).hasPlayer){
                                currentSelection = p;
                                (observerMap.get(currentSelection)).getJPanel().setBorder(new LineBorder(Color.BLUE, 3));
                                launchMPButton.setEnabled(true);
                            }
                        }
                    }
                );
                observerPanel.add(pane, observerConstraints);
            }
        }

        pack();
    }
    void initPlayerLocs(Set<Point> clientSet) {
        for(Point p : clientSet) {
            observerMap.get(p).setHasPlayer(true);
        }
    }
}
