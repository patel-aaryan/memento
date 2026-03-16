from datetime import datetime, timedelta, timezone
from typing import Optional
import hashlib
import hmac
import secrets
from jose import JWTError, jwt
from passlib.context import CryptContext
from app.config.settings import get_settings

settings = get_settings()

# Password hashing
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto", bcrypt__default_rounds=12)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a plain password against a hashed password."""
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    """Hash a password."""
    return pwd_context.hash(password)


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    """Create a JWT access token."""
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=settings.access_token_expire_minutes)
    
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, settings.secret_key, algorithm=settings.algorithm)
    return encoded_jwt


def decode_access_token(token: str) -> Optional[dict]:
    """Decode and verify a JWT access token."""
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=[settings.algorithm])
        return payload
    except JWTError as e:
        # Log the error for debugging (you can remove this in production)
        print(f"JWT decode error: {e}")
        return None
    except Exception as e:
        # Catch any other errors (like expired token, invalid format, etc.)
        print(f"Token decode error: {e}")
        return None


def generate_reset_code() -> str:
    """Generate a 6-digit numeric OTP for password reset."""
    return f"{secrets.randbelow(1_000_000):06d}"


def hash_reset_code(email: str, code: str) -> str:
    """
    Hash a reset code using HMAC-SHA256 with SECRET_KEY as pepper.
    This avoids storing the raw OTP in the database.
    """
    message = f"{email.strip().lower()}:{code}".encode("utf-8")
    return hmac.new(settings.secret_key.encode("utf-8"), message, hashlib.sha256).hexdigest()
