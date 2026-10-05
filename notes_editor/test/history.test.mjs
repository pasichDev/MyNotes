import { test } from 'node:test'
import assert from 'node:assert/strict'
import { openPage, fakeEditor, settle } from './page.mjs'

test('a note opened in reading mode records edits once editing starts', async () => {
  const page = openPage('?readonly=1')
  const { editor, state } = fakeEditor([{ id: 'a', type: 'paragraph', data: { text: 'x' } }], {
    readOnly: true
  })
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: 'T', valueJson: state.blocks })
  await settle()

  await page.window.setReadMode(false, null)
  await settle()
  state.blocks[0].data = { text: 'xy' }
  page.fire('document', 'beforeinput', { inputType: 'insertText', data: 'y' })
  await settle()

  assert.deepEqual(page.callsTo('onHistoryChanged').at(-1), [true, false])
})
