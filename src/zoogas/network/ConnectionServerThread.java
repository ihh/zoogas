import java.net.*;
import java.io.*;

public class ConnectionServerThread extends Thread {
    private ConnectionServer connServer = null;
    private Socket socket = null;

    public ConnectionServerThread(ConnectionServer connServer, Socket socket) {
	super("ConnectionServerThread");
	this.connServer = connServer;
	this.socket = socket;
    }

    public void run() {

	try {
	    BufferedReader in = new BufferedReader(
				    new InputStreamReader(
				    socket.getInputStream()));

	    String inputLine;
	    Boolean listening = true;
	    while (listening && (inputLine = in.readLine()) != null) {
		connServer.processPacket (inputLine, listening);
	    }
	    in.close();
	    socket.close();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
}
