#!/usr/bin/env python3
"""
Project — Mias Desktop Model Server
# Release: #001 (first working build)
# package: io.mias.app
Serves Qwen3-Coder-Next via llama-cpp-python using the Model Context Protocol (MCP) over HTTP.

Endpoints:
    GET  /health            → Health check
    POST /rpc               → MCP JSON-RPC endpoint

ZERO-CLOUD: This server runs entirely on local hardware and expects Tailscale connectivity.
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
    from fastapi import FastAPI, HTTPException, Request, Depends
    from fastapi.responses import JSONResponse
    from fastapi.security import APIKeyHeader
    import uvicorn
except ImportError:
    logger.error("Missing dependencies. Run: pip install -r requirements.txt")
    sys.exit(1)

app = FastAPI(title="Mias Desktop Model Server", version="release-001")

# ---------------------------------------------------------------------------
# Authentication (shared secret for non-Tailscale environments)
# ---------------------------------------------------------------------------

API_TOKEN = os.environ.get("MIAS_TOKEN", "")
api_key_header = APIKeyHeader(name="X-Mias-Token", auto_error=False)


async def verify_token(api_key: str = Depends(api_key_header)):
    """Verify shared secret if MIAS_TOKEN is configured."""
    if API_TOKEN and api_key != API_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid or missing X-Mias-Token")
    return api_key

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

        # Fallback empty check to allow startup testing even without a model path
        if not os.path.exists(MODEL_DIR):
            logger.warning(f"MODEL_DIR {MODEL_DIR} not found, initializing LLM engine stub only.")
            return None

        model_files = [f for f in os.listdir(MODEL_DIR) if f.endswith(".gguf")]
        if not model_files:
            logger.warning(f"No .gguf model found in {MODEL_DIR}. LLM will operate as a stub.")
            return None

        model_path = os.path.join(MODEL_DIR, model_files[0])
        logger.info("Loading model: %s", model_path)
        _llm = Llama(
            model_path=model_path,
            n_ctx=8192,
            n_gpu_layers=-1,
            verbose=False,
        )
        logger.info("Model loaded successfully")
    return _llm

# ---------------------------------------------------------------------------
# MCP Handlers
# ---------------------------------------------------------------------------

async def handle_initialize(params):
    return {
        "protocolVersion": "2025-03-26",
        "serverInfo": {
            "name": "Mias Desktop Server",
            "version": "release-001"
        },
        "capabilities": {
            "tools": {"listChanged": False}
        }
    }

async def handle_tools_list(params):
    return {
        "tools": [
            {
                "name": "generate",
                "description": "Generate text using the high-parameter desktop model",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "prompt": {"type": "string"},
                        "max_tokens": {"type": "string"}
                    },
                    "required": ["prompt"]
                }
            }
        ]
    }

async def handle_tools_call(params):
    name = params.get("name")
    args = params.get("arguments", {})
    
    if name == "generate":
        prompt = args.get("prompt", "")
        max_tokens = int(args.get("max_tokens", "2048"))
        
        llm = get_llm()
        if not llm:
             # Just an echo/stub if model is not loaded for dev loop testing
             return f"[DESKTOP-STUB] I received your prompt: '{prompt}'. Native LLM mapping is bypassed."
             
        start = time.perf_counter()
        output = llm(
            prompt,
            max_tokens=max_tokens,
            temperature=0.7,
            top_p=0.9,
        )
        elapsed = (time.perf_counter() - start) * 1000
        text = output["choices"][0]["text"]
        logger.info(f"Generated {output['usage']['completion_tokens']} tokens in {elapsed:.2f}ms")
        return text
        
    return f"Error: Unknown tool '{name}'"

# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    model_loaded = _llm is not None
    model_files = []
    if os.path.exists(MODEL_DIR):
        model_files = [f for f in os.listdir(MODEL_DIR) if f.endswith(".gguf")]
    return {
        "status": "ready" if model_loaded else "loading",
        "model": model_files[0] if model_files else "none",
        "version": "release-001",
        "service": "mias-desktop-server",
        "model_dir": MODEL_DIR,
    }

@app.post("/rpc", dependencies=[Depends(verify_token)])
async def mcp_rpc(request: Request):
    data = await request.json()
    req_id = data.get("id")
    method = data.get("method")
    params = data.get("params", {})
    
    result = None
    error = None
    
    try:
        if method == "initialize":
            result = await handle_initialize(params)
        elif method == "notifications/initialized":
            # Client acknowledgment — no response needed for notifications
            result = {}
        elif method == "tools/list":
            result = await handle_tools_list(params)
        elif method == "tools/call":
            result = await handle_tools_call(params)
        else:
            error = {"code": -32601, "message": f"Method '{method}' not supported."}
    except Exception as e:
        logger.exception("MCP execution failed")
        error = {"code": -32000, "message": str(e)}

    response = {"jsonrpc": "2.0"}
    if req_id is not None:
        response["id"] = req_id
    if error:
        response["error"] = error
    else:
        response["result"] = result
        
    return JSONResponse(content=response)

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Mias Desktop MCP Server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8401)
    args = parser.parse_args()

    logger.info("Starting desktop MCP server on %s:%d", args.host, args.port)
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")
