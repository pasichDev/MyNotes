package com.pasich.mynotes.utils.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class ImageOptimizerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void sampleSize_leavesOrdinaryPhotosAlone() {
        assertEquals(1, ImageOptimizer.sampleSizeFor(4000, 3000, 4096));
        assertEquals(1, ImageOptimizer.sampleSizeFor(4096, 100, 4096));
    }

    @Test
    public void sampleSize_bringsHugePhotosUnderTheLimit() {
        // 48 MP
        assertEquals(2, ImageOptimizer.sampleSizeFor(8000, 6000, 4096));
        // 108 MP
        assertEquals(4, ImageOptimizer.sampleSizeFor(12000, 9000, 4096));
        assertEquals(2, ImageOptimizer.sampleSizeFor(6000, 8192, 4096));
        assertEquals(4, ImageOptimizer.sampleSizeFor(6000, 8193, 4096));
    }

    @Test
    public void orientationMatrix_isNullOnlyForUprightPictures() {
        assertNull(ImageOptimizer.orientationMatrix(ExifInterface.ORIENTATION_NORMAL));
        assertNull(ImageOptimizer.orientationMatrix(ExifInterface.ORIENTATION_UNDEFINED));
        assertNotNull(ImageOptimizer.orientationMatrix(ExifInterface.ORIENTATION_ROTATE_90));
        assertNotNull(ImageOptimizer.orientationMatrix(ExifInterface.ORIENTATION_TRANSVERSE));
    }

    @Test
    public void rotatedJpeg_isStoredUpright() throws Exception {
        byte[] raw = jpeg(40, 20, ExifInterface.ORIENTATION_ROTATE_90);

        byte[] out = ImageOptimizer.optimizeRawImage(raw, ImageOptimizer.OutFormat.JPEG, false);

        BitmapFactory.Options bounds = decodeBounds(out);
        assertEquals(20, bounds.outWidth);
        assertEquals(40, bounds.outHeight);
    }

    @Test
    public void uprightJpeg_keepsItsSize() throws Exception {
        byte[] raw = jpeg(40, 20, ExifInterface.ORIENTATION_NORMAL);

        byte[] out = ImageOptimizer.optimizeRawImage(raw, ImageOptimizer.OutFormat.JPEG, true);

        BitmapFactory.Options bounds = decodeBounds(out);
        assertEquals(40, bounds.outWidth);
        assertEquals(20, bounds.outHeight);
    }

    @Test
    public void undecodableBytes_comeBackUnchanged() {
        byte[] raw = new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

        assertEquals(
                raw, ImageOptimizer.optimizeRawImage(raw, ImageOptimizer.OutFormat.JPEG, false));
    }

    private byte[] jpeg(int width, int height, int orientation) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
        File file = tmp.newFile("photo.jpg");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bos.toByteArray());
        }
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
        exif.saveAttributes();
        return Files.readAllBytes(file.toPath());
    }

    private static BitmapFactory.Options decodeBounds(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        return bounds;
    }
}
