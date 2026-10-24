from functools import lru_cache

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ORU API"
    environment: str = "development"
    database_url: str = "sqlite:///./oru.db"
    jwt_secret: str = Field(default="change-this-development-secret-before-deploying", min_length=32)
    jwt_expire_minutes: int = 60 * 24
    cors_origins: list[str] = ["http://localhost:3000", "http://localhost:5173"]
    media_directory: str = "./data/media"
    max_audio_bytes: int = 100 * 1024 * 1024
    max_image_bytes: int = 10 * 1024 * 1024
    admin_emails: str = ""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @model_validator(mode="after")
    def check_production_secret(self):
        if self.environment.lower() in {"production", "prod"} and self.jwt_secret in {
            "change-this-development-secret-before-deploying",
            "replace-this-with-a-long-random-secret",
        }:
            raise ValueError("Set JWT_SECRET to a private random value before starting in production")
        return self

    def is_admin_email(self, email: str) -> bool:
        return email.lower() in {item.strip().lower() for item in self.admin_emails.split(",") if item.strip()}


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
