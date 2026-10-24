from collections import defaultdict, deque
from time import monotonic

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
from sqlalchemy import text

from app.api import accounts, community, moderation, music
from app.config import settings
from app.database import engine


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


app = FastAPI(title="ORU health service", docs_url=None, redoc_url=None, openapi_url=None)
app.add_middleware(SimpleRateLimitMiddleware)
app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_credentials=True, allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"], allow_headers=["Authorization", "Content-Type"])


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    details = [{"field": ".".join(str(part) for part in item["loc"] if part != "body"), "message": item["msg"]} for item in exc.errors()]
    return JSONResponse(status_code=422, content={"error": {"code": "validation_error", "message": "Some of the submitted data is invalid", "details": details}})


@app.exception_handler(HTTPException)
async def http_error_handler(request: Request, exc: HTTPException):
    detail = exc.detail
    error = detail if isinstance(detail, dict) and "code" in detail and "message" in detail else {"code": "http_error", "message": str(detail)}
    return JSONResponse(status_code=exc.status_code, content={"error": error}, headers=exc.headers)


@app.exception_handler(Exception)
async def unexpected_error_handler(request: Request, exc: Exception):
    # Keep internal details out of public responses. The server logs retain the traceback.
    import logging
    logging.getLogger("oru").exception("Unexpected request error")
    return JSONResponse(status_code=500, content={"error": {"code": "internal_error", "message": "Something went wrong"}})


@app.get("/health", tags=["health"])
def health():
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except Exception:
        return JSONResponse(status_code=503, content={"status": "unhealthy", "database": "unavailable"})
    return {"status": "ok", "database": "ok"}


api = FastAPI(title="ORU API", description="Versioned HTTP API for ORU music discovery and artist tools.", version="0.1.0")
api.include_router(accounts.router)
api.include_router(music.router)
api.include_router(community.router)
api.include_router(moderation.router)
api.add_exception_handler(RequestValidationError, validation_error_handler)
api.add_exception_handler(HTTPException, http_error_handler)
api.add_exception_handler(Exception, unexpected_error_handler)
app.mount("/api/v1", api)
