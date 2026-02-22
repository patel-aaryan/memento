from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config.settings import get_settings
from app.routers import health, auth, albums, images, audio, upload, users

settings = get_settings()

app = FastAPI(
    title="Memento API",
    description="FastAPI server with Neon database backend",
    version="1.0.0",
    debug=settings.debug,
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
            "/images",
            "/audio",
            "/upload",
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
app.include_router(users.router)


@app.get("/")
async def root():
    return {"message": "Welcome to Memento API"}
