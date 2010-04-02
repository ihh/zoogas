import java.net.*;
import java.util.regex.*;
import java.io.*;

public class UpdateServer extends BoardServer {
    private DatagramSocket socket = null;
    private static int maxPacketSize = 1024;  // needs to be significantly bigger than Particle.maxNameLength

    UpdateServer (Board board, int port, BoardRenderer renderer) throws IOException {
	super(board,port,renderer);
        if(renderer == null)
            System.out.println("Update server renderer should not be null");
	socket = new DatagramSocket(port);
    }

    public void run() {
	Boolean listening = true;
	while (listening) {
	    try {
		byte[] buf = new byte[maxPacketSize];
		DatagramPacket packet = new DatagramPacket(buf, maxPacketSize);
		socket.receive(packet);

		String packetString = new String (packet.getData());
		Matcher m = commandRegex.matcher(packetString);
		if (m.find()) {
		    String command = m.group(1);
		    processPacket (command, listening);
		} else {
		    System.err.println ("BoardServer: Ignoring empty or unterminated line " + packetString);
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	socket.close();
    }
}
