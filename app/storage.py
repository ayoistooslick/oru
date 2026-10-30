import base64
import logging
import os
import uuid
from io import BytesIO
from pathlib import Path
from typing import BinaryIO

from app.config import settings


log = logging.getLogger(__name__)
_miss = object()


class UploadTooLarge(Exception):
    pass


class LocalStorage:
    """Small storage adapter used in development; media stays outside the database."""

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


class S3Storage:
    """Same interface, backed by an S3-compatible bucket for audio + cover images."""

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


_storage: LocalStorage | S3Storage
try:
    if settings.storage == "s3" and S3Storage()._ready():
        _storage = S3Storage()
        log.info("using s3 storage in bucket %s", settings.s3_bucket_name)
    else:
        raise RuntimeError("s3 storage not configured")
except Exception as e:
    log.warning("s3 storage unavailable (%s); falling back to local storage", e)
    _storage = LocalStorage()
    log.info("using local storage in %s", _storage.root)


storage: LocalStorage | S3Storage = _storage


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
