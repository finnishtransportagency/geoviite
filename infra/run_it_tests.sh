#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Running IT Tests..."
./gradlew integrationtest --rerun --tests "*IT"
