FROM postgres:15.3-alpine

ENV POSTGIS_VERSION 3.3.3
ENV POSTGIS_SHA256 441bb33bd2be362c7c463161207f290a1c25083027a069322696eeec74fc916b

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
