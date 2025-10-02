import resolve from '@rollup/plugin-node-resolve'
import commonjs from '@rollup/plugin-commonjs'
import terser from '@rollup/plugin-terser'

const banner = `/*!
 * This project uses Editor.js (MIT License)
 * https://github.com/codex-team/editor.js
 */`

export default [
  // 1️⃣ Editor.js + плагіни
  {
    input: 'index.js',
    output: {
      file: 'dist/editor-bundle.min.js',
      format: 'umd',
      name: 'EditorBundle',
      sourcemap: true,
      banner
    },
    plugins: [
      resolve(),
      commonjs(),
      terser({
        mangle: { keep_fnames: true },
        format: { comments: false }
      })
    ]
  }
]
