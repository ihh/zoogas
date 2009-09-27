import java.net.*;
import java.io.*;

public class BoardServer extends Thread {
    private ZooGas gas = null;
    private int port = -1;

    BoardServer (ZooGas gas, int port) throws IOException {
	super("BoardServer");
	this.gas = gas;
	this.port = port;
    }

    public void run() {
	try {
	    ServerSocket serverSocket = null;
	    boolean listening = true;

	    try {
		serverSocket = new ServerSocket(port);
	    } catch (IOException e) {
		System.err.println("Could not listen on port: " + port + ".");
		System.exit(-1);
	    }

	    System.err.println ("[BoardServer] Listening for connections on port " + port + ".");
	    while (listening)
		new BoardServerThread(gas,serverSocket.accept()).start();

	    serverSocket.close();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
}
