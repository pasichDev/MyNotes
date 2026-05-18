package com.pasich.mynotes.utils.file;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.InputStream;

/**
 * SafeImageLoader
 *
 * <p>Safely load large images from disk/URI without OutOfMemory. Can: • read bounds without
 * decoding (to determine size) • scale via inSampleSize • correct EXIF rotations
 */
public class SafeImageLoader {
    public static Bitmap load(Context ctx, File file, int targetW, int targetH) throws Exception {
        Uri uri = Uri.fromFile(file);
        return load(ctx, uri, targetW, targetH);
    }

    /**
     * Main method for loading an image.
     *
     * <p>Steps: 1) Read only the dimensions (inJustDecodeBounds=true) to determine the optimal
     * scale. 2) Calculate inSampleSize so as not to load the photo in full size (saving RAM). 3)
     * Decode the actual image with the desired scaling. 4) Read EXIF and return the image in the
     * correct orientation.
     *
     * @param ctx context
     * @param uri file Uri
     * @param targetW desired width (for scaling)
     * @param targetH desired height (for scaling)
     */
    public static Bitmap load(Context ctx, Uri uri, int targetW, int targetH) throws Exception {

        // read only bounds (dimensions)
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }

        int srcW = bounds.outWidth;
        int srcH = bounds.outHeight;

        // calculate scaling (inSampleSize)
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inSampleSize = calculateInSampleSize(srcW, srcH, targetW, targetH);

        // decoding a real photo
        Bitmap bitmap;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(is, null, opts);
        }

        // EXIF correction (rotation)
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            assert is != null;
            ExifInterface exif = new ExifInterface(is);
            int orientation =
                    exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            bitmap = rotateFromExif(bitmap, orientation);
        }

        return bitmap;
    }

    /**
     * Calculates the optimal inSampleSize.
     *
     * <p>Algorithm: • Divide the dimensions by 2 until they fit into targetW/targetH. •
     * inSampleSize is always a power of two (Android requirement).
     */
    private static int calculateInSampleSize(int srcW, int srcH, int reqW, int reqH) {
        int inSampleSize = 1;

        if (srcH > reqH || srcW > reqW) {
            int halfH = srcH / 2;
            int halfW = srcW / 2;

            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /** Applies EXIF rotation to Bitmap. Supports 90 / 180 / 270 degrees. */
    private static Bitmap rotateFromExif(Bitmap bmp, int orientation) {
        Matrix matrix = new Matrix();

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return bmp;
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }
}
