all:
	${DEVELOP_HOME}/tools/sbt/bin/sbt assembly

clean:
	rm *~ *.log
