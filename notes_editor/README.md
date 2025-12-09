## Notes Editor JS

JavaScript bundle used by the My Notes Android application.
This module provides the full integration of Editor.js inside Android WebView, including custom
plugins, runtime logic, localization, and JS ↔ Android bridge mechanics.

It compiles all editor components into optimized assets automatically delivered to:

```
MyNotes/app/src/main/assets/editor/
```

## ✨ Features

- ⚡ **Full Editor.js integration** optimized for mobile WebView
- 🧩 **Custom tools**, including:
    - `ImageToolClickable`
    - Modified `attaches.min.js`
- 🌍 Built-in **localization handler** (`locales.js`)
- 🔌 **Android bridge** (`runtime.js`) that handles:
    - Content sync
    - Theme changes
    - Callbacks
    - File uploads
    - Communication Android → JS & JS → Android
- 🎨 Custom **editor UI styling** (`editor.css`)
- 🛠️ Fully **offline** — no external CDN
- 📦 Automatic **asset copying** via `copy-dist.js`

## 📦 Output Files

- **Production bundles:**
    - `dist/editor-bundle.min.js`
        - Editor.js core
        - All integrated Editor.js tools
        - Custom tools from `/src/tools`
        - Editor init (`editor-init.js`)
    - `dist/custom.js`
        - Android runtime (`src/custom/runtime.js`)
        - Localization logic (`src/custom/locales.js`)
        - Glue layer for Android ↔ Editor.js

These files are then copied into Android assets automatically.

## 🔨 Build Instructions

1. Install dependencies:
   npm install

2. Build bundles:
   npm run build

3. After building, the compiled files will appear in:
   **dist/**

4. Then they are copied to:

 ``` 
MyNotes/app/src/main/assets/editor/js/
```

## Libraries & Plugins Used

- [Editor.js](https://editorjs.io/)
