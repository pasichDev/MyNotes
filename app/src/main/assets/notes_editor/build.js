const fs = require('fs')
const path = require('path')
і
const distDir = path.join(__dirname, 'dist')

if (!fs.existsSync(distDir)) fs.mkdirSync(distDir)

const tools = [
  {
    name: 'editorjs',
    src: 'node_modules/@editorjs/editorjs/dist/editorjs.umd.js'
  },
  { name: 'header', src: 'node_modules/@editorjs/header/dist/header.js' },
  { name: 'list', src: 'node_modules/@editorjs/list/dist/list.js' },
  {
    name: 'paragraph',
    src: 'node_modules/@editorjs/paragraph/dist/paragraph.js'
  },
  {
    name: 'delimiter',
    src: 'node_modules/@editorjs/delimiter/dist/delimiter.js'
  },
  { name: 'marker', src: 'node_modules/@editorjs/marker/dist/marker.js' },
  {
    name: 'inline-code',
    src: 'node_modules/@editorjs/inline-code/dist/inline-code.js'
  }
]

if (!fs.existsSync(path.join(distDir, 'js')))
  fs.mkdirSync(path.join(distDir, 'js'))

tools.forEach(tool => {
  const dest = path.join(distDir, 'js', tool.name + '.js')
  fs.copyFileSync(tool.src, dest)
})

console.log('Editor.js site built in dist/')
