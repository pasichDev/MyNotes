const fs = require('fs')
const path = require('path')

// Папка з бандлом
const distDir = path.join(__dirname, 'dist')
const srcDir = path.join(__dirname, 'src')

// Кінцева папка (Assets в Android проекті)
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

// Мінімізований бандл
const bundleSrc = path.join(distDir, 'editor-bundle.min.js')
const bundleDest = path.join(destDir, 'editor-bundle.min.js')
fs.copyFileSync(bundleSrc, bundleDest)
console.log(`✅ Copied bundle: ${bundleDest}`)

// Мінімізований бандл кастомних налаштувань
const customSrc = path.join(srcDir, 'custom.js')
const customDest = path.join(destDir, 'custom.min.js')
fs.copyFileSync(customSrc, customDest)
console.log(`✅ Copied custom: ${customDest}`)

// Локалізації
const localesSrc = path.join(srcDir, 'locales.js')
const localesDest = path.join(destDir, 'locales.js')
fs.copyFileSync(localesSrc, localesDest)
console.log(`✅ Copied locales: ${localesDest}`)

// Видаляємо папку dist після копіювання
fs.rmSync(distDir, { recursive: true, force: true })
console.log(`🗑️ Removed dist folder: ${distDir}`)
console.log('🎉 Build process completed!')
