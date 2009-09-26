import java.util.TimerTask;

public class ZooGasRefresher extends TimerTask {
    ZooGas gas;
    ZooGasRefresher (ZooGas gas) {
	this.gas = gas;
    }

    public void run() {
	gas.plotCounts();
	gas.redrawBoard();
	gas.refreshBuffer();
    }
};
