# program name
ZOOGAS = ZooGas

# set heap to 512MB
JAVA = java -Xmx512m

# max compiler warnings
JAVAC = javac -Xlint:unchecked

# find all .java files and substitute .class for .java
CLASSFILES = $(subst .java,.class,$(wildcard *.java))

# targets
all zoogas: classfiles
	$(JAVA) $(ZOOGAS)

classfiles class: $(CLASSFILES)

clean:
	rm $(CLASSFILES)

testserver: classfiles
	$(JAVA) $(ZOOGAS) 4444

testclient: classfiles
	$(JAVA) $(ZOOGAS) 4445 localhost 4444

%.class: %.java
	$(JAVAC) $<

# keep everything when done
.SECONDARY:
