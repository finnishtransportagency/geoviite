ARG OPENJDK_IMAGE=""

FROM $OPENJDK_IMAGE

COPY build/libs/infra-SNAPSHOT.jar/ /geoviite/infra/
COPY build/reports/dependency-license /geoviite/infra/dependency-license
COPY build/LICENSE.txt /geoviite/infra/
COPY script/start.sh /geoviite/infra/

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MinRAMPercentage=25.0 -XX:MaxRAMPercentage=80.0"

EXPOSE 8080/TCP

ENTRYPOINT ["/bin/sh", "/geoviite/infra/start.sh"]
