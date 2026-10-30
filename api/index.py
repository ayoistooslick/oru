"""Production entrypoint for the ORU API.

Freebuff installs the root requirements.txt and runs the Python files in this
folder while the built frontend in dist/ is served separately. The routes are
the same ones the local server exposes; this file only adapts the shared
FastAPI application to whichever calling convention the host uses.

The host publishes this one function at `/api`, so requests arrive with the
path they want in the `__path` query parameter and the application restores it
before routing. Failures are written to the log rather than the response, and
every response carries the path it was routed as `x-oru-path`, which is how the
routing of the host can be checked from the outside.
"""

from __future__ import annotations

import asyncio
import base64
import json
import sys
import traceback
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import quote, unquote_to_bytes

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Loading the application is the step most likely to break on the host: a
# missing package, a bad setting or a read-only disk all fail here. The error
# is kept for the log so a broken deployment can still answer a request.
_LOAD_ERROR: str | None = None

try:
    from app.main import app as fastapi_app
    from app.main import ensure_schema
except Exception:  # noqa: BLE001 - the traceback is the point of keeping it
    _LOAD_ERROR = traceback.format_exc()
    fastapi_app = None

    def ensure_schema() -> None:  # type: ignore[misc]
        return None

_ready = False


def _prepare() -> None:
    global _ready
    if not _ready:
        if _LOAD_ERROR:
            print(_LOAD_ERROR, file=sys.stderr)  # the log keeps the detail
            raise RuntimeError("the API could not start")
        ensure_schema()
        _ready = True


async def _error(send, status: int, path: str = "") -> None:
    """Answer in the API's own error shape, carrying the path that was seen."""
    headers = [(b"content-type", b"application/json")]
    if path:
        headers.append((b"x-oru-path", path.encode("utf-8", "replace")))
    body = json.dumps({"error": {"code": "service_error", "message": "Something went wrong. Try again."}})
    await send({"type": "http.response.start", "status": status, "headers": headers})
    await send({"type": "http.response.body", "body": body.encode("utf-8")})


async def _call(scope, receive, send, path: str) -> None:
    """Run the request through the application; failures go to the log."""
    started = False

    async def send_with_path(message):
        nonlocal started
        if message["type"] == "http.response.start":
            started = True
            headers = list(message.get("headers", []))
            headers.append((b"x-oru-path", path.encode("utf-8", "replace")))
            message = {"type": "http.response.start", "status": message["status"], "headers": headers}
        await send(message)

    try:
        _prepare()
        await fastapi_app(scope, receive, send_with_path)
    except Exception:  # noqa: BLE001 - details belong in the log, not the reply
        if started:
            raise
        traceback.print_exc()
        await _error(send, 500, path)


def _requested_path(scope: dict) -> str:
    """The path the application will route, which may arrive in `__path`."""
    query = scope.get("query_string", b"")
    marker = b"__path="
    if marker not in query:
        return scope.get("path", "")
    value = query.split(marker, 1)[1].split(b"&", 1)[0]
    target = unquote_to_bytes(value).decode("utf-8", "replace")
    return target.partition("?")[0]


async def app(scope, receive, send):
    """ASGI entrypoint: the host may call the module's `app` directly."""
    if scope.get("type") != "http":
        return
    await _call(scope, receive, send, _requested_path(scope))


# Hosts look for either of these names; both point at the same application.
application = app
asgi_app = app


def handler(*args, **kwargs):
    """Accept both an ASGI call and a serverless (event, context) call."""
    if len(args) == 3:
        return app(*args, **kwargs)
    if args and isinstance(args[0], dict):
        return _serverless_response(args[0])
    raise TypeError("handler expects (scope, receive, send) or (event, context)")


def _event_headers(event: dict) -> list[tuple[bytes, bytes]]:
    multi = event.get("multiValueHeaders") or {}
    source = multi if multi else (event.get("headers") or {})
    pairs = []
    for key, value in source.items():
        items = value if isinstance(value, list) else [value]
        for item in items:
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
