# Meeting Notes- Week of March 10th

## Full app flow walkthrough
- Entire team ran through the app flow as a whole, going through each entry point, user experience, etc.
- Noted down bugs and things to fix as well as next goals

## Rescoping and timeline
- Rescoped project and looked at remaining weeks
- **This week (week 10) is our last dev and clean-up week**
- Next week we will stop with new features and work on presentation, cleaning up bugs, etc.

## Demos and debugging
- Alex demo-ed the Google Maps feature
- Notifications did not trigger; Chiara and Aaryan will be continuing work on that
- Debugged the camera to work; need to fix permissions for that
- Helena and Amirdha debugged the issue of add photo to new album not working

---

## P3 report compliance / proposal comparison

Helena compared the codebase to the initial proposal report. Below: what we’re skipping (or optional), what we need to do (with assignees), partial items, and one open question.

### Probably don’t have time (if anyone wants to take it, text in GC)
- **(Stretch) Albums map:** View albums in map view by average location — we only have map for individual albums; skipping unless someone wants it.
- **Contact synchronization (optional):** Sync contacts to find friends on app — if we could do this, great; any takers?
- **(Stretch) Read-only web link:** Temp password-protected web link to album — fine without it.
- **(Stretch) Offline access:** Take photos, queue for later sync — nah.

### We need to do (assigned)
- **Bulk camera upload:** Buffer multiple images before sending (currently one photo at a time) → **Priyanshu**
- **Bulk upload:** Select a list of images from camera roll → **Amirdha** (confirm this aligns with your current week tasks)
- **Invite notifications:** Push when invited to shared album or friend request → **Aaryan** (notifications-related)
- **Activity alerts:** Option to receive notifications when friend adds photo to shared album → **Aaryan** (notifications-related)
- **Password change:** Only via email to that address (we don’t have it) → **Priyanshu** (or Helena if no BE needed)

### Partial (assigned)
- **Friend requests: send, accept, decline** — We don’t have decline → **Helena** will add decline.
- **Visual clarity: map clustering** — >10 photos in 50px radius → single numbered bubble → **Alex** (please confirm this is what you’re working on this week)

### Not sure — need team input
- **Camera: use CameraX for hardware heterogeneity** — Read the notes in the report and lmk what you think; do we have to change from current camera implementation?

---

## Task assignments

### Chiara
- Make the location a field in backend so we're not calling the Google Maps API each time we click into the photo—call it once when we add a photo/edit a photo, then save it to that field so when we open a photo we directly use from DB (also fixes photos on homepage showing just coords)
- Issue w/ location Google Maps API (Chiara has the details)
- When photo doesn't have any metadata directly: if the user allows location, autofill the user's location and timestamp; if not, set template "No location/timestamp provided" text
- Add no location, no audio, etc. spaces in the photo
- Try to make continuous scroll (for feed view rather than currently it's)

### Helena
- Not cutting the photo off—keep original size
- Lazy load still not working
- Remove sort by from map view
- Fix everything to be dark mode (except for map)
- Tap to play move it centered
- Go through report and make sure we have everything
- **Friend requests: add decline** (we have send/accept but not decline)
- **Password change:** If no BE needed, Helena can take it (otherwise Priyanshu)

### Priyanshu
- Edit in album: if you own the album you can edit the name, and you can delete all of the files in the album (select and mass delete). If you don't own then you can only select and mass delete photos you own
- When you switch into an album keep preference (default preservation is grid)
- Have a default profile pic
- **Bulk camera upload:** Buffer multiple images before sending (currently one photo at a time)
- **Password change:** Only via email to that address (or Helena if no BE needed)
- Delete settings; to change notifications go to your own system settings (add disclaimer text at bottom)

### Amirdha
- Add "personal album" title and specific album title—just render it
- Make sure click applies to entire widget (saving details )
- Fix timezone stuff
- Fix swipe direction
- Fix multi select album bug: try to make it not flash the homepage; change "add photo twice" text in an album; layering issue and change look of sort by (make it consistent with album filters)
- **Bulk upload:** Select a list of images from camera roll (confirm this aligns with your current week tasks)

### Alex
- Fix map if it's too big (too many photos in different locations)—at least show one default location and user can scroll themselves
- **Map clustering (visual clarity):** If it's clumped then just show a number; click on it auto zooms in to show more specific places (>10 photos in 50px radius → single numbered bubble)—please confirm this is what you're working on this week
- If photos don't have location/timestamp: add text at bottom of map saying _ number of photos don't have location UI at all / timestamp at all

### Aaryan
- Notifications related to new album, shared with you, photos
- **Invite notifications:** Push when invited to shared album or friend request
- **Activity alerts:** Option to receive notifications when friend adds photo to shared album
- Enable camera access—if user rejects it we need flow to give popup to allow permissions; implement flow for uploading via camera
- Fix notifications: need to fix getting the actual notification to the device (endpoint is already made by Chiara)

---

## Other decisions
- **Grid vs list:** Keep both grid and list option (yes)
