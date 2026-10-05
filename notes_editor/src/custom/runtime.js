/**
 * Runtime bridge for Android ↔ Editor.js
 */

let editor = null
let isReadMode = false
let __lastSavedJson = null

/**
 * Safe Android call with return value.
 */
function safeAndroidCall (func, ...args) {
  if (window.Android && typeof window.Android[func] === 'function') {
    try {
      return window.Android[func](...args)
    } catch (e) {
      console.error(`[AndroidBridge] ${func} failed:`, e)
    }
  }
  return null
}

/**
 * Called from editor-init after EditorJS is created.
 */
window.attachEditorInstance = function (instance) {
  editor = instance
  window.__EDITOR_READY = true
  // The editor may start read-only (Android's "open in reading mode"); the title follows it.
  isReadMode = !!instance.readOnly?.isEnabled
  applyTitleEditable()
}

/**
 * Options Android passes in the page URL: whether a double tap in reading mode starts editing.
 */
const __pageParams = new URLSearchParams(window.location.search)
const __doubleTapToEdit = __pageParams.get('dbltap') === '1'

/**
 * Save current blocks and send to Android.
 */

function saveContent () {
  if (!editor) return

  editor
    .save()
    .then(output => {
      const blocks = output.blocks
      const jsonStr = JSON.stringify(blocks)

      // Якщо нічого не змінилось — ідемо спати
      if (jsonStr === __lastSavedJson) {
        return
      }

      // Оновлюємо останнє збереження
      __lastSavedJson = jsonStr

      // Шлемо в Android
      safeAndroidCall('onContentChanged', jsonStr)
    })
    .catch(err => console.error('[Editor] Save failed:', err))
}

/**
 * Send the document right now, without waiting for Editor.js to batch its changes.
 * Android asks for this when the screen stops, so the last keystrokes are saved too.
 */
function flushContent () {
  if (!editor) return Promise.resolve()

  return editor
    .save()
    .then(output => {
      const jsonStr = JSON.stringify(output.blocks)
      if (jsonStr === __lastSavedJson) return
      __lastSavedJson = jsonStr
      safeAndroidCall('onContentFlushed', jsonStr)
    })
    .catch(err => console.error('[Editor] Flush failed:', err))
}

/**
 * The page is about to be destroyed. Hands the undo history to Android first when asked (the
 * screen is being recreated, for example rotated), then sends the document, and always ends with
 * onPageFinished: Android keeps the WebView alive until then, so the last edit reaches it.
 */
function finishPage (keepHistory) {
  const history = keepHistory ? exportHistory() : Promise.resolve()
  return history
    .catch(err => console.error('[History] export failed:', err))
    .then(() => flushContent())
    .finally(() => safeAndroidCall('onPageFinished'))
}

/**
 * Reading position: which block is at the top of the viewport once scrolling settles.
 * Android keeps it so a rotation or recreation reopens the note where it was being read.
 */
let __anchorTimer = null

function reportViewportAnchor () {
  if (!editor) return
  if (window.scrollY < 1) {
    safeAndroidCall('onViewportAnchor', -1, 0)
    return
  }
  const count = editor.blocks.getBlocksCount()
  for (let i = 0; i < count; i++) {
    const block = editor.blocks.getBlockByIndex(i)
    const rect = block?.holder?.getBoundingClientRect()
    if (rect && rect.bottom > 0) {
      safeAndroidCall('onViewportAnchor', i, Math.round(rect.top))
      return
    }
  }
}

window.addEventListener(
  'scroll',
  () => {
    clearTimeout(__anchorTimer)
    __anchorTimer = setTimeout(reportViewportAnchor, 200)
  },
  { passive: true }
)

/**
 * Where the note is being read or edited, for Android to keep per note on this device: the block
 * at the top of the viewport and the block, input and character offset of the caret. Block ids
 * survive edits and saves, so the position can be found again after the note changed.
 * Reported once scrolling or the caret settles, and only after a reopened note's saved position
 * has been applied, so the start of the note never overwrites it.
 */
let __viewStateTimer = null
let __viewStateReady = false

function blockForHolder (holder) {
  const count = editor.blocks.getBlocksCount()
  for (let i = 0; i < count; i++) {
    const block = editor.blocks.getBlockByIndex(i)
    if (block?.holder === holder) return block
  }
  return null
}

function editableInputs (holder) {
  return [...holder.querySelectorAll('[contenteditable="true"]')]
}

