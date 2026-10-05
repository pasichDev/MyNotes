package com.pasich.mynotes.extendedEditor.utils;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import com.pasich.mynotes.utils.editor.NoteViewState;
import com.pasich.mynotes.utils.editor.PositionRestorer;
import java.util.Arrays;
import org.junit.Test;

public class ExtendedViewStateJsonTest {

    @Test
    public void fromPage_readsTheReportedPosition() {
        NoteViewState.Extended state =
                ExtendedViewStateJson.fromPage(
                        "{\"scrollTop\":840,\"ratio\":0.42,\"topId\":\"b2\",\"topIndex\":3,"
                                + "\"topOffset\":-18,\"caret\":{\"id\":\"b5\",\"input\":1,"
                                + "\"offset\":27}}");

        assertThat(state).isNotNull();
        assertThat(state.scrollTop).isEqualTo(840);
        assertThat(state.scrollRatio).isWithin(1e-4f).of(0.42f);
        assertThat(state.topBlockId).isEqualTo("b2");
        assertThat(state.topBlockIndex).isEqualTo(3);
        assertThat(state.topOffsetPx).isEqualTo(-18);
        assertThat(state.caretBlockId).isEqualTo("b5");
        assertThat(state.caretInput).isEqualTo(1);
        assertThat(state.caretOffset).isEqualTo(27);
    }

    @Test
    public void fromPage_withoutCaretOrTopBlock_leavesThemUnset() {
        NoteViewState.Extended state =
                ExtendedViewStateJson.fromPage(
                        "{\"scrollTop\":0,\"ratio\":0,\"topId\":null,\"topIndex\":-1,"
                                + "\"topOffset\":0,\"caret\":null}");

        assertThat(state).isNotNull();
        assertThat(state.caretBlockId).isNull();
        assertThat(state.topBlockId).isNull();
        assertThat(state.topBlockIndex).isEqualTo(-1);
    }

    @Test
    public void fromPage_rejectsGarbage() {
        assertThat(ExtendedViewStateJson.fromPage(null)).isNull();
        assertThat(ExtendedViewStateJson.fromPage("")).isNull();
        assertThat(ExtendedViewStateJson.fromPage("not json")).isNull();
        assertThat(ExtendedViewStateJson.fromPage("[1,2]")).isNull();
    }

    @Test
    public void blockIds_readsIdsInOrder_andKeepsThePlaceOfBlocksWithoutOne() {
        String json =
                "[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{}},"
                        + "{\"type\":\"paragraph\",\"data\":{}},"
                        + "{\"id\":\"c\",\"type\":\"header\",\"data\":{}}]";

        assertThat(ExtendedViewStateJson.blockIds(json)).containsExactly("a", "", "c").inOrder();
    }

    @Test
    public void blockIds_acceptsAWholeEditorDocument() {
        assertThat(ExtendedViewStateJson.blockIds("{\"blocks\":[{\"id\":\"x\"}]}"))
                .containsExactly("x");
    }

    @Test
    public void blockIds_ofNothingIsEmpty() {
        assertThat(ExtendedViewStateJson.blockIds(null)).isEmpty();
        assertThat(ExtendedViewStateJson.blockIds("  ")).isEmpty();
        assertThat(ExtendedViewStateJson.blockIds("{broken")).isEmpty();
    }

    @Test
    public void toPage_carriesTheTargetForTheRuntime() {
        NoteViewState.Extended saved = new NoteViewState.Extended();
        saved.caretBlockId = "c";
        saved.caretOffset = 4;
        saved.topBlockId = "b";
        saved.topBlockIndex = 1;
        saved.topOffsetPx = 12;
        PositionRestorer.ExtendedTarget target =
                PositionRestorer.restoreExtended(saved, Arrays.asList("a", "b", "c"));

        JsonObject page = ExtendedViewStateJson.toPage(target);

        assertThat(page.get("caretId").getAsString()).isEqualTo("c");
        assertThat(page.get("caretOffset").getAsInt()).isEqualTo(4);
        assertThat(page.get("topId").getAsString()).isEqualTo("b");
        assertThat(page.get("topIndex").getAsInt()).isEqualTo(1);
        assertThat(page.get("topOffset").getAsInt()).isEqualTo(12);
    }
}
