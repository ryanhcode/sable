# Build container versions pinned on 04-18-26
FROM messense/cargo-xwin@sha256:9856b895265d4966f212228ba64802cf89337e2a2a537aa2533c1b8784cbc81b

ARG RUST_VERSION
RUN rustup default ${RUST_VERSION}
RUN rustup target add x86_64-pc-windows-msvc
RUN rustup target add aarch64-pc-windows-msvc
RUN cargo xwin cache xwin

WORKDIR /
