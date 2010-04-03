package zoogas.gui;

import java.util.*;

import java.awt.*;

import java.io.*;

import zoogas.core.Board;
import zoogas.core.SprayTool;

public class ToolBox {
    // data
    private ArrayList<SprayTool> tools = new ArrayList<SprayTool>();
    SprayTool currentTool = null; // current spray tool

    // dimensions
    int toolHeight = 0, toolReserveBarWidth = 0, toolTextWidth = 0;

    // constructors
    static ToolBox fromStream(InputStream in, Board board) {
        ToolBox tb = new ToolBox();
        InputStreamReader read = new InputStreamReader(in);
        BufferedReader buff = new BufferedReader(read);
        try {
            while (buff.ready()) {
                String s = buff.readLine();
                SprayTool st = SprayTool.fromString(s, board);
                if (st != null)
                    tb.tools.add(st);
            }
            buff.close();
            tb.currentTool = tb.tools.size() > 0 ? tb.tools.get(0) : null;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return tb;
    }

    public static ToolBox fromFile(String filename, Board board, int toolHeight, int toolReserveBarWidth, int toolTextWidth) {
        try {
            FileInputStream fis = new FileInputStream(filename);
            ToolBox toolBox = fromStream(fis, board);
            toolBox.toolHeight = toolHeight;
            toolBox.toolReserveBarWidth = toolReserveBarWidth;
            toolBox.toolTextWidth = toolTextWidth;
            return toolBox;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println("No TOOLS files found");
        return null;
    }

    // render method
    public void plotReserves(Graphics g) {
        for (int row = 0; row < tools.size(); ++row) {
            SprayTool st = tools.get(row);
            st.plotReserve(g, toolHeight * row, toolHeight, toolReserveBarWidth, toolTextWidth, st == currentTool);
        }
    }

    // refill method
    public void refill(double scale) {
        for (SprayTool st : tools)
            st.refill(scale);
    }

    /**
     *Process key presses. If the toolbox contains a SprayTool associated with this key, the SprayTool is used.
     * @param c
     * @return true if the ToolBox fired a SprayTool
     */
    public boolean hotKeyPressed(char c) {
        for (SprayTool st : tools) {
            if (st.isHotKey(c)) {
                currentTool = st;
                return true;
            }
        }
        return false;
    }

    // process click-select
    public boolean clickSelect(int ypos) {
        int row = ypos / toolHeight;
        if (row >= 0 && row < tools.size()) {
            currentTool = tools.get(row);
            return true;
        }
        return false;
    }

    public ArrayList<SprayTool> getTools() {
        return tools;
    }
    public SprayTool getCurrentTool() {
        return currentTool;
    }
}

