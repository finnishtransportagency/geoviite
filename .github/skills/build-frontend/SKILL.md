---
name: build-frontend
description: Guide for building Geoviite frontend (ui directory)
---

Geoviite frontend is built with npm. Remember to use project specific nvm to lock node/npm version. Because the shell
runs as `bash --norc --noprofile`, nvm must be sourced explicitly before use:
`. "$NVM_DIR/nvm.sh" && cd ui && nvm use && npm ci`.

You can also use the provided build-script for a full frontend build. It handles the nvm stuff internally and can be run
regardless of workdir, for example: `ui/build.sh`.

Dev tools that aren't exposed as npm scripts (e.g. `tsc`, `eslint`) are still available as dev dependencies after
`npm ci` and can be run directly via `npx`, e.g. `npx tsc --noEmit` for type-checking.
