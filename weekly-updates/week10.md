# Project Memento Week 10 Update

Mar 6 - Mar 12

- Met this week to run through the full app flow, rescope the remaining timeline, and assign final dev and clean-up tasks [meeting minutes here](./meeting-minutes/week10.md)
- Entire team walked through each entry point and user experience, noted bugs and next goals
- Helena and Amirdha debugged add photo to new album not working
- Camera was debugged to work but permissions still need to be fixed
- Alex demo-ed the Google Maps feature
- Notifications did not trigger, Chiara and Aaryan continuing on that
- **Timeline:** This week is our last dev and clean-up week; next week we stop new features and focus on presentation, cleaning up bugs, etc.
- Work split for this period:
  - **Chiara:** Location as a backend field (save on add/edit photo, use from DB when opening—fixes homepage coords); Google Maps API location issue (giving wrong location); autofill current location/timestamp when no metadata if user allows location; no location/no audio etc. placeholders in photo; continuous scroll; delete settings and add disclaimer for notifications via system settings
  - **Helena:** Keep original photo size (no cutting off); lazy load; remove sort by from map view; dark mode everywhere except map; tap to play centered; go through report to ensure we have everything
  - **Priyanshu:** Edit in album (owner: edit name, mass delete; non-owner: mass delete own photos only); preserve grid/list preference when switching albums (default grid); default profile pic
  - **Amirdha:** Personal album title and specific album title; click applies to entire widget; timezone fix; swipe direction fix; multi select album bug (no homepage flash, "add photo twice" text, layering, sort by consistent with album filters)
  - **Alex:** Map show at least one location when too big; clumped locations show count and click to zoom; map footer text for count of photos without location/timestamp
  - **Aaryan:** Notifications (new album, shared with you, photos); camera permissions flow and upload-via-camera; fix delivery of notifications to device (Chiara’s endpoint)
- Decided to keep both grid and list view options
- **P3 report compliance:** Helena compared the codebase to the initial proposal. Find in the meeting minutes info about items we’re skipping or optionla, need to do, partially done, and any open questions 
- Goal/Task: Complete assigned dev and clean-up tasks; next week switch to presentation prep and bug fixes only
