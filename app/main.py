import logging
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from time import monotonic
from urllib.parse import unquote_to_bytes

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.middleware.base import BaseHTTPMiddleware
from sqlalchemy import inspect, text

from app.api import accounts, community, moderation, music
from app.social import render_document, request_origin
from app.config import BASE_DIR, settings
from app.database import Base, engine

logger = logging.getLogger("oru")

FRONTEND_DIST = BASE_DIR / "dist"


class SimpleRateLimitMiddleware(BaseHTTPMiddleware):
    """Per-process safety net. Use a shared edge limiter for multi-worker production."""
    def __init__(self, app, limit: int = 120, window: int = 60):
        super().__init__(app)
        self.limit = limit
        self.window = window
        self.requests = defaultdict(deque)

    async def dispatch(self, request: Request, call_next):
        if request.url.path.startswith("/api/"):
            client_ip = request.client.host if request.client else "unknown"
            is_auth = request.url.path in {"/api/v1/auth/login", "/api/v1/auth/register"}
            key = f"{client_ip}:{'auth' if is_auth else 'api'}"
            limit = 10 if is_auth else self.limit
            now = monotonic()
            bucket = self.requests[key]
            while bucket and bucket[0] <= now - self.window:
                bucket.popleft()
            if len(bucket) >= limit:
                return JSONResponse(status_code=429, content={"error": {"code": "rate_limited", "message": "Too many requests. Try again shortly."}})
            bucket.append(now)
        return await call_next(request)


class HostPathMiddleware:
    """Put the real API path back when the host can only route one path.

    The static host publishes a single function route (`/api`) and serves every
    other path as a file, so the frontend carries the path it wants in the
    `__path` query parameter. Restoring it here, before routing, means the dev
    server, a single process serving the build and that function all behave the
    same way: they all end up in this application.
    """

    PARAM = b"__path="

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        await self.app(self.rewrite(scope), receive, send)

    def rewrite(self, scope):
        if scope.get("type") != "http":
            return scope
        query = scope.get("query_string", b"")
        if self.PARAM not in query:
            return scope
        value = query.split(self.PARAM, 1)[1].split(b"&", 1)[0]
        # The frontend encodes the whole path with encodeURIComponent, so decode
        # first and only then split the query string off. Decoding afterwards
        # would leave `limit%3D5`, which is a key, not a parameter.
        target = unquote_to_bytes(value).decode("utf-8", "replace")
        path, _, extra = target.partition("?")
        if not path.startswith("/") or ".." in path:
            return scope
        remaining = [part for part in query.split(b"&") if part and not part.startswith(self.PARAM)]
        rewritten = dict(scope)
        rewritten["path"] = path
        rewritten["raw_path"] = path.encode("utf-8", "replace")
        rewritten["query_string"] = b"&".join(([extra.encode()] if extra else []) + remaining)
        return rewritten


def validation_error_handler(request: Request, exc: RequestValidationError):
    details = [{"field": ".".join(str(part) for part in item["loc"] if part != "body"), "message": item["msg"]} for item in exc.errors()]
    return JSONResponse(status_code=422, content={"error": {"code": "validation_error", "message": "Some of the submitted data is invalid", "details": details}})


def http_error_handler(request: Request, exc: HTTPException):
    detail = exc.detail
    error = detail if isinstance(detail, dict) and "code" in detail and "message" in detail else {"code": "http_error", "message": str(detail)}
    return JSONResponse(status_code=exc.status_code, content={"error": error}, headers=exc.headers)


def unexpected_error_handler(request: Request, exc: Exception):
    # Keep internal details out of public responses. The server logs retain the traceback.
    logger.exception("Unexpected request error")
    return JSONResponse(status_code=500, content={"error": {"code": "internal_error", "message": "Something went wrong"}})


def index_response(request: Request):
    """The single page, with the share tags rewritten for this route.

    Crawlers for WhatsApp, Slack, Discord, X and the rest fetch this HTML
    without running JavaScript, so the preview they show has to be here.
    """
    source = FRONTEND_DIST / "index.html"
    try:
        document = source.read_text(encoding="utf-8")
    except OSError:
        return FileResponse(source)
    try:
        document = render_document(document, request_origin(request), request.url.path)
    except Exception:
        logger.exception("Could not render page metadata")
    return HTMLResponse(document)


