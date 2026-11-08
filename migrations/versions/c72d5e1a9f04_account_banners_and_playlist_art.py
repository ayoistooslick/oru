"""account banners and playlist artwork

Revision ID: c72d5e1a9f04
Revises: b1f4c9d27a10
Create Date: 2026-11-08 08:10:00.000000

"""

from typing import Union

import sqlalchemy as sa
from alembic import op

revision: str = "c72d5e1a9f04"
down_revision: Union[str, None] = "b1f4c9d27a10"
branch_labels: Union[str, None] = None
depends_on: Union[str, None] = None


def upgrade() -> None:
    # Banners move from artist profiles to the account, so listeners can have
    # one too. image_version keeps a replaced picture from being served cached.
    op.add_column("users", sa.Column("banner_key", sa.String(length=500), nullable=True))
    op.add_column("users", sa.Column("image_version", sa.Integer(), nullable=False, server_default="0"))
    # Playlists grow a square cover and a wide banner of their own.
    op.add_column("playlists", sa.Column("cover_key", sa.String(length=500), nullable=True))
    op.add_column("playlists", sa.Column("banner_key", sa.String(length=500), nullable=True))
    op.add_column("playlists", sa.Column("image_version", sa.Integer(), nullable=False, server_default="0"))


def downgrade() -> None:
    op.drop_column("playlists", "image_version")
    op.drop_column("playlists", "banner_key")
    op.drop_column("playlists", "cover_key")
    op.drop_column("users", "image_version")
    op.drop_column("users", "banner_key")
