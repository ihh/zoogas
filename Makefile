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

MAINCLASSFILES := $(CLASSES_DIR)$(ZOOGAS).class

# targets
all zoogas: $(MAINCLASSFILES)
	$(JAVA) $(ZOOGAS)

testserver: $(MAINCLASSFILES)
	$(JAVA) $(ZOOGAS) -s

testclient: $(MAINCLASSFILES)
	$(JAVA) $(ZOOGAS) -p 4445 -c localhost

loader: $(MAINCLASSFILES)
	$(JAVA) $(LOADER)

jar: $(MAINCLASSFILES)
	jar -c -v -m META-INF/MANIFEST.MF -f ZooGas.jar -C classes/ .

clean:
	find . -name "*.class" -print0 | xargs -0 -r rm

$(MAINCLASSFILES): $(CLASSES_DIR)
	$(JAVAC) $(SOURCE_DIR)$(ZOOGAS).java

$(CLASSES_DIR):
	mkdir classes

# keep everything when done
.SECONDARY:
