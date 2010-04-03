# source directory
SOURCE_DIR = src/

# branch directories
DIRS := zoogas zoogas/gui zoogas/network zoogas/core zoogas/core/rules zoogas/core/topology

# output dir
CLASSES_DIR = classes/

# program name
ZOOGAS = zoogas/ZooGas

# loader and world server
LOADER = zoogas/Loader

# set heap to 512MB
JAVA = java -Xmx512m -classpath $(CLASSES_DIR)

# max compiler warnings, use SOURCE_DIR as source directory and output classes to CLASSES_DIR
JAVAC = javac -Xlint:unchecked -sourcepath $(SOURCE_DIR) -d $(CLASSES_DIR)

#CLASSFILES := $(subst $(SOURCE_DIR), $(CLASSES_DIR), $(subst .java,.class, $(foreach dir, $(DIRS), $(wildcard $(SOURCE_DIR)$(dir)/*.java))))
CLASSFILES := $(CLASSES_DIR)$(ZOOGAS).class

# targets
all zoogas: $(CLASSFILES)
	$(JAVA) $(ZOOGAS)

testserver: $(CLASSFILES)
	$(JAVA) $(ZOOGAS) -s

testclient: $(CLASSFILES)
	$(JAVA) $(ZOOGAS) -p 4445 -c localhost
    
loader: $(CLASSFILES)
	$(JAVA) $(LOADER)

clean:
	find . -name "*.class" -print0 | xargs -0 -r rm

$(CLASSFILES):
	$(JAVAC) $(SOURCE_DIR)$(ZOOGAS).java

# keep everything when done
.SECONDARY:
