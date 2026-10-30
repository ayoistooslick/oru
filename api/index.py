"""Production entrypoint for the ORU API.

Freebuff installs the root requirements.txt and runs the Python files in this
folder while the built frontend in dist/ is served separately. The routes are
the same ones the local server exposes; this file only adapts the shared
FastAPI application to whichever calling convention the host uses.
"""

from __future__ import annotations

import asyncio
import base64
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.main import app, ensure_schema  # noqa: E402  (needs ROOT on sys.path)

# Hosts look for either of these names; both point at the same application.
application = app
asgi_app = app

_ready = False


def _prepare() -> None:
    global _ready
    if not _ready:
        ensure_schema()
        _ready = True


def handler(*args, **kwargs):
    """Accept both an ASGI call and a serverless (event, context) call."""
    _prepare()
    if len(args) == 3:
        return app(*args, **kwargs)
    if args and isinstance(args[0], dict):
        return _serverless_response(args[0])
    raise TypeError("handler expects (scope, receive, send) or (event, context)")


def _event_headers(event: dict) -> list[tuple[bytes, bytes]]:
    multi = event.get("multiValueHeaders") or {}
    source = multi if multi else (event.get("headers") or {})
    pairs: list[tuple[bytes, bytes]] = []
    for key, value in source.items():
        values = value if isinstance(value, list) else [value]
        for item in values:
            if item is None:
                continue
            pairs.append((key.lower().encode("latin-1"), str(item).encode("latin-1")))
    return pairs


def _event_query_string(event: dict) -> str:
    if event.get("rawQueryString") is not None:
        return event["rawQueryString"]
    parts = []
    for key, value in (event.get("multiValueQueryStringParameters") or {}).items():
        for item in value:
            parts.append(f"{quote(key)}={quote(item or '')}")
    for key, value in (event.get("queryStringParameters") or {}).items():
        parts.append(f"{quote(key)}={quote(value or '')}")
    return "&".join(parts)


def _serverless_response(event: dict) -> dict:
    """Run one HTTP request through the ASGI app and return a Lambda-style result."""
    headers = _event_headers(event)
    header_map = {key.decode("latin-1"): value.decode("latin-1") for key, value in headers}
    http = (event.get("requestContext") or {}).get("http") or {}
    method = http.get("method") or event.get("httpMethod") or "GET"
    path = event.get("rawPath") or event.get("path") or "/"
    body = event.get("body") or ""
    if event.get("isBase64Encoded"):
        body = base64.b64decode(body)
    elif isinstance(body, str):
        body = body.encode("utf-8")

    scope = {
        "type": "http",
        "asgi": {"version": "3.0", "spec_version": "2.3"},
        "http_version": "1.1",
        "method": method,
        "scheme": header_map.get("x-forwarded-proto", "https"),
        "path": path,
        "raw_path": path.encode("utf-8"),
        "query_string": _event_query_string(event).encode("utf-8"),
        "root_path": "",
        "headers": headers,
        "client": (http.get("sourceIp") or "0.0.0.0", 0),
        "server": (header_map.get("host", "localhost").split(":")[0], 443),
    }

    async def receive():
        return {"type": "http.request", "body": body, "more_body": False}

    captured: dict = {"status": 500, "headers": [], "body": bytearray()}

    async def send(message):
        if message["type"] == "http.response.start":
            captured["status"] = message["status"]
            captured["headers"] = message.get("headers", [])
        elif message["type"] == "http.response.body":
            captured["body"] += message.get("body", b"")

    def run():
        asyncio.run(app(scope, receive, send))

    try:
        asyncio.get_running_loop()
    except RuntimeError:
        run()
    else:
        # The host is already inside an event loop, so run this request elsewhere.
        with ThreadPoolExecutor(max_workers=1) as pool:
            pool.submit(run).result()

    payload = bytes(captured["body"])
    response_headers = {}
    for key, value in captured["headers"]:
        name, text = key.decode("latin-1"), value.decode("latin-1")
        if name.lower() in {"content-length", "content-encoding"}:
            continue
        response_headers[name] = f"{response_headers[name]}, {text}" if name in response_headers else text
    try:
        body_text = payload.decode("utf-8")
        is_base64 = False
    except UnicodeDecodeError:
        body_text = base64.b64encode(payload).decode("ascii")
        is_base64 = True
    return {"statusCode": captured["status"], "headers": response_headers, "body": body_text, "isBase64Encoded": is_base64}
