
all zoogas: ZooGas.class IntegerRandomVariable.class
	java ZooGas

%.class: %.java
	javac $<

.SECONDARY:
