# Meeting Notes- Week of March 17th

## Attendance
- Chiara did not attend this meeting
- Check in with Chiara separately to see if she finished her work

## Roundtable: what everyone worked on and completed
- Went around the table and discussed what each person worked on and completed since last time

## Testing and demos (what we verified)
- **Notifications:** Work and open to the notified item; linking notification to the actual new album / album photos were added to already works
- **Bulk upload:** Tested bulk upload using camera — works; bulk upload using existing photos — works
- **Map:** Location clustering with number works; UI suggests which photos don’t have location (to fix)

## Presentation prep — device testing
- Use **Pixel 8 or Pixel 9** emulator for testing to better replicate real Android devices for the final presentation

## Remaining polish / tasks

### Helena
- Fix the cutoff arrow at top
- Greyed-out boxes / skeleton placeholders for lazy view

### Priyanshu
- Fix the flash when deleting multiple items
- **AI idea (optional / stretch):** e.g. auto-group photos in personal album by similar content (multiple photos of cats) → suggest a banner at top: “Create a cat album using these photos?” Yes / No

### Alex
- **Clustering + album sort:** If there are 4 photos at DC, clicking the cluster opens the first photo at DC, then sort the album by location so the same-location photos are grouped together
- Make sure the **entire list item** is clickable (not just the image) where it’s missing
- **Map UX:** Arrow pointing toward where else on the map you have images; if clickable, even better

### Aaryan
- **Two toggles** to show whether locations are enabled (location toggle in settings)

---

## Final presentation planning

### If live demo is too tight
- Add **videos** for flows that take a long time to set up so we can still show them

### Main demo flows (outline)
1. **New user flow (Aaryan / other device):** Someone adds you to an existing album → see notification → see photos → **logout** (show **dark mode**)
2. **Curated user login:** Bulk upload new album; add locations to photos; add friend / remove friend; click into photo; edit (show you **can’t** edit if you didn’t add it — someone on another device); editing a photo add **audio** and save; show **list vs grid** view
3. **Map:** Many locations, spread-out times, plus images with **no location**; **filters** in the view
4. **Homepage:** Show **AI banner** (if implemented), approve suggested album, **search bar**, AI, filters

### Presentation slide / content breakdown
- **Quick reminder what the project is about** (+ optional: major requirement changes since proposal) → **Chiara**
- **Top 3 most significant design decisions** (architecture, patterns, practices, frameworks) → **Chiara**
- **Primary user scenario(s)** — complete picture of how a user finds the app useful; use the **3 flows from this meeting:** (1) new user + invite notification to existing album + logout, (2) log into curated user + bulk photos + edit photos, go in and tap audio, filters, etc (3) map functionalty and clustering (4) homepage search and filters and list/grid view  → **Amirdha**
- **Completeness** (core functional + NFRs), **Utility** (stakeholders, human values), **Polish** (intuitive UX, minimal bugs/glitches, waiting time, NFR optimization — ~80% = normal expectations), **Difficulty** (challenge for a term — ~80% = normal expectations) → **Helena**

### Who is working on presentation this week
- **Helena, Amirdha, Chiara** — presentation materials and coordination
- **Helena** will also go through the **frontend** and refactor a bit to follow design patterns
- **Helena** will be **out next week** and **cannot attend the meeting**; she will still make a **demo video** for bonus points

---

## Other notes
- Continue bug fixes and polish alongside presentation prep
