#!/usr/bin/env python3
"""
Project #001 {Kid} — Desktop Model Server
Serves Qwen3-Coder-Next (or any GGUF model) via llama-cpp-python.

Endpoints:
    GET  /health            → Health check
    POST /v1/completions    → Text completion (OpenAI-compatible format, LOCAL only)

ZERO-CLOUD: This server runs entirely on local hardware.
"""

import argparse
import json
import logging
import os
import sys
import time

logging.basicConfig(level=logging.INFO, format="%(asctime)s [DesktopServer] %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

try:
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel, Field
    import uvicorn
except ImportError:
    logger.error("Missing dependencies. Run: pip install -r requirements.txt")
    sys.exit(1)

app = FastAPI(title="Kid Desktop Model Server", version="0.1.0")

# ---------------------------------------------------------------------------
# Model loading
# ---------------------------------------------------------------------------

_llm = None
MODEL_DIR = os.environ.get("MODEL_DIR", "/models")


def get_llm():
    global _llm
    if _llm is None:
        try:
            from llama_cpp import Llama
        except ImportError:
            raise HTTPException(500, "llama-cpp-python not installed")

        model_files = [f for f in os.listdir(MODEL_DIR) if f.endswith(".gguf")]
        if not model_files:
            raise HTTPException(503, f"No .gguf model found in {MODEL_DIR}")

        model_path = os.path.join(MODEL_DIR, model_files[0])
        logger.info("Loading model: %s", model_path)
        _llm = Llama(
            model_path=model_path,
            n_ctx=8192,
            n_gpu_layers=-1,  # Offload all layers to GPU
            verbose=False,
        )
        logger.info("Model loaded successfully")
    return _llm


# ---------------------------------------------------------------------------
# API Models
# ---------------------------------------------------------------------------

class CompletionRequest(BaseModel):
    prompt: str
    max_tokens: int = Field(default=512, ge=1, le=8192)
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    top_p: float = Field(default=0.9, ge=0.0, le=1.0)


class CompletionResponse(BaseModel):
    text: str
    tokens_generated: int
    elapsed_ms: float


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {"status": "ok", "service": "kid-desktop-server", "model_dir": MODEL_DIR}


@app.post("/v1/completions", response_model=CompletionResponse)
def completions(req: CompletionRequest):
    llm = get_llm()
    start = time.perf_counter()

    output = llm(
        req.prompt,
        max_tokens=req.max_tokens,
        temperature=req.temperature,
        top_p=req.top_p,
    )

    elapsed = (time.perf_counter() - start) * 1000
    text = output["choices"][0]["text"]
    tokens = output["usage"]["completion_tokens"]

    return CompletionResponse(text=text, tokens_generated=tokens, elapsed_ms=round(elapsed, 2))


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Kid Desktop Model Server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8400)
    args = parser.parse_args()

    logger.info("Starting desktop model server on %s:%d", args.host, args.port)
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")
