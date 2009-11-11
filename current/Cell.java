import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cell {
    // state of this cell
    Particle particle = null;
    // bonds
    ConcurrentHashMap<String,Point> incoming = new ConcurrentHashMap<String,Point>();
    HashMap<String,Point> outgoing = new HashMap<String,Point>();
    // internal info
    int writeCount = 0;
}
