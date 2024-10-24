#!/bin/bash

set -a
source .env
set +a

up() {
    case "$1" in
        backend)
            echo "Starting Geoviite..."
            docker compose up -d --build --wait backend
            echo "Frontend available at $URL_GEOVIITE"
            ;;
        ext-api)
            echo "Starting Geoviite external api service..."
            docker compose up -d --build --wait ext-api
            echo "Swagger available at $URL_GEOVIITE_EXT_API"
            ;;
        e2e-stack)
            echo "Starting Geoviite E2E testing services..."
            docker compose up -d --build --wait e2e-backend
            echo "E2E Frontend available at $E2E_URL_GEOVIITE"
            echo "E2E Runner VNC available at $E2E_URL_REMOTE_SELENIUM_HUB_VNC"
            ;;
        db)
           echo "Starting Geoviite database..."
           docker compose up -d --build --wait db-service
           echo "Geoviite Postgres available at :$HOST_PORT_DB"
            ;;
        db-test)
           echo "Starting Geoviite database (test)..."
           docker compose up -d --build --wait test-db-service
           echo "Geoviite Postgres (test) available at :$HOST_PORT_DB_TEST"
            ;;
        *)
            echo "Usage: $0 up {backend|ext-api|e2e-stack|db|db-test}"
            exit 1
            ;;
    esac
}

down() {
    case "$1" in
        all)
            echo "Stopping entire Geoviite stack..."
            docker compose down
            ;;
        backend)
            echo "Stopping Geoviite..."
            docker compose down backend
            ;;
        ext-api)
            echo "Stopping Geoviite external api service..."
            docker compose down ext-api
            ;;
        e2e-stack)
            echo "Stopping Geoviite E2E stack..."
            docker compose down e2e-backend
            docker compose down e2e-chrome-service
            ;;
        db)
           echo "Stopping Geoviite database..."
           docker compose down db-service
            ;;
        db-test)
           echo "Stopping Geoviite database (test)..."
           docker compose down test-db-service
            ;;
        *)
            echo "Usage: $0 down {all|backend|ext-api|e2e-stack|db|db-test}"
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
        integration-without-cache)
            echo "Running integration tests without cache..."
            docker compose run --rm --build backend-integration-tests-without-cache
            ;;
        e2e)
            run_e2e_tests "Running all end-to-end tests..." "*UI"
            ;;
        e2e-1)
            run_e2e_tests "Running end-to-end test group 1..." "*testgroup1.*UI"
            ;;
        e2e-2)
            run_e2e_tests "Running end-to-end test group 2..." "*testgroup2.*UI"
            ;;
        e2e-pattern)
            run_e2e_tests "Running end-to-end tests with custom pattern..." "*$2*"
            ;;
        *)
            echo "Usage: $0 test {unit|integration|integration-without-cache|e2e|e2e-1|e2e-2|e2e-pattern <pattern>}"
            exit 1
            ;;
    esac
}

clean() {
      case "$1" in
          unit)
              echo "Cleaning all Geoviite docker stack related images..."
              docker-compose down --rmi all
              ;;
          *)
              echo "Usage: $0 clean {images}"
              exit 1
              ;;
      esac
}

run_e2e_tests() {
    echo "$1 ($2)"
    docker compose run --rm --build --service-ports e2e-tests \
      ./gradlew --no-daemon --offline test --tests "$2"
}

case "$1" in
    up)
        up "$2"
        ;;
    down)
        down "$2"
        ;;
    test)
        test "$2" "$3"
        ;;
    *)
        echo "Usage: $0 {up|down|test|clean}"
        exit 1
        ;;
esac
