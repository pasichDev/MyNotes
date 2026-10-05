package com.pasich.mynotes.extendedEditor.models;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.net.Uri;
import org.junit.Test;

/** The page's upload request is matched to the picked file Android already holds. */
public class PickedFileTest {

    private final Uri uri = mock(Uri.class);

    @Test
    public void matchesByDisplayName() {
        PickedFile picked = new PickedFile(uri, "IMG_2041.jpg", 1_204_000);

        assertThat(picked.matches("IMG_2041.jpg", 0)).isTrue();
    }

    @Test
    public void matchesBySizeWhenTheNameDiffers() {
        // Some providers report a different name to the page than to the resolver.
        PickedFile picked = new PickedFile(uri, "image:1834", 1_204_000);

        assertThat(picked.matches("IMG_2041.jpg", 1_204_000)).isTrue();
    }

    @Test
    public void doesNotMatchAnotherFile() {
        PickedFile picked = new PickedFile(uri, "IMG_2041.jpg", 1_204_000);

        assertThat(picked.matches("pasted.png", 5_000)).isFalse();
    }

    @Test
    public void unknownSizeNeverMatchesBySize() {
        PickedFile picked = new PickedFile(uri, null, -1);

        assertThat(picked.matches("pasted.png", -1)).isFalse();
    }
}
