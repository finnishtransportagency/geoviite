#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Running IT Tests..."
./gradlew cleanTest integrationtest-without-cache --tests "*DaoIT"
./gradlew integrationtest --tests "*IT"
