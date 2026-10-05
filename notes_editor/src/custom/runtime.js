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
}

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
  if (!editor) return

  editor
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

  editor.render({ blocks }).then(() => {
    __lastSavedJson = JSON.stringify(blocks)
    restoreViewportAnchor(note.anchor)
    safeAndroidCall('onNoteRendered')
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

    setTimeout(() => {
      safeAndroidCall('onAttachmentBlockDeletedResponse', blockId, fileUrl)
    }, 30)
  } catch (err) {
    console.error('[Delete] JS ERROR:', err)
  }
}

/**
 * Placeholder logic for title
 */
function updateTitlePlaceholder () {
  const titleEl = document.getElementById('noteTitleInput')
  if (!titleEl.innerText.trim()) {
    titleEl.setAttribute('data-placeholder', 'Title...')
  } else {
    titleEl.removeAttribute('data-placeholder')
  }
}

const titleEl = document.getElementById('noteTitleInput')

titleEl.addEventListener('input', () => {
  updateTitlePlaceholder()
  safeAndroidCall('onTitleChanged', titleEl.innerText.trim())
})

/**
 * Toggle read-only mode
 */
function toggleReadModeFromAndroid () {
  isReadMode = !isReadMode
  if (editor?.readOnly) editor.readOnly.toggle()
}

window.setThemeColors = setThemeColors
window.loadNote = loadNote
window.uploadAttachment = uploadAttachment
window.toggleReadModeFromAndroid = toggleReadModeFromAndroid
window.saveContent = saveContent
window.currentBlockIndex = currentBlockIndex
window.insertUploadedBlockFromAndroid = insertUploadedBlockFromAndroid
window.flushContent = flushContent
// expose globally
window.uploadImage = uploadImage
