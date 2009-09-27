import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class RemoteCellCoord {
    InetSocketAddress sockAddr;
    InetAddress addr = null;
    int port = -1;
    Socket socket = null;
    Point p = null;

    RemoteCellCoord (Socket socket, InetSocketAddress sockAddr, Point p) {
	this.sockAddr = sockAddr;
	this.addr = sockAddr.getAddress();
	this.port = sockAddr.getPort();
	this.socket = socket;
	this.p = p;
    }

    RemoteCellCoord (InetSocketAddress sockAddr, Point p) {
	try {
	    this.sockAddr = sockAddr;
	    this.addr = sockAddr.getAddress();
	    this.port = sockAddr.getPort();
	    this.socket = new Socket(addr, port);
        } catch (UnknownHostException e) {
            System.err.println("In RemoteCellCoord: don't know about host: " + sockAddr.getHostName() + ".");
        } catch (IOException e) {
            System.err.println("In RemoteCellCoord: couldn't get I/O for the connection to: " + sockAddr);
            System.err.println(e.toString());
        }

	this.p = p;
    }
}
