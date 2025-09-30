#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Running DAO IT Tests without caches..."
./gradlew integrationtest-without-cache --rerun --tests "*DaoIT"
