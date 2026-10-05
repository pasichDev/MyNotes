import { test } from 'node:test'
import assert from 'node:assert/strict'
import { openPage, fakeEditor, settle } from './page.mjs'

async function shownNote (search, note = {}) {
  const page = openPage(search)
  const blocks = [0, 1, 2, 3, 4, 5].map(i => ({ id: 'b' + i, type: 'paragraph', data: {} }))
  const { editor } = fakeEditor(blocks, { readOnly: search.includes('readonly=1') })
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: 'T', valueJson: blocks, ...note })
  await settle()
  page.runFrames()
  return page
}

test('the position can be read right away once the note is shown, also in reading mode', async () => {
  const page = openPage('?readonly=1')
  assert.equal(page.window.currentViewStateJson(), null)

  const shown = await shownNote('?readonly=1')
  shown.window.scrollY = 250
  const state = JSON.parse(shown.window.currentViewStateJson())
  assert.equal(state.scrollTop, 250)
})

test('a long scroll in reading mode is reported while it goes on', async () => {
  const page = await shownNote('?readonly=1')
  const before = page.callsTo('onViewState').length
  // A fling: scroll events for a while, the note is left before they settle.
  for (let i = 0; i < 4; i++) {
    page.window.scrollY += 100
    page.fire('window', 'scroll')
    await new Promise(resolve => setTimeout(resolve, 90))
  }
  assert.ok(page.callsTo('onViewState').length > before)
})

test('the position is reported as the page is hidden', async () => {
  const page = await shownNote('?readonly=1')
  const before = page.callsTo('onViewState').length
  page.window.scrollY = 120
  page.document.hidden = true
  page.fire('document', 'visibilitychange')
  assert.equal(page.callsTo('onViewState').length, before + 1)
})

test('a note opened at its start shows the title, even if placing the caret scrolled', async () => {
  const page = openPage()
  const blocks = [{ id: 'a', type: 'paragraph', data: {} }]
  const { editor } = fakeEditor(blocks)
  editor.caret.setToFirstBlock = () => { page.window.scrollY = 80 }
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: 'T', valueJson: blocks, focusStart: true })
  await settle()

  assert.equal(page.window.scrollY, 0)
})
