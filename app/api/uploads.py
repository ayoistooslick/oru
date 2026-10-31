"""Shared media handling: uploads, size limits and range aware responses.

Audio is played straight from the API, and a browser can only move around
inside a track when the response tells it the file can be asked for in pieces.
Without that the player restarts the download each time someone drags the
progress bar, which looks like the song jumping back to the beginning.
"""

import mimetypes
import re
from pathlib import Path

from fastapi import HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, Response

from app.storage import UploadTooLarge, storage

AUDIO_TYPES = {
    ".mp3": {"audio/mpeg", "audio/mp3", "application/octet-stream"},
    ".wav": {"audio/wav", "audio/x-wav", "audio/wave", "application/octet-stream"},
    ".ogg": {"audio/ogg", "application/ogg", "application/octet-stream"},
    ".m4a": {"audio/mp4", "audio/x-m4a", "application/octet-stream"},
    ".flac": {"audio/flac", "audio/x-flac", "application/octet-stream"},
    ".opus": {"audio/ogg", "audio/opus", "application/octet-stream"},
}
IMAGE_TYPES = {
    ".jpg": {"image/jpeg"},
    ".jpeg": {"image/jpeg"},
    ".png": {"image/png"},
    ".webp": {"image/webp"},
}

RANGE = re.compile(r"bytes=(\d*)-(\d*)")


def save_upload(file: UploadFile, allowed: dict[str, set[str]], max_bytes: int) -> tuple[str, int]:
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in allowed or file.content_type not in allowed[suffix]:
        raise HTTPException(status_code=415, detail={"code": "unsupported_file", "message": "Use a supported audio or image file type"})
    try:
        key, size = storage.save(file.file, suffix, max_bytes)
    except UploadTooLarge:
        raise HTTPException(status_code=413, detail={"code": "file_too_large", "message": "The uploaded file is larger than the allowed limit"})
    if size == 0:
        storage.delete(key)
        raise HTTPException(status_code=400, detail={"code": "empty_file", "message": "The uploaded file is empty"})
    return key, size


def parse_range(header: str | None, total: int) -> tuple[int, int] | None:
    """Return the inclusive (start, end) a client asked for, or None for all of it."""
    if not header or total <= 0:
        return None
    match = RANGE.fullmatch(header.strip())
    if match is None:
        return None
    start_text, end_text = match.groups()
    if start_text == "" and end_text == "":
        return None
    if start_text == "":
        length = int(end_text)
        if length <= 0:
            return None
        start = max(total - length, 0)
        return start, total - 1
    start = int(start_text)
    if start >= total:
        return None
    end = int(end_text) if end_text else total - 1
    return start, min(end, total - 1)


def media_response(key: str, media_type: str | None, request: Request, filename: str | None = None,
                   cache_control: str = "public, max-age=3600") -> Response:
    """Serve one stored file, honouring a Range request when the client sends one."""
    local: Path | None = None
    try:
        local = storage.path_for(key)
    except FileNotFoundError:
        local = None

    if local is not None:
        if request.headers.get("range") is None:
            return FileResponse(local, media_type=media_type, filename=filename,
                                content_disposition_type="inline", headers={"Cache-Control": cache_control})
        total = local.stat().st_size
        window = parse_range(request.headers.get("range"), total)
        if window is None:
            return Response(status_code=416, headers={"Content-Range": f"bytes */{total}"})
        start, end = window
        with local.open("rb") as handle:
            handle.seek(start)
            chunk = handle.read(end - start + 1)
        return _partial(chunk, media_type, start, end, total, cache_control)

    total = storage.size(key)
    if total is None:
        raise HTTPException(status_code=503, detail={"code": "media_unavailable", "message": "This file is temporarily unavailable"})
    window = parse_range(request.headers.get("range"), total)
    if window is None:
        data = storage.read_bytes(key)
        if data is None:
            raise HTTPException(status_code=503, detail={"code": "media_unavailable", "message": "This file is temporarily unavailable"})
        headers = {"Accept-Ranges": "bytes", "Content-Length": str(len(data)), "Cache-Control": cache_control}
        if filename:
            headers["Content-Disposition"] = f'inline; filename="{filename}"'
        return Response(content=data, media_type=media_type, headers=headers)
    start, end = window
    chunk = storage.read_range(key, start, end)
    if chunk is None:
        raise HTTPException(status_code=503, detail={"code": "media_unavailable", "message": "This file is temporarily unavailable"})
    return _partial(chunk, media_type, start, end, total, cache_control)


def _partial(chunk: bytes, media_type: str | None, start: int, end: int, total: int, cache_control: str) -> Response:
    return Response(
        content=chunk,
        status_code=206,
        media_type=media_type,
        headers={
            "Accept-Ranges": "bytes",
            "Content-Range": f"bytes {start}-{end}/{total}",
            "Content-Length": str(len(chunk)),
            "Cache-Control": cache_control,
        },
    )


def guess_image_type(key: str) -> str:
    return mimetypes.guess_type(key)[0] or "image/jpeg"
