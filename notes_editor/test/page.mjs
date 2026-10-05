// A minimal stand-in for the editor page, enough to run src/custom/runtime.js in Node and check
// its logic: no layout, no real Editor.js. Run the tests with `npm test`.
import fs from 'node:fs'
import vm from 'node:vm'

const RUNTIME = new URL('../src/custom/runtime.js', import.meta.url)

function element () {
  return {
    attrs: {},
    innerText: '',
    textContent: '',
    listeners: {},
    isContentEditable: false,
    setAttribute (k, v) { this.attrs[k] = String(v) },
    removeAttribute (k) { delete this.attrs[k] },
    getAttribute (k) { return this.attrs[k] },
    addEventListener (type, f) { (this.listeners[type] ||= []).push(f) },
    dispatchEvent (event) { (this.listeners[event.type] || []).forEach(f => f(event)) },
    focus () {},
    blur () {},
    contains () { return false },
    getBoundingClientRect () { return { top: 0, bottom: 0 } }
  }
}

/** Loads runtime.js into a fresh page. {@code search} is the page URL's query. */
export function openPage (search = '') {
  const calls = []
  const listeners = {}
  const frames = []
  const title = element()
  const document = {
    documentElement: { scrollHeight: 4000, style: { setProperty () {} } },
    getElementById: id => (id === 'noteTitleInput' ? title : element()),
    addEventListener: (type, f) => { (listeners['document:' + type] ||= []).push(f) },
    activeElement: null,
    hidden: false,
    createRange: () => ({ selectNodeContents () {}, setEnd () {}, toString: () => '' })
  }
  const android = new Proxy({}, { get: (_, name) => (...args) => { calls.push([name, ...args]) } })
  const window = {
    location: { search },
    scrollY: 0,
    innerHeight: 800,
    Android: android,
    addEventListener: (type, f) => { (listeners['window:' + type] ||= []).push(f) },
    getSelection: () => null,
    scrollTo (x, y) { window.scrollY = y },
    scrollBy (x, y) { window.scrollY += y }
  }
  window.window = window
  const context = {
    window,
    document,
    console,
    URLSearchParams,
    Promise,
    setTimeout,
    clearTimeout,
    Date,
    JSON,
    requestAnimationFrame: f => frames.push(f),
    NodeFilter: { SHOW_TEXT: 4 },
    Event: class { constructor (type) { this.type = type } }
  }
  vm.createContext(context)
  vm.runInContext(
    fs.readFileSync(RUNTIME, 'utf8') +
      '\n;window.__internals = { get history () { return __hist } }',
    context
  )
  return {
    window,
    document,
    title,
    calls,
    /** Sends a DOM event to the listeners the runtime registered on document or window. */
    fire (target, type, event = {}) {
      for (const f of listeners[target + ':' + type] || []) f({ type, ...event })
    },
    /** Runs the animation frames requested so far, and those they request. */
    runFrames () { while (frames.length) frames.shift()() },
    callsTo (name) { return calls.filter(c => c[0] === name).map(c => c.slice(1)) }
  }
}

/**
 * A stand-in for the Editor.js instance: blocks laid out 100px apart, and save() refusing to work
 * while read-only, as Editor.js does.
 */
export function fakeEditor (blocks, { readOnly = false } = {}) {
  const state = { readOnly, blocks: blocks.map(b => ({ ...b })) }
  const block = i => state.blocks[i] && {
    id: state.blocks[i].id,
    holder: {
      getBoundingClientRect: () => ({ top: 100 * i, bottom: 100 * i + 90 }),
      querySelectorAll: () => []
    }
  }
  const editor = {
    readOnly: {
      get isEnabled () { return state.readOnly },
      toggle: async value => { state.readOnly = value; return value }
    },
    save: () =>
      state.readOnly
        ? Promise.reject(new Error("Editor's content can not be saved in read-only mode"))
        : Promise.resolve({ blocks: state.blocks.map(b => ({ ...b })) }),
    render: async ({ blocks }) => { state.blocks = blocks.map(b => ({ ...b })) },
    blocks: {
      getBlocksCount: () => state.blocks.length,
      getBlockByIndex: block,
      getById: id => block(state.blocks.findIndex(b => b.id === id)) || null,
      update: async (id, data) => {
        const target = state.blocks.find(b => b.id === id)
        if (target) target.data = data
      }
    },
    caret: { setToBlock () {}, setToFirstBlock () {} }
  }
  return { editor, state }
}

/** Lets pending promises and zero-delay timers run. */
export const settle = () => new Promise(resolve => setTimeout(resolve, 10))
