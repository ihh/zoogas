import java.net.*;
import java.io.*;

public class ConnectionServerThread extends Thread {
    private Board board = null;
    private Socket socket = null;

    public ConnectionServerThread(Board board, Socket socket) {
	super("ConnectionServerThread");
	this.board = board;
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
		BoardServer.process (board, inputLine, listening);
	    }
	    in.close();
	    socket.close();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
}
