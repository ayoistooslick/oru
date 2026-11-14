import base64
import logging
import mimetypes
import os
import uuid
from io import BytesIO
from pathlib import Path
from typing import BinaryIO
from urllib.parse import urlsplit

import httpx

from app.config import settings


log = logging.getLogger(__name__)
_miss = object()


class UploadTooLarge(Exception):
    pass


class StorageUnavailable(Exception):
    """The media service could not be reached, or refused the upload."""


class LocalStorage:
    """Small storage adapter used in development; media stays outside the database."""

    name = "local"

    def __init__(self, root: str | None = None):
        self.root = Path(root or settings.media_directory).resolve()
        self.root.mkdir(parents=True, exist_ok=True)

    def save(self, source: BinaryIO, suffix: str, max_bytes: int) -> tuple[str, int]:
        key = f"{uuid.uuid4().hex}{suffix}"
        destination = self.root / key
        total = 0
        try:
            with destination.open("wb") as output:
                while chunk := source.read(1024 * 1024):
                    total += len(chunk)
                    if total > max_bytes:
                        raise UploadTooLarge
                    output.write(chunk)
        except Exception:
            destination.unlink(missing_ok=True)
            raise
        return key, total

    def path_for(self, key: str) -> Path:
        path = (self.root / key).resolve()
        if path.parent != self.root or not path.is_file():
            raise FileNotFoundError
        return path

    def url_for(self, key: str) -> str:
        """Keys on this storage already are the address to fetch them from."""
        return key

    def delete(self, key: str) -> None:
        try:
            self.path_for(key).unlink(missing_ok=True)
        except FileNotFoundError:
            pass

    def read_bytes(self, key: str) -> bytes | None:
        try:
            return self.path_for(key).read_bytes()
        except FileNotFoundError:
            return None

    def size(self, key: str) -> int | None:
        try:
            return self.path_for(key).stat().st_size
        except FileNotFoundError:
            return None

    def read_range(self, key: str, start: int, end: int) -> bytes | None:
        try:
            with self.path_for(key).open("rb") as handle:
                handle.seek(start)
                return handle.read(end - start + 1)
        except FileNotFoundError:
            return None


class S3Storage:
    """Same interface, backed by an S3-compatible bucket for audio + cover images."""

    name = "s3"

    def __init__(self) -> None:
        self.client = None
        self.bucket = None
        if not self._ready():
            return
        import boto3
        from botocore.exceptions import ClientError

        client_config: dict[str, object] = {}
        if settings.aws_endpoint_url_s3:
            client_config["endpoint_url"] = settings.aws_endpoint_url_s3
        self.client = boto3.client(
            "s3",
            region_name=settings.aws_region or "us-east-1",
            aws_access_key_id=settings.aws_access_key_id,
            aws_secret_access_key=settings.aws_secret_access_key,
            **client_config,
        )
        self.bucket = settings.s3_bucket_name

    def _ready(self) -> bool:
        if any(
            v is None or str(v).strip() == ""
            for v in (
                settings.aws_access_key_id,
                settings.aws_secret_access_key,
                settings.s3_bucket_name,
            )
        ):
            return False
        return True

    def save(self, source: BinaryIO, suffix: str, max_bytes: int) -> tuple[str, int]:
        if self.client is None or self.bucket is None:
            raise RuntimeError("storage is not ready")
        key = f"{uuid.uuid4().hex}{suffix}"
        # Read the entire object so we can validate size and upload once.
        # The web upload path streams small files, so this is fine in practice.
        data = source.read()
        total = len(data)
        if total > max_bytes:
            raise UploadTooLarge
        self.client.put_object(Bucket=self.bucket, Key=key, Body=data)
        return key, total

    def url_for(self, key: str) -> str:
        return key

    def path_for(self, key: str) -> Path:
        if self.client is None or self.bucket is None:
            raise RuntimeError("storage is not ready")
        local = Path(settings.media_directory) / key
        if local.is_file():
            return local
        raise FileNotFoundError

    def delete(self, key: str) -> None:
        if self.client is None or self.bucket is None:
            raise RuntimeError("storage is not ready")
        try:
            self.client.delete_object(Bucket=self.bucket, Key=key)
        except Exception as e:
            log.warning("failed to delete s3 object %r: %s", key, e)

    def read_bytes(self, key: str) -> bytes | None:
        if self.client is None or self.bucket is None:
            return None
        try:
            resp = self.client.get_object(Bucket=self.bucket, Key=key)
            return resp["Body"].read()
        except Exception as e:
            log.warning("failed to read s3 object %r: %s", key, e)
            return None

    def size(self, key: str) -> int | None:
        if self.client is None or self.bucket is None:
            return None
        try:
            resp = self.client.head_object(Bucket=self.bucket, Key=key)
            return int(resp["ContentLength"])
        except Exception as e:
            log.warning("failed to stat s3 object %r: %s", key, e)
            return None

    def read_range(self, key: str, start: int, end: int) -> bytes | None:
        if self.client is None or self.bucket is None:
            return None
        try:
            resp = self.client.get_object(Bucket=self.bucket, Key=key, Range=f"bytes={start}-{end}")
            return resp["Body"].read()
        except Exception as e:
            log.warning("failed to read s3 object range %r: %s", key, e)
            return None

    def list_files(self, prefix: str) -> list[str]:
        if self.client is None or self.bucket is None:
            raise RuntimeError("storage is not ready")
        resp: list[dict[str, object]] = []
        marker: str | None = None
        while True:
            kwargs: dict[str, object] = {"Bucket": self.bucket, "Prefix": prefix}
            if marker is not None:
                kwargs["Marker"] = marker
            resp = self.client.list_objects_v2(**kwargs)
            for obj in list(resp.get("Contents", [])):
                key = str(obj["Key"])
                if key != prefix:
                    yield key
            if not resp.get("IsTruncated"):
                break
            marker = str(resp.get("NextMarker", "")) or (
                str(resp["Contents"][-1]["Key"]) if resp.get("Contents") else ""
            )


