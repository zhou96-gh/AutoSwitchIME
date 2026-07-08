FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV SHELL=/bin/bash

ARG http_proxy
ARG https_proxy
ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV http_proxy=${http_proxy:-}
ENV https_proxy=${https_proxy:-}
ENV HTTP_PROXY=${HTTP_PROXY:-}
ENV HTTPS_PROXY=${HTTPS_PROXY:-}

SHELL ["/bin/bash", "-c"]

# === 基础工具（合并为一个 RUN 减少层数）===
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates git gnupg zip unzip pkg-config build-essential \
    && rm -rf /var/lib/apt/lists/*

# === JDK 21 (Eclipse Temurin) ===
RUN curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && apt-get update && apt-get install -y temurin-21-jdk \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# === Rust + mingw-w64 x86-64 only ===
RUN apt-get update && apt-get install -y --no-install-recommends \
    g++-mingw-w64-x86-64 binutils-mingw-w64-x86-64 \
    && rm -rf /var/lib/apt/lists/* \
    && curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable

ENV PATH="/root/.cargo/bin:${PATH}"
RUN rustup target add x86_64-pc-windows-gnu

# === Node.js 22 (NodeSource) ===
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# === VSCE ===
RUN npm install -g @vscode/vsce

# === 验证 ===
RUN echo "=== Versions ===" && \
    java -version 2>&1 | head -1 && \
    node --version && npm --version && \
    rustc --version && cargo --version && \
    rustup target list --installed && \
    x86_64-w64-mingw32-gcc --version | head -1 && \
    echo "=== All set ==="

WORKDIR /workspace
CMD ["bash"]
