from functools import lru_cache
from pathlib import Path

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Everything on disk is resolved from the repository root so the API behaves the
# same whether it starts from the project folder, a preview process or a
# deployment that runs with a different working directory.
BASE_DIR = Path(__file__).resolve().parent.parent


def resolve_path(value: str) -> Path:
    path = Path(value).expanduser()
    return path if path.is_absolute() else (BASE_DIR / path).resolve()


class Settings(BaseSettings):
    app_name: str = "ORU API"
    environment: str = "development"
    database_url: str = "sqlite:///./oru.db"
    jwt_secret: str = Field(default="change-this-development-secret-before-deploying", min_length=32)
    jwt_expire_minutes: int = 60 * 24
    # The API authenticates with bearer tokens rather than cookies, so an open
    # origin list is safe and keeps the preview and deployment origins working.
    cors_origins: list[str] = ["*"]
    media_directory: str = "./data/media"
    max_audio_bytes: int = 100 * 1024 * 1024
    max_image_bytes: int = 10 * 1024 * 1024
    admin_emails: str = ""
    # Public origin used for share previews (og:url and og:image). When it is
    # empty the request's own host is used, which is right behind a proxy that
    # forwards the public hostname.
    site_url: str = ""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @model_validator(mode="after")
    def normalize_paths(self):
        # A relative SQLite URL is resolved against the repository root.
        prefix = "sqlite:///"
        if self.database_url.startswith(prefix) and not Path(self.database_url[len(prefix):]).is_absolute():
            self.database_url = f"{prefix}{BASE_DIR / self.database_url[len(prefix):]}"
        return self

    @model_validator(mode="after")
    def check_production_secret(self):
        if self.environment.lower() in {"production", "prod"} and self.jwt_secret in {
            "change-this-development-secret-before-deploying",
            "replace-this-with-a-long-random-secret",
        }:
            raise ValueError("Set JWT_SECRET to a private random value before starting in production")
        return self

    @property
    def media_path(self) -> Path:
        return resolve_path(self.media_directory)

    @property
    def sqlite_path(self) -> Path | None:
        prefix = "sqlite:///"
        if not self.database_url.startswith(prefix):
            return None
        return Path(self.database_url[len(prefix):])

    @property
    def allow_wildcard_cors(self) -> bool:
        return "*" in self.cors_origins

    def is_admin_email(self, email: str) -> bool:
        return email.lower() in {item.strip().lower() for item in self.admin_emails.split(",") if item.strip()}


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
