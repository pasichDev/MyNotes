import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

// Емуляція __dirname в ESM
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// Папка з бандлом
const distDir = path.join(__dirname, 'dist')
const srcDir = path.join(__dirname, 'src')

// Кінцева папка (Assets в Android)
const destDir = path.join(
  __dirname,
  '..',
  'app',
  'src',
  'main',
  'assets',
  'editor',
  'js'
)

// Створюємо директорію, якщо нема
if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true })
}

// Копіювання файлів
const copy = (src, dest, label) => {
  fs.copyFileSync(src, dest)
  console.log(`✅ Copied ${label}: ${dest}`)
}

// Мінімізований бандл
copy(
  path.join(distDir, 'editor-bundle.min.js'),
  path.join(destDir, 'editor-bundle.min.js'),
  'bundle'
)

// Кастомні налаштування
copy(
  path.join(srcDir, 'custom.js'),
  path.join(destDir, 'custom.min.js'),
  'custom'
)

// Локалізації
copy(
  path.join(srcDir, 'locales.js'),
  path.join(destDir, 'locales.js'),
  'locales'
)

// --- Custom Attaches from src ---
const attachesSrc = path.join(srcDir, 'attaches.umd.js')
const attachesDest = path.join(destDir, 'attaches.umd.js')

if (fs.existsSync(attachesSrc)) {
  copy(attachesSrc, attachesDest, 'attaches')
} else {
  console.log(`⚠️ Attaches skipped (file not found): ${attachesSrc}`)
}

// Видаляємо dist
fs.rmSync(distDir, { recursive: true, force: true })
console.log(`🗑️ Removed dist folder: ${distDir}`)

console.log('🎉 Build process completed!')
