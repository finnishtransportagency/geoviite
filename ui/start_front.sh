#!/bin/bash
set -e
cd $(dirname $0)
set +e
source "${HOME}/.nvm/nvm.sh"
set -e
nvm install --latest-npm
set +e
nvm use
set -e
rm -r node_modules || true
npm ci
rm -f src/**/*scss.d.ts
npm start --
