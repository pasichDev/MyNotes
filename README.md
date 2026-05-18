# My Notes

**My Notes** is a fast, clean, and fully private note-taking app for Android.  
No accounts, no cloud, no ads — just your content stored safely on your device.

---

![GitHub release](https://img.shields.io/github/v/release/pasichDev/MyNotes?style=flat-square&label=release)
![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/pasichDev/MyNotes/ci.yml?branch=master&style=flat-square&label=build)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![API](https://img.shields.io/badge/API-26%2B-brightgreen?style=flat-square)
![GitHub stars](https://img.shields.io/github/stars/pasichDev/MyNotes?style=flat-square)
![GitHub issues](https://img.shields.io/github/issues/pasichDev/MyNotes?style=flat-square)
![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=flat-square)

---

[![My Notes on Product Hunt](https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1027550&theme=light&width=200)](https://www.producthunt.com/products/my-notes-4?utm_source=badge-featured&utm_medium=badge&utm_source=badge-my-notes-204c8f1f-1e34-423b-9817-bb05b82b69e2)
&nbsp;&nbsp;
[![Download on Google Play](https://img.shields.io/badge/Google%20Play-Download-blue?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.pasich.mynotes)

---

## Screenshots

<div style="display: flex; justify-content: center; gap: 10px;">
  <img src="doc/scr1.jpg" width="280" />
  <img src="doc/scr2.jpg" width="280" />
</div>

## Features

- 📥 Import from **Google Keep**
- 🏷️ **Tags** for sorting and searching notes
- ✍️ **Advanced editor** — headings, lists, quotes, formatting (Editor.js)
- 🔔 **Reminders** with repeat schedules
- 🎨 **Themes and colors** to match your mood
- 🚫 **No ads** — just your notes
- 🔒 **Fully offline** — no servers, no cloud
- 💻 **Open-source** — transparent and accessible code
- 🎯 **Modern and intuitive design**

## Privacy

My Notes does not collect or send any data to external servers.  
All notes, attachments, and preferences are stored **locally on the device**.  
No tracking, no analytics, no cloud — full privacy by design.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java |
| DI | Dagger Hilt |
| DB | Room |
| Editor | Editor.js (WebView) |
| UI | ViewBinding / DataBinding |
| Build | Gradle + Spotless |

## Development

**Code style:** [Spotless](https://github.com/diffplug/spotless) + Google Java Format (AOSP, 4-space indent).

```bash
# Check formatting
./gradlew :app:spotlessCheck

# Auto-fix formatting
./gradlew :app:spotlessApply
```

**Git hooks** — install once after cloning:

```bash
git config core.hooksPath .githooks
```

- **pre-commit** — runs `spotlessCheck` (~15s). Fails fast on format violations.
- **pre-push** — runs `lintDebug` (~2–3 min). Skip with `SKIP_LINT=1 git push`.

## Contribution

Found a bug or have an idea? Open a new [Issue](https://github.com/pasichDev/MyNotes/issues).  
Pull requests are welcome — please run `spotlessApply` before submitting.

## License

This project is licensed under the terms of the [Apache License 2.0](./LICENSE).
