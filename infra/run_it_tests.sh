#!/bin/bash
set -e

cd $(dirname $0)

echo "Starting up DB for IT tests..."
../devenv/db_run.sh

echo "Running IT Tests..."
./gradlew cleanTest test --tests "*IT"
