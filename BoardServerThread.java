import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.Point;

public class BoardServerThread extends Thread {
    private ZooGas gas = null;
    private String[] args = null;

    public BoardServerThread(ZooGas gas, String[] args) {
	super("BoardServerThread");
	this.gas = gas;
	this.args = args;
    }

    public void run() {
    }
}
