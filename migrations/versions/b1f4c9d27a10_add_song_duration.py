"""add song duration

Revision ID: b1f4c9d27a10
Revises: 43887bda4968
Create Date: 2026-11-04 20:05:00.000000

"""

from typing import Union

import sqlalchemy as sa
from alembic import op

revision: str = "b1f4c9d27a10"
down_revision: Union[str, None] = "43887bda4968"
branch_labels: Union[str, None] = None
depends_on: Union[str, None] = None


def upgrade() -> None:
    op.add_column("songs", sa.Column("duration_ms", sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column("songs", "duration_ms")
