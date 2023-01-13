#!/bin/bash
set -e

cd $(dirname $0)

BUILD_TIMESTAMP=$(date +%Y-%m-%d_%H-%M)

echo "Sourcing NVM script..."
set +e
source "${HOME}/.nvm/nvm.sh"
echo "Activating NVM in directory..."
nvm use
set -e

echo "Using latest NPM..."
nvm install --latest-npm

echo "Installing NPM Dependencies..."
npm ci
echo "Building frontend..."
npm run build --
cp ../LICENSE.txt dist/
