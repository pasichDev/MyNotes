import EditorJS from '@editorjs/editorjs'
import Header from '@editorjs/header'
import List from '@editorjs/list'
import Paragraph from '@editorjs/paragraph'
import Delimiter from '@editorjs/delimiter'
import Marker from '@editorjs/marker'
import InlineCode from '@editorjs/inline-code'
import ImageTool from '@editorjs/image'

window.EditorJS = EditorJS
window.Header = Header
window.List = List
window.Paragraph = Paragraph
window.Delimiter = Delimiter
window.Marker = Marker
window.InlineCode = InlineCode
window.ImageTool = ImageTool
;(function (global) {
  function initEditor (locale, i18n) {
    global.__EDITOR_READY = false
    global.__saveTimer = null

    const titleDiv = document.getElementById('noteTitleInput')
    const AttachesTool = global.AttachesTool

    class ParagraphCustom extends Paragraph {
      onKeyDown (event) {
        if (event.key === 'Enter') event.preventDefault()
        else super.onKeyDown(event)
      }
    }

    const editor = new EditorJS({
      holder: 'editorjs',
      i18n,
      placeholder: i18n.placeholder,
      autofocus: true,
      readOnly: false,

      tools: {
        paragraph: { class: ParagraphCustom, inlineToolbar: true },
        Headers: Header,
        list: List,
        delimiter: Delimiter,
        marker: Marker,
        inlineCode: InlineCode,
        attaches: {
          class: AttachesTool,
          config: {
            uploader: {
              uploadByFile: file => global.uploadAttachment(file)
            },
            openFile: file =>
              global.Android?.openAttachment(JSON.stringify(file))
          }
        },
        image: {
          class: ImageTool,
          config: {
            features: {
              caption: false
            },
            uploader: {
              uploadByFile: async file => {
                return await window.uploadImage(file)
              },

              uploadByUrl: async url => {
                // додамо потім
                return { success: 0 }
              }
            }
          }
        }
      },

      onReady () {
        global.editor = editor
        global.attachEditorInstance?.(editor)
        global.__EDITOR_READY = true

        document.getElementById('loading').style.display = 'none'
        document.getElementById('editorjs').style.display = 'block'

        global.Android?.onEditorReady()

        updateTitlePlaceholder(i18n.title_placeholder || 'Title...')
      },

      onChange () {
        if (!global.__EDITOR_READY) return

        clearTimeout(global.__saveTimer)
        global.__saveTimer = setTimeout(() => {
          global.saveContent?.()
        }, 120)
      }
    })

    function updateTitlePlaceholder (text) {
      const value = titleDiv.textContent.trim()
      if (!value) titleDiv.setAttribute('data-placeholder', text)
      else titleDiv.removeAttribute('data-placeholder')
    }

    titleDiv.addEventListener('input', () => {
      updateTitlePlaceholder(i18n.title_placeholder || 'Title...')
      global.Android?.onTitleChanged(titleDiv.innerText.trim())
    })

    titleDiv.addEventListener('keydown', e => {
      if (e.key === 'Enter') {
        e.preventDefault()
        const first = editor.blocks.getBlockByIndex(0)
        const content = first?.holder.querySelector("[contenteditable='true']")
        content?.focus()
      }
    })

    global.updateTitlePlaceholder = updateTitlePlaceholder

    return editor
  }

  global.initEditor = initEditor
})(window)
