package com.pasich.mynotes.extendedEditor.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.webkit.WebView;
import com.pasich.mynotes.extendedEditor.models.SettingsEditorJsBridge;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The extended editor keeps its WebView until the page has handed over its last edit, and never
 * sends anything to a destroyed WebView.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EditorJSInterfaceFinishTest {

    private WebView webView;
    private EditorJSInterface bridge;
    private final AtomicInteger destroyed = new AtomicInteger();

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        webView = mock(WebView.class);
        bridge =
                new EditorJSInterface(
                        mock(EditorJSInterface.EditorListener.class),
                        webView,
                        context,
                        new SettingsEditorJsBridge(false));
    }

    private void destroyWebView() {
        destroyed.incrementAndGet();
        bridge.release();
    }

    private static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    @Test
    public void finishing_waitsForThePageToAnswer() {
        bridge.finishPage(true, this::destroyWebView);

        verify(webView).evaluateJavascript(contains("finishPage(true)"), any());
        idleFor(EditorJSInterface.FINISH_TIMEOUT_MS / 2);
        assertThat(destroyed.get()).isEqualTo(0);

        // The page answers on the bridge thread once its last edit was sent.
        bridge.onPageFinished();
        idleFor(0);
        assertThat(destroyed.get()).isEqualTo(1);

        idleFor(EditorJSInterface.FINISH_TIMEOUT_MS * 2);
        assertThat(destroyed.get()).isEqualTo(1);
    }

    @Test
    public void aPageThatNeverAnswers_isDestroyedAfterTheTimeout() {
        bridge.finishPage(false, this::destroyWebView);

        idleFor(EditorJSInterface.FINISH_TIMEOUT_MS + 10);
        assertThat(destroyed.get()).isEqualTo(1);

        bridge.onPageFinished();
        idleFor(10);
        assertThat(destroyed.get()).isEqualTo(1);
    }

    @Test
    public void afterTheWebViewIsDestroyed_nothingReachesIt() throws Exception {
        bridge.release();

        bridge.requestFlush();
        bridge.undo();
        bridge.toggleReadMode();
        // A flush asked for from another thread just before the WebView went.
        Thread other = new Thread(bridge::requestFlush);
        other.start();
        other.join();
        idleFor(10);

        verify(webView, never()).evaluateJavascript(any(), any());
    }

    @Test
    public void aFlushPostedBeforeTheWebViewWent_isDroppedWhenItRuns() throws Exception {
        Thread other = new Thread(bridge::requestFlush);
        other.start();
        other.join();
        bridge.release();
        idleFor(10);

        verify(webView, never()).evaluateJavascript(any(), any());
    }

    @Test
    public void finishingAnAlreadyDestroyedPage_runsAtOnce() {
        bridge.release();
        bridge.finishPage(false, destroyed::incrementAndGet);
        assertThat(destroyed.get()).isEqualTo(1);
    }
}
