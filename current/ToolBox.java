import java.lang.*;
import java.util.*;
import java.awt.*;
import java.text.*;
import java.net.*;
import java.io.*;

public class ToolBox {
    // data
    ArrayList<SprayTool> tool = new ArrayList<SprayTool>();
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
	    tb.currentTool = tb.tool.get(0);
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
        g.setColor(Color.black);
        g.fillRect(topLeft.x, topLeft.y, toolReserveBarWidth+toolTextWidth, toolHeight * tool.size());
        
	for (int row = 0; row < tool.size(); ++row) {
	    SprayTool st = tool.get(row);
	    st.plotReserve(g, topLeft, toolHeight,toolReserveBarWidth,toolTextWidth,st==currentTool);
	    topLeft.y += toolHeight;
	}
    }

    // refill method
    void refill(double scale) {
	for(SprayTool st : tool)
	    st.refill(scale);
    }

    // process keypress
    boolean hotKeyPressed (char c) {
	boolean foundKey = false;
	for (int t = 0; t < tool.size(); ++t) {
	    SprayTool st = tool.get(t);
	    if (st.isHotKey(c)) {
		foundKey = true;
		currentTool = st;
		break;
	    }
	}
	return foundKey;
    }

    // process click-select
    boolean clickSelect (int ypos) {
	int row = ypos / toolHeight;
	if (row >= 0 && row < tool.size()) {
	    currentTool = tool.get(row);
	    return true;
	}
	return false;
    }
}
