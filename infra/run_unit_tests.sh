#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Running Unit tests..."
./gradlew test --rerun --tests "*Test"
