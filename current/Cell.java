import java.util.*;
import java.awt.*;

public class Cell {
    // state of this cell
    Particle particle = null;
    // bonds
    HashMap<String,Point>
	incoming = new HashMap<String,Point>(),
	outgoing = new HashMap<String,Point>();
    // internal info
    int writeCount = 0;
}
