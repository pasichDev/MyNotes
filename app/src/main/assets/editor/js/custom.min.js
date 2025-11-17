let isReadMode = false
const titleDiv = document.getElementById('noteTitleInput')
let currentHeadingName = 'Title'

// --- Функція збереження контенту ---
function saveContent () {
  if (!editor) return
  editor
    .save()
    .then(output => {
      if (window.Android && window.Android.onContentChanged) {
        const jsonData = JSON.stringify(output.blocks) // рядок JSON
        window.Android.onContentChanged(jsonData)
      }
    })
    .catch(err => console.error('Save failed:', err))
}
// --- Встановлення кольорів теми ---
function setThemeColors (colors) {
  const root = document.documentElement
  if (!colors) return
  Object.keys(colors).forEach(key =>
    root.style.setProperty(`--${key}`, colors[key])
  )
}

function loadNote (note) {
  if (!editor) return

  // Встановлюємо title
  if (note.title) {
    titleDiv.innerText = note.title
    updateTitlePlaceholder()
  }

  const blocks = []

  if (note.plainTextFallback && note.plainText) {
    // старі нотатки — вставляємо plainText у перший параграф
    blocks.push({
      type: 'paragraph',
      data: { text: note.plainText.replace(/\n/g, '<br>') } // <-- конвертуємо перенос рядка
    })
  } else if (note.valueJson) {
    note.valueJson.forEach(block => {
      blocks.push(block)
    })
  }

  editor.render({ blocks }).catch(err => console.error(err))
}

// --- Отримати дані Note ---
function getNoteData () {
  if (!editor) return
  editor
    .save()
    .then(output => {
      const jsonData = JSON.stringify(output.blocks)
      if (window.Android) window.Android.onContentChanged(jsonData)
    })
    .catch(err => console.error(err))
}

function toggleReadModeFromAndroid () {
  isReadMode = !isReadMode
  if (editor && editor.readOnly) {
    editor.readOnly.toggle()
  }

  const title = document.getElementById('noteTitleInput')
  if (title) {
    title.contentEditable = !isReadMode
    title.style.pointerEvents = isReadMode ? 'none' : 'auto'
    title.style.userSelect = isReadMode ? 'none' : 'text'
  }
}

// Показуємо placeholder
function updateTitlePlaceholder (headingName = 'Heading') {
  const text = headingName + '...'
  if (!titleDiv.innerText.trim()) {
    titleDiv.setAttribute('data-placeholder', text)
  } else {
    titleDiv.removeAttribute('data-placeholder')
  }
}

// Відстежуємо input заголовку
titleDiv.addEventListener('input', () => {
  updateTitlePlaceholder()

  if (window.Android && window.Android.onTitleChanged) {
    window.Android.onTitleChanged(titleDiv.innerText.trim())
  }
})

// Enter у заголовку → фокус на перший блок Editor.js
titleDiv.addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    e.preventDefault()
    if (window.editor) {
      const firstBlock = editor.blocks.getBlockByIndex(0)
      if (firstBlock) {
        const blockContent = firstBlock.holder.querySelector(
          "[contenteditable='true']"
        )
        if (blockContent) {
          blockContent.focus()
          const range = document.createRange()
          const sel = window.getSelection()
          range.selectNodeContents(blockContent)
          range.collapse(false)
          sel.removeAllRanges()
          sel.addRange(range)
        }
      }
    }
  }
})
updateTitlePlaceholder()


window.addEventListener('load', () => {
  // Беремо локаль з URL ?locale=uk
  const params = new URLSearchParams(window.location.search)
  const locale = params.get('locale') || 'en'

  // Передаємо в глобальну конфігурацію
  window.EditorConfig = { locale }
  const i18n = getTranslationsForLocale(locale)
  currentHeadingName = i18n.title_placeholder || 'Title...'

  updateTitlePlaceholder(currentHeadingName)
  // Ініціалізуємо редактор із цією локаллю
  initEditor(locale)
})

// --- Convert File → Base64 ---
async function fileToBase64 (file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()

    reader.onload = () => {
      console.log('fileToBase64: length =', reader.result.length)
      resolve(reader.result)
    }

    reader.onerror = err => {
      console.error('fileToBase64 ERROR:', err)
      reject(err)
    }

    reader.readAsDataURL(file)
  })
}

// --- Upload to Android (AttachesTool will call this) ---
async function uploadAttachment (file) {
  console.log('uploadAttachment: file =', file)

  if (!window.Android || !window.Android.uploadFile) {
    console.error('uploadAttachment ERROR: window.Android.uploadFile NOT FOUND')
    return {
      success: 0,
      file: null
    }
  }

  const base64 = await fileToBase64(file)

  console.log('uploadAttachment: base64 OK, sending to Android...')

  let url = ''
  try {
    url = window.Android.uploadFile(base64, file.name)
  } catch (e) {
    console.error('uploadAttachment: Android upload FAILED:', e)
    return { success: 0 }
  }

  console.log('uploadAttachment: Android returned URL:', url)

  return {
    success: 1,
    file: {
      url: url,
      name: file.name,
      size: file.size,
      extension: file.name.split('.').pop()
    }
  }
}

// --- Глобальні функції для WebView ---
window.setThemeColors = setThemeColors
window.loadNote = loadNote
window.getNoteData = getNoteData

// --- Export for Editor.js Tools (AttachesTool uses this) ---
window.uploadAttachment = uploadAttachment
