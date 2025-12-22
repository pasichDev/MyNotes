import EditorJS from '@editorjs/editorjs'
import Header from '@editorjs/header'
import List from '@editorjs/list'
import Paragraph from '@editorjs/paragraph'
import Delimiter from '@editorjs/delimiter'
import Marker from '@editorjs/marker'
import InlineCode from '@editorjs/inline-code'
import ImageTool from '@editorjs/image'
import ImageTunePlus from "editorjs-image-tune-plus";
import ImageToolClickable from './tools/ImageToolClickable.js';
import SpacerTool from './tools/spacer'

window.EditorJS = EditorJS
window.Header = Header
window.List = List
window.Paragraph = Paragraph
window.Delimiter = Delimiter
window.Marker = Marker
window.InlineCode = InlineCode
window.ImageTool = ImageTool
window.ImageTunePlus = ImageTunePlus
window.ImageToolClickable = ImageToolClickable
window.SpacerTool = SpacerTool

;(function (global) {
  function initEditor (locale, i18n) {
    global.__EDITOR_READY = false
    global.__saveTimer = null

    const titleDiv = document.getElementById('noteTitleInput')
    const AttachesTool = global.AttachesTool



    const editor = new EditorJS({
      holder: 'editorjs',
      i18n,
      placeholder: i18n.placeholder,
      autofocus: true,
      readOnly: false,

      tools: {
        paragraph: { class: Paragraph, inlineToolbar: true },
        Headers: Header,
        list: List,
        delimiter: Delimiter,
        marker: Marker,
        inlineCode: InlineCode,
          spacer: {
              class: SpacerTool
            },
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
          class: ImageToolClickable,
          tunes: ['imageTunePlus'],
          config: {
            features: {
              caption: false
            },
            uploader: {
              uploadByFile: async file => {
                return await window.uploadImage(file)
              },

              uploadByUrl: async url => {
                return { success: 0 }
              }
            }
          }
        },
        imageTunePlus: {
             class: ImageTunePlus,
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

          global.saveContent?.()
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
