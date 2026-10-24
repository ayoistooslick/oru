import os
import uuid
from pathlib import Path
from typing import BinaryIO

from app.config import settings


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


storage = LocalStorage()
