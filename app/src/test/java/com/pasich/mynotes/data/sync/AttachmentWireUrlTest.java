package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class AttachmentWireUrlTest {

    private static final String ID = "7d444840-9dc0-11d1-b245-5ffdce74fad2";

    @Test
    public void roundTripsALogicalId() {
        String wire = AttachmentWireUrl.forLogicalId(ID);

        assertThat(AttachmentWireUrl.logicalIdOf(wire)).isEqualTo(ID);
    }

    @Test
    public void isNotMistakenForALocalReference() {
        // A wire reference must never parse as a note-folder path, or a leaked one would be
        // taken for a file this device owns.
        assertThat(
                        com.pasich.mynotes.extendedEditor.attach.AttachmentUrl.parse(
                                AttachmentWireUrl.forLogicalId(ID)))
                .isNull();
        assertThat(AttachmentWireUrl.logicalIdOf("editorjs://attachments/note_5/photo.png"))
                .isNull();
        assertThat(AttachmentWireUrl.logicalIdOf("mynotes-sync://attachment/not-a-uuid")).isNull();
        assertThat(AttachmentWireUrl.logicalIdOf(null)).isNull();
    }
}
