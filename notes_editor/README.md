# Notes Editor JS

JS bundle for the built-in notes editor in the Android app.  
This project provides seamless integration of **Editor.js** with the mobile application, including custom logic for Android.

## Description

During the build process, two JavaScript files are generated:

- **Editor.js + plugins** → `editor-bundle.min.js`
- **Android interfaces & customization** → `custom.js`

After the build is completed, both files are automatically copied into the Android Assets directory:  
`MyNotes/app/src/main/assets/editor/js/`

## Usage

1. Install dependencies  `npm install`
2. Run the build process
`npm run build`
3. The generated files will be available in::
`MyNotes/app/src/main/assets/editor/js/`

## Libraries & Plugins Used

- [Editor.js](https://editorjs.io/)
