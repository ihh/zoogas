import java.net.*;
import java.util.regex.*;
import java.io.*;

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
    
    enum packetCommand {
	BYE(1),
	EVOLVE(11),
	RETURN(6),
	CONNECT(7);
	
	private packetCommand(int numArgs) {
	    expectedArgs = numArgs;
	}
	private int expectedArgs;
	public boolean matchArgCount(int numArgs) {
	    return numArgs == expectedArgs;
	}
    }

    protected void processPacket (String data, Boolean listening) {
	try {
	    String[] args = data.split (" ");

	    // uncomment to log all incoming commands
	    //	    logCommand (args);
	    
            // convert first arg to int, then find ordinal
	    packetCommand command = packetCommand.values()[Integer.valueOf(args[0])];
	    
	    if(!command.matchArgCount(args.length)) {
	        System.err.println("BoardServer: " + command.toString() + " packet does not have the correct number of args");
		return;
	    }

	    switch(command)
	    {
		case BYE:
		    listening = false;
		    break;
		case EVOLVE:    
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
		    break;
		case RETURN:    
		    Point localSource = new Point(toInt(args[1]), toInt(args[2]));
		    Particle newSourceState = board.getParticleByName (args[3]);
		    if (newSourceState == null) {
			// TODO: request information about newSourceState from connecting board
		    } else {
			double energyInput = toDouble(args[4]);
			int oldWriteCount = toInt(args[5]);
    
			if (oldWriteCount == board.getCellWriteCount(localSource)) {
			    board.removeBonds (localSource);
			    board.writeCell (localSource, newSourceState);
			    // note that incoming particles are never bonded to anything...
			    // ...so we always accept the move regardless of the energy input
			    renderer.drawCell (localSource);
			}
		    }
		    break;
		case CONNECT:    
		    // connect a remote cell
		    Point localCell = new Point(toInt(args[1]), toInt(args[2]));
		    Point remoteCell = new Point(toInt(args[3]), toInt(args[4]));
		    InetSocketAddress sockAddr = new InetSocketAddress (args[5], toInt(args[6]));
    
		    board.addRemoteCellCoord (localCell, sockAddr, remoteCell);
    
		    // debug
		    // System.err.println (command + " " + localCell + " " + remoteCell + " " + sockAddr);
		    break;
	    }
	} catch(IllegalArgumentException e) {
	    System.err.println ("BoardServer: Ignoring unrecognized command string " + data);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    private static int toInt(String s) { return Integer.valueOf(s); }
    private static double toDouble(String s) { return Double.valueOf(s); }

    private static void logCommand (String[] args) {
	StringBuffer join = new StringBuffer("BoardServer: >>");
	for (int a = 0; a < args.length; ++a) {
	    join.append (" " + args[a]);
	}
	System.err.println (join + " <<");
    }

    void sendDatagram (InetAddress addr, int port, String data) {
	// uncomment to log all outgoing datagrams
	//	System.err.println ("Send UDP datagram '" + data + "' to " + addr + " port " + port);
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
	return packetCommand.CONNECT.ordinal() + " " + remoteCell.x + " " + remoteCell.y + " " + localCell.x + " " + localCell.y + " " + localHost + " " + localPort;
    }

    void sendConnectDatagram (InetAddress addr, int port, Point remoteCell, Point localCell, String localHost, int localPort) {
	sendDatagram (addr, port, connectString (remoteCell, localCell, localHost, localPort));
    }

    void sendEvolveDatagram (InetAddress addr, int port, Point remoteTarget, Particle oldSourceState, Point localSource, int dir, double energyBarrier, String returnHost, int returnPort, int writeCount) {
	sendDatagram (addr, port, packetCommand.EVOLVE.ordinal() + " " + remoteTarget.x + " " + remoteTarget.y + " " + oldSourceState.name + " " + dir + " " + energyBarrier + " " + localSource.x + " " + localSource.y + " " + returnHost + " " + returnPort + " " + writeCount);
    }

    void sendReturnDatagram (InetAddress addr, int port, Point remoteSource, Particle newSourceState, int writeCount, double energyInput) {
	sendDatagram (addr, port, packetCommand.RETURN.ordinal() + " " + + remoteSource.x + " " + remoteSource.y + " " + newSourceState.name + " " + energyInput + " " + writeCount);
    }
}
