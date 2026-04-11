"""
MCP Bridge — Contract Tests

Tests the MCP bridge server's JSON-RPC protocol compliance.
These tests start the bridge in a subprocess and send requests to it.
"""

import json
import os
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.error

import pytest

BRIDGE_SCRIPT = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "../../scripts/mcp_bridge.py")
)
BRIDGE_HOST = "127.0.0.1"
BRIDGE_PORT = 18401  # Use non-standard port for tests


def _port_is_open(host: str, port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except (OSError, ConnectionRefusedError):
        return False


def _send_jsonrpc(method: str, params: dict = None, req_id: int = 1) -> dict:
    payload = {"jsonrpc": "2.0", "id": req_id, "method": method}
    if params:
        payload["params"] = params
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        f"http://{BRIDGE_HOST}:{BRIDGE_PORT}/",
        data=data,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read())


@pytest.fixture(scope="module")
def bridge_server():
    """Start the MCP bridge as a subprocess for the test module."""
    if not os.path.exists(BRIDGE_SCRIPT):
        pytest.skip("MCP bridge script not found")

    proc = subprocess.Popen(
        [sys.executable, BRIDGE_SCRIPT, "--host", BRIDGE_HOST, "--port", str(BRIDGE_PORT)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    # Wait for server to start
    for _ in range(30):
        if _port_is_open(BRIDGE_HOST, BRIDGE_PORT):
            break
        time.sleep(0.1)
    else:
        proc.terminate()
        pytest.fail("MCP bridge did not start in time")

    yield proc

    proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()


class TestMcpBridgeContract:
    """Verify MCP bridge follows the JSON-RPC / MCP protocol."""

    def test_health_endpoint(self, bridge_server):
        """GET /health returns ok status."""
        req = urllib.request.Request(f"http://{BRIDGE_HOST}:{BRIDGE_PORT}/health")
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read())
        assert data["status"] == "ok"
        assert "kid-mcp-bridge" in data["service"]

    def test_initialize(self, bridge_server):
        """initialize method returns protocol version and capabilities."""
        resp = _send_jsonrpc("initialize")
        assert "result" in resp
        assert resp["result"]["protocolVersion"] == "2025-03-26"
        assert "tools" in resp["result"]["capabilities"]
        assert resp["result"]["serverInfo"]["name"] == "kid-mcp-bridge"

    def test_tools_list(self, bridge_server):
        """tools/list returns available tools."""
        resp = _send_jsonrpc("tools/list")
        assert "result" in resp
        tools = resp["result"]["tools"]
        tool_names = [t["name"] for t in tools]
        assert "generate" in tool_names
        assert "health" in tool_names

    def test_tools_list_schema(self, bridge_server):
        """Each tool has a valid inputSchema."""
        resp = _send_jsonrpc("tools/list")
        for tool in resp["result"]["tools"]:
            assert "name" in tool
            assert "description" in tool
            assert "inputSchema" in tool
            assert tool["inputSchema"]["type"] == "object"

    def test_unknown_method_returns_error(self, bridge_server):
        """Unknown methods return JSON-RPC error."""
        resp = _send_jsonrpc("nonexistent/method")
        assert "error" in resp
        assert resp["error"]["code"] == -32601

    def test_health_tool_call(self, bridge_server):
        """tools/call with 'health' tool returns content (may report server unreachable)."""
        resp = _send_jsonrpc("tools/call", {"name": "health", "arguments": {}})
        assert "result" in resp
        assert "content" in resp["result"]
        assert len(resp["result"]["content"]) > 0
        assert resp["result"]["content"][0]["type"] == "text"

    def test_jsonrpc_response_format(self, bridge_server):
        """All responses include jsonrpc version and request id."""
        resp = _send_jsonrpc("initialize", req_id=42)
        assert resp["jsonrpc"] == "2.0"
        assert resp["id"] == 42

    def test_404_for_unknown_path(self, bridge_server):
        """GET on unknown path returns 404."""
        req = urllib.request.Request(f"http://{BRIDGE_HOST}:{BRIDGE_PORT}/nonexistent")
        try:
            urllib.request.urlopen(req, timeout=5)
            assert False, "Expected 404"
        except urllib.error.HTTPError as e:
            assert e.code == 404
