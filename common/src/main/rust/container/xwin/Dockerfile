# Build container versions pinned on 04-18-26
FROM messense/cargo-xwin@sha256:f3e680c963eff35f77908c78d97ac095d4bef1f69e4fe200447576b34cd251cb

ARG RUST_VERSION
RUN rustup default ${RUST_VERSION}
RUN rustup target add x86_64-pc-windows-msvc
RUN rustup target add aarch64-pc-windows-msvc
RUN cargo xwin cache xwin

WORKDIR /
