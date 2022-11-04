#!/bin/sh
exec $JAVA_HOME/bin/java $JAVA_OPTS -Djava.awt.headless=true -Dspring.profiles.active=$PROFILES -jar /geoviite/infra/infra-SNAPSHOT.jar --version.release.number=$RELEASE_VERSION
