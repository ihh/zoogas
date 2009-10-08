import java.net.*;
import java.io.*;
import java.awt.*;

public class BoardServer extends Thread {
    private ZooGas gas = null;
    private int port = -1;
    private DatagramSocket socket = null;

    BoardServer (ZooGas gas, int port) throws IOException {
	super("BoardServer");
	this.gas = gas;
	this.port = port;
	socket = new DatagramSocket(port);
    }

    public void run() {
	Boolean listening = true;
	while (listening) {
	    try {
		byte[] buf = new byte[256];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		socket.receive(packet);

		String packetString = new String (packet.getData());
		process (gas, packetString, listening);
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	socket.close();
    }

    static void process (ZooGas gas, String data, Boolean listening) {
	try {
	    String[] args = data.split (" ");

	    int[] intArgs = new int[args.length];
	    for (int a = 0; a < args.length; ++a)
		try {
		    intArgs[a] = new Integer(args[a]).intValue();
		} catch (NumberFormatException e) { }

	    if (args.length >= 1) {

		String command = args[0];

		// uncomment to log all incoming commands
		// logCommand (args);
		    
		if (command.equalsIgnoreCase ("BYE"))
		    listening = false;

		else if (command.equalsIgnoreCase ("EVOLVE")) {

		    Point localTarget = new Point(intArgs[1], intArgs[2]);
		    Particle oldSourceState = gas.getParticleByName (args[3]);
		    if (oldSourceState == null) {
			// TODO: request information about oldSourceState from connecting board
		    } else {
			Point remoteSource = new Point(intArgs[4], intArgs[5]);
			InetAddress returnAddr = InetAddress.getByName (args[6]);
			int returnPort = intArgs[7];
			int remoteSourceWriteCount = intArgs[8];

			Particle newSourceState = gas.evolveLocalTargetForRemoteSource (localTarget, oldSourceState);

			sendReturnDatagram (returnAddr, returnPort, remoteSource, newSourceState, remoteSourceWriteCount);
		    }

		} else if (command.equalsIgnoreCase ("RETURN")) {

		    Point localSource = new Point(intArgs[1], intArgs[2]);
		    Particle newSourceState = gas.getParticleByName (args[3]);
		    if (newSourceState == null) {
			// TODO: create newSourceState as an inert "guest" particle and write it
			// TODO: request information about newSourceState from connecting board
		    } else {
			int oldWriteCount = intArgs[4];

			if (oldWriteCount == gas.getCellWriteCount(localSource))
			    gas.writeCell (localSource, newSourceState);
		    }

		} else if (command.equalsIgnoreCase ("CONNECT")) {

		    // connect a remote cell
		    Point localCell = new Point(intArgs[1], intArgs[2]);
		    Point remoteCell = new Point(intArgs[3], intArgs[4]);
		    InetSocketAddress sockAddr = new InetSocketAddress (args[5], intArgs[6]);

		    gas.addRemoteCellCoord (localCell, sockAddr, remoteCell);

		    // debug
		    // System.err.println (command + " " + localCell + " " + remoteCell + " " + sockAddr);
		}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    private static void logCommand (String[] args) {
	StringBuffer join = new StringBuffer();
	for (int a = 0; a < args.length - 1; ++a) {
	    join.append (" " + args[a]);
	}
	System.err.println (join);
    }

    static void sendDatagram (InetAddress addr, int port, String data) {
	// uncomment to log all outgoing datagrams
	// System.err.println ("Send UDP datagram '" + data + "' to " + addr + " port " + port);
	try {
	    // get a datagram socket
	    DatagramSocket socket = new DatagramSocket();

	    // send request
	    String paddedData = data + " ";  // add a space so that last element in split isn't padded with zeroes
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

    static void sendEvolveDatagram (InetAddress addr, int port, Point remoteTarget, Particle oldSourceState, Point localSource, String returnHost, int returnPort, int writeCount) {
	sendDatagram (addr, port, "EVOLVE " + remoteTarget.x + " " + remoteTarget.y + " " + oldSourceState.name + " " + localSource.x + " " + localSource.y + " " + returnHost + " " + returnPort + " " + writeCount);
    }

    static void sendReturnDatagram (InetAddress addr, int port, Point remoteSource, Particle newSourceState, int writeCount) {
	sendDatagram (addr, port, "RETURN " + remoteSource.x + " " + remoteSource.y + " " + newSourceState.name + " " + writeCount);
    }

}
