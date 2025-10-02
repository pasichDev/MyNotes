const fs = require('fs')
const path = require('path')

const src = path.join(__dirname, 'dist', 'editor-bundle.js')
const destDir = path.join(__dirname, '..', 'editor', 'js')

if (!fs.existsSync(destDir)) fs.mkdirSync(destDir, { recursive: true })

const dest = path.join(destDir, 'editor-bundle.js')
fs.copyFileSync(src, dest)
console.log('Copied:', dest)
