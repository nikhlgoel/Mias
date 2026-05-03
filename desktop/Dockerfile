# ============================================================================
# Mias — Desktop Model Server
# Runs Qwen3-Coder-Next via llama.cpp / vLLM (local GPU inference)
# ============================================================================
# ZERO-CLOUD: This container never contacts external services.
# ============================================================================

FROM nvidia/cuda:12.8.1-runtime-ubuntu24.04

LABEL maintainer="Mias Project"
LABEL description="Local-only desktop model server for Mias ecosystem"

ENV DEBIAN_FRONTEND=noninteractive
ENV MODEL_DIR=/models
ENV HOST=0.0.0.0
ENV PORT=8400

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 python3-pip python3-venv \
        build-essential cmake git \
        curl ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies (pinned versions, no cloud SDKs)
COPY desktop/requirements.txt .
RUN python3 -m venv /app/venv && \
    /app/venv/bin/pip install --upgrade pip && \
    /app/venv/bin/pip install --no-cache-dir --default-timeout=300 --retries=10 -r requirements.txt

COPY desktop/server.py .

# Model directory (mount at runtime with -v)
RUN mkdir -p ${MODEL_DIR}

EXPOSE ${PORT}

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["/app/venv/bin/python3", "server.py"]
CMD ["--host", "0.0.0.0", "--port", "8400"]
