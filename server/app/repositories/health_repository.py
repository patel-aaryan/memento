from sqlalchemy import text
from app.config.db import get_db


class HealthRepository:
    def __init__(self):
        self.db = next(get_db())

    def ping_database(self) -> dict:
        """Execute a simple query to verify database connectivity."""
        result = self.db.execute(text("SELECT message FROM health"))
        row = result.fetchone()
        return {"message": row[0] if row else "No message found"}
