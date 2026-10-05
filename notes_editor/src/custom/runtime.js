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
 * Upload attachment via Android and return Editor.js result.
 */
async function uploadAttachment (file) {
  const base64 = await fileToBase64(file)
  const url = safeAndroidCall('uploadFile', base64, file.name)

  return {
    success: url ? 1 : 0,
    file: url
      ? {
          url,
          name: file.name,
          size: file.size,
          extension: file.name.split('.').pop()
        }
      : null
  }
}

/**
 * Upload image via Android and return ImageTool format.
 */
async function uploadImage (file) {
  const base64 = await fileToBase64(file)
  const respJson = safeAndroidCall('uploadImage', base64, file.name)

  if (!respJson) {
    return { success: 0 }
  }

  try {
    return JSON.parse(respJson)
  } catch (e) {
    console.error('[ImageUpload] Invalid JSON:', respJson)
    return { success: 0 }
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
window.flushContent = flushContent
// expose globally
window.uploadImage = uploadImage
