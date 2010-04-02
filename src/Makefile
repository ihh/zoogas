# program name
ZOOGAS = ZooGas

# loader and world server
LOADER = Loader

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
	rm *.class

testserver: classfiles
	$(JAVA) $(ZOOGAS) -s

testclient: classfiles
	$(JAVA) $(ZOOGAS) -p 4445 -c localhost
    
loader: classfiles
	$(JAVA) $(LOADER)

%.class: %.java
	$(JAVAC) $<

# keep everything when done
.SECONDARY:
