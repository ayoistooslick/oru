from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import settings


class Base(DeclarativeBase):
    pass


is_sqlite = settings.database_url.startswith("sqlite")
# The busy timeout keeps a second writer from failing immediately while the
# first transaction finishes, which matters once more than one process shares
# the database file.
connect_args = {"check_same_thread": False, "timeout": 30} if is_sqlite else {}

if is_sqlite and settings.sqlite_path is not None:
    settings.sqlite_path.parent.mkdir(parents=True, exist_ok=True)

engine = create_engine(settings.database_url, connect_args=connect_args, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


if is_sqlite:
    @event.listens_for(engine, "connect")
    def _sqlite_pragmas(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA busy_timeout=30000")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
