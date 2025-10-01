      // --- Функція збереження контенту ---
      function saveContent() {
        if (!editor) return;
        editor
          .save()
          .then((output) => {
            if (window.Android && window.Android.onContentChanged) {
              const jsonData = JSON.stringify(output.blocks); // рядок JSON
              window.Android.onContentChanged(jsonData);
            }
          })
          .catch((err) => console.error("Save failed:", err));
      }
      // --- Встановлення кольорів теми ---
      function setThemeColors(colors) {
        const root = document.documentElement;
        if (!colors) return;
        Object.keys(colors).forEach((key) =>
          root.style.setProperty(`--${key}`, colors[key])
        );
      }

function loadNote(note) {
  if (!editor) return;

  // Встановлюємо title
  if (note.title) {
    titleDiv.innerText = note.title;
    updateTitlePlaceholder();
  }

  const blocks = [];

  if (note.plainTextFallback && note.plainText) {
    // старі нотатки — вставляємо plainText у перший параграф
    blocks.push({
      type: "paragraph",
      data: { text: note.plainText }
    });
  } else if (note.valueJson) {
    note.valueJson.forEach((block) => {
      blocks.push(block);
    });
  }

  editor.render({ blocks }).catch((err) => console.error(err));
}



      // --- Отримати дані Note ---
      function getNoteData() {
        if (!editor) return;
        editor
          .save()
          .then((output) => {
            const jsonData = JSON.stringify(output.blocks);
            if (window.Android) window.Android.onContentChanged(jsonData);
          })
          .catch((err) => console.error(err));
      }
     
      // --- Глобальна функція для отримання всіх даних нотатки ---
function getNoteSnapshot() {
  const title = document.getElementById("noteTitleInput").innerText.trim();
  let blocksJson = "[]";

  if (window.editor) {
    return editor.save().then((output) => {
      blocksJson = JSON.stringify(output.blocks || []);
      return JSON.stringify({ title, blocksJson });
    });
  } else {
    return Promise.resolve(JSON.stringify({ title, blocksJson }));
  }
}

// --- Глобальні функції для WebView ---
      window.setThemeColors = setThemeColors;
      window.loadNote = loadNote;
      window.getNoteData = getNoteData;
      window.getNoteSnapshot = getNoteSnapshot;
      window.addEventListener("load", initEditor);