# Project Memento Week 9 Update

Feb 28 - Mar 5

- Met March 3rd to assign remaining tasks, walk through overall user flow (align on design and scope for homepage, feed view, maps, and notifications) [meeting minutes here](./meeting-minutes/week9.md)
- Helena and Amirdha walked through the full flow from homepage (new photo/album/camera entry points) and their respective flows; discussed bulk upload goals (select multiple, then edit each one at a time) and maps integration (start with pin, then images on map, maps toggle for albums only)
- Finalized notifications: backend-only, location-based (when user is at a photo location); demo plan is to upload/edit a photo with location set to Googleplex and receive a notification within 60s, or use an existing photo at that location
- Redefined feed view and homepage design: homepage will use squares (single photo or 4 oldest photos for albums, album name under; no more list view); feed scroll and sorting to match album homepage filters
- Work split for next period:
  - **Priyanshu:** Log out, lazy load (show loading shapes instead of previous album flash when switching albums), popup native screen for add friend link (directly to messages), comments from multiple users under caption
  - **Amirdha:** Fix add friend issue (work directly on create album page), new homepage design (squares, album cover = first four photos), mass upload (select multiple, edit captions/locations etc. one by one)
  - **Alex:** Maps in stages—pin for location, then photo on map, then clickable full-screen view; maps toggle for albums only
  - **Helena:** Delete functionality (owner deletes album = for everyone; shared user = unshare for them only; photos deletable by owner or uploader), feed view with filters from album homepage, fix photo detail UI (no audio/save when none; edit in three dots only for uploader), add-photo flow (edit details on upload then save, not full-screen edit), send email to prof re presentation format
  - **Chiara:** Remove settings; add disclaimer to change notifications via system settings; continue Google search for edit location and finish notifications
  - **Aaryan:** Test camera on actual device, device token persistence for notifications, work on showing who added/edited note
- Goal/Task: Finish up assigned tasks, leaves us ready to connect peices next meeting and work on maps stretch goal