package com.pasich.mynotes.extendedEditor.view;

import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

public interface OnAttachmentDeleteListener {
    void onDeleteAttachment(EditorAttachment attachment, boolean fileLost);
}
