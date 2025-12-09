# CHANGELOG

## [2.4.42] - 08.12.2025

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
