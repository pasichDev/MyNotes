# CHANGELOG

## [Unreleased]

**New**

- **Meet Encly:** A new page in the menu, and a one-time dialog after this update, introduces
  Encly, the encrypted, offline successor to My Notes. With Encly installed, one tap moves your
  notes (including the trash), tasks, tags and task lists to it. Attachments, images, reminders
  and pins stay behind, and the page tells you how many. Nothing is deleted from My Notes; after
  a successful move you can clear it as a separate, confirmed step.
- **Notes reopen where you left them.** A long note opens at the line you were reading, and if
  you were editing, the cursor is back where it was as soon as you continue. This works in both
  editors and survives restarting the app. If the note changed in the meantime, here or on
  another device, the same place is found again by the text around it; a note that was rewritten
  or emptied opens at the top. Positions stay on this device and are never synced.
- **Choose how notes open.** Settings → Interaction has three new options. Open notes in
  reading mode, editing mode, or automatically as before (reading in the simple editor, editing
  in the extended one). Open them where you left off or always at the beginning. And, if you like,
  double-tap the text in reading mode to start editing right where you tapped. New notes always
  open ready for typing. These choices stay on this device.
- **Custom repeat for reminders.** Next to Daily, Weekly and Monthly, Custom… repeats a note
  reminder every so many hours, days, weeks, months or years, from 1 to 999. The rule shows
  wherever the reminder does, as in "Every 3 hours", on the note card, at the top of the note and
  in More. Each time is counted from the one you picked, so a reminder never drifts later, a
  monthly reminder on the 31st rings on the last day of shorter months and is back on the 31st
  afterwards, and a yearly one on 29 February rings on 28 February in other years. Daily, weekly
  and monthly reminders keep working in older versions of the app on your other devices.
- **Version history.** More → Version history lists earlier versions of a note, newest first,
  with when and why each was kept: while you edit (at most every ten minutes, or at once when a
  large part of the text is removed), and always before a sync, a conflict choice or a restore
  replaces the text. Open a version to see it next to the current note with the difference
  highlighted, and restore it in one tap; the text it replaces is kept too, so a restore can be
  undone. Up to 20 versions per note stay on this device only. They are not synced or included in
  backups, and they are deleted together with the note when it is deleted for good. A version
  brings back text; attachments that are no longer in the note do not come back with it.

**Improvements**

- The main screen's search bar is tidier. Sorting and the list or grid layout now live together
  under one View options button, which opens a sheet where both can be changed in one visit; the
  button shows the layout you are using. The search hint no longer gets cut off with large text,
  and the menu puts Tags and Trash first, then Tasks, with a single way to support the developer.
- The note toolbar keeps only Back, the save status and More. An upcoming reminder now shows as a
  chip at the top of the note with its day and time; tap it to change or remove the reminder. New
  reminders are set from More.
- Search puts the best match first. A note whose title is exactly what you typed comes first,
  then titles that start with it, contain it as a word or contain it at all, then notes whose
  tag matches, and only then notes that mention it in their text; pinned and newer notes lead
  within each group. Case, accents and extra spaces no longer matter, so "cafe" finds "Café".
  A single letter already finds titles, and the matching part of each result is highlighted.

**Fixes**

- Reading mode in the extended editor now locks the note's title too; it could still be edited
  while the rest of the note was read-only. Reading mode also keeps your place when you switch it
  on or off, and the toolbar button always shows the right action.
- Tapping a reminder opens the note in the editor it belongs to. Notes with attachments, and all
  notes when the extended editor is on, used to open in the simple editor from a reminder.
- Switching between list and grid no longer makes cards slide over one another: the list fades,
  takes its new shape and fades back in. The layout button also shows the right layout as soon
  as the app opens, instead of the opposite one.
- Notes no longer vanish from the main screen after a Google Drive sync. The list, the tags row
  and the selected category stay in step with what sync changed: a category renamed or merged on
  another device stays selected, and one deleted there falls back to All notes instead of
  showing an empty page. A sync that changes nothing leaves the screen untouched.
- A sync no longer rewrites notes, tags, tasks and task lists that did not change. Only what
  another device actually changed is written, so a sync with nothing new finishes without the
  main list redrawing or a card flickering.
- The main list animates smoothly again. Cards no longer slide over one another when a note moves
  to the top or the grid is rearranged, the list can no longer get stuck invisible or half-faded
  after quick changes, and returning from a note no longer shuffles cards under the closing
  animation. With system animations turned off, every change is applied instantly.
- Editing a long note no longer throws you around. Opening or closing the keyboard keeps the text
  where it was and the cursor in view, tapping Edit puts the cursor on the part of the note you
  are reading instead of jumping to its end, and the cursor no longer falls back to the top of
  the note so that typing lands in the first line. Links in the text still open with a tap.
  Typing stays smooth in long notes, and the rich editor now moves its text above the keyboard.
