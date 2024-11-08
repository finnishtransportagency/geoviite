#!/bin/bash
set -e
cd $(dirname $0)

DB_URL=localhost:5435/geoviite DB_USERNAME=dev-geouser DB_PASSWORD=dev-geouser-password SPRING_PROFILES_ACTIVE=noauth,backend java -jar build/libs/infra-SNAPSHOT.jar
