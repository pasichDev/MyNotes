import resolve from '@rollup/plugin-node-resolve'
import commonjs from '@rollup/plugin-commonjs'
import terser from '@rollup/plugin-terser'
import css from 'rollup-plugin-css-only'

const isProd = true

const banner = `/*!
 * My Notes — Editor Runtime Build
 * (c) 2025 pasichDev. Editor.js — MIT
 */`

export default [
  // ----------------------------------------------------
  // 1) Editor.js core + plugins
  // ----------------------------------------------------
  // ----------------------------------------------
  // BUNDLE: EditorJS core + plugins
  // ----------------------------------------------
  {
    input: 'src/index.js',
    output: {
      file: 'dist/editor-bundle.min.js',
      format: 'iife',
      name: 'EditorBundle',
      sourcemap: false
    },
    plugins: [resolve(), commonjs(), terser()]
  },

  // ----------------------------------------------------
  // 2) Initialization module (initEditor)
  // ----------------------------------------------------
  {
    input: 'src/editor-init.js',
    output: {
      file: 'dist/editor-init.min.js',
      format: 'iife',
      name: 'EditorInitModule',
      sourcemap: !isProd,
      banner
    },
    plugins: [
      resolve(),
      commonjs(),
      terser({
        keep_fnames: true,
        keep_classnames: true,
        mangle: false
      })
    ]
  },

  // ----------------------------------------------------
  // 3) Android bridge runtime (save/load/delete/etc.)
  // ----------------------------------------------------
  {
    input: 'src/custom/runtime.js',
    treeshake: false,
    output: {
      file: 'dist/runtime.min.js',
      format: 'iife',
      sourcemap: !isProd,
      banner
    },
    plugins: [
      resolve(),
      commonjs(),
      ...(isProd
        ? [
            terser({
              mangle: {
                keep_fnames: true,
                reserved: [
                  'attachEditorInstance',
                  'deleteAttachmentBlockFromAndroid',
                  'toggleReadModeFromAndroid',
                  'saveContent',
                  'uploadAttachment',
                  'loadNote',
                  'setThemeColors'
                ]
              }
            })
          ]
        : [])
    ]
  }
]
