#!/bin/bash
set -e

NVM_VERSION="v0.38.0"
NVM_DIR="${HOME}/.nvm"

rm -rf NVM_DIR
set +e
git clone https://github.com/nvm-sh/nvm.git "$NVM_DIR"
set -e
cd "$NVM_DIR"
git fetch
git checkout $NVM_VERSION