- Nothing typed in the last moments before leaving a note is lost any more: switching apps,
  rotating the screen or opening the file picker saves it straight away. After a rotation the note
  reopens at the same place, with the cursor where it was.
- Adding a picture in the rich editor no longer makes the note flicker or jump. The editor stays
  responsive while the picture is saved, its place is kept at the right size from the start so
  the text around it does not move, and the editor no longer flashes white when it opens,
  especially in the dark theme. A picture picked while the screen was recreated (for example
  after rotating it) is still added where you inserted it, and a picture you have just added can
  no longer be cleaned up before the note has saved it.
- Repeating reminders are kept up to date. One missed while the phone was off or the app was
  stopped now arrives once when it is back and then continues on schedule, instead of stopping
  for good. Snoozing no longer cancels a repeating reminder, a snooze survives a restart, and
  "repeat notification" no longer pushes the next occurrence later. A reminder set, changed or
  removed on another device takes effect here as soon as the sync finishes. Without the "Alarms
  & reminders" permission reminders are still delivered, possibly a little late, and the app
  offers to turn it on instead of silently skipping them.

## [2.6.55] - 25.09.2026

**Improvements**

- The app no longer interrupts you with a full-screen update prompt on start. Google Play keeps
  My Notes up to date on its own, following your Play Store settings.

**Fixes**

- A new rich note you open and leave without typing is discarded instead of showing up in the
  list and statistics as an empty note.

## [2.6.54] - 05.09.2026

**New**

- **Google Drive sync:** Keep notes, tasks, tags, settings, and attachments in sync across your
  devices, while the app keeps working fully offline. Sync is optional and off until you sign in.
  The first sync explains exactly what will be uploaded and waits for your confirmation.
- **Your data:** The backup screen gained an Account tab — sign in, see sync status and when the
  last sync ran, sync on demand, and turn on background sync. Backup, export, and import stay in
  their own tabs and work without an account.
- **Choosing between two versions:** When the same note was edited on two devices, the app now
  shows both versions side by side with their times, marks the newer one, and highlights exactly
  where they differ, so you pick a version instead of guessing which side is yours.

**Improvements**

- Attachments are uploaded once and verified by content, so the same image shared between notes
  never travels twice and a damaged upload is detected rather than trusted.
- Background sync runs only on unmetered networks and not on a low battery, and it skips
  publishing entirely when nothing has changed.
- The Account tab now says plainly when sync cannot be offered on a device that has no Google
  Play services, instead of showing controls that lead nowhere.
- Translations updated across all supported languages for sync and the account screen.

**Fixes**

- Restoring a backup no longer loses a note when the backup mixes restored and renumbered
  entries, and a note's attachments now follow it into the restored note.
- Leaving the screen during a sync, or a sync interrupted partway, no longer loses the time of
  the last successful sync or the edits that were being uploaded.
- Fixed a blank strip drawn above the toolbar on the Tasks and Help screens, which looked like a
  second, empty app bar.
- Fixed Google sign-in on Android 8.0 and 8.1.

## [2.6.46] - 18.05.2026

**New**

- **Notification sound:** Changed default notification sound; choose a custom melody in
  Settings → Media.
- **Notification volume:** Adjust notification volume directly from the app in Settings → Media.
- **Reminder repeat:** When setting a reminder, toggle "Repeat notification" to receive the
  alert again every 5, 10, 15, 30, or 60 minutes until the reminder is cleared. Works for
  both note and task reminders.

**Improvements**

- **Help:** Updated the in-app guide — added dedicated sections for Tasks and Reminders with
  step-by-step descriptions of all key features.

## [2.5.45] - 10.05.2026

**New**

- **Reminders:** You can now set a date and time reminder on any note. Tap the bell icon in the
  note editor toolbar or open the note options menu to set, edit, or delete a reminder. When the
  time comes, you'll receive a notification — tap it to open the note directly. Reminders support
  repeat intervals (daily, weekly, monthly) and a snooze option (10 minutes, 1 hour, or tomorrow
  morning). Reminders are restored automatically after a device reboot.
- **Tasks:** A new dedicated screen for managing tasks and to-dos. Create tasks with titles and
  optional descriptions, organize them into color-coded categories, reorder by dragging, and mark
  them as complete. Completed tasks are grouped separately and can be cleared in bulk.
- **Task reminders:** Set a date and time reminder on any active task — a notification arrives at
  the chosen time and tapping it opens the task list directly. Only future times are selectable.
  Reminders are restored automatically after a device reboot.

**Fixes**

- Fixed memory leaks that could occur during note editing and when navigating between screens.

**Improvements**

- **Pin notes:** You can now pin any note to keep it at the top of the list. Tap the note options
  menu and select "Pin note" — pinned notes always appear first, regardless of the current sort
  order. A small pin icon is displayed on pinned note cards. Tap "Unpin note" to remove the pin.
