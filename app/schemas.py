import re
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, field_validator


class RegisterRequest(BaseModel):
    email: EmailStr
    handle: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z0-9_]+$")
    display_name: str = Field(min_length=1, max_length=80)
    password: str = Field(min_length=10, max_length=128)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class ProfileUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=80)
    bio: str | None = Field(default=None, max_length=1000)


class ArtistProfileCreate(BaseModel):
    stage_name: str = Field(min_length=1, max_length=100)


class ReleaseCreate(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    kind: Literal["single", "ep", "album"]
    song_ids: list[int] = Field(min_length=1, max_length=40)

    @field_validator("song_ids")
    @classmethod
    def unique_song_ids(cls, value):
        if len(value) != len(set(value)):
            raise ValueError("A song can only appear once in a release")
        return value


class PlaylistCreate(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    is_public: bool = True


class PlaylistUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=100)
    is_public: bool | None = None


class ListeningCreate(BaseModel):
    listened_ms: int = Field(ge=0, le=24 * 60 * 60 * 1000)


class ReportCreate(BaseModel):
    target_type: Literal["song", "artist", "post"]
    target_id: int = Field(gt=0)
    reason: Literal["copyright", "harassment", "spam", "impersonation", "other"]
    details: str = Field(default="", max_length=2000)


class ModerationDecision(BaseModel):
    action: Literal["hide", "dismiss", "restore"]
    reason: str = Field(min_length=5, max_length=1000)


class ArtistPostCreate(BaseModel):
    body: str = Field(min_length=1, max_length=3000)
