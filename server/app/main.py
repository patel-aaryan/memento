import asyncio
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config.settings import get_settings
from app.config.db import SessionLocal
from app.config.firebase import initialize_firebase
from app.routers import health, auth, albums, images, audio, upload, location, users, friends, notifications
from app.services.notification_service import run_daily_anniversary_job

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
settings = get_settings()


async def _anniversary_scheduler_loop():
    """Run the anniversary job every hour so users get notified when they arrive at a place later in the day."""
    while True:
        db = SessionLocal()
        try:
            await asyncio.to_thread(run_daily_anniversary_job, db)
        finally:
            db.close()
        await asyncio.sleep(60 * 60)  # 1 hour


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Handle application lifespan events"""
    # Startup
    initialize_firebase()
    scheduler_task = asyncio.create_task(_anniversary_scheduler_loop())
    yield
    # Shutdown
    scheduler_task.cancel()
    await scheduler_task


app = FastAPI(
    title="Memento API",
    description="FastAPI server with Neon database backend",
    version="1.0.0",
    debug=settings.debug,
    lifespan=lifespan,
)


def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
    from fastapi.openapi.utils import get_openapi

    openapi_schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
    )
    # Add security scheme
    openapi_schema["components"]["securitySchemes"] = {
        "bearerAuth": {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
        }
    }

    # Add security requirement to all protected endpoints
    if "paths" in openapi_schema:
        # List of paths that require authentication (exact matches and patterns)
        protected_path_patterns = [
            "/auth/me",
            "/albums",
            "/users",
            "/friends",
            "/images",
            "/audio",
            "/upload",
            "/notifications",
        ]

        for path, methods in openapi_schema["paths"].items():
            # Check if path should be protected
            is_protected = False

            # Check exact match or if path starts with any protected pattern
            for pattern in protected_path_patterns:
                if path == pattern or path.startswith(pattern + "/"):
                    is_protected = True
                    break

            if is_protected:
                # Add security to all HTTP methods for this path
                for method in ["get", "post", "put", "delete", "patch"]:
                    if method in methods:
                        methods[method]["security"] = [{"bearerAuth": []}]

    app.openapi_schema = openapi_schema
    return app.openapi_schema


app.openapi = custom_openapi

# CORS middleware configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Update this with your frontend URL in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(health.router)
app.include_router(auth.router)
app.include_router(albums.router)
app.include_router(images.router)
app.include_router(audio.router)
app.include_router(upload.router)
app.include_router(location.router)
app.include_router(users.router)
app.include_router(friends.router)
app.include_router(notifications.router)


@app.get("/")
async def root():
    return {"message": "Welcome to Memento API"}
