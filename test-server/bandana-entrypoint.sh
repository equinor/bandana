#!/bin/sh

exec "$JAVA_HOME/bin/java" -cp "${FUSEKI_DIR}/${FUSEKI_JAR}:${FUSEKI_DIR}/ext/bandana.jar" org.apache.jena.fuseki.main.cmds.FusekiMainCmd --modules true $@