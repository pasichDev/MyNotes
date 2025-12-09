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

After running the build process, the following files are generated and copied into the Android
assets directory.

### **Bundled Production JS Files (`dist/`):**

- **`editor-bundle.min.js`**  
  Contains:
  - Editor.js core
  - All integrated Editor.js tools
  - Custom tools from `/src/tools`

- **`editor-init.min.js`**  
  Contains:
  - Initialization logic from `src/editor-init.js`
  - Editor configuration & startup

- **`runtime.min.js`**  
  Contains:
  - Android bridge (`src/custom/runtime.js`)
  - Internal event handlers
  - Communication JS ↔ Android

### **Additional Files Included in the Build:**

- **`attaches.min.js`** (copied directly from `/src/tools`)  
  Customized Attaches Tool adapted for Android WebView.

- **`locales.js`** (from `/src/custom/`)  
  Localization handler for Editor.js UI.

- **`editor.css`**  
  Stylesheet for Editor UI (app theme, mobile UI tweaks).

- **`editor.html`**  
  Base HTML template loaded inside Android WebView.

---

All generated assets are automatically copied to:

```
MyNotes/app/src/main/assets/editor/
```

## 🔨 Build Instructions

1. Install dependencies:
   npm install

2. Build bundles:
   npm run build

3. After building, the compiled files will appear in:
   **dist/**

4. Then they are copied to:

 ```
MyNotes/app/src/main/assets/editor/
```

## Libraries & Plugins Used

- [Editor.js](https://editorjs.io/)
