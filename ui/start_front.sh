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
npm ci
npm start --
