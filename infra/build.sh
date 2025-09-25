#!/bin/bash
set -e

cd "$(dirname "$0")"
echo "Running build..."
./gradlew build -x test -x spotlessCheck --info
./check_licenses.sh
cp ../LICENSE.txt build/
