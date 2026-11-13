package com.pasich.mynotes.extendedEditor.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.webkit.WebView;
import com.pasich.mynotes.extendedEditor.models.SettingsEditorJsBridge;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Values from a note's document reach the page's scripts as string literals, never as code. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EditorJSInterfaceScriptTest {

    @Test
    public void aBlockIdAndUrlCannotBreakOutOfTheirStrings() {
        WebView webView = mock(WebView.class);
        EditorJSInterface bridge =
                new EditorJSInterface(
                        mock(EditorJSInterface.EditorListener.class),
                        webView,
                        RuntimeEnvironment.getApplication(),
                        new SettingsEditorJsBridge(false));

        bridge.deleteAttachmentBlockRequest("a');Android.x();('", "editorjs://a\\'b");

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(webView).evaluateJavascript(script.capture(), any());
        assertThat(script.getValue())
                .isEqualTo(
                        "deleteAttachmentBlockFromAndroid(\"a');Android.x();('\","
                                + " \"editorjs:\\/\\/a\\\\'b\");");
    }
}
