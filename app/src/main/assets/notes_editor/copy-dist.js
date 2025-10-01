// copy-dist.js
const fs = require("fs");
const path = require("path");

const src = path.join(__dirname, "dist", "editor-bundle.js");
const destDir = path.join(__dirname, "..", "editor", "js");

// створюємо папку якщо її нема
if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true });
}

const dest = path.join(destDir, "editor-bundle.js");

// копіюємо файл
fs.copyFileSync(src, dest);

console.log("Copied:", dest);
