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
	while (true) {
	    try {
		byte[] buf = new byte[256];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		socket.receive(packet);

		String packetString = new String (packet.getData());
		String[] args = packetString.split (" ");

		int[] intArgs = new int[args.length];
		for (int a = 0; a < args.length; ++a)
		    try {
			intArgs[a] = new Integer(args[a]).intValue();
		    } catch (NumberFormatException e) { }

		if (args.length >= 1) {

		    String command = args[0];

		    // debug
		    /*
		    StringBuffer join = new StringBuffer (command);
		    for (int a = 1; a < args.length - 1; ++a) {
			join.append (" " + args[a]);
		    }
		    System.err.println (join);
		    */
		    
		    if (command.equalsIgnoreCase ("BYE"))
			break;

		    else if (command.equalsIgnoreCase ("EVOLVE")) {

			    Point localTarget = new Point(intArgs[1], intArgs[2]);
			    int oldSourceState = intArgs[3];
			    Point remoteSource = new Point(intArgs[4], intArgs[5]);
			    InetAddress returnAddr = InetAddress.getByName (args[6]);
			    int returnPort = intArgs[7];

			    int newSourceState = gas.evolveTarget (localTarget, oldSourceState);

			    sendDatagram (returnAddr, returnPort, "RETURN " + remoteSource.x + " " + remoteSource.y + " " + newSourceState);

		    } else if (command.equalsIgnoreCase ("RETURN")) {

			    Point localSource = new Point(intArgs[1], intArgs[2]);
			    int newSourceState = intArgs[3];

			    gas.writeCell (localSource, newSourceState);

		    } else if (command.equalsIgnoreCase ("CONNECT")) {

			// connect a remote cell
			Point localCell = new Point(intArgs[1], intArgs[2]);
			Point remoteCell = new Point(intArgs[3], intArgs[4]);
			InetSocketAddress sockAddr = new InetSocketAddress (args[5], intArgs[6]);

			gas.addRemoteCellCoord (localCell, new RemoteCellCoord (sockAddr, remoteCell));

			// debug
			// System.err.println (command + " " + localCell + " " + remoteCell + " " + sockAddr);
		    }
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	socket.close();
    }

    static void sendDatagram (InetAddress addr, int port, String data) {
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
}
