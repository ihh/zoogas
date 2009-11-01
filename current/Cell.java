import java.util.*;
import java.awt.*;

public class Cell {
    // state of this cell
    Particle particle = null;
    // bonds
    HashMap<String,Integer>
	incoming = new HashMap<String,Integer>(),
	outgoing = new HashMap<String,Integer>();
    // internal info
    int writeCount = 0;
}
