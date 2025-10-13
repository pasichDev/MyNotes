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

// --- Глобальні функції для WebView ---
window.setThemeColors = setThemeColors
window.loadNote = loadNote
window.getNoteData = getNoteData

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
