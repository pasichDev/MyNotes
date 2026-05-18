# CHANGELOG

## [2.6.46] - 18.05.2026

**New**

- **Notification sound:** Changed default notification sound; choose a custom melody in
  Settings → Media.
- **Notification volume:** Adjust notification volume directly from the app in Settings → Media.
- **Reminder repeat:** When setting a reminder, toggle "Repeat notification" to receive the
  alert again every 5, 10, 15, 30, or 60 minutes until the reminder is cleared. Works for
  both note and task reminders.

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