function caretPosition () {
  if (!editor || isReadMode) return null
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || !selection.focusNode) return null
  const node = selection.focusNode
  const element = node.nodeType === 1 ? node : node.parentElement
  const input = element?.closest('[contenteditable="true"]')
  const holder = input?.closest('.ce-block')
  if (!holder) return null
  const block = blockForHolder(holder)
  if (!block) return null
  const range = document.createRange()
  range.selectNodeContents(input)
  range.setEnd(node, selection.focusOffset)
  return {
    id: block.id,
    input: Math.max(0, editableInputs(holder).indexOf(input)),
    offset: range.toString().length
  }
}

function currentViewState () {
  const scrollable = document.documentElement.scrollHeight - window.innerHeight
  const state = {
    scrollTop: Math.round(window.scrollY),
    ratio: scrollable > 0 ? Math.min(1, window.scrollY / scrollable) : 0,
    topId: null,
    topIndex: -1,
    topOffset: 0,
    caret: caretPosition()
  }
  if (window.scrollY >= 1) {
    const count = editor.blocks.getBlocksCount()
    for (let i = 0; i < count; i++) {
      const block = editor.blocks.getBlockByIndex(i)
      const rect = block?.holder?.getBoundingClientRect()
      if (rect && rect.bottom > 0) {
        state.topId = block.id
        state.topIndex = i
        state.topOffset = Math.round(rect.top)
        break
      }
    }
  }
  return state
}

function reportViewState () {
  if (!editor || !__viewStateReady) return
  safeAndroidCall('onViewState', JSON.stringify(currentViewState()))
}

function scheduleViewStateReport () {
  clearTimeout(__viewStateTimer)
  __viewStateTimer = setTimeout(reportViewState, 200)
}

window.addEventListener('scroll', scheduleViewStateReport, { passive: true })
document.addEventListener('selectionchange', scheduleViewStateReport)

/**
 * Puts the caret {@code offset} characters into {@code input}, at its end when the text is now
 * shorter, so a saved offset can never point outside the text.
 */
function placeCaret (input, offset) {
  const walker = document.createTreeWalker(input, NodeFilter.SHOW_TEXT)
  let remaining = Math.max(0, offset || 0)
  let node = walker.nextNode()
  let last = null
  while (node) {
    last = node
    if (remaining <= node.length) break
    remaining -= node.length
    node = walker.nextNode()
  }
  const range = document.createRange()
  if (node) range.setStart(node, remaining)
  else if (last) range.setStart(last, last.length)
  else range.setStart(input, 0)
  range.collapse(true)
  const selection = window.getSelection()
  selection.removeAllRanges()
  selection.addRange(range)
}

/**
 * Applies a saved position Android resolved against the note's current blocks: the caret first
 * (placing it may scroll), then the block that was at the top of the viewport.
 */
function restoreViewState (state) {
  if (!editor || !state) return
  if (state.caretId && !isReadMode) {
    const block = editor.blocks.getById(state.caretId)
    if (block?.holder) {
      try {
        editor.caret.setToBlock(block, 'start')
      } catch (e) {
        console.error('[Editor] setToBlock failed:', e)
      }
      const inputs = editableInputs(block.holder)
      const input = inputs[Math.min(state.caretInput || 0, inputs.length - 1)]
      if (input) {
        input.focus({ preventScroll: true })
        placeCaret(input, state.caretOffset)
      }
    }
  }
  let target = state.topId ? editor.blocks.getById(state.topId) : null
  if (!target && state.topIndex >= 0) {
    const count = editor.blocks.getBlocksCount()
    if (count > 0) target = editor.blocks.getBlockByIndex(Math.min(state.topIndex, count - 1))
  }
  if (!target?.holder) return
  requestAnimationFrame(() => {
    const top = target.holder.getBoundingClientRect().top
    window.scrollTo(0, window.scrollY + top - (state.topOffset || 0))
  })
}

function restoreViewportAnchor (anchor) {
  if (!editor || !anchor || anchor.index < 0) return
  const count = editor.blocks.getBlocksCount()
  if (count === 0) return
  const block = editor.blocks.getBlockByIndex(Math.min(anchor.index, count - 1))
  if (!block?.holder) return
  requestAnimationFrame(() => {
    const top = block.holder.getBoundingClientRect().top
    window.scrollTo(0, window.scrollY + top - (anchor.offset || 0))
  })
}

/**
 * Keep the caret visible when the page height changes (the keyboard opens or closes).
 * Only scrolls when the caret would otherwise be hidden, so nothing moves needlessly.
 */
function keepCaretVisible () {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return
  const node = selection.focusNode
  const element = node && (node.nodeType === 1 ? node : node.parentElement)
  if (!element || !element.isContentEditable) return

  const range = selection.getRangeAt(0).cloneRange()
  range.collapse(false)
  let rect = range.getClientRects()[0]
  if (!rect || (rect.top === 0 && rect.bottom === 0)) {
    rect = element.getBoundingClientRect()
  }

  const margin = 24
  const viewport = window.innerHeight
  if (rect.bottom > viewport - margin) {
    window.scrollBy(0, rect.bottom - viewport + margin)
  } else if (rect.top < margin) {
    window.scrollBy(0, rect.top - margin)
  }
}

