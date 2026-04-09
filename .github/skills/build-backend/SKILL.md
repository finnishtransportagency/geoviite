---
name: build-backend
description: Guide for building Geoviite backend (infra directory)
---

Geoviite backend is built with gradle. Remember to use the project specific gradle wrapper, for example:
`cd infra && ./gradlew build`. You can assume that a suitable JDK is available. If it's not, inform the user that the
environment is incomplete, rather than trying to install it yourself.

When building the geoviite backend in its entirety like it's done in CI, you can also use the provided build.sh script.
It can be run regardless of current workdir, for example: `infra/build.sh`.
