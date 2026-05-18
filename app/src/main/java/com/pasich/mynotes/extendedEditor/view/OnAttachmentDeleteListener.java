package com.pasich.mynotes.extendedEditor.view;

import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

/** Callback invoked when an attachment is requested to be deleted. */
public interface OnAttachmentDeleteListener {
    void onDeleteAttachment(EditorAttachment attachment, boolean fileLost);
}
