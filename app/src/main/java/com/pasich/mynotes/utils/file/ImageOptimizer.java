package com.pasich.mynotes.utils.file;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Lightweight image optimizer for Editor.js attachments.
 *
 * <p>Designed for byte[] → byte[] processing with no I/O inside.
 *
 * <p>Behavior: - Detects input type by magic bytes (JPEG / PNG / WEBP) - PNG with transparency →
 * stays PNG - PNG without transparency → converted to JPEG (smaller) - JPEG stays JPEG - WEBP stays
 * WEBP (WEBP_LOSSY on Android R+) - UNKNOWN formats (HEIC, GIF, PDF, TXT, ZIP, etc.) → returned
 * unchanged
 *
 * <p>A photo longer than {@link #MAX_DIMENSION} on its long side is decoded subsampled, and the
 * EXIF orientation is applied to the pixels, since re-encoding drops the EXIF block that told a
 * viewer to turn the picture upright.
 *
 * <p>Quality: - extraOptimize = true → strong compression (~60) - extraOptimize = false → normal
 * compression (~85)
 */
public class ImageOptimizer {

    private static final String TAG = "ImageOptimizer";

    /**
     * The longest side a decoded image may have.
     *
     * <p>Decoding a 48–108 MP camera photo at full size takes 190–430 MB as ARGB_8888, more than
     * the heap allows. An ordinary 12 MP photo (4000 px) still decodes at full size.
     */
    static final int MAX_DIMENSION = 4096;

    /** Detect format by magic bytes. */
    public static OutFormat detectFormat(byte[] data) {
        if (data == null || data.length < 12) return OutFormat.UNKNOWN;

        // JPEG: FF D8
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return OutFormat.JPEG;

        // PNG
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47)
            return OutFormat.PNG;

        // WEBP: RIFFxxxxWEBP
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[8] == 'W' && data[9] == 'E')
            return OutFormat.WEBP;

        return OutFormat.UNKNOWN;
    }

    /**
     * Optimizes raw byte[] image data.
     *
     * <p>- extraOptimize = true → lower quality (≈60) - extraOptimize = false → higher quality
     * (≈85)
     *
     * <p>PNG with alpha → stays PNG. PNG without alpha → JPEG. JPEG stays JPEG. WEBP stays WEBP
     * (lossy if Android R+).
     *
     * @return optimized image bytes, or the original bytes on any error, running out of memory
     *     included.
     */
    public static byte[] optimizeRawImage(
            byte[] raw, OutFormat inputFormat, boolean extraOptimize) {
        Bitmap bitmap = null;
        try {
            // We skip HEIC, GIF, PDF, DOC, ZIP — everything UNKNOWN
            if (inputFormat == OutFormat.UNKNOWN) {
                return raw;
            }

            int quality = extraOptimize ? 60 : 85;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);

            bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
            if (bitmap == null) return raw;

            Matrix orientation = orientationMatrix(readOrientation(raw));
            if (orientation != null) {
                Bitmap upright =
                        Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                bitmap.getWidth(),
                                bitmap.getHeight(),
                                orientation,
                                true);
                if (upright != bitmap) {
                    bitmap.recycle();
                    bitmap = upright;
                }
            }

            OutFormat outFormat;

            if (inputFormat == OutFormat.PNG) {
                if (bitmap.hasAlpha()) {
                    outFormat = OutFormat.PNG;
                } else {
                    outFormat = OutFormat.JPEG;
                }
            } else {
                // JPEG → JPEG, WEBP → WEBP
                outFormat = inputFormat;
            }

            Bitmap.CompressFormat fmt;

            switch (outFormat) {
                case PNG -> fmt = Bitmap.CompressFormat.PNG;

                case WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        fmt = Bitmap.CompressFormat.WEBP_LOSSY;
                    } else {
                        fmt = Bitmap.CompressFormat.WEBP;
                    }
                }

                case JPEG -> fmt = Bitmap.CompressFormat.JPEG;

                default -> {
                    return raw; // safety fallback
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!bitmap.compress(fmt, quality, bos)) return raw;

            return bos.toByteArray();

        } catch (Exception | OutOfMemoryError e) {
            // Running out of memory is recoverable here: the bitmap is dropped and the original
            // bytes, which are already in memory, are stored as they came.
            Log.w(TAG, "Keeping the original image; it could not be optimized", e);
            return raw;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    /**
     * The smallest power-of-two subsample (the only kind the decoder honours) that brings the long
     * side to at most {@code maxDimension}.
     */
    @VisibleForTesting
    static int sampleSizeFor(int width, int height, int maxDimension) {
        int longSide = Math.max(width, height);
        int sample = 1;
        while ((longSide + sample - 1) / sample > maxDimension) {
            sample *= 2;
        }
        return sample;
    }

    private static int readOrientation(@NonNull byte[] raw) {
        try {
            return new ExifInterface(new ByteArrayInputStream(raw))
                    .getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    /**
     * The transform that turns a picture stored with this EXIF orientation upright, or {@code null}
     * when it already is.
     */
    @VisibleForTesting
    @Nullable
    static Matrix orientationMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1, 1);
            case ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1, -1);
            case ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
            }
            case ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90);
            case ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
            }
            case ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90);
            default -> {
                return null;
            }
        }
        return matrix;
    }

    /** Allowed output formats */
    public enum OutFormat {
        JPEG,
        PNG,
        WEBP,
        UNKNOWN
    }
}
