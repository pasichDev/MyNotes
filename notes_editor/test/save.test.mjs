import { test } from 'node:test'
import assert from 'node:assert/strict'
import { openPage, fakeEditor, settle } from './page.mjs'

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

async function loadedPage () {
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

test('a burst of changes sends the document to Android once, after typing pauses', async () => {
  const { page, state, saves } = await loadedPage()
  const before = saves()

  for (let i = 0; i < 10; i++) {
    state.blocks[0].data = { text: 'one' + 'x'.repeat(i + 1) }
    page.window.saveContent()
  }
  await settle()
  assert.equal(page.callsTo('onContentChanged').length, 0, 'nothing is sent while typing')

  await wait(800)
  const sent = page.callsTo('onContentChanged')
  assert.equal(sent.length, 1)
  assert.equal(JSON.parse(sent[0][0])[0].data.text, 'one' + 'x'.repeat(10))
  assert.equal(saves() - before, 1, 'the document is serialized once for the whole burst')
})

test('a flush sends the latest document at once and the pending send is dropped', async () => {
  const { page, state } = await loadedPage()

  state.blocks[0].data = { text: 'one two' }
  page.window.saveContent()
  await page.window.flushContent()

  const flushed = page.callsTo('onContentFlushed')
  assert.equal(flushed.length, 1)
  assert.equal(JSON.parse(flushed[0][0])[0].data.text, 'one two')

  await wait(800)
  assert.equal(page.callsTo('onContentChanged').length, 0, 'the same document is not sent twice')
})

test('steady typing still reaches Android every few seconds', async () => {
  const { page, state } = await loadedPage()

  const started = Date.now()
  let i = 0
  while (Date.now() - started < 3400) {
    state.blocks[0].data = { text: 'one ' + i++ }
    page.window.saveContent()
    await wait(100)
  }

  assert.ok(page.callsTo('onContentChanged').length >= 1, 'a change was sent before the pause')
})

test('history states keep the document once, as JSON', async () => {
  const { page, state } = await loadedPage()

  state.blocks[0].data = { text: 'one two' }
  page.fire('document', 'beforeinput', { inputType: 'insertText', data: ' ' })
  page.window.historyEditorChanged({ type: 'block-changed' })
  await wait(800)

  const hist = page.window.__internals.history
  assert.equal(hist.undo.length, 1)
  for (const entry of [...hist.undo, hist.current]) {
    assert.equal(entry.doc, undefined)
    assert.equal(typeof entry.json, 'string')
  }

  page.window.historyUndo()
  await settle()
  assert.equal(state.blocks[0].data.text, 'one')
})

/** What Android does when it turns the blocks back into plain text: texts joined with newlines. */
const toPlain = blocks => blocks.map(b => b.data.text.replace(/<br>/g, '\n')).join('\n')

test('a plain-text note opens with a block per line and gives back the same text', () => {
  const page = openPage()
  const lines = []
  for (let i = 0; i < 1300; i++) lines.push(i % 7 === 3 ? '' : `paragraph ${i}: the quick brown fox`)
  lines[10] = '   '
  const text = lines.join('\n')

  const blocks = page.window.plainTextBlocks(text)

  assert.equal(toPlain(blocks), text)
  for (const block of blocks) {
    assert.equal(block.type, 'paragraph')
    const shown = block.data.text.split('<br>')
    assert.notEqual(shown.at(-1).trim(), '', 'every block ends with a line of text, so none is empty')
    for (const blank of shown.slice(0, -1)) assert.equal(blank.trim(), '', 'only blank lines lead a block')
  }
  assert.equal(blocks.length, lines.filter(l => l.trim() !== '').length)
})

test('blank lines become space before the next line', () => {
  const page = openPage()
  const blocks = page.window.plainTextBlocks('one\n\ntwo\nthree')
  assert.deepEqual(JSON.parse(JSON.stringify(blocks.map(b => b.data.text))), ['one', '<br>two', 'three'])
})

test('blank lines around the note are left out, as Android trims them', () => {
  const page = openPage()
  const blocks = page.window.plainTextBlocks('\n\none\ntwo\n\n')
  assert.deepEqual(JSON.parse(JSON.stringify(blocks.map(b => b.data.text))), ['one', 'two'])
})
