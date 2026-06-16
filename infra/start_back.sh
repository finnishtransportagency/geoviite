#!/bin/bash
set -e
cd $(dirname $0)

./gradlew bootRun --args='--spring.profiles.active=dev,noauth,backend'