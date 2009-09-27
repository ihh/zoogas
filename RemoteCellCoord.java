import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class RemoteCellCoord {
    InetSocketAddress sockAddr = null;
    Point p = null;

    RemoteCellCoord (InetSocketAddress sockAddr, Point p) {
	this.sockAddr = sockAddr;
	this.p = p;
    }
}
