#!/usr/bin/env python3
"""
Project #001 {Kid} — MCP Bridge Server
Bridges Android ↔ Desktop model inference over the Tailscale P2P mesh.

This server:
  - Listens ONLY on 127.0.0.1 / Tailscale IP (no public interfaces)
  - Forwards inference requests from Android to the desktop model server
  - Implements MCP (Model Context Protocol) for tool/resource discovery
  - ZERO cloud dependencies — all traffic is local

Usage:
    python3 scripts/mcp_bridge.py
    python3 scripts/mcp_bridge.py --host 100.x.y.z --port 8401
"""

import argparse
import json
import logging
import socket
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Any

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [MCP-Bridge] %(levelname)s %(message)s",
)
logger = logging.getLogger(__name__)

DESKTOP_MODEL_SERVER = "http://127.0.0.1:8400"

# ---------------------------------------------------------------------------
# MCP Protocol Types
# ---------------------------------------------------------------------------

def make_jsonrpc_response(req_id: Any, result: dict) -> dict:
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def make_jsonrpc_error(req_id: Any, code: int, message: str) -> dict:
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


# ---------------------------------------------------------------------------
# MCP Bridge Handler
# ---------------------------------------------------------------------------

class MCPBridgeHandler(BaseHTTPRequestHandler):
    """Handles MCP JSON-RPC requests over HTTP."""

    def do_POST(self) -> None:
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length > 1_048_576:  # 1 MB max
            self._send_json(400, {"error": "Payload too large"})
            return

        body = self.rfile.read(content_length)
        try:
            request = json.loads(body)
        except json.JSONDecodeError:
            self._send_json(400, {"error": "Invalid JSON"})
            return

        req_id = request.get("id")
        method = request.get("method", "")

        logger.info("RPC method=%s id=%s", method, req_id)

        if method == "initialize":
            response = make_jsonrpc_response(req_id, {
                "protocolVersion": "2025-03-26",
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "kid-mcp-bridge", "version": "0.1.0"},
            })
        elif method == "tools/list":
            response = make_jsonrpc_response(req_id, {
                "tools": [
                    {
                        "name": "generate",
                        "description": "Generate text using the local desktop model (Qwen3-Coder-Next)",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "prompt": {"type": "string", "description": "The prompt to send"},
                                "max_tokens": {"type": "integer", "default": 512},
                            },
                            "required": ["prompt"],
                        },
                    },
                    {
                        "name": "health",
                        "description": "Check desktop model server health",
                        "inputSchema": {"type": "object", "properties": {}},
                    },
                ]
            })
        elif method == "tools/call":
            tool_name = request.get("params", {}).get("name", "")
            tool_args = request.get("params", {}).get("arguments", {})
            response = self._handle_tool_call(req_id, tool_name, tool_args)
        else:
            response = make_jsonrpc_error(req_id, -32601, f"Unknown method: {method}")

        self._send_json(200, response)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send_json(200, {"status": "ok", "service": "kid-mcp-bridge"})
        else:
            self._send_json(404, {"error": "Not found"})

    def _handle_tool_call(self, req_id: Any, tool_name: str, args: dict) -> dict:
        if tool_name == "health":
            try:
                import urllib.request
                req = urllib.request.Request(f"{DESKTOP_MODEL_SERVER}/health")
                with urllib.request.urlopen(req, timeout=5) as resp:
                    data = json.loads(resp.read())
                return make_jsonrpc_response(req_id, {
                    "content": [{"type": "text", "text": json.dumps(data)}]
                })
            except Exception as e:
                return make_jsonrpc_response(req_id, {
                    "content": [{"type": "text", "text": f"Desktop server unreachable: {e}"}],
                    "isError": True,
                })

        elif tool_name == "generate":
            prompt = args.get("prompt", "")
            max_tokens = args.get("max_tokens", 512)
            try:
                import urllib.request
                payload = json.dumps({"prompt": prompt, "max_tokens": max_tokens}).encode()
                req = urllib.request.Request(
                    f"{DESKTOP_MODEL_SERVER}/v1/completions",
                    data=payload,
                    headers={"Content-Type": "application/json"},
                )
                with urllib.request.urlopen(req, timeout=120) as resp:
                    data = json.loads(resp.read())
                return make_jsonrpc_response(req_id, {
                    "content": [{"type": "text", "text": json.dumps(data)}]
                })
            except Exception as e:
                return make_jsonrpc_response(req_id, {
                    "content": [{"type": "text", "text": f"Inference failed: {e}"}],
                    "isError": True,
                })

        return make_jsonrpc_error(req_id, -32602, f"Unknown tool: {tool_name}")

    def _send_json(self, status: int, data: dict) -> None:
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: Any) -> None:
        logger.debug(format, *args)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="Kid MCP Bridge Server")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=8401, help="Port (default: 8401)")
    args = parser.parse_args()

    # Security: refuse to bind to 0.0.0.0
    if args.host == "0.0.0.0":
        logger.error("Refusing to bind to 0.0.0.0 — Kid is local-only. Use 127.0.0.1 or a Tailscale IP.")
        sys.exit(1)

    server = HTTPServer((args.host, args.port), MCPBridgeHandler)
    logger.info("MCP Bridge listening on %s:%d", args.host, args.port)
    logger.info("Desktop model server target: %s", DESKTOP_MODEL_SERVER)
    logger.info("Press Ctrl+C to stop")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down MCP bridge")
        server.shutdown()


if __name__ == "__main__":
    main()
