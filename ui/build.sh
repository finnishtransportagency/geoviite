#!/bin/bash
set -e

cd $(dirname $0)

BUILD_TIMESTAMP=$(date +%Y-%m-%d_%H-%M)

echo "Sourcing NVM script..."
set +e
source "${HOME}/.nvm/nvm.sh"
set -e
echo "Setting node version via NVM..."
nvm install --latest-npm
echo "Activating NVM in directory..."
set +e
nvm use
set -e

echo "Installing NPM Dependencies..."
npm ci
echo "Building frontend..."
npm run build --
cp ../LICENSE.txt dist/
