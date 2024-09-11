FROM postgres:16.3-alpine

# The available postgres versions can be checked with:
# aws rds describe-db-engine-versions --engine aurora-postgresql --query '*[].[EngineVersion]' --output text --profile geoviite-kehitys --region eu-west-1

# Also check that Postgres and PostGIS versions are compatible with Aurora, so that dev uses the same version as prod
# https://docs.aws.amazon.com/AmazonRDS/latest/AuroraPostgreSQLReleaseNotes/AuroraPostgreSQL.Extensions.html
ENV POSTGIS_VERSION=3.4.2

# Check SHA to ensure the downloaded file is not corrupted. To get this, run `sha256sum postgis.tar.gz` on the correct file on your own computer
ENV POSTGIS_SHA256=17aa8760a5c4fcb9a1fdc750c1c9aca0198a35dd1e320628064c43f178eefed2

RUN addgroup -g 1000 -S pg-user && adduser -G pg-user -u 1000 -S pg-user

RUN set -eux \
    && apk add --no-cache --virtual .fetch-deps \
        openssl \
        tar \
    \
    && wget -O postgis.tar.gz "https://github.com/postgis/postgis/archive/${POSTGIS_VERSION}.tar.gz" \
    && echo "${POSTGIS_SHA256} *postgis.tar.gz" | sha256sum -c - \
    && mkdir -p /usr/src/postgis \
    && tar \
        --extract \
        --file postgis.tar.gz \
        --directory /usr/src/postgis \
        --strip-components 1 \
    && rm postgis.tar.gz \
    \
    && apk add --no-cache --virtual .build-deps \
        geos-dev \
        proj-dev \
        proj-util \
        $DOCKER_PG_LLVM_DEPS \
        autoconf \
        automake \
        file \
        g++ \
        gcc \
        gettext-dev \
        json-c-dev \
        libtool \
        libxml2-dev \
        make \
        pcre2-dev \
        perl \
        protobuf-c-dev \
    \
    # build PostGIS
    && cd /usr/src/postgis \
    && gettextize \
    && ./autogen.sh \
    && ./configure \
        --without-raster \
        --enable-lto \
    && make -j$(nproc) \
    && make install \
    \
    # add .postgis-rundeps
    && apk add --no-cache --virtual .postgis-rundeps \
        geos \
        proj \
        json-c \
        libstdc++ \
        pcre2 \
        protobuf-c \
    \
    # clean
    && cd / \
    && rm -rf /usr/src/postgis \
    && apk del .fetch-deps .build-deps
