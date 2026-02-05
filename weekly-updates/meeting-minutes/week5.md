## Goals for this week
- get android studio running
- fully run through flow of app → split work
- gonna use jetpack compose for UI
    - helena: homepage
    - amirdha: gallery/modal popup/thing with a grid
    - alex: everything else

## App flow run through

### home page
- search (name of album, direct location, tags (eg. waterloo will show all the photos with the #waterloo tag)
- list item-y component (name, most recent date, shared/folder icon, etc) → we can figure out details later
  - no photos in the home page list view, each of them will be the name of the location or an album
- if you go to a location multiple times, once you click into it it’ll show all instances in a feed view but with timestamps of when you visited
Add an album icon
- to add content
  - plus button in homepage, design modification: three options camera, existing photos, album (to add a new album)
  - to add to an existing album you click into the modal gallery/album view below and go from there

### modal gallery/album view
- instagram profile kind of gallery view
- delete → deletes entire album
- modification to design: add your friends will be a floating icon on top right (eg. to avoid edge cases like when you have no friends then just a blank divider at bottom)
- still have the camera/photo add popup

### in a specific location (feed view)
- if it’s just a singular photo for that location just show that (like instagram post)
- if there’s multiple that you’ve added for singular location, or within an album multiple posts from friends, show it like instagram scrolling feed
- standard structure: photo, who added it (profile and name), audio (if any), caption (if any), tags (#waterloo, #gradtrip)
standard edit button, back button, delete button
