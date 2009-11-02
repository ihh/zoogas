import java.io.*;
import java.net.*;
import java.util.*;

public class RemoteCellCoord {
    InetSocketAddress sockAddr;
    InetAddress addr = null;
    int port = -1;
    Point p = null;

    RemoteCellCoord (InetSocketAddress sockAddr, Point p) {
	this.sockAddr = sockAddr;
	this.addr = sockAddr.getAddress();
	this.port = sockAddr.getPort();
	this.p = new Point(p);
    }
}