window.addEventListener('resize', () => requestAnimationFrame(keepCaretVisible))

/**
 * Undo and redo.
 *
 * The history is a list of whole document states (title and blocks), each with the caret that
 * was in it. Changes are batched: a state is kept once typing pauses, a word or line is finished,
 * a block is added, removed or moved, or a batch has run for a few seconds. Undo and redo apply a
 * state with the smallest edit that reaches it: blocks whose content differs are updated in place
 * and only a change in the list of blocks renders the document again, so the page does not jump.
 * Each applied state is an ordinary change for Android, so it is autosaved like typing.
 *
 * The history belongs to the note on screen: loading a note starts it again, and it is never kept
 * across notes. A page recreated with the screen (a rotation) takes over the history of the page
 * it replaces, through Android, when it shows the same document.
 */
const HISTORY_LIMIT = 100
const HISTORY_IDLE_MS = 700
const HISTORY_MAX_BATCH_MS = 3000
// Editor.js reports a change up to 400 ms after it happened; a report that late about a state
// this history applied itself is not a new change.
const HISTORY_SETTLE_MS = 600

const __hist = {
  undo: [],
  redo: [],
  current: null,
  pending: false,
  // Whether the open batch has typed anything other than whitespace.
  batchHasText: false,
  caretBefore: null,
  lastCaret: null,
  batchStart: 0,
  timer: null,
  applying: false,
  settleUntil: 0,
  epoch: 0,
  changeSeq: 0,
  queue: Promise.resolve(),
  reported: ''
}

function historyTitleElement () {
  return document.getElementById('noteTitleInput')
}

/** The caret as { title: true, offset } in the title, or { id, input, offset } in a block. */
function historyCaret () {
  if (!editor || isReadMode) return null
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || !selection.focusNode) return null
  const title = historyTitleElement()
  if (title && title.contains(selection.focusNode)) {
    const range = document.createRange()
    range.selectNodeContents(title)
    range.setEnd(selection.focusNode, selection.focusOffset)
    return { title: true, offset: range.toString().length }
  }
  return caretPosition()
}

function readDocument () {
  return editor.save().then(output => ({
    title: historyTitleElement()?.innerText || '',
    blocks: output.blocks
  }))
}

function historyEntry (doc, caret) {
  return { doc, json: JSON.stringify(doc), caret }
}

function reportHistory () {
  const canUndo = __hist.undo.length > 0 || __hist.pending
  const canRedo = __hist.redo.length > 0 && !__hist.pending
  const key = `${canUndo}|${canRedo}`
  if (key === __hist.reported) return
  __hist.reported = key
  safeAndroidCall('onHistoryChanged', canUndo, canRedo)
}

/** Attachment URLs a document refers to. */
function attachmentUrls (doc) {
  const urls = new Set()
  for (const block of doc?.blocks || []) {
    const data = block?.data
    if (data?.file?.url) urls.add(data.file.url)
    if (Array.isArray(data?.files)) {
      for (const file of data.files) if (file?.url) urls.add(file.url)
    }
  }
  return urls
}

/**
 * Files a step takes out of the note may come back with an undo. Android removes files no saved
 * note refers to, so it is told to keep these.
 */
function keepAttachmentsForUndo (fromDoc, toDoc) {
  const kept = attachmentUrls(toDoc)
  const leaving = [...attachmentUrls(fromDoc)].filter(url => !kept.has(url))
  if (leaving.length > 0) safeAndroidCall('keepForUndo', JSON.stringify(leaving))
}

/** Forgets the history; nothing is recorded until resetHistory() reads the new document. */
function stopHistory () {
  __hist.epoch++
  clearTimeout(__hist.timer)
  __hist.timer = null
  __hist.undo = []
  __hist.redo = []
  __hist.current = null
  __hist.pending = false
  __hist.caretBefore = null
  __hist.lastCaret = null
  __hist.applying = false
  __hist.settleUntil = 0
  reportHistory()
}

/**
 * Largest history handed over to a recreated page, in characters of document JSON. The oldest
 * undo steps and the farthest redo steps are left out first.
 */
const HISTORY_EXPORT_MAX_CHARS = 1500000

// History handed over by the page this one replaces; used when the history starts on the same
// document, then dropped.
let __historyToImport = null

function exportedEntry (entry) {
  return { json: entry.json, caret: entry.caret || null }
}

function importedEntry (entry) {
  return historyEntry(JSON.parse(entry.json), entry.caret || null)
}

