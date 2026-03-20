from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    database_url: str
    debug: bool = False
    secret_key: str = "your-secret-key-change-in-production"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 60 * 24  # 24 hours
    password_reset_code_expire_minutes: int = 15

    # Cloudinary settings
    cloudinary_cloud_name: str = ""
    cloudinary_api_key: str = ""
    cloudinary_api_secret: str = ""

    # Google API settings
    google_api_key: str = ""

    # Resend settings (used for password reset emails)
    resend_api_key: str = ""
    resend_from_email: str = ""

    # Base URL for friend invite links (e.g. https://api.memento.app)
    app_base_url: str = "http://localhost:8000"

    # Timezone for anniversary "today" (e.g. America/New_York, UTC)
    anniversary_timezone: str = "America/New_York"

    # OpenAI: album suggestions from "My Photos" (optional; worker skips if empty)
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    # Minimum seconds between OpenAI calls for the same user (poll + worker + uploads share this)
    album_suggestion_openai_cooldown_seconds: int = 0

    class Config:
        env_file = ".env"


@lru_cache()
def get_settings() -> Settings:
    return Settings()
