FROM ubuntu:24.04

ARG RUST_VERSION
ARG NDK_VERSION=r27c

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    curl unzip ca-certificates gcc \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" \
    -o /tmp/ndk.zip \
    && unzip /tmp/ndk.zip -d /opt \
    && mv /opt/android-ndk-${NDK_VERSION} /opt/ndk \
    && rm /tmp/ndk.zip

ENV ANDROID_NDK_HOME=/opt/ndk

RUN curl https://sh.rustup.rs -sSf | sh -s -- -y --no-modify-path --default-toolchain none
ENV PATH="/root/.cargo/bin:${PATH}"

RUN rustup toolchain install ${RUST_VERSION}
RUN rustup default ${RUST_VERSION}

RUN rustup target add aarch64-linux-android
RUN rustup target add x86_64-linux-android

WORKDIR /