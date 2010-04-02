package zoogas.gui;

public class Icon {
    // method to initialize optional "icon" (bitmask encoded as a hex string)
    public Icon(String iconEncoding) {
	// hexadecimal string, length L
	// number of bits encoded is B = 4L
	// L = B/4
	// this represents a square array with sides of length S
	// B = S^2, L = S^2/4
	// S = sqrt(B)
	//   = sqrt(4L)
	// S = 2 * sqrt(L)
	// e.g.
	//  L=1, S=2
	//  L=4, S=4
	//  L=16, S=8
	int L = iconEncoding.length();
	size = 2 * (int) Math.sqrt(L);
	if (L == size*size/4) {
	    mask = new boolean[size][size];
	    int lastNybbleIndex = -1, nybble = -1;
	    for (int row = 0; row < size; ++row)
		for (int col = 0; col < size; ++col) {
		    int bitIndex = row*size + col;
		    int nybbleIndex = bitIndex / 4;
		    if (nybbleIndex != lastNybbleIndex) {
			String nybbleString = iconEncoding.substring(nybbleIndex,nybbleIndex+1);
			nybble = Integer.parseInt(nybbleString,16);
			lastNybbleIndex = nybbleIndex;
		    }
		    int nybbleBitIndex = bitIndex % 4;
		    mask[row][col] = (nybble & (8 >> nybbleBitIndex)) > 0;
		}
	} else {
	    throw new RuntimeException (L + "-byte string " + iconEncoding + " is not a valid int-square-bitmap string");
	}
    }
    
    private int size = 0;
    public boolean[][] mask = null;  // mask[row][col]
    
    public int getSize() {
        return size;
    }
}
