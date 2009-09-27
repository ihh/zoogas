import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.Point;

public class BoardServerThread extends Thread {
    private ZooGas gas = null;
    private Socket socket = null;

    public BoardServerThread(ZooGas gas, Socket socket) {
	super("BoardServerThread");
	this.gas = gas;
	this.socket = socket;
    }

    public void run() {

	try {
	    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
	    BufferedReader in = new BufferedReader(
				    new InputStreamReader(
				    socket.getInputStream()));

	    String command;
	    boolean ok = false, evolve = false, connect = false;
	    String[] args = null;  // command arguments
	    int[] intArgs = null;  // integer values of command arguments (some may not be integers but that's OK)
	    int expect = 0;  // number of command arguments to expect

	    while (true) {
		if ((command = in.readLine()) != null) {

		    // debug
		    // System.err.println ("BoardServerThread received command " + command);

		    if (command.equalsIgnoreCase ("BYE")) break;
		    if (command.equalsIgnoreCase ("EVOLVE")) { ok = evolve = true; expect = 3; }
		    if (command.equalsIgnoreCase ("CONNECT")) { ok = connect = true; expect = 6; }

		    if (expect > 0) {
			args = new String[expect];
			intArgs = new int[expect];
			for (int a = 0; a < args.length; ++a)
			    if (ok = ok && ((args[a] = in.readLine()) != null))
				{
				    try {
					intArgs[a] = new Integer(args[a]).intValue();
				    } catch (NumberFormatException e) { }
				}
		    }

		    if (ok) {
			if (evolve) {
			    Point target = new Point(intArgs[0], intArgs[1]);
			    int oldSourceState = intArgs[2];
			    out.println (gas.evolveTarget (target, oldSourceState));

			    // debug
			    // System.err.println (command + " " + target + " " + oldSourceState);
			} else if (connect)
			    {
				// connect a remote cell
				Point localCell = new Point(intArgs[0], intArgs[1]);
				Point remoteCell = new Point(intArgs[2], intArgs[3]);
				InetSocketAddress sockAddr = new InetSocketAddress (args[4], intArgs[5]);
				gas.addRemoteCellCoord (localCell, new RemoteCellCoord (sockAddr, remoteCell));

				// debug
				System.err.println (command + " " + localCell + " " + remoteCell + " " + sockAddr);
			    }
		    }
		}
	    }

	    out.close();
	    in.close();
	    socket.close();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
}