/** Sends the history to Android (onHistoryExported) to be given to the next page. */
function exportHistory () {
  return runHistoryAction(() =>
    (isReadMode ? Promise.resolve() : captureHistory(true)).then(() => {
      if (!__hist.current) return
      const undo = __hist.undo.map(exportedEntry)
      const redo = __hist.redo.map(exportedEntry)
      const size = list => list.reduce((sum, e) => sum + e.json.length, 0)
      let total = __hist.current.json.length + size(undo) + size(redo)
      while (total > HISTORY_EXPORT_MAX_CHARS && (undo.length || redo.length)) {
        // The oldest undo step is first; the farthest redo step is first too.
        const dropped = undo.length >= redo.length ? undo.shift() : redo.shift()
        total -= dropped.json.length
      }
      if (total > HISTORY_EXPORT_MAX_CHARS) return
      safeAndroidCall(
        'onHistoryExported',
        JSON.stringify({ current: exportedEntry(__hist.current), undo, redo })
      )
    })
  )
}

/** Takes over a handed-over history when it was recorded on the document now on screen. */
function importHistory () {
  const imported = __historyToImport
  __historyToImport = null
  if (!imported?.current || imported.current.json !== __hist.current?.json) return
  try {
    __hist.undo = (imported.undo || []).map(importedEntry).slice(-HISTORY_LIMIT)
    __hist.redo = (imported.redo || []).map(importedEntry).slice(-HISTORY_LIMIT)
    __hist.current.caret = imported.current.caret || null
  } catch (e) {
    console.error('[History] import failed:', e)
    __hist.undo = []
    __hist.redo = []
  }
}

/**
 * Starts the history again from the document now on screen. Editor.js refuses to save while it is
 * read-only, so a note shown in reading mode has no history until editing starts; setReadMode()
 * starts it then.
 *
 * @param settle ignore Editor.js's late change reports for a moment, for a document it has just
 *     rendered again.
 */
function resetHistory (settle) {
  stopHistory()
  if (!editor || isReadMode) return
  const epoch = __hist.epoch
  readDocument()
    .then(doc => {
      if (epoch !== __hist.epoch) return
      __hist.current = historyEntry(doc, null)
      if (settle) __hist.settleUntil = Date.now() + HISTORY_SETTLE_MS
      importHistory()
      reportHistory()
    })
    .catch(err => console.error('[History] reset failed:', err))
}

/**
 * Something in the note changed, or is about to. {@code immediate} closes the batch now.
 * {@code fromEditor} marks Editor.js's own late change report.
 */
function historyNoteChange (immediate, fromEditor) {
  if (!__hist.current || __hist.applying || isReadMode) return
  if (fromEditor && Date.now() < __hist.settleUntil) return
  __hist.changeSeq++
  if (!__hist.pending) {
    __hist.pending = true
    __hist.batchHasText = false
    __hist.batchStart = Date.now()
    if (!__hist.caretBefore) __hist.caretBefore = __hist.lastCaret
  }
  clearTimeout(__hist.timer)
  const overdue = Date.now() - __hist.batchStart >= HISTORY_MAX_BATCH_MS
  __hist.timer = setTimeout(captureHistory, immediate || overdue ? 0 : HISTORY_IDLE_MS)
  reportHistory()
}

/** Called before a key or an input changes the note: remembers where the caret was. */
function historyInputStarting () {
  if (!__hist.current || __hist.applying || isReadMode) return
  if (!__hist.pending && !__hist.caretBefore) __hist.caretBefore = historyCaret()
}

/**
 * Keeps the document as a new state if it differs from the last one. With {@code force} the
 * document is compared even when no change was reported.
 */
function captureHistory (force) {
  clearTimeout(__hist.timer)
  __hist.timer = null
  if (!editor || !__hist.current || (!__hist.pending && !force)) return Promise.resolve()
  const epoch = __hist.epoch
  const seq = __hist.changeSeq
  return readDocument().then(doc => {
    if (epoch !== __hist.epoch || !__hist.current) return
    const caretBefore = __hist.caretBefore
    const changedMeanwhile = seq !== __hist.changeSeq
    __hist.pending = changedMeanwhile
    __hist.caretBefore = null
    if (changedMeanwhile) __hist.batchStart = Date.now()
    else __hist.batchHasText = false
    const entry = historyEntry(doc, historyCaret())
    if (entry.json !== __hist.current.json) {
      keepAttachmentsForUndo(__hist.current.doc, doc)
      __hist.undo.push({ ...__hist.current, caret: caretBefore || __hist.current.caret })
      if (__hist.undo.length > HISTORY_LIMIT) __hist.undo.shift()
      __hist.redo = []
      __hist.current = entry
    }
    reportHistory()
  })
}

