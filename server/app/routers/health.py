from fastapi import APIRouter, HTTPException
from app.database import get_supabase

router = APIRouter(prefix="/health", tags=["health"])


@router.get("")
async def health_check():
    try:
        supabase = get_supabase()
        # Simple query to verify database connection
        response = supabase.rpc("heartbeat", {}).execute()
        return {"status": response, "database": "connected"}
    except Exception as e:
        raise HTTPException(status_code=503, detail={
                            "status": "unhealthy", "database": str(e)})
