import logging
import requests

from app.config.settings import get_settings

logger = logging.getLogger(__name__)


def send_password_reset_code(email: str, code: str) -> bool:
    """
    Send a password reset code using Resend's email API.
    Returns True when email is sent, False when Resend is not configured.
    """
    settings = get_settings()
    if not settings.resend_api_key or not settings.resend_from_email:
        logger.warning(
            "Resend not configured; cannot send password reset email to %s",
            email,
        )
        return False

    text_body = (
        "We received a request to reset your password.\n\n"
        f"Your reset code is: {code}\n\n"
        f"This code expires in {settings.password_reset_code_expire_minutes} minutes.\n"
        "If you did not request this, you can ignore this email."
    )
    html_body = (
        "<p>We received a request to reset your password.</p>"
        f"<p><strong>Your reset code is: {code}</strong></p>"
        f"<p>This code expires in {settings.password_reset_code_expire_minutes} minutes.</p>"
        "<p>If you did not request this, you can ignore this email.</p>"
    )

    response = requests.post(
        "https://api.resend.com/emails",
        headers={
            "Authorization": f"Bearer {settings.resend_api_key}",
            "Content-Type": "application/json",
        },
        json={
            "from": settings.resend_from_email,
            "to": [email],
            "subject": "Your Memento password reset code",
            "text": text_body,
            "html": html_body,
        },
        timeout=15,
    )
    response.raise_for_status()
    return True
