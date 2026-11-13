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

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

/** A loaded page whose editor counts how often the whole document is saved. */
async function countingPage () {
  const page = openPage()
  const { editor, state } = fakeEditor([{ id: 'a', type: 'paragraph', data: { text: 'one' } }])
  let saves = 0
  const save = editor.save
  editor.save = () => { saves++; return save() }
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: 'T', valueJson: state.blocks })
  await settle()
  return { page, state, saves: () => saves }
}

/** Types {@code text} into the block, a character at a time, as beforeinput reports it. */
function type (page, state, text) {
  for (const ch of text) {
    page.fire('document', 'beforeinput', { inputType: 'insertText', data: ch })
    state.blocks[0].data = { text: state.blocks[0].data.text + ch }
  }
}

test('a finished word becomes its own step after a short pause, not on the spot', async () => {
  const { page, state, saves } = await countingPage()
  const before = saves()

  type(page, state, ' two ')
  await settle()
  assert.equal(saves() - before, 0, 'the space does not save the whole note at once')

  await wait(350)
  assert.equal(saves() - before, 1)
  assert.equal(page.window.__internals.history.undo.length, 1)

  page.window.historyUndo()
  await settle()
  assert.equal(state.blocks[0].data.text, 'one')
})

test("Editor.js's late report of the typing does not push the word's step back", async () => {
  const { page, state } = await countingPage()

  type(page, state, ' two ')
  await wait(150)
  page.window.historyEditorChanged({ type: 'block-changed' })
  await wait(250)

  assert.equal(page.window.__internals.history.undo.length, 1, 'closed 300 ms after the space')
})

test('a line break closes its step the same way', async () => {
  const { page, state, saves } = await countingPage()
  type(page, state, ' two')
  const before = saves()

  page.fire('document', 'beforeinput', { inputType: 'insertParagraph' })
  await settle()
  assert.equal(saves() - before, 0)

  await wait(350)
  assert.equal(page.window.__internals.history.undo.length, 1)
})
