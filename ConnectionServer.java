import java.net.*;
import java.io.*;

public class ConnectionServer extends Thread {
    private Board board = null;
    private int port = -1;

    public ConnectionServer (Board board, int port) {
	super("ConnectionServer");
	this.board = board;
	this.port = port;
    }

    public void run() {
        ServerSocket serverSocket = null;
        boolean listening = true;

        try {
            serverSocket = new ServerSocket(port);

	    while (listening)
		new ConnectionServerThread(board,serverSocket.accept()).start();

	    serverSocket.close();

        } catch (IOException e) {
	    e.printStackTrace();
        }
    }
}
