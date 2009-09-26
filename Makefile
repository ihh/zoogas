

all zoogas: $(subst .java,.class,$(wildcard *.java))
	java ZooGas

%.class: %.java
	javac $<

.SECONDARY:
