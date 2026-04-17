FROM messense/cargo-zigbuild:latest

# Rust
ENV RUST_VERSION=nightly-2026-01-29

RUN rustup default $RUST_VERSION
RUN rustup target add x86_64-pc-windows-gnu
RUN rustup target add aarch64-apple-darwin
RUN rustup target add x86_64-unknown-linux-gnu
RUN rustup target add aarch64-unknown-linux-gnu

WORKDIR /
