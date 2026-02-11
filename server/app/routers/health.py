from fastapi import APIRouter
from app.repositories.health_repository import HealthRepository

router = APIRouter(prefix="/health", tags=["health"])
repository = HealthRepository()


@router.get("")
async def health_check():
    return repository.ping_database()
