#!/bin/bash
cd "$(dirname "$0")" || exit

dependency-check/bin/dependency-check.sh \
    --project "geoviite"\
    --out ./report \
    --format JUNIT \
    --junitFailOnCVSS 7 \
    --data nvd \
    --scan ../ui \
    --suppression ./dependency-check-supression.xml
