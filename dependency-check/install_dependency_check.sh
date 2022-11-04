#!/bin/bash
cd "$(dirname "$0")" || exit

# Update dependency check version here
DEPENDENCY_CHECK_VERSION="7.3.0"
wget "https://github.com/jeremylong/DependencyCheck/releases/download/v$DEPENDENCY_CHECK_VERSION/dependency-check-$DEPENDENCY_CHECK_VERSION-release.zip"
unzip "dependency-check-$DEPENDENCY_CHECK_VERSION-release.zip"
chmod +x dependency-check/bin/dependency-check.sh
