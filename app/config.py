from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any

from pydantic import Field, PostgresDsn, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


BASE_DIR = Path(__file__).resolve().parent.parent


def _postgres(url: str) -> PostgresDsn:
    if url.startswith("sqlite"):
        return url
    if not url.startswith("postgresql"):
        url = url.replace("postgres://", "postgresql://", 1)
    return PostgresDsn(url)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env.local", env_file_encoding="utf-8", extra="ignore"
    )

    environment: str = Field(default="", validation_alias="ENVIRONMENT")
    site_url: str = Field(default="http://localhost:5173", validation_alias="SITE_URL")
    media_directory: str = Field(default="/tmp/oru/media", validation_alias="MEDIA_DIRECTORY")
    database_url: str = Field(default="sqlite:///./data/oru.db", validation_alias="DATABASE_URL")
    jwt_secret: str = Field(default="change-me-in-production", validation_alias="JWT_SECRET")

    # Explicit S3 bucket name (sender set this as the bucket name).
    s3_bucket_name: str = Field(default="", validation_alias="S3_BUCKET_NAME")
    aws_endpoint_url_s3: str = Field(default="", validation_alias="AWS_ENDPOINT_URL_S3")
    aws_access_key_id: str = Field(default="", validation_alias="AWS_ACCESS_KEY_ID")
    aws_secret_access_key: str = Field(default="", validation_alias="AWS_SECRET_ACCESS_KEY")
    aws_region: str = Field(default="us-east-1", validation_alias="AWS_REGION")
    s3_presigned_url_expiry: int = Field(default=3600, validation_alias="S3_PRESIGNED_URL_EXPIRY")
    allowed_origins_s3: str = Field(default="", validation_alias="ALLOWED_ORIGINS_S3")
    storage: str = Field(default="local", validation_alias="STORAGE")
    cors_origins: list[str] = Field(default_factory=list, validation_alias="CORS_ORIGINS")
    allow_wildcard_cors: bool = Field(default=False, validation_alias="ALLOW_WILDCARD_CORS")
    admin_emails: str = Field(default="", validation_alias="ADMIN_EMAILS")
    jwt_expire_minutes: int = Field(default=60 * 24, validation_alias="JWT_EXPIRE_MINUTES")
    max_audio_bytes: int = Field(default=100 * 1024 * 1024, validation_alias="MAX_AUDIO_BYTES")
    max_cover_bytes: int = Field(default=5 * 1024 * 1024, validation_alias="MAX_COVER_BYTES")

    @property
    def is_admin_email(self) -> callable:
        targets = set(filter(None, self.admin_emails.split(",")))

        def check(email: str) -> bool:
            return email in targets

        return check


def _log_once(key: str, value: str) -> None:
    if key and key in os.environ:
        return
    log.warning("%s is set in the environment but not in .env.local", key)


settings = Settings()

if __name__ == "__main__":
    print("environment:", repr(settings.environment))
    print("storage:", repr(settings.storage))
    print("s3_bucket_name:", repr(settings.s3_bucket_name))
    print("aws_region:", repr(settings.aws_region))

