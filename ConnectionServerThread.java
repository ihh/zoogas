import java.net.*;
import java.io.*;

public class ConnectionServerThread extends Thread {
    private ZooGas gas = null;
    private Socket socket = null;

    public ConnectionServerThread(ZooGas gas, Socket socket) {
	super("ConnectionServerThread");
	this.gas = gas;
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
		BoardServer.process (gas, inputLine, listening);
	    }
	    in.close();
	    socket.close();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
}
