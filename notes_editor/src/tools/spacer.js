export default class SpacerTool {
  static get toolbox () {
    return {
      title: 'Spacer',
      icon: '⸻'
    }
  }

 constructor ({ data }) {
   const size = data?.size || 'm'
   this.data = { size }
 }


  render () {
    const el = document.createElement('div')
    el.className = `ce-spacer ce-spacer-${this.data.size}`
    return el
  }

 save () {
   return { size: this.data.size || 'm' }
 }


  validate () {
    return true
  }

  static get isReadOnlySupported () {
    return true
  }
}
