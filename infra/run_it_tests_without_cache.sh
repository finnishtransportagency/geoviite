#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Running DAO IT Tests without caches..."
./gradlew cleanTest integrationtest-without-cache --tests "*DaoIT"
