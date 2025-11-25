/**
 * Runtime bridge for Android ↔ Editor.js
 */

let editor = null
let isReadMode = false

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
      safeAndroidCall('onContentChanged', JSON.stringify(output.blocks))
    })
    .catch(err => console.error('[Editor] Save failed:', err))
}

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

  editor.render({ blocks })
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
// expose globally
window.uploadImage = uploadImage