/** Whether two block lists hold the same blocks, by id and type, in the same order. */
function sameBlockList (a, b) {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i].id !== b[i].id || a[i].type !== b[i].type) return false
  }
  return true
}

/**
 * Whether the editor shows exactly the blocks of {@code blocks}. Saving leaves out blocks with
 * nothing in them (an empty paragraph), so the saved document and the screen can differ.
 */
function editorShows (blocks) {
  if (editor.blocks.getBlocksCount() !== blocks.length) return false
  for (let i = 0; i < blocks.length; i++) {
    if (editor.blocks.getBlockByIndex(i)?.id !== blocks[i].id) return false
  }
  return true
}

/** The block with this id, or null; unlike getById it does not warn about a missing one. */
function findBlock (id) {
  if (!id) return null
  const count = editor.blocks.getBlocksCount()
  for (let i = 0; i < count; i++) {
    const block = editor.blocks.getBlockByIndex(i)
    if (block?.id === id) return block
  }
  return null
}

function restoreHistoryCaret (caret, keep, fallbackId) {
  const title = historyTitleElement()
  if (caret?.title && title) {
    title.focus({ preventScroll: true })
    placeCaret(title, caret.offset)
  } else if (caret?.id && findBlock(caret.id)) {
    restoreViewState({
      caretId: caret.id,
      caretInput: caret.input,
      caretOffset: caret.offset,
      topId: findBlock(keep?.topId) ? keep.topId : null,
      topIndex: keep ? keep.topIndex : -1,
      topOffset: keep?.topOffset || 0
    })
  } else {
    const block = findBlock(fallbackId)
    if (block) {
      try {
        editor.caret.setToBlock(block, 'end')
      } catch (e) {
        console.error('[History] placing the caret failed:', e)
      }
    }
    if (keep) {
      restoreViewState({
        topId: findBlock(keep.topId) ? keep.topId : null,
        topIndex: keep.topIndex,
        topOffset: keep.topOffset
      })
    }
  }
  // restoreViewState scrolls on the next frame; the caret is brought into view after that.
  requestAnimationFrame(() => requestAnimationFrame(keepCaretVisible))
}

/** Puts the document of {@code entry} on screen with the smallest change that gets there. */
function applyHistoryEntry (entry) {
  const from = __hist.current.doc
  const to = entry.doc
  const epoch = __hist.epoch
  __hist.applying = true
  keepAttachmentsForUndo(from, to)

  const title = historyTitleElement()
  if (title && from.title !== to.title) {
    title.innerText = to.title
    // The title's own listeners update its placeholder and tell Android.
    title.dispatchEvent(new Event('input'))
  }

  let keep = null
  let changedId = null
  let work
  if (sameBlockList(from.blocks, to.blocks) && editorShows(from.blocks)) {
    const updates = []
    for (let i = 0; i < to.blocks.length; i++) {
      if (JSON.stringify(from.blocks[i]) === JSON.stringify(to.blocks[i])) continue
      changedId = to.blocks[i].id
      updates.push(editor.blocks.update(to.blocks[i].id, to.blocks[i].data, to.blocks[i].tunes))
    }
    work = Promise.all(updates)
  } else {
    keep = currentViewState()
    work = editor.render({ blocks: to.blocks })
  }

  return work
    .then(() => readDocument())
    .then(doc => {
      if (epoch !== __hist.epoch) return
      __hist.current = historyEntry(doc, entry.caret)
      __hist.settleUntil = Date.now() + HISTORY_SETTLE_MS
      restoreHistoryCaret(entry.caret, keep, changedId)
      saveContent()
    })
    .catch(err => console.error('[History] applying a step failed:', err))
    .finally(() => {
      if (epoch === __hist.epoch) __hist.applying = false
      reportHistory()
    })
}

function runHistoryAction (action) {
  __hist.queue = __hist.queue.then(action).catch(err => console.error('[History]', err))
  return __hist.queue
}

function historyUndo () {
  if (!editor || isReadMode) return
  runHistoryAction(() =>
    captureHistory(true).then(() => {
      const entry = __hist.undo.pop()
      if (!entry || !__hist.current) return
      __hist.redo.push({ ...__hist.current, caret: historyCaret() || __hist.current.caret })
      return applyHistoryEntry(entry)
    })
  )
}

function historyRedo () {
  if (!editor || isReadMode) return
  runHistoryAction(() =>
    captureHistory(true).then(() => {
      const entry = __hist.redo.pop()
      if (!entry || !__hist.current) return
      __hist.undo.push({ ...__hist.current, caret: historyCaret() || __hist.current.caret })
      return applyHistoryEntry(entry)
    })
  )
}