def _bank_file_id(key: str) -> str | None:
    """The service's file id, read back out of the public URL we stored."""
    parts = urlsplit(key)
    if parts.scheme not in ("http", "https"):
        return None
    path = parts.path.rstrip("/")
    if not path.startswith("/f/"):
        return None
    return path[len("/f/"):] or None


class GittyBankStorage:
    """Media lives in Gitty Bank, and the key for a file is its public URL.

    The service is an upload API with one public download URL per file, so a key
    in the database is that URL (``https://gitty-bank-api.vercel.app/f/med_x``).
    ``url_for`` keeps those keys working when the service changes hosts.
    Nothing streams through this app: the media responses redirect to it, which
    keeps Range requests working because the service answers those itself, and
    keeps audio out of the API's bandwidth.

    Deleting a file asks the service to remove it, which any key with write
    permission may do; a read-only key is refused. A refusal is logged, never
    raised, because the app has already forgotten the file at that point, and
    nothing confidential belongs in an upload either way.
    """

    TIMEOUT = 120.0
    name = "gitty"

    def __init__(self) -> None:
        if not settings.gitty_bank_api_key:
            raise RuntimeError("GITTY_BANK_API_KEY is not set")
        self.base = settings.gitty_bank_base_url.rstrip("/")
        self.token = settings.gitty_bank_api_key
        self.root = None

    def save(self, source: BinaryIO, suffix: str, max_bytes: int) -> tuple[str, int]:
        # Measure, then rewind: a song is streamed to the service from the spooled
        # upload rather than read into memory, so large files do not double up.
        source.seek(0, os.SEEK_END)
        total = source.tell()
        if total > max_bytes:
            raise UploadTooLarge
        source.seek(0)
        name = f"{uuid.uuid4().hex}{suffix}"
        media_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
        try:
            response = httpx.post(
                f"{self.base}/api/v1/upload",
                headers={"Authorization": f"Bearer {self.token}"},
                files={"file": (name, source, media_type)},
                timeout=self.TIMEOUT,
            )
        except httpx.HTTPError as error:
            raise StorageUnavailable(str(error)) from error
        if response.status_code >= 400:
            raise StorageUnavailable(f"upload refused with {response.status_code}: {response.text[:200]}")
        try:
            payload = response.json()
        except ValueError as error:
            raise StorageUnavailable("upload response was not JSON") from error
        url = payload.get("url") or (f"{self.base}/f/{payload['id']}" if payload.get("id") else None)
        if not url:
            raise StorageUnavailable("upload response had no file URL")
        return str(url), total

    def path_for(self, key: str) -> Path:
        raise FileNotFoundError

    def url_for(self, key: str) -> str:
        """The same file, addressed on the service this app talks to now.

        A key is the public URL the service handed back at upload time, and a
        file keeps its path when the service moves hosts. Reading the stored
        path through today's base URL is what lets files uploaded while it
        answered elsewhere keep working after the move.
        """
        parts = urlsplit(key)
        if parts.scheme in ("http", "https") and parts.path.startswith("/f/"):
            return f"{self.base}{parts.path}"
        return key

    def delete(self, key: str) -> None:
        file_id = _bank_file_id(self.url_for(key))
        if file_id is None:
            return
        try:
            response = httpx.delete(
                f"{self.base}/api/v1/files/{file_id}",
                headers={"Authorization": f"Bearer {self.token}"},
                timeout=self.TIMEOUT,
            )
        except httpx.HTTPError as error:
            log.warning("failed to delete %r: %s", key, error)
            return
        # 404 means the file is already gone, which is what we wanted anyway.
        if response.status_code in (200, 202, 204, 404):
            return
        log.warning("media service kept %r (%s): %s", key, response.status_code,
                    response.text[:200])

    def read_bytes(self, key: str) -> bytes | None:
        try:
            response = httpx.get(self.url_for(key), timeout=self.TIMEOUT,
                                 follow_redirects=True)
        except httpx.HTTPError as error:
            log.warning("failed to read %r: %s", key, error)
            return None
        return response.content if response.status_code == 200 else None

    def size(self, key: str) -> int | None:
        try:
            response = httpx.get(self.url_for(key),
                                 headers={"Range": "bytes=0-0"},
                                 timeout=self.TIMEOUT, follow_redirects=True)
        except httpx.HTTPError as error:
            log.warning("failed to stat %r: %s", key, error)
            return None
        content_range = response.headers.get("content-range", "")
        if "/" in content_range:
            try:
                return int(content_range.rsplit("/", 1)[1])
            except ValueError:
                return None
        return len(response.content) if response.status_code == 200 else None

    def read_range(self, key: str, start: int, end: int) -> bytes | None:
        try:
            response = httpx.get(self.url_for(key),
                                 headers={"Range": f"bytes={start}-{end}"},
                                 timeout=self.TIMEOUT, follow_redirects=True)
        except httpx.HTTPError as error:
            log.warning("failed to read %r range: %s", key, error)
            return None
        return response.content if response.status_code in (200, 206) else None


