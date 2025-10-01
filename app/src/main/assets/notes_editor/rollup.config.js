import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';

export default {
  input: 'index.js',
  output: {
    file: 'dist/editor-bundle.js',
    format: 'umd',
    name: 'EditorBundle'
  },
  plugins: [
    resolve(),
    commonjs()
  ]
};
