#!/bin/bash
set -e

cd "$(dirname "$0")"
echo "Running build..."
./gradlew build -x test -x ktfmtCheck -x ktfmtCheckMain -x ktfmtCheckTest -x ktfmtCheckScripts --info
./check_licenses.sh
cp ../LICENSE.txt build/
