#!/bin/bash
cd "$(dirname "$0")" || exit

dependency-check/bin/dependency-check.sh \
    --project "geoviite"\
    --out ./report \
    --format HTML \
    --format JSON \
    --format JUNIT \
    --prettyPrint \
    --disableNuspec \
    --disableAssembly \
    --disableYarnAudit \
    -d nvd \
    --failOnCVSS 1 \
    --scan ../infra \
    --scan ../ui \
    --suppression ./dependency-check-supression.xml

