#!/bin/bash
echo "Checking allowed licences in used libraries."

set +e
rm -rf "$(dirname $0)/build/reports/dependency-license"
./gradlew checkLicense
RESULT=$?
set -e

if [[ $RESULT -ne 0 ]]; then
    echo "Some non-allowed licenses found. You may have added a dependency with a new license type."
    echo "  Allowed licenses are configured in allowed-licenses.json and should first be confirmed as compatible with EUPL:"
    echo "  Official compatibility list can be found at https://joinup.ec.europa.eu/collection/eupl/matrix-eupl-compatible-open-source-licences"
    echo "List of libraries with unconfirmed licenses:"
    cat "$(dirname $0)/build/reports/dependency-license/dependencies-without-allowed-license.json"
    exit $RESULT
fi
