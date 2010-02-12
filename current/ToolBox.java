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
                SprayTool st = SprayTool.fromString(s,board);
                if(st != null)
                    tb.tool.add(st);
	    }
	    buff.close();
            tb.currentTool = tb.tool.size() > 0? tb.tool.get(0) : null;
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
        System.err.println("No TOOLS files found");
	return null;
    }

    // render method
    void plotReserves (Graphics g) {
	for (int row = 0; row < tool.size(); ++row) {
	    SprayTool st = tool.get(row);
	    st.plotReserve(g, toolHeight*row, toolHeight, toolReserveBarWidth,toolTextWidth,st==currentTool);
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
	    currentTool = tool.get(row);
	    return true;
	}
	return false;
    }
}

