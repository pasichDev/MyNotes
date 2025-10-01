const titleDiv = document.getElementById("noteTitleInput");

// Показуємо placeholder
function updateTitlePlaceholder() {
  if (!titleDiv.innerText.trim()) {
    titleDiv.setAttribute("data-placeholder", "Enter title...");
  } else {
    titleDiv.removeAttribute("data-placeholder");
  }
}

// Відстежуємо input заголовку
titleDiv.addEventListener("input", () => {
  updateTitlePlaceholder();

  if (window.Android && window.Android.onTitleChanged) {
    window.Android.onTitleChanged(titleDiv.innerText.trim());
  }
});

updateTitlePlaceholder();

// Enter у заголовку → фокус на перший блок Editor.js
titleDiv.addEventListener("keydown", (e) => {
  if (e.key === "Enter") {
    e.preventDefault();
    if (window.editor) {
      const firstBlock = editor.blocks.getBlockByIndex(0);
      if (firstBlock) {
        const blockContent = firstBlock.holder.querySelector("[contenteditable='true']");
        if (blockContent) {
          blockContent.focus();
          const range = document.createRange();
          const sel = window.getSelection();
          range.selectNodeContents(blockContent);
          range.collapse(false);
          sel.removeAllRanges();
          sel.addRange(range);
        }
      }
    }
  }
});