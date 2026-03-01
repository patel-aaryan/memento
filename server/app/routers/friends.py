from fastapi import APIRouter, Depends, HTTPException, status, Security
from fastapi.responses import HTMLResponse
from sqlalchemy.orm import Session
from app.config.db import get_db
from app.config.settings import get_settings
from app.dependencies.auth import get_current_user, security
from app.repositories.user_repository import get_user_by_email, get_user_by_id, get_users_by_ids
from app.repositories.friend_repository import add_friendship, get_friend_ids
from app.schemas.auth import UserResponse
from app.schemas.friend import AddFriendRequest, AddFriendByLinkRequest, FriendLinkResponse
from app.utils.auth import create_access_token, decode_access_token
from datetime import timedelta

router = APIRouter(prefix="/friends", tags=["Friends"])
settings = get_settings()

FRIEND_LINK_EXPIRE_MINUTES = 60 * 24 * 7  # 7 days


@router.post("/add_friend", response_model=UserResponse)
async def add_friend_by_email(
    body: AddFriendRequest,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Add a friend by their email. Friendship is bidirectional."""
    if body.email.lower() == current_user["email"].lower():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot add yourself as a friend",
        )
    friend = get_user_by_email(db, body.email)
    if not friend:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No user found with that email",
        )
    added = add_friendship(db, current_user["id"], friend["id"])
    if not added:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Already friends with this user",
        )
    return UserResponse(**get_user_by_id(db, friend["id"]))


@router.get("/get_friend_link", response_model=FriendLinkResponse)
async def get_friend_link(
    current_user: dict = Depends(get_current_user),
):
    """Get a shareable link. When someone clicks it and opens the app, they become your friend."""
    token = create_access_token(
        data={"inviter_id": current_user["id"], "purpose": "friend_link"},
        expires_delta=timedelta(minutes=FRIEND_LINK_EXPIRE_MINUTES),
    )
    base = settings.app_base_url.rstrip("/")
    link = f"{base}/friends/add_friend?token={token}"
    return FriendLinkResponse(link=link)


@router.post("/add_friend_by_link", response_model=UserResponse)
async def add_friend_by_link(
    body: AddFriendByLinkRequest,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Add the inviter as a friend using a token from a friend link. Call this when the app opens from a friend link."""
    payload = decode_access_token(body.token)
    if not payload or payload.get("purpose") != "friend_link":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired friend link",
        )
    inviter_id = payload.get("inviter_id")
    if inviter_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid friend link",
        )
    try:
        inviter_id = int(inviter_id)
    except (ValueError, TypeError):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid friend link",
        )
    if inviter_id == current_user["id"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot add yourself as a friend",
        )
    inviter = get_user_by_id(db, inviter_id)
    if not inviter:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Inviter no longer exists",
        )
    added = add_friendship(db, current_user["id"], inviter_id)
    if not added:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Already friends with this user",
        )
    return UserResponse(**inviter)


@router.get("", response_model=list[UserResponse])
async def get_my_friends(
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Get list of friends (full user objects)."""
    friend_ids = get_friend_ids(db, current_user["id"])
    if not friend_ids:
        return []
    users = get_users_by_ids(db, friend_ids)
    return [UserResponse(**u) for u in users]


# Unauthenticated route: serves HTML that redirects to app deep link
# GET /friends/add_friend?token=... - no auth, redirects to memento://add_friend?token=...
@router.get("/add_friend", response_class=HTMLResponse, include_in_schema=False)
async def add_friend_redirect_page(token: str):
    """
    Web page for friend links. Redirects to memento://add_friend?token=...
    so the Android app can open and call POST /friends/add_friend_by_link.
    """
    escaped = token.replace('"', "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Add Friend - Memento</title>
    <meta http-equiv="refresh" content="0;url=memento://add_friend?token={escaped}">
</head>
<body>
    <p>Opening Memento...</p>
    <p>If the app doesn't open, <a href="memento://add_friend?token={escaped}">tap here</a>.</p>
</body>
</html>"""
    return HTMLResponse(html)