/**
 * A block Android has deleted together with its file is taken out of every state, so no undo
 * can bring back a block whose file is gone. Steps left with nothing to change are dropped.
 */
function forgetHistoryBlock (blockId) {
  const strip = entry => {
    const blocks = entry.doc.blocks.filter(block => block.id !== blockId)
    if (blocks.length === entry.doc.blocks.length) return entry
    return historyEntry({ title: entry.doc.title, blocks }, entry.caret)
  }
  const distinct = list => list.filter((entry, i) => i === 0 || entry.json !== list[i - 1].json)
  if (__hist.current) __hist.current = strip(__hist.current)
  const undo = distinct(__hist.undo.map(strip))
  if (__hist.current && undo.length && undo[undo.length - 1].json === __hist.current.json) undo.pop()
  // The next redo is the last element; walk them from the current state outwards.
  const redo = distinct(__hist.redo.map(strip).reverse())
  if (__hist.current && redo.length && redo[0].json === __hist.current.json) redo.shift()
  __hist.undo = undo
  __hist.redo = redo.reverse()
  reportHistory()
}

/** Editor.js reported changes; a new, removed or moved block closes the batch at once. */
function historyEditorChanged (event) {
  const events = Array.isArray(event) ? event : [event]
  const structural = events.some(e => e && e.type && e.type !== 'block-changed')
  historyNoteChange(structural, true)
}

function isHistoryShortcut (event) {
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return null
  const key = (event.key || '').toLowerCase()
  if (key === 'z') return event.shiftKey ? 'redo' : 'undo'
  if (key === 'y' && !event.shiftKey) return 'redo'
  return null
}

function isEditingKey (event) {
  if (event.ctrlKey || event.metaKey || event.altKey) return false
  if (event.keyCode === 229) return true
  const key = event.key || ''
  return key.length === 1 || key === 'Enter' || key === 'Backspace' || key === 'Delete' || key === 'Tab'
}

document.addEventListener(
  'keydown',
  event => {
    const action = isHistoryShortcut(event)
    if (action) {
      event.preventDefault()
      event.stopPropagation()
      if (action === 'undo') historyUndo()
      else historyRedo()
      return
    }
    if (isEditingKey(event)) {
      historyInputStarting()
      if (event.key === 'Enter') historyNoteChange(true, false)
    }
  },
  true
)

document.addEventListener(
  'beforeinput',
  event => {
    if (event.inputType === 'historyUndo' || event.inputType === 'historyRedo') {
      // The browser's own undo knows nothing about blocks; ours replaces it.
      event.preventDefault()
      if (event.inputType === 'historyUndo') historyUndo()
      else historyRedo()
      return
    }
    historyInputStarting()
    // A finished word or line closes the batch, the space or line break with it; a space that
    // only follows a pause stays with the word typed after it.
    const data = typeof event.data === 'string' ? event.data : ''
    const lineBreak = event.inputType === 'insertParagraph' || event.inputType === 'insertLineBreak'
    const endsWord = lineBreak || (/\s$/.test(data) && (__hist.batchHasText || /\S/.test(data)))
    historyNoteChange(endsWord, false)
    if (/\S/.test(data)) __hist.batchHasText = true
  },
  true
)

document.addEventListener('selectionchange', () => {
  if (!__hist.pending && !__hist.applying) __hist.lastCaret = historyCaret()
})

/**
 * Apply theme colors from Android.
 */
function setThemeColors (colors) {
  if (!colors) return

  const root = document.documentElement
  for (const key in colors) {
    root.style.setProperty(`--${key}`, colors[key])
  }
}

/**
 * Load note into editor
 */
function loadNote (note) {
  if (!editor) return

  const titleEl = document.getElementById('noteTitleInput')
  titleEl.innerText = note.title || ''
  updateTitlePlaceholder()

  let blocks = []
  if (note.plainTextFallback && note.plainText) {
    blocks = [
      {
        type: 'paragraph',
        data: { text: note.plainText.replace(/\n/g, '<br>') }
      }
    ]
  } else if (note.valueJson) {
    blocks = note.valueJson
  }

  __viewStateReady = false
  // Undo never reaches into another note, or into this one as it was before a reload; only a
  // page recreated on the same document takes over its predecessor's history.
  stopHistory()
  __historyToImport = note.history || null
  editor.render({ blocks }).then(() => {
    __lastSavedJson = JSON.stringify(blocks)
    resetHistory()
    if (note.viewState) restoreViewState(note.viewState)
    else restoreViewportAnchor(note.anchor)
    // "Open in editing mode" with no caret to restore: the caret starts at the block shown at
    // the top, or at the first block. The saved scroll position is applied after this.
    if (note.focusStart && !isReadMode && !note.viewState?.caretId) {
      const vs = note.viewState
      let block = vs?.topId ? editor.blocks.getById(vs.topId) : null
      if (!block && vs?.topIndex >= 0) {
        const count = editor.blocks.getBlocksCount()
        if (count > 0) block = editor.blocks.getBlockByIndex(Math.min(vs.topIndex, count - 1))
      }
      try {
        if (block) editor.caret.setToBlock(block, 'start')
        else editor.caret.setToFirstBlock('start')
      } catch (e) {
        console.error('[Editor] placing the caret failed:', e)
      }
    }
    safeAndroidCall('onNoteRendered')
    // Both restores scroll on the next frame; start reporting once that has happened.
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        __viewStateReady = true
        reportViewState()
      })
    )
  })
}