def _choose_storage() -> LocalStorage | S3Storage | GittyBankStorage:
    """Pick where uploads go: Gitty Bank when there is a key, else S3, else disk."""
    mode = settings.storage.strip().lower()
    if mode in ("", "auto"):
        if settings.gitty_bank_api_key:
            mode = "gitty"
        elif S3Storage()._ready():
            mode = "s3"
        else:
            mode = "local"
    if mode == "local":
        chosen = LocalStorage()
        log.info("using local storage in %s", chosen.root)
        return chosen
    if mode == "gitty":
        try:
            chosen = GittyBankStorage()
        except Exception as e:
            log.warning("gitty bank unavailable (%s); falling back to local storage", e)
        else:
            log.info("using gitty bank storage at %s", chosen.base)
            return chosen
        return LocalStorage()
    if mode == "s3":
        try:
            if S3Storage()._ready():
                chosen = S3Storage()
                log.info("using s3 storage in bucket %s", settings.s3_bucket_name)
                return chosen
            raise RuntimeError("s3 storage not configured")
        except Exception as e:
            log.warning("s3 storage unavailable (%s); falling back to local storage", e)
            return LocalStorage()
    log.warning("unknown storage mode %r; using local storage", mode)
    return LocalStorage()


storage: LocalStorage | S3Storage | GittyBankStorage = _choose_storage()


def save(source: BinaryIO, suffix: str, max_bytes: int) -> tuple[str, int]:
    return _storage.save(source, suffix, max_bytes)


def path_for(key: str) -> Path:
    return _storage.path_for(key)


def delete(key: str) -> None:
    _storage.delete(key)


def list_files(prefix: str) -> list[str]:
    if hasattr(_storage, "list_files"):
        return list(_storage.list_files(prefix))
    if isinstance(_storage, LocalStorage):
        return sorted(p.name for p in _storage.path_for(prefix).parent.glob(f"{prefix}*") if p.is_file())
    return list(_storage.list_files(prefix))


def list_storage_files(prefix: str) -> list[str]:
    return list_files(prefix)
