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

test('a change is sent to Android at once', async () => {
  const { page, state } = await loadedPage()

  state.blocks[0].data = { text: 'one two' }
  page.window.saveContent()
  await settle()

  const sent = page.callsTo('onContentChanged')
  assert.equal(sent.length, 1)
  assert.equal(JSON.parse(sent[0][0])[0].data.text, 'one two')
})

test('a flush sends only what Android does not have yet', async () => {
  const { page, state } = await loadedPage()

  state.blocks[0].data = { text: 'one two' }
  page.window.saveContent()
  await settle()
  await page.window.flushContent()
  assert.equal(page.callsTo('onContentFlushed').length, 0, 'the same document is not sent twice')

  state.blocks[0].data = { text: 'one two three' }
  await page.window.flushContent()
  const flushed = page.callsTo('onContentFlushed')
  assert.equal(flushed.length, 1)
  assert.equal(JSON.parse(flushed[0][0])[0].data.text, 'one two three')
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
  for (let i = 0; i < 201; i++) lines.push(i % 7 === 3 ? '' : `paragraph ${i}: the quick brown fox`)
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

test('a very long plain-text note opens in blocks of about a kilobyte that give back the same text', () => {
  const page = openPage()
  const lines = []
  for (let i = 0; i < 1300; i++) lines.push(i % 7 === 3 ? '' : `paragraph ${i}: the quick brown fox`)
  const text = lines.join('\n')

  const blocks = page.window.plainTextBlocks(text)

  assert.equal(toPlain(blocks), text)
  assert.ok(blocks.length > 20 && blocks.length < 120, `${blocks.length} blocks`)
  for (const block of blocks) {
    assert.ok(block.data.text.length < 2500, 'each block stays small')
    assert.ok(!block.data.text.startsWith('<br>') && !block.data.text.endsWith('<br>'),
      'no block starts or ends with an empty line, which the editor could drop')
  }
})

test('markup in a plain-text note is shown as text, never run as HTML', () => {
  const page = openPage()
  const text = '<img src=x onerror="Android.x()">\n\nList<String> a && b > c'

  const blocks = page.window.plainTextBlocks(text)

  assert.deepEqual(JSON.parse(JSON.stringify(blocks.map(b => b.data.text))), [
    '&lt;img src=x onerror="Android.x()"&gt;',
    '<br>List&lt;String&gt; a &amp;&amp; b &gt; c'
  ])
  const decode = html =>
    html.replace(/<br>/g, '\n').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  assert.equal(blocks.map(b => decode(b.data.text)).join('\n'), text)
})

test('markup is escaped in a very long plain-text note too', () => {
  const page = openPage()
  const lines = []
  for (let i = 0; i < 300; i++) lines.push(`<b>line ${i}</b> & more`)

  const blocks = page.window.plainTextBlocks(lines.join('\n'))

  assert.ok(blocks.length > 1)
  for (const block of blocks) {
    assert.ok(!/<(?!br>)/.test(block.data.text), 'no tag other than <br> is left')
  }
})
