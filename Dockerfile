ARG IMAGE_BASE_BACKEND_BUILD=public.ecr.aws/docker/library/eclipse-temurin:17-jdk
ARG IMAGE_BASE_FRONTEND_BUILD=public.ecr.aws/docker/library/node:22-alpine
ARG IMAGE_BASE_DISTRIBUTION=public.ecr.aws/docker/library/eclipse-temurin:17-jre

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
    ./

RUN bash ./gradlew downloadDependencies --no-daemon


# Backend build
ARG IMAGE_BACKEND_DEPENDENCIES
FROM ${IMAGE_BACKEND_DEPENDENCIES} AS geoviite-backend-build

COPY ./infra/src/ ./src/

RUN bash ./gradlew assemble testClasses

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
    ./

# License file is purposefully copied to root due to webpack config.
COPY ./LICENSE.txt /

COPY ui/src ./src

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

WORKDIR /app

COPY --from=geoviite-distribution-build-combiner /app/infra-SNAPSHOT.jar ./infra-SNAPSHOT.jar

EXPOSE 8080/TCP
CMD ["java", "-XX:+UseContainerSupport", "-XX:MinRAMPercentage=25.0", "-XX:MaxRAMPercentage=80.0", "-jar", "infra-SNAPSHOT.jar"]
