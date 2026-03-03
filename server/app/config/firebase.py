import firebase_admin
from firebase_admin import credentials
import os


def initialize_firebase():
    """Initialize Firebase Admin SDK"""
    # Path to your service account key file
    service_account_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
        "serviceAccountKey.json"
    )

    if not os.path.exists(service_account_path):
        raise FileNotFoundError(
            f"Firebase service account key not found at {service_account_path}. "
            "Please download it from Firebase Console and place it in the server directory."
        )

    cred = credentials.Certificate(service_account_path)
    firebase_admin.initialize_app(cred)
