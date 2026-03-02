# ---- Builder Stage ----
FROM python:3.12-slim AS builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies into a prefix directory
COPY requirements.txt .
RUN pip install --prefix=/install --no-cache-dir -r requirements.txt

# ---- Runtime Stage ----
FROM python:3.12-slim AS runtime

WORKDIR /app

# Install only the runtime system library for psycopg2
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Copy installed packages from builder
COPY --from=builder /install /usr/local

# Create a non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy application source
COPY --chown=appuser:appgroup app/ ./app/

# Copy Firebase service account key (optional, for local Docker only)
# For production, use FIREBASE_SERVICE_ACCOUNT_JSON environment variable
COPY --chown=appuser:appgroup serviceAccountKey.json ./serviceAccountKey.json 2>/dev/null || true

USER appuser

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
