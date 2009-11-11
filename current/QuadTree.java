import java.util.*;

public class QuadTree {
    // data
    private double[] quadRate = null;
    private int K = 0;  // K = log_2(size)

    // constructor
    QuadTree (int size) {
	int tmp = size;
	for (K = 0; tmp > 1; ) {
	    if ((tmp & 1) != 0)
		throw new RuntimeException("While building quad tree: board size is not a power of 2");
	    tmp = tmp >> 1;
	    ++K;
	}
	int totalNodes = (4*size*size - 1) / 3;
	quadRate = new double[totalNodes];  // initialized to zero
    }

    // public methods
    public int log2size() { return K; }

    public void updateQuadTree(Point p,double val) {
	double oldVal = quadRate[quadNodeIndex(p,K)];
	double diff = val - oldVal;
	for (int lev = 0; lev <= K; ++lev) {
	    int n = quadNodeIndex(p,lev);
	    quadRate[n] = Math.max (quadRate[n] + diff, 0);
	}
    }

    public void sampleQuadLeaf(Point p) {
	int node = 0;
	p.x = p.y = 0;
	for (int lev = 0; lev < K; ++lev) {
	    double prob = Math.random() * quadRate[node];
	    int whichChild = 0, childNode = -1;
	    while (true) {
		childNode = quadChildIndex(node,lev,whichChild);
		prob -= quadRate[childNode];
		if (prob < 0 || whichChild == 3)
		    break;
		++whichChild;
	    }
	    node = childNode;
	    p.y = (p.y << 1) | (whichChild >> 1);
	    p.x = (p.x << 1) | (whichChild & 1);
	}
    }

    public double topQuadRate() { return quadRate[0]; }

    // private methods
    // quad-tree indexing
    private int quadNodeIndex(Point p,int level) {
	int nodesBeforeLevel = ((1 << (level << 1)) - 1) / 3;
	int msbY = p.y >> (K - level);
	int msbX = p.x >> (K - level);
	return msbX + (msbY << level) + nodesBeforeLevel;
    }

    private int quadChildIndex(int parentIndex,int parentLevel,int whichChild) {
	int childLevel = parentLevel + 1;
	int nodesBeforeParent = ((1 << (parentLevel << 1)) - 1) / 3;
	int nodesBeforeChild = ((1 << (childLevel << 1)) - 1) / 3;
	int parentOffset = parentIndex - nodesBeforeParent;
	int msbParentY = parentOffset >> parentLevel;
	int msbParentX = parentOffset - (msbParentY << parentLevel);
	int msbChildY = (msbParentY << 1) | (whichChild >> 1);
	int msbChildX = (msbParentX << 1) | (whichChild & 1);
	return msbChildX + (msbChildY << childLevel) + nodesBeforeChild;
    }
}