/**
 * Convert file to Base64
 */
function fileToBase64 (file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.onerror = reject
    r.readAsDataURL(file)
  })
}

/**
 * Uploads run on Android's side, off the JS thread. Each request gets an id; Android answers
 * through window.__onUploadFinished(id, result) once the file is stored. A synchronous bridge
 * call carrying the whole file used to block the page for the length of the save, which froze
 * the editor and made it jump when it caught up.
 */
const __pendingUploads = new Map()
let __uploadSeq = 0

function requestUpload (kind, file) {
  return new Promise(resolve => {
    const id = `u${Date.now()}_${++__uploadSeq}`
    __pendingUploads.set(id, resolve)

    // A file from the system picker: Android already holds its content URI and reads it itself.
    const started = safeAndroidCall(
      'requestPickedUpload',
      id,
      kind,
      file.name || '',
      file.size || 0
    )
    if (started === true) return

    // Pasted or dropped: hand the bytes over; Android stores them in the background.
    fileToBase64(file)
      .then(base64 => {
        const accepted = safeAndroidCall(
          'uploadBase64Async',
          id,
          kind,
          base64,
          file.name || 'file'
        )
        if (accepted !== true) finishUpload(id, null)
      })
      .catch(() => finishUpload(id, null))
  })
}

function finishUpload (id, result) {
  const resolve = __pendingUploads.get(id)
  if (!resolve) return
  __pendingUploads.delete(id)
  resolve(result)
}

window.__onUploadFinished = finishUpload

/**
 * Upload attachment via Android and return Editor.js result.
 */
async function uploadAttachment (file) {
  const result = await requestUpload('file', file)
  if (!result || !result.success || !result.file) {
    return { success: 0, file: null }
  }
  return {
    success: 1,
    file: {
      url: result.file.url,
      name: file.name,
      size: file.size,
      extension: file.name.split('.').pop()
    }
  }
}

/**
 * Upload image via Android and return ImageTool format. The stored image's size travels with
 * the block, so its space is reserved before it has loaded, now and every time the note opens.
 */
async function uploadImage (file) {
  const result = await requestUpload('image', file)
  if (!result || !result.success || !result.file) {
    return { success: 0 }
  }
  return { success: 1, file: result.file }
}

/**
 * Index of the block holding the caret, so a picked file can be put back in the same place if
 * the screen is recreated while the picker is open.
 */
function currentBlockIndex () {
  if (!editor) return -1
  return editor.blocks.getCurrentBlockIndex()
}

/**
 * Inserts a block for a file Android stored after the page was recreated (the picker answered
 * a page that no longer exists).
 */
function insertUploadedBlockFromAndroid (kind, file, index) {
  if (!editor || !file || !file.url) return
  const count = editor.blocks.getBlocksCount()
  const at = index >= 0 && index <= count ? index : count
  if (kind === 'image') {
    editor.blocks.insert('image', { file }, undefined, at, false)
  } else {
    editor.blocks.insert(
      'attaches',
      { file, title: file.name || '' },
      undefined,
      at,
      false
    )
  }
}

/**
 * Delete attachment block from Android request.
 */
window.deleteAttachmentBlockFromAndroid = function (blockId, fileUrl) {
  if (!editor) return

  try {
    const blockAPI = editor.blocks.getById(blockId)
    if (!blockAPI) return

    const el = blockAPI.holder
    const index = [...el.parentNode.children].indexOf(el)
    if (index < 0) return

    editor.blocks.delete(index)
    // Its file is deleted next; no undo may bring the block back without it.
    forgetHistoryBlock(blockId)

    setTimeout(() => {
      safeAndroidCall('onAttachmentBlockDeletedResponse', blockId, fileUrl)
    }, 30)
  } catch (err) {
    console.error('[Delete] JS ERROR:', err)
  }
}

/**
 * Shows the title hint while the title is empty, in the page's language (editor-init sets it
 * from the locale Android passes).
 */
