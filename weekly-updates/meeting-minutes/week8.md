# Meeting Notes- Feb 22nd and 24th 2026

### Pre-meeting work
- Helena worked on selected photo and feed view (rendering the image, notes, extracting timestamp and location), integrated the homepage and selected album/memory pages
- Amirdha worked on functionality for plus button (add album, upload photo and camera three options, add flow for new album), ran into some issues at the end so will continue working on that for next week as well
- Alex finished profile portion
- Priyanshu added all the backend endpoints and cloudinary configuration for storing and extracting photos, details as followed:
  - Created a backend client for the app, feel free to use the helper, will make backend queries easier
  - Migrated login/auth to this new client, and added device level caching of auth token so user should only log in once
  - Integrated album GET. When opening the app, we will call a GET to pull all albums for a user
  - When testing, use Swagger to add albums since we still have not made the album creation page
  - When opening an album, we GET the images in the album, and caption editing is now working
  - When adding an image to an album, the app sends the image to cloudinary, gets a url then sends the url to the backend, thus images now persist and when closing and opening the app will stay (be aware of what you upload now, they are all saved)
- Aaryan worked on Neon architecture and backend
- Chiara worked on integrating the Google Maps API to extract the location for the photos


### Worked on P4 presentation
- Created slides, all of us worked on it based on presentation requirements and split up presentation slides to present
- Priyanshu created the architecture diagrams for the presentation
- Worked on final touches such as creating two accounts to demo shared photo albums and syncing
- Practiced presentation and run throughs

### Seeding test data/examples for presentation demo
- First, created several different users with different logins
- Used swagger new album endpoint to create new test albums, created different shared/access albums with the newly created users
- Then imported photos for each album, checked that location and time extraction was working
- Logged into other user and made sure that newly uploaded photos were working

### Work for next week:
- Alex: will start working on basic map UI
- Aaryan: exploring firebase and onesignal (another service built on top of firebase) will setup the notification service using whichever of the two services is most developer friendly
- Amirdha: Fixing issues with the album page and moving onto working on the feed view that we were discussing previously (should have sorts for the feed items)
- Helena: add audio to the photo contents, add editing ability to the photo contents (in top right three dots apart from delete add edit icon where you can edit the content, then save and that sends the edit endpoint),
- Chiara: Figuring out notifications, focus on getting the app to ping the users location to the backend (even when closed) and then writing an algo to send notifications
- Priyanshu: working on any remaining/ad hoc BE endpoints, working on the invite friends/link process
- For next time we meet we want to rediscuss what is left in the app and scope out the remaining tasks and timeline in terms of project completion
