# My Notes

**My Notes** is a simple and convenient app for quickly jotting down notes.  
Organize your ideas, tasks, and important things without distractions.

[![My Notes on Product Hunt](https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1027550&theme=light&width=200)](https://www.producthunt.com/products/my-notes-4?utm_source=badge-featured&utm_medium=badge&utm_source=badge-my-notes-204c8f1f-1e34-423b-9817-bb05b82b69e2)

[![Download on Google Play](https://img.shields.io/badge/Google%20Play-Download-blue?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.pasich.mynotes)

---

## Features

- 📥 Import from **Google Keep**
- 🏷️ **Tags** for sorting and searching notes
- ✍️ **Advanced editor** (headings, lists, quotes, formatting)
- 🎨 **Themes and colors** to match your mood
- 🚫 **No ads** — just your notes
- 🔒 **Fully offline** — no servers or remote cloud
- 💻 **Open-source** — transparent and accessible code
- 🎯 **Modern and intuitive design**

**My Notes** helps you capture ideas, organize your day, and always keep important information at hand.

---

## Contribution

If you find bugs, issues, or have ideas to improve the app,  
please open a new [Issue](https://github.com/pasichDev/My-Notes/issues) in the project repository.

---

## Building Your Own Version

You can create your own custom version of **My Notes**,  
but the project requires a secure encryption key for attachments.

### 1. Create the `secret.properties` file

Inside the `app/` directory, you’ll find a template file:
```
app/secret.properties.example
```

Rename it to:
```
app/secret.properties
```

### 2. Generate your own encryption key

Add your custom secure 32-byte Base64-encoded key:
```
ATTACH_KEY=your_custom_generated_base64_key_here
```

This key is used to encrypt and decrypt attachments stored on the device.

**Example:** You can generate a key using any Base64 or AES-256 key generator.

---

### 3. Build the project

Once `secret.properties` contains your key, build the project:

#### Using Android Studio:
1. Open the project.
2. Sync Gradle.
3. Select **Build → Make Project**.
4. Run on a device or create an APK/AAB.

#### Using the command line:
```
./gradlew assembleDebug
```

For a release build:
```
./gradlew assembleRelease
```

---

### ⚠️ Important Notes

- The app will not build without a valid `ATTACH_KEY`.
- Each build uses your own private key — encrypted attachments will only be readable with that key.
- **Never commit the `secret.properties` file to your Git repository!**

---

## License

This project is licensed under the terms of the [Apache License 2.0](./LICENSE).