def spa_response(request: Request, full_path: str):
    """Serve a built asset, or index.html so client-side routes survive a reload.

    Kept as a plain route rather than a StaticFiles subclass: the static handler's
    signature changed between Starlette releases, and this works on either.
    """
    if full_path.startswith(("api/", "v1/")):
        return JSONResponse(status_code=404, content={"error": {"code": "not_found", "message": "Not found"}})
    if full_path:
        candidate = (FRONTEND_DIST / full_path).resolve()
        if candidate.is_file() and candidate.is_relative_to(FRONTEND_DIST.resolve()):
            return FileResponse(candidate)
    return index_response(request)


def ensure_schema() -> None:
    # Alembic (`alembic upgrade head`) stays the migration path for an existing
    # database. On a fresh data directory this only creates the current schema so
    # the API can start wherever it is deployed.
    Base.metadata.create_all(bind=engine)
    # create_all never alters existing tables, so columns added to a live database
    # are attached here. Each statement is guarded, which keeps this idempotent on
    # both SQLite and Postgres.
    existing = {column["name"] for column in inspect(engine).get_columns("songs")}
    additions = {
        "shared_by_user_id": "ALTER TABLE songs ADD COLUMN shared_by_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL",
        "source_url": "ALTER TABLE songs ADD COLUMN source_url VARCHAR(500)",
    }
    with engine.begin() as conn:
        for name, statement in additions.items():
            if name not in existing:
                conn.execute(text(statement))
                logger.info("added songs.%s", name)


@asynccontextmanager
async def lifespan(app: FastAPI):
    ensure_schema()
    yield


def build_api() -> FastAPI:
    api = FastAPI(title="ORU API", description="Versioned HTTP API for ORU music discovery and artist tools.", version="0.1.0")
    api.include_router(accounts.router)
    api.include_router(music.router)
    api.include_router(community.router)
    api.include_router(moderation.router)
    api.add_exception_handler(RequestValidationError, validation_error_handler)
    api.add_exception_handler(HTTPException, http_error_handler)
    # Starlette raises its own HTTPException for a path that matches no route;
    # without this the response would fall back to {"detail": "Not Found"}.
    api.add_exception_handler(StarletteHTTPException, http_error_handler)
    api.add_exception_handler(Exception, unexpected_error_handler)
    return api


def build_app(api: FastAPI) -> FastAPI:
    root = FastAPI(title="ORU health service", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
    root.add_middleware(SimpleRateLimitMiddleware)
    root.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=not settings.allow_wildcard_cors,
        allow_methods=["GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
    )
    root.add_exception_handler(RequestValidationError, validation_error_handler)
    root.add_exception_handler(HTTPException, http_error_handler)
    root.add_exception_handler(StarletteHTTPException, http_error_handler)
    root.add_exception_handler(Exception, unexpected_error_handler)
    # Added last, so it runs first: the rate limiter and the handlers below must
    # already see the path the client actually asked for.
    root.add_middleware(HostPathMiddleware)

    @root.get("/health", tags=["health"])
    def health():
        try:
            with engine.connect() as connection:
                connection.execute(text("SELECT 1"))
        except Exception:
            return JSONResponse(status_code=503, content={"status": "unhealthy", "database": "unavailable"})
        return {"status": "ok", "database": "ok"}

    # The canonical prefix plus one for hosts that route the app beneath /api.
    root.mount("/api/v1", api)
    root.mount("/v1", api)

    # The manifest and the worker need types and cache rules of their own;
    # the generic asset route below would guess wrong for a .webmanifest file.
    @root.get("/manifest.webmanifest", include_in_schema=False)
    def web_manifest():
        return FileResponse(
            FRONTEND_DIST / "manifest.webmanifest",
            media_type="application/manifest+json",
            headers={"Cache-Control": "public, max-age=300"},
        )

    @root.get("/sw.js", include_in_schema=False)
    def service_worker():
        return FileResponse(
            FRONTEND_DIST / "sw.js",
            media_type="text/javascript",
            headers={"Cache-Control": "no-cache", "Service-Worker-Allowed": "/"},
        )

    if (FRONTEND_DIST / "index.html").is_file():
        root.get("/{full_path:path}", include_in_schema=False)(spa_response)
    return root


api = build_api()
app = build_app(api)
