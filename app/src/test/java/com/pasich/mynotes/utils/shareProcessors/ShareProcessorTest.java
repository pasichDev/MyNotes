package com.pasich.mynotes.utils.shareProcessors;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.net.Uri;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The exported "open with" handler reads only other apps' content into a note. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ShareProcessorTest {

    private static final String PACKAGE = "com.pasich.mynotes";

    private static boolean readable(String uri) {
        return ShareProcessor.isReadableSharedUri(uri == null ? null : Uri.parse(uri), PACKAGE);
    }

    @Test
    public void anotherAppsContentIsRead() {
        assertThat(readable("content://com.android.providers.downloads.documents/document/12"))
                .isTrue();
        assertThat(readable("content://com.pasich.mynotesfake/file.txt")).isTrue();
    }

    @Test
    public void nothingFilesOrTheAppsOwnProvidersAreRefused() {
        assertThat(readable(null)).isFalse();
        assertThat(readable("file:///data/data/com.pasich.mynotes/databases/notes.db")).isFalse();
        assertThat(readable("FILE:///sdcard/a.txt")).isFalse();
        assertThat(readable("content://com.pasich.mynotes.provider/files/a.txt")).isFalse();
        assertThat(readable("content://COM.PASICH.MYNOTES.provider/a")).isFalse();
        assertThat(readable("content://0@com.pasich.mynotes.provider/a")).isFalse();
        assertThat(readable("content://com.pasich.mynotes/a")).isFalse();
        assertThat(readable("https://example.com/a.txt")).isFalse();
    }

    @Test
    public void aProviderWithNothingToReadReportsAnErrorInsteadOfCrashing() throws Exception {
        ContentResolver resolver = RuntimeEnvironment.getApplication().getContentResolver();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        ShareProcessor.readFileAsync(
                resolver,
                Uri.parse("content://com.example.missing/a.txt"),
                new ShareProcessor.FileReadCallback() {
                    @Override
                    public void onSuccess(String content) {
                        done.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        error.set(e);
                        done.countDown();
                    }
                });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNotNull();
    }
}
