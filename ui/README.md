# Shell scripts

## build_and_test.sh

Runs Jest tests and then builds the frontend bundle. Used by the AWS CI/CD pipeline.
Can also be run locally to replicate what CI does.

## build.sh

Builds the frontend bundle without running tests.

## start_front.sh

Starts the local development server. Cleans `node_modules` and regenerates SCSS type
definitions before starting webpack. Use this when developing locally.
