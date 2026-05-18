package com.pasich.mynotes.utils.file;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import java.io.ByteArrayOutputStream;

/**
 * Ultra-lightweight image optimizer for Editor.js attachments.
 *
 * <p>Designed specifically for: - byte[] → byte[] processing (no I/O inside) - very fast path (no
 * resizing, no EXIF parsing, no metadata work)
 *
 * <p>Behavior: - Detects input type by magic bytes (JPEG / PNG / WEBP) - PNG with transparency →
 * stays PNG - PNG without transparency → converted to JPEG (smaller) - JPEG stays JPEG - WEBP stays
 * WEBP (WEBP_LOSSY on Android R+) - UNKNOWN formats (HEIC, GIF, PDF, TXT, ZIP, etc.) → returned
 * unchanged
 *
 * <p>Quality: - extraOptimize = true → strong compression (~60) - extraOptimize = false → normal
 * compression (~85)
 */
public class ImageOptimizer {

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
     * @return optimized image bytes or original bytes on error.
     */
    public static byte[] optimizeRawImage(
            byte[] raw, OutFormat inputFormat, boolean extraOptimize) {
        try {
            // We skip HEIC, GIF, PDF, DOC, ZIP — everything UNKNOWN
            if (inputFormat == OutFormat.UNKNOWN) {
                return raw;
            }

            int quality = extraOptimize ? 60 : 85;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
            if (bitmap == null) return raw;

            OutFormat outFormat;

            // PNG
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
            bitmap.compress(fmt, quality, bos);
            bitmap.recycle();

            return bos.toByteArray();

        } catch (Exception e) {
            return raw; // fallback
        }
    }

    /** Allowed output formats */
    public enum OutFormat {
        JPEG,
        PNG,
        WEBP,
        UNKNOWN
    }
}
