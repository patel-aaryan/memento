# Meeting Notes- March 3rd

## Remaining tasks:
- log out: Priyanshu
- lazy load (right now when we switch between albums we see a flash of the previous album, it should show the loading shapes instead): Priyanshu
- fix add friend issue (leave the album then go back in then it works, we want it to work directly in the create album page): Amirdha
- keep on working on maps: stages of implementation: just a pin for the location, adding the photo on top for location, making the photo clickable into full screen view: Alex
- delete functionality -> albums if the owner deletes it, deletes for everyone, if shared person deletes it, only deletes for them (unremoved from album), for photos only the person who owns the album or uploaded the photo can delete it: Helena
- feed view: scrollable based on exact filters from album homepage (if we sort by a specific user, then when we click into it, it should also only show the specific user sorted photos, etc): Helena
- edit: right now, the clicked in photo still isn’t the right UI (if no audio the audio shouldn't show up, there should be no save button) fix that, only if you uploaded the photo, you get the edit option in the three dots: Helena
- when you add a photo from an album, shouldn’t directly upload it and then you edit in it's full screen view (old flow), give you edit details directly when you upload and then save: Helena
- homepage should not be lists anymore, squares of singular photo, or 4 oldest photos if it’s an album, if album add name under, if not album, nameless: Amirdha
- popup native screen for add friend link (directly bring you to messages): Priyanshu
- delete settings, to change notifications go to your own system settings (add disclaimer text at bottom): Chiara
- continue with google search for edit location and finish notifications: Chiara
- search in homepage: name of album versus location of singular photos
- comments from prof of allowing "comments" from multiple users underneath the caption: priyanshu
- mass upload (mass upload/select photos, put captions, locations, etc individually but atleast they’re all together one by one): Amirdha
- send email to prof based on presentation format that image editing navigation means just caption, etc: Helena
- consider showing who added/edited the note: Aaryan
- test and debug if camera works on actual device: Aaryan
- device token persists for notifications: Aaryan

## Discussing through overall user flow
- Helena and Amirdha fully walked through the flow, from homepage new photo/album/camera entry points and their respective flows
- Talked about possible bulk upload goals, you can select multiple and then go through the editing process (if the geolocation is wrong, add caption, etc) for those one at a time rather than having to upload each individually for that
- Talked about goals for the Maps integration. Start with pin then move onto the actual images showing, only have maps toggle for albums not for the homepage
- New homepage design of having squares for the images rather than list views, almost like Spotify and with albums have first four photos be the album cover
- Redefine feed view, figure out sorting of the images, what the continuous scroll looks like, etc
- Revisted notifications, finalized that there's only a BE notification for when you're at the location rather than any FE banner, etc
  - Demo plan: so in the demo i'm assuming there will be a part where we upload a photo and then edit it's details, if you edit the details to Googleplex (since that's where the emulator's location is) and set the date to a year ago, it should give a notification within 60s (i can change that to be shorter for the purposes of the demo)
  - or alternatively, have a photo already uploaded with a location set to Googleplex and then about a minute into the demo you should get a notification.
  - basically it checks your location every 60s and if any of the photos locations are within a 500m radius of your current location it pushes a notification. limited to 1 notif per location.