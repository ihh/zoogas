import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;

public class ToolBox {
    // data
    Vector<SprayTool> tool = new Vector<SprayTool>();
    SprayTool currentTool = null;  // current spray tool

    // dimensions
    int toolHeight = 30, toolReserveBarWidth = 100, toolTextWidth = 100;

    // constructors
    static ToolBox fromStream (InputStream in, Board board) {
	ToolBox tb = new ToolBox();
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		tb.tool.add (SprayTool.fromString(s,board));
	    }
	    buff.close();
	    tb.currentTool = tb.tool.elementAt(0);
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return tb;
    }

    static ToolBox fromFile (String filename, Board board) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis,board);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }

    // render method
    void plotReserves (Graphics g, Point topLeft) {
	for (int row = 0; row < tool.size(); ++row) {
	    SprayTool st = tool.elementAt(row);
	    st.plotReserve(g,new Point(topLeft.x,topLeft.y+row*toolHeight),toolHeight,toolReserveBarWidth,toolTextWidth,st==currentTool);
	}
    }

    // refill method
    void refill(double scale) {
	for(SprayTool st : tool)
	    st.refill(scale);
    }

    // process keypress
    boolean hotKeyPressed (char c) {
	for(SprayTool st : tool)
	{
	    if (st.isHotKey(c)) {
		currentTool = st;
		return true;
	    }
	}
	return false;
    }

    // process click-select
    boolean clickSelect (int ypos) {
	int row = ypos / toolHeight;
	if (row >= 0 && row < tool.size()) {
	    currentTool = tool.elementAt(row);
	    return true;
	}
	return false;
    }
}
