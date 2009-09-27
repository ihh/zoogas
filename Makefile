

all zoogas: classfiles
	java ZooGas

testserver: classfiles
	java ZooGas 4444

testclient: classfiles
	java ZooGas 4445 localhost 4444

classfiles class: $(subst .java,.class,$(wildcard *.java))

%.class: %.java
	javac $<

.SECONDARY:
