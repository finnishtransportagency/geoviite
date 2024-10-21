ARG IMAGE_BACKEND=scratch
ARG IMAGE_FRONTEND=scratch

# Backend dependencies
FROM eclipse-temurin:17-jdk-alpine AS geoviite-backend-dependencies

WORKDIR /infra

COPY ./infra/gradle ./gradle
COPY \
    ./infra/build.gradle.kts \
    ./infra/settings.gradle.kts \
    ./infra/gradle.properties \
    ./infra/gradlew \
    ./

RUN ./gradlew downloadDependencies --no-daemon

# Backend build
FROM geoviite-backend-dependencies AS geoviite-backend-build
COPY ./infra/src/ ./src/

RUN ./gradlew assemble testClasses

# Frontend build
FROM node:20-alpine AS geoviite-frontend-build

WORKDIR /frontend

COPY ui/package.json ui/package-lock.json ./
RUN npm ci

COPY \
    ui/index.d.ts \
    ui/tsconfig.json \
    ui/.eslintrc.js \
    ui/webpack.config.js \
    ./

# License file is purposefully copied to root due to webpack config.
COPY ./LICENSE.txt /

COPY ui/src ./src

RUN npm run build

# Combined backend+frontend image
FROM ${IMAGE_BACKEND} AS geoviite-versioned-backend-build
FROM ${IMAGE_FRONTEND} AS geoviite-versioned-frontend-build
FROM eclipse-temurin:17-jdk-alpine AS geoviite-distribution-build-combiner

WORKDIR /app

COPY --from=geoviite-versioned-backend-build /infra/build/libs/infra-SNAPSHOT.jar ./infra-SNAPSHOT.jar
COPY --from=geoviite-versioned-frontend-build /frontend/dist ./tmp/BOOT-INF/classes/static/frontend

RUN jar uf infra-SNAPSHOT.jar -C ./tmp .

# Distribution image
FROM eclipse-temurin:17-jre-alpine AS geoviite-distribution-build
WORKDIR /app

ENV JAVA_OPTS="-XX:+UseContainerSupport-XX:MinRAMPercentage=25.0 -XX:MaxRAMPercentage=80.0"

COPY --from=geoviite-distribution-build-combiner /app/infra-SNAPSHOT.jar ./infra-SNAPSHOT.jar

EXPOSE 8080/TCP
CMD ["java", "-jar", "infra-SNAPSHOT.jar"]
