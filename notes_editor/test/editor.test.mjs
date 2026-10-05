import { test } from 'node:test'
import assert from 'node:assert/strict'
import { openPage, fakeEditor, settle } from './page.mjs'

test('the title hint is the localized one, also after a note is loaded', async () => {
  const page = openPage()
  page.window.__titlePlaceholder = 'Назва'
  const { editor } = fakeEditor([])
  page.window.attachEditorInstance(editor)
  page.window.loadNote({ title: '', valueJson: [] })
  await settle()

  assert.equal(page.title.getAttribute('data-placeholder'), 'Назва')
})

test('opening the keyboard gives the editor a caret when it has none', async () => {
  const page = openPage()
  const { editor } = fakeEditor([{ id: 'a', type: 'paragraph', data: { text: 'x' } }])
  let placed = 0
  editor.caret.setToFirstBlock = () => {
    placed++
    page.window.scrollY = 300
  }
  page.window.attachEditorInstance(editor)

  page.window.focusCaretFromAndroid()
  assert.equal(placed, 1)
  // Placing it does not move the page.
  assert.equal(page.window.scrollY, 0)

  page.document.activeElement = { isContentEditable: true, focus () {} }
  page.window.focusCaretFromAndroid()
  assert.equal(placed, 1)
})

test('in reading mode no caret is placed', async () => {
  const page = openPage('?readonly=1')
  const { editor } = fakeEditor([], { readOnly: true })
  let placed = 0
  editor.caret.setToFirstBlock = () => placed++
  page.window.attachEditorInstance(editor)

  page.window.focusCaretFromAndroid()
  assert.equal(placed, 0)
})