- Delete confirmation dialogs for tasks and categories now display a clear visual style
  with an error-tinted icon and the item name.

## [2.4.44] - 28.01.2026

- Fixed several issues reported in the previous version


## [2.4.43] - 22.12.2025

- Fixed several issues reported in the previous version
- Introduced a dedicated dialog for copying notes in the advanced editor
- **Advanced editor:** Added a new Spacer block for better layout control
- You can now apply a tag to multiple notes at once


## [2.4.42] - 09.12.2025

**New**

- Added a new Silver accent theme for improved visual customization.
- Introduced the Tag Overview dialog — long-press on “All Notes” to quickly browse and select tags.
- Implemented seamless switching between the Simple Editor and Extended Editor directly from the
  note editing screen.
- Enhanced user experience when copying notes — improved animations and clearer visual feedback.

**Improvements**

- Conducted extensive code and UI review to improve overall application stability and
  maintainability.
- Refined the interface and visual design across multiple screens: polished spacing, colors,
  animations, and component behavior.
- Improved interaction logic in the notes list, including selection modes, swipe actions, and tag
  operations.

**Fixes**

- Resolved a large number of bugs affecting note rendering, list updates, tag behavior, and
  selection mode.
- Fixed issues related to trash restore actions, animations, and state synchronization.
- Fixed incorrect ordering of tags in MoreNotes Dialog (now uses unified TagsSorter logic).
- Improved overall performance and stability to ensure a smoother and more reliable user experience.

## [2.4.41] - 01.12.2025

- **Advanced editor:** added full support for inserting images into notes.
- **Advanced editor:** added the ability to attach files to notes (up to 20 MB).
- Added adjustable UI text scale (independent from system font).
- Added new screen displaying the list of application dependencies.
- Added automatic optimization of uploaded images.
- Added adaptive app icon, including a monochrome variant for Material You themed icons.
- Incorrect processing of backups has been fixed and compatibility with previous versions has been
  improved.
- Code review and optimization performed, obsolete and unnecessary functionality removed, known bugs
  fixed

## [2.3.40] - 13.10.2025

- Optimized code and improved app stability
- Added translation support for the advanced editor

## [2.3.38] - [2.3.39] - 02.10.2025

- Added an Extended Notes Editor with formatting options: headings, lists, quotes, and other tools
  for creating structured and visually rich notes. The extended editor can be enabled in Settings →
  Interaction
- Improved interface on the settings page and other design elements
- Fixed a bug displaying snackbar on the home screen
- Fixed a bug when restoring backup copies: long wait for the progress dialog
- Update help section

## [2.2.37] - 28.09.2025

- The “Backups” section has been moved to “Your data.” The ability to store backups on Google Drive
  and in device memory has also been added.
- The contrast of the design has been improved and some interface elements have been updated for
  more convenient operation.
- All network access requests have been removed; the app works completely offline. The only Internet
  permission required is for updates and in-app purchases.
- Removed Google sign-in and Google Drive sync.

## [2.2.36] - 23.09.2025

- Added search debounce — search now triggers shortly after user stops typing.
- Optimized code and improved app stability
- Restored display of the new version in the MainDrawer

## [2.2.35] - 21.09.2025

- Added import of notes, trashed notes, and tags from other apps (e.g., Google Keep)
- Improved backup and data export — easier to save and restore notes
- Updated tag management: sorting added and drag-and-drop support for custom arrangement
- Fixed MainDrawer display issues
- General UI enhancements and bug fixes
- Updated links to website and privacy policy
- Fixed keyboard overlapping text issue
- Fixed automatic text scrolling on devices

## [2.2.34] - 05.09.2025

- Interface updates on some screens
- Fixed obtaining a backup copy from the cloud
- Fixed vibration feedback when opening dialogs

## [2.2.33] - 04.09.2025

- Added auto-save when editing notes
- Added the ability to save selected text via the context menu
- Added the ability to export notes for viewing or quick sharing with others.
- Now you can easily share your notes or save them locally! TXT, PDF, and HTML formats are
  available, as well as the option to send them via Google Drive or other applications.
- Fixed main thread operations that could cause the app to freeze or crash.
- Fixed data retrieval when sharing content from other apps.
- Fixed saving notes when there are many changes in processing
- Minimum Android support has been increased: the app now works on Android 8.0 (API 26) and above.

## [2.1.32] - 30.08.2025

- Added support for quick actions Create note, Search
- Added Help section with detailed information about the app’s features
- Fixed an issue with BackupAgent
- Optimized app performance and improved stability
- Added a navigation drawer for easier interface interaction

## [2.1.30 - 2.1.31] - 28.08.2025

- Added support for Android 16
- Added screen protection feature
- Added developer support
- Code refactoring and preparation for upcoming large-scale features
- Increased maximum number of tags to 25
- Fixed a bug when adding tags
- Improved user experience when interacting with the interface
- Optimized performance and overall stability
- Removed support for creating home screen shortcuts
