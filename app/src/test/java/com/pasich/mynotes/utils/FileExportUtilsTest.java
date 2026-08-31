package com.pasich.mynotes.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.pasich.mynotes.utils.file.FileExportUtils;
import org.junit.Test;

public class FileExportUtilsTest {

    @Test
    public void canHandleIntent_withoutMatchingActivity_returnsFalse() {
        PackageManager packageManager = mock(PackageManager.class);
        Intent intent = mock(Intent.class);
        when(intent.resolveActivity(packageManager)).thenReturn(null);

        assertThat(FileExportUtils.canHandleIntent(packageManager, intent)).isFalse();
    }

    @Test
    public void canHandleIntent_withMatchingActivity_returnsTrue() {
        PackageManager packageManager = mock(PackageManager.class);
        Intent intent = mock(Intent.class);
        when(intent.resolveActivity(packageManager)).thenReturn(mock(ComponentName.class));

        assertThat(FileExportUtils.canHandleIntent(packageManager, intent)).isTrue();
    }
}
