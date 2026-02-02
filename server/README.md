# Memento API Server

FastAPI server with Supabase backend.

## Setup

1. **Create virtual environment**

   ```bash
   cd server
   python -m venv .venv
   ```

2. **Activate virtual environment**

   ```bash
   # Windows
   .\.venv\Scripts\Activate.ps1

   # macOS/Linux
   source .venv/bin/activate
   ```

3. **Install dependencies**

   ```bash
   pip install -r requirements.txt
   ```

4. **Configure environment variables**

   ```bash
   cp .env.example .env
   ```

   Update `.env` with your Supabase credentials:
   - `SUPABASE_URL` - Your Supabase project URL
   - `SUPABASE_PUBLISHABLE_KEY` - Your Supabase publishable (anon) key

5. **Run the server**
   ```bash
   uvicorn app.main:app --reload
   ```

## URLs

| URL                          | Description              |
| ---------------------------- | ------------------------ |
| http://localhost:8000        | API root                 |
| http://localhost:8000/docs   | Swagger UI documentation |
| http://localhost:8000/redoc  | ReDoc documentation      |
| http://localhost:8000/health | Health check endpoint    |

## Architecture

The API follows a layered architecture:

```
app/
├── routers/        # Route definitions - HTTP endpoints
├── controllers/    # Request/response handling - HTTP concerns
├── services/       # Business logic - orchestration layer
├── repositories/   # Data access - database queries
└── config/         # Configuration - settings & clients
```

| Layer            | Responsibility                                              |
| ---------------- | ----------------------------------------------------------- |
| **Routers**      | Define API endpoints, delegate to controllers               |
| **Controllers**  | Handle HTTP concerns (status codes, input validation, auth) |
| **Services**     | Handling business logic, orchestrate repositories           |
| **Repositories** | Executing database queries via Supabase client              |
