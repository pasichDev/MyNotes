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

async function editedPage () {
  const page = openPage()
  const { editor, state } = fakeEditor([{ id: 'a', type: 'paragraph', data: { text: 'one' } }])
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: 'T', valueJson: state.blocks })
  await settle()
  state.blocks[0].data = { text: 'one two' }
  page.fire('document', 'beforeinput', { inputType: 'insertText', data: ' ' })
  await settle()
  return { page, editor, state }
}

test('a recreated page takes over the undo history of the page it replaces', async () => {
  const before = await editedPage()
  await before.page.window.finishPage(true)

  const names = before.page.calls.map(c => c[0])
  assert.ok(names.indexOf('onHistoryExported') < names.indexOf('onPageFinished'))
  const [exported] = before.page.callsTo('onHistoryExported').at(-1)

  const after = openPage()
  const { editor, state } = fakeEditor(before.state.blocks)
  after.window.attachEditorInstance(editor)
  after.window.loadNote({ title: 'T', valueJson: state.blocks, history: JSON.parse(exported) })
  await settle()
  assert.deepEqual(after.callsTo('onHistoryChanged').at(-1), [true, false])

  after.window.historyUndo()
  await settle()
  assert.equal(state.blocks[0].data.text, 'one')
})

test('a handed-over history recorded on another document is dropped', async () => {
  const before = await editedPage()
  await before.page.window.finishPage(true)
  const [exported] = before.page.callsTo('onHistoryExported').at(-1)

  const after = openPage()
  const { editor, state } = fakeEditor([{ id: 'a', type: 'paragraph', data: { text: 'other' } }])
  after.window.attachEditorInstance(editor)
  after.window.loadNote({ title: 'T', valueJson: state.blocks, history: JSON.parse(exported) })
  await settle()
  assert.deepEqual(after.callsTo('onHistoryChanged').at(-1), [false, false])
})

test('finishing the page always answers, even when the document cannot be saved', async () => {
  const page = openPage('?readonly=1')
  const { editor, state } = fakeEditor([{ id: 'a', type: 'paragraph', data: {} }], {
    readOnly: true
  })
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: 'T', valueJson: state.blocks })
  await settle()

  await page.window.finishPage(true)
  assert.equal(page.callsTo('onPageFinished').length, 1)
})

test('the last edit is sent before the page reports it is finished', async () => {
  const { page, state } = await editedPage()
  state.blocks[0].data = { text: 'one two three' }
  await page.window.finishPage(false)

  const names = page.calls.map(c => c[0])
  assert.ok(names.lastIndexOf('onContentFlushed') < names.indexOf('onPageFinished'))
  assert.match(page.callsTo('onContentFlushed').at(-1)[0], /three/)
})
