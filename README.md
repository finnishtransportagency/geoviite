# Geoviite

## Purpose
Geoviite is an application for Finnish Transport Infrastructure Agency. Its users are professional operators, and the main purpose is:
- Storing and validating designed plans for railways, roads and waterways from various infrastructure projects
- Maintaining a nation-wide layout of railways for mapping purposes, using geometric data from the plans

## Development

### Modules
- ui: Frontend code
- infra: Backend services and database initialization
 
### Further documentation
The bulk of Geoviite documentation is in Finnish, found [HERE](./doc/readme.md)

### Code Conventions
See [CODE_CONVENTIONS.md](./CODE_CONVENTIONS.md) for code conventions and guidelines on code style etc.

### Contributing
We thank you for your interest, but we are not currently looking for community contributions.

For reporting vulnerabilities or security defects, see [SECURITY.md](./SECURITY.md)

### Licensing
[LICENSE.txt](./LICENSE.txt)

### Development Environment Requirements
- IntelliJ Idea (Ultimate)
    - Kotlin plugin
    - [KTFMT](./doc/ktfmt.md)
- JDK 17: Favor [Temurin](https://adoptium.net/temurin/releases/)
    - MacBook installation:
        - ``curl -O -L https://raw.githubusercontent.com/Homebrew/homebrew-cask/4565865e9d7c3d3018ee8aa67803ea68c54dde86/Casks/temurin.rb``
        - ``brew install --cask temurin.rb``
    - After installing the JDK, make sure Idea uses it in: File -> Project Structure -> (Left bar) Platform Settings -> SDKs
    - The project should already be set to use JDK 11 by default, but if there's a hickup, leave module settings to "project default" and set the project default to 11
- Docker
- Bash
- 7zip
- wget (in Mac)
