#!/bin/bash

set -a
source .env
set +a

up() {
    case "$1" in
        all)
            ./geoviite.sh up backend
            ./geoviite.sh up ext-api
            ;;
        backend)
            echo "Starting geoviite..."
            docker compose up -d --build --wait backend
            echo "Frontend available at http://localhost:$HOST_PORT_GEOVIITE/app/index.html"
            ;;
        ext-api)
            echo "Starting geoviite external api service..."
            docker compose up -d --build --wait ext-api
            echo "Swagger available at http://localhost:$HOST_PORT_EXT_API/rata-vkm/swagger-ui.html"
            ;;
        *)
            echo "Usage: $0 up {all|backend|ext-api}"
            exit 1
            ;;
    esac
}

down() {
    case "$1" in
        all)
            echo "Stopping entire geoviite stack..."
            docker compose down
            ;;
        backend)
            echo "Stopping geoviite..."
            docker compose down backend
            ;;
        ext-api)
            echo "Stopping geoviite external api service..."
            docker compose down ext-api
            ;;
        *)
            echo "Usage: $0 down {all|backend|ext-api}"
            exit 1
            ;;
    esac
}

test() {
    case "$1" in
        unit)
            echo "Running unit tests..."
            docker compose run --rm --build backend-unit-tests
            ;;
        integration)
            echo "Running integration tests..."
            docker compose run --rm --build backend-integration-tests
            ;;
        e2e)
            echo "Running end-to-end tests..."
            # TODO
            ;;
        e2e-1)
            echo "Running end-to-end test group 1..."
            # TODO
            ;;
        e2e-2)
            echo "Running end-to-end test group 2..."
            # TODO
            ;;
        *)
            echo "Usage: $0 test {unit|integration|e2e|e2e-1|e2e-2}"
            exit 1
            ;;
    esac
}

case "$1" in
    up)
        up "$2"
        ;;
    down)
        down "$2"
        ;;
    test)
        test "$2"
        ;;
    *)
        echo "Usage: $0 {up|down|test}"
        exit 1
        ;;
esac
