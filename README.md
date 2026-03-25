# Geoviite

## Purpose

Geoviite is an application for Finnish Transport Infrastructure Agency. Its users are professional operators, and the
main purpose is:

- Storing and validating designed plans for railways, roads and waterways from various infrastructure projects
- Maintaining a nation-wide layout of railways for mapping purposes, using geometric data from the plans

## Modules and related documentation

- ui: Frontend code
- infra: Backend services and database initialization
- General documentation
    - [Code conventions](./CODE_CONVENTIONS.md)
    - See [LICENSE.txt](./LICENSE.txt)
    - Project documentation (in Finnish): [doc/readme.md](./doc/readme.md)

## Development

### Contributing

We thank you for your interest, but we are not currently looking for community contributions.

For reporting vulnerabilities or security defects, see [SECURITY.md](./SECURITY.md)

### Requirements

- IntelliJ Idea (Ultimate)
    - Kotlin plugin
- JDK 17: Favor [Temurin](https://adoptium.net/temurin/releases/)
    - MacBook installation:
        ```
        curl -O -L https://raw.githubusercontent.com/Homebrew/homebrew-cask/4565865e9d7c3d3018ee8aa67803ea68c54dde86/Casks/temurin.rb
        ```
        ```
        brew install --cask temurin.rb
        ```
    - After installing the JDK, make sure Idea uses it in: File -> Project Structure -> (Left bar) Platform Settings ->
      SDKs
- Docker with Docker Compose
- Bash
- 7zip
- wget (in Mac)

### Running the full stack locally with Docker

The project includes a `geoviite.sh` helper script for building and running the entire stack (backend, frontend, and
database) using Docker Compose. Configuration is defined in `.env` and `docker-compose.yml`.

```bash
# Start the backend (includes frontend, database, and all dependencies)
./geoviite.sh up backend

# Start the external API service
./geoviite.sh up ext-api

# Start just the database
./geoviite.sh up postgres

# Stop everything
./geoviite.sh down all
```

#### Running tests

```bash
# Unit tests
./geoviite.sh test unit

# Integration tests (starts a test database automatically)
./geoviite.sh test integration

# End-to-end tests
./geoviite.sh test e2e
```

#### Cleaning up

```bash
# Remove all Geoviite Docker images and build cache
./geoviite.sh clean images
```
