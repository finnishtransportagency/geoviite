#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Running IT Tests..."
./gradlew cleanTest test-without-cache --tests "*DaoIT"
./gradlew cleanTest test --tests "*IT"
