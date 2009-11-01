import java.net.*;
import java.util.regex.*;
import java.io.*;
import java.awt.*;

public class BoardServer extends Thread {
    protected Board board = null;
    protected int port = -1;
    protected BoardRenderer renderer = null;

    BoardServer (Board board, int port, BoardRenderer renderer) {
	super("BoardServer");
	this.board = board;
	this.port = port;
	this.renderer = renderer;
    }

    protected static Pattern commandRegex = Pattern.compile("([^\n]*)\n");

    protected void process (String data, Boolean listening) {
	try {
	    String[] args = data.split (" ");

	    // uncomment to log all incoming commands
	    //	    logCommand (args);

	    if (match(args,"BYE",1))
		listening = false;

	    else if (match(args,"EVOLVE",11)) {

		Point localTarget = new Point(toInt(args[1]), toInt(args[2]));
		Particle oldSourceState = board.getOrCreateParticle (args[3]);  // this should really be qualified with a PatternSet, but put that off until we can request PatternSets from remote Boards
		if (oldSourceState == null) {
		    System.err.println("Don't know particle " + args[3]);
		    // TODO: request information about oldSourceState from connecting board
		} else {
		    int dir = toInt(args[4]);
		    double energyBarrier = toDouble(args[5]);
		    Point remoteSource = new Point(toInt(args[6]), toInt(args[7]));
		    InetAddress returnAddr = InetAddress.getByName (args[8]);
		    int returnPort = toInt(args[9]);
		    int remoteSourceWriteCount = toInt(args[10]);

		    Particle newSourceState = board.evolveLocalTargetForRemoteSource(localTarget,oldSourceState,dir,energyBarrier);

		    if (newSourceState != oldSourceState)
			sendReturnDatagram (returnAddr, returnPort, remoteSource, newSourceState, remoteSourceWriteCount, -energyBarrier);
		}

	    } else if (match(args,"RETURN",6)) {

		Point localSource = new Point(toInt(args[1]), toInt(args[2]));
		Particle newSourceState = board.getParticleByName (args[3]);
		if (newSourceState == null) {
		    // TODO: request information about newSourceState from connecting board
		} else {
		    double energyInput = toDouble(args[4]);
		    int oldWriteCount = toInt(args[5]);

		    if (oldWriteCount == board.getCellWriteCount(localSource))
			// TODO: replace this call to energyDeltaAcceptable with a bond-based test
			if (board.energyDeltaAcceptable(localSource,newSourceState,-energyInput)) {
			    board.removeBonds (localSource);
			    board.writeCell (localSource, newSourceState);
			    // note that incoming particles are never bonded to anything...
			    renderer.drawCell (localSource);
			}
		}

	    } else if (match(args,"CONNECT",7)) {

		// connect a remote cell
		Point localCell = new Point(toInt(args[1]), toInt(args[2]));
		Point remoteCell = new Point(toInt(args[3]), toInt(args[4]));
		InetSocketAddress sockAddr = new InetSocketAddress (args[5], toInt(args[6]));

		board.addRemoteCellCoord (localCell, sockAddr, remoteCell);

		// debug
		// System.err.println (command + " " + localCell + " " + remoteCell + " " + sockAddr);
	    } else {
		System.err.println ("BoardServer: Ignoring unrecognized command string " + data);
	    }

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    private static boolean match(String[] args,String command,int expectedArgs) {
	return args.length == expectedArgs && args[0].equalsIgnoreCase(command);
    }

    private static int toInt(String s) { return Integer.parseInt(s); }
    private static double toDouble(String s) { return Double.parseDouble(s); }

    private static void logCommand (String[] args) {
	StringBuffer join = new StringBuffer("BoardServer: >>");
	for (int a = 0; a < args.length - 1; ++a) {
	    join.append (" " + args[a]);
	}
	System.err.println (join + " <<");
    }

    static void sendDatagram (InetAddress addr, int port, String data) {
	// uncomment to log all outgoing datagrams
	// System.err.println ("Send UDP datagram '" + data + "' to " + addr + " port " + port);
	try {
	    // get a datagram socket
	    DatagramSocket socket = new DatagramSocket();

	    // send request
	    String paddedData = data + "\n";
	    byte[] buf = paddedData.getBytes();
	    DatagramPacket packet = new DatagramPacket(buf, buf.length, addr, port);
	    socket.send(packet);

        } catch (Exception e) {
	    System.err.println ("While trying to send '" + data + "' to " + addr + " port " + port);
	    e.printStackTrace();
        }
    }

    static void sendTCPPacket (InetAddress addr, int port, String data) {
	String[] dataArray = new String[1];
	dataArray[0] = data;
	sendTCPPacket (addr, port, dataArray);
    }

    static void sendTCPPacket (InetAddress addr, int port, String[] data) {
        Socket socket = null;
        PrintWriter out = null;

        try {
            socket = new Socket(addr, port);
            out = new PrintWriter(socket.getOutputStream(), true);

	    for (int i = 0; i < data.length; ++i) {
		out.println(data[i]);
		// uncomment to log all outgoing TCP packets
		// System.err.println ("Send TCP packet '" + data[i] + "' to " + addr + " port " + port);
	    }

	    out.close();
	    socket.close();

        } catch (UnknownHostException e) {
	    e.printStackTrace();
        } catch (IOException e) {
	    e.printStackTrace();
        }
    }

    static String connectString (Point remoteCell, Point localCell, String localHost, int localPort) {
	return new String ("CONNECT " + remoteCell.x + " " + remoteCell.y + " " + localCell.x + " " + localCell.y + " " + localHost + " " + localPort);
    }

    static void sendConnectTCPPacket (InetAddress addr, int port, Point remoteCell, Point localCell, String localHost, int localPort) {
	sendTCPPacket (addr, port, connectString (remoteCell, localCell, localHost, localPort));
    }

    static void sendConnectDatagram (InetAddress addr, int port, Point remoteCell, Point localCell, String localHost, int localPort) {
	sendDatagram (addr, port, connectString (remoteCell, localCell, localHost, localPort));
    }

    static void sendEvolveDatagram (InetAddress addr, int port, Point remoteTarget, Particle oldSourceState, Point localSource, int dir, double energyBarrier, String returnHost, int returnPort, int writeCount) {
	sendDatagram (addr, port, "EVOLVE " + remoteTarget.x + " " + remoteTarget.y + " " + oldSourceState.name + " " + dir + " " + energyBarrier + " " + localSource.x + " " + localSource.y + " " + returnHost + " " + returnPort + " " + writeCount);
    }

    static void sendReturnDatagram (InetAddress addr, int port, Point remoteSource, Particle newSourceState, int writeCount, double energyInput) {
	sendDatagram (addr, port, "RETURN " + remoteSource.x + " " + remoteSource.y + " " + newSourceState.name + " " + energyInput + " " + writeCount);
    }

}
