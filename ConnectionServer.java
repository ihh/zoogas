import java.net.*;
import java.io.*;

public class ConnectionServer extends Thread {
    private ZooGas gas = null;
    private int port = -1;

    public ConnectionServer (ZooGas gas, int port) {
	super("ConnectionServer");
	this.gas = gas;
	this.port = port;
    }

    public void run() {
        ServerSocket serverSocket = null;
        boolean listening = true;

        try {
            serverSocket = new ServerSocket(port);

	    while (listening)
		new ConnectionServerThread(gas,serverSocket.accept()).start();

	    serverSocket.close();

        } catch (IOException e) {
	    e.printStackTrace();
        }
    }
}
