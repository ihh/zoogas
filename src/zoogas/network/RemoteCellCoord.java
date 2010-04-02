package zoogas.network;

import java.net.*;

import zoogas.core.Point;

public class RemoteCellCoord {
    public RemoteCellCoord (InetSocketAddress sockAddr, Point p) {
	this.sockAddr = sockAddr;
	this.addr = sockAddr.getAddress();
	this.port = sockAddr.getPort();
	this.p = new Point(p);
    }
    
    private InetSocketAddress sockAddr;
    private InetAddress addr = null;
    private int port = -1;
    Point p = null;
    
    public InetAddress getAddress() {
        return addr;
    }
    
    public InetSocketAddress getSocketAddress() {
        return sockAddr;
    }
    
    public int getPort() {
        return port;
    }
    
    public Point getPoint() {
        return p;
    }
}
