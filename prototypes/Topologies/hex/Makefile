CLASSFILES = $(subst .java,.class,$(wildcard *.java))

all zoogas: classfiles
	java ZooGas

classfiles class: $(CLASSFILES)

clean:
	rm $(CLASSFILES)

TOOLS.txt ECOLOGY.txt: TestEcology.class
	java TestEcology

testserver: classfiles
	java ZooGas 4444

testclient: classfiles
	java ZooGas 4445 localhost 4444

%.class: %.java
	javac -Xlint:unchecked $<

.SECONDARY:
