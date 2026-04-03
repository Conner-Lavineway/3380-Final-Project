build: all

all: 
	javac PopulateDB.java
	javac DBConsole.java

run: all
	java -cp .:mssql-jdbc-11.2.0.jre11.jar DBConsole

clean:
	rm *.class