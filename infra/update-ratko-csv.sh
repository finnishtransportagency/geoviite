#!/bin/bash
set -e

cd $(dirname $0)

echo "Copying ratko csv files..."
cp ../ratko-export/exported-csv/01-track-number.csv ./src/main/resources/ratkoImport/
cp ../ratko-export/exported-csv/02-alignment.csv ./src/main/resources/ratkoImport/
cp ../ratko-export/exported-csv/03-segment.csv ./src/main/resources/ratkoImport/

echo "Finished copying ratko csv files."
