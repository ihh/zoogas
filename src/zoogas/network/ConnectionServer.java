package zoogas.network;

import java.net.*;
import java.io.*;

import zoogas.core.Board;

public class ConnectionServer extends BoardServer {
    public ConnectionServer (Board board, int port) {
	super(board,port,null);
    }

    public void run() {
        ServerSocket serverSocket = null;
        boolean listening = true;

        try {
            serverSocket = new ServerSocket(port);

	    while (listening)
		new ConnectionServerThread(this,serverSocket.accept()).start();

	    serverSocket.close();

        } catch (IOException e) {
	    e.printStackTrace();
        }
    }
}
