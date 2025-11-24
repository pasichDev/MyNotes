import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

// emulate __dirname in ES modules
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const distDir = path.join(__dirname, 'dist')
const srcDir = path.join(__dirname, 'src')
const toolsDir = path.join(__dirname, 'src/tools')
const customDir = path.join(__dirname, 'src/custom')
const htmlFile = path.join(srcDir, 'editor.html')

// Android destination folder
const destRoot = path.join(
  __dirname,
  '..',
  'app',
  'src',
  'main',
  'assets',
  'editor'
)

// paths inside assets/editor/
const destJs = path.join(destRoot, 'js')
const destCss = path.join(destRoot, 'css')

// Ensure dirs exist
for (const p of [destRoot, destJs, destCss]) {
  if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true })
}

// --------- COPY HELPERS ---------
function copy (src, dest, label) {
  fs.copyFileSync(src, dest)
  console.log(`✅ Copied ${label} → ${dest}`)
}

// --------- COPY HTML ---------
copy(htmlFile, path.join(destRoot, 'editor.html'), 'editor HTML')

// --------- COPY JS bundles ---------
copy(
  path.join(distDir, 'editor-bundle.min.js'),
  path.join(destJs, 'editor-bundle.min.js'),
  'Editor.js bundle'
)

copy(
  path.join(distDir, 'editor-init.min.js'),
  path.join(destJs, 'editor-init.min.js'),
  'Init'
)

copy(
  path.join(distDir, 'runtime.min.js'),
  path.join(destJs, 'runtime.min.js'),
  'Runtime'
)

copy(
  path.join(toolsDir, 'attaches.min.js'),
  path.join(destJs, 'attaches.min.js'),
  'Attaches'
)

// --------- COPY locales.js ---------
copy(
  path.join(customDir, 'locales.js'),
  path.join(destJs, 'locales.js'),
  'Locales'
)

// --------- COPY CSS built by plugin ---------
copy(
  path.join(srcDir, 'editor.css'),
  path.join(destCss, 'editor.css'),
  'Editor CSS'
)

console.log('🎉 Build + copy completed successfully!')
