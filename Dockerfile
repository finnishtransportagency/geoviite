ARG IMAGE_BASE_BACKEND_BUILD=public.ecr.aws/docker/library/eclipse-temurin:25-jdk-noble
ARG IMAGE_BASE_FRONTEND_BUILD=public.ecr.aws/docker/library/node:22-alpine
ARG IMAGE_BASE_DISTRIBUTION=public.ecr.aws/docker/library/eclipse-temurin:25-jre-noble

ARG IMAGE_BACKEND_DEPENDENCIES="geoviite-backend-dependencies"
ARG IMAGE_BACKEND=geoviite-backend-build
ARG IMAGE_FRONTEND=geoviite-frontend-build

# Backend dependencies
FROM ${IMAGE_BASE_BACKEND_BUILD} AS geoviite-backend-dependencies

WORKDIR /infra

COPY ./infra/gradle ./gradle
COPY \
    ./infra/build.gradle.kts \
    ./infra/settings.gradle.kts \
    ./infra/gradle.properties \
    ./infra/gradlew \
    ./infra/allowed-licenses.json \
    ./

RUN bash ./gradlew downloadDependencies --no-daemon --no-configuration-cache


# Backend build
ARG IMAGE_BACKEND_DEPENDENCIES
FROM ${IMAGE_BACKEND_DEPENDENCIES} AS geoviite-backend-build

# License file is purposefully copied to root, matching the path the Jar task in infra/build.gradle.kts expects
# (rootProject.projectDir/.. relative to the infra module), and to also bundle it in the license report below.
COPY ./LICENSE.txt /

COPY ./infra/src/ ./src/

RUN bash ./gradlew assemble testClasses
# License check uses non-serializable classes, so config-cache isn't supported.
# See: https://github.com/jk1/Gradle-License-Report/issues/255
RUN bash ./gradlew checkLicense -Dorg.gradle.configuration-cache=false \
    && cp /LICENSE.txt build/

# Frontend build
ARG IMAGE_BASE_FRONTEND_BUILD
FROM ${IMAGE_BASE_FRONTEND_BUILD} AS geoviite-frontend-build

WORKDIR /frontend

COPY ui/package.json ui/package-lock.json ./
RUN npm ci

COPY \
    ui/index.d.ts \
    ui/tsconfig.json \
    ui/eslint.config.mjs \
    ui/webpack.config.js \
    ui/jest.config.json \
    ./

# License file is purposefully copied to root due to webpack config.
COPY ./LICENSE.txt /

COPY ui/src ./src
COPY ui/test ./test
COPY ui/__mocks__ ./__mocks__

RUN npm test -- --ci
RUN npm run build

# Combined backend+frontend image
ARG IMAGE_BACKEND
ARG IMAGE_FRONTEND

FROM ${IMAGE_BACKEND} AS geoviite-versioned-backend-build
FROM ${IMAGE_FRONTEND} AS geoviite-versioned-frontend-build

FROM ${IMAGE_BASE_BACKEND_BUILD} AS geoviite-distribution-build-combiner

WORKDIR /app

COPY --from=geoviite-versioned-backend-build /infra/build/libs/infra-SNAPSHOT.jar ./infra-SNAPSHOT.jar
COPY --from=geoviite-versioned-frontend-build /frontend/dist ./tmp/BOOT-INF/classes/static/frontend

RUN jar uf infra-SNAPSHOT.jar -C ./tmp .

# Distribution image
ARG IMAGE_BASE_DISTRIBUTION
FROM ${IMAGE_BASE_DISTRIBUTION} AS geoviite-distribution-build

RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=geoviite-distribution-build-combiner /app/infra-SNAPSHOT.jar ./infra-SNAPSHOT.jar
COPY --from=geoviite-versioned-backend-build /infra/build/reports/dependency-license ./dependency-license
COPY --from=geoviite-versioned-backend-build /infra/build/LICENSE.txt ./LICENSE.txt

EXPOSE 8080/TCP
CMD ["java", "-XX:+UseContainerSupport", "-XX:MinRAMPercentage=25.0", "-XX:MaxRAMPercentage=80.0", "-jar", "infra-SNAPSHOT.jar"]