function updateTitlePlaceholder () {
  const titleEl = document.getElementById('noteTitleInput')
  if (!titleEl.innerText.trim()) {
    titleEl.setAttribute('data-placeholder', window.__titlePlaceholder || '')
  } else {
    titleEl.removeAttribute('data-placeholder')
  }
}

const titleEl = document.getElementById('noteTitleInput')

titleEl.addEventListener('input', () => {
  updateTitlePlaceholder()
  safeAndroidCall('onTitleChanged', titleEl.innerText.trim())
  historyNoteChange(false, false)
})

/**
 * The title is edited outside Editor.js, so reading mode has to lock it separately.
 */
function applyTitleEditable () {
  const title = document.getElementById('noteTitleInput')
  if (!title) return
  title.setAttribute('contenteditable', isReadMode ? 'false' : 'true')
  if (isReadMode && document.activeElement === title) title.blur()
}

/**
 * Switches reading mode on or off. Editor.js renders every block again, so the block at the top
 * of the viewport is put back afterwards and the page does not jump. Android hears about every
 * change, including one made by a double tap, so its toolbar always shows the actual mode.
 *
 * @param readOnly the mode to switch to.
 * @param caret where to put the caret once editing is on ({ id, input, offset }), or null.
 */
function setReadMode (readOnly, caret) {
  if (!editor?.readOnly) return Promise.resolve(isReadMode)
  const keep = currentViewState()
  isReadMode = readOnly
  applyTitleEditable()
  return editor.readOnly.toggle(readOnly).then(() => {
    // Opened in reading mode, the note had no history to record edits into.
    if (!readOnly && !__hist.current) resetHistory(true)
    restoreViewState({
      caretId: !readOnly && caret ? caret.id : null,
      caretInput: caret ? caret.input : 0,
      caretOffset: caret ? caret.offset : 0,
      topId: keep.topId,
      topIndex: keep.topIndex,
      topOffset: keep.topOffset
    })
    safeAndroidCall('onReadModeChanged', readOnly, !!caret)
    return readOnly
  })
}

/**
 * Android is about to open the keyboard: makes sure the editor holds a caret for it to type at.
 * A caret the page already has (a restored position, a tap) is kept; otherwise it goes to the
 * start of the first block, without moving the page.
 */
function focusCaretFromAndroid () {
  if (!editor || isReadMode) return
  const active = document.activeElement
  if (active && active.isContentEditable) {
    active.focus({ preventScroll: true })
    return
  }
  const y = window.scrollY
  try {
    editor.caret.setToFirstBlock('start')
  } catch (e) {
    console.error('[Editor] placing the caret failed:', e)
  }
  window.scrollTo(0, y)
}

/**
 * Toggle read-only mode
 */
function toggleReadModeFromAndroid () {
  setReadMode(!isReadMode, null)
}

/**
 * The text position under a point in reading mode, as { id, input, offset }: the block, which of
 * its inputs (a list has one per item) and how many characters into it.
 */
function positionAtPoint (x, y) {
  const range = document.caretRangeFromPoint?.(x, y)
  if (!range) return null
  const node = range.startContainer
  const element = node.nodeType === 1 ? node : node.parentElement
  const holder = element?.closest('.ce-block')
  if (!holder) return null
  const block = blockForHolder(holder)
  if (!block) return null
  const inputs = [...holder.querySelectorAll('[contenteditable]')]
  const input = inputs.find(candidate => candidate.contains(node))
  if (!input) return { id: block.id, input: 0, offset: 0 }
  const before = document.createRange()
  before.selectNodeContents(input)
  before.setEnd(node, range.startOffset)
  return {
    id: block.id,
    input: inputs.indexOf(input),
    offset: before.toString().length
  }
}

document.addEventListener('dblclick', event => {
  if (!__doubleTapToEdit || !isReadMode || !editor) return
  const position = positionAtPoint(event.clientX, event.clientY)
  // A double tap selects a word; editing starts with a plain caret instead.
  window.getSelection()?.removeAllRanges()
  if (!position) return
  event.preventDefault()
  setReadMode(false, position)
})

window.setThemeColors = setThemeColors
window.loadNote = loadNote
window.uploadAttachment = uploadAttachment
window.toggleReadModeFromAndroid = toggleReadModeFromAndroid
window.setReadMode = setReadMode
window.saveContent = saveContent
window.currentBlockIndex = currentBlockIndex
window.insertUploadedBlockFromAndroid = insertUploadedBlockFromAndroid
window.flushContent = flushContent
window.finishPage = finishPage
window.focusCaretFromAndroid = focusCaretFromAndroid
window.historyUndo = historyUndo
window.historyRedo = historyRedo
window.historyEditorChanged = historyEditorChanged
// expose globally
window.uploadImage = uploadImage
