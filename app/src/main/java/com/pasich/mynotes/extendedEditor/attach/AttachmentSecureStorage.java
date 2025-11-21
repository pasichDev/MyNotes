package com.pasich.mynotes.extendedEditor.attach;

import android.content.Context;
import android.util.Log;

import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AttachmentSecureStorage {

    private static final String TAG = "AttachmentSecureStorage";

    private final byte[] key;

    public AttachmentSecureStorage() {
        key = SecureKeyProvider.getKey();
    }

    private File getBaseDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), AttachmentsConst.ATTACH_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create base attach dir");
        }
        return dir;
    }

    private File getNoteFolder(Context ctx, int noteId) {
        File dir = new File(getBaseDir(ctx), "note_" + noteId);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create note folder!");
        }
        return dir;
    }

    private byte[] encrypt(byte[] raw) throws Exception {
        if (key == null) return raw;

        SecretKeySpec sk = new SecretKeySpec(key, "AES");
        byte[] iv = Arrays.copyOfRange(key, 0, 16);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, sk, ivSpec);

        return c.doFinal(raw);
    }

    private byte[] decrypt(byte[] enc) throws Exception {
        if (key == null) return enc;

        SecretKeySpec sk = new SecretKeySpec(key, "AES");
        byte[] iv = Arrays.copyOfRange(key, 0, 16);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, sk, ivSpec);

        return c.doFinal(enc);
    }

    public File saveEncrypted(Context ctx, int noteId, String originalName, byte[] raw) {
        try {
            File noteFolder = getNoteFolder(ctx, noteId);

            String ext = "";
            int dot = originalName.lastIndexOf('.');
            if (dot != -1) {
                ext = originalName.substring(dot);
            }

            byte[] tsBytes = String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[raw.length + tsBytes.length];

            System.arraycopy(raw, 0, combined, 0, raw.length);
            System.arraycopy(tsBytes, 0, combined, raw.length, tsBytes.length);

            String hash = md5(combined);


            String finalName = hash + ext + ".enc";

            File out = new File(noteFolder, finalName);

            byte[] encData = encrypt(raw);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(encData);
            }

            return out;

        } catch (Exception e) {
            Log.e(TAG, "Save error", e);
            return null;
        }
    }


    public byte[] loadDecrypted(File file) {
        try {
            byte[] enc = Files.readAllBytes(file.toPath());
            return decrypt(enc);

        } catch (Exception e) {
            Log.e(TAG, "Decrypt error", e);
            return null;
        }
    }

    public static File decryptTemp(Context ctx, EditorAttachment att) {
        try {
            AttachmentSecureStorage storage = new AttachmentSecureStorage();

            File encrypted = resolveFilePath(ctx, att);
            if (encrypted == null) return null;

            byte[] raw = storage.loadDecrypted(encrypted);
            if (raw == null) return null;

            File tempDir = new File(ctx.getCacheDir(), AttachmentsConst.ATTACH_TEMP);
            if (!tempDir.exists()) tempDir.mkdirs();

            String clean = encrypted.getName().replace(".enc", "");
            File out = new File(tempDir, clean);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(raw);
            }

            return out;

        } catch (Exception e) {
            return null;
        }
    }

    private static File resolveFilePath(Context ctx, EditorAttachment att) {
        try {
            var uri = android.net.Uri.parse(att.url);
            List<String> seg = uri.getPathSegments();

            if (seg.size() < 2) return null;

            String folder = seg.get(0);
            String name = seg.get(1);

            File base = new File(ctx.getFilesDir(), AttachmentsConst.ATTACH_DIR);
            File noteFolder = new File(base, folder);

            return new File(noteFolder, name);

        } catch (Exception e) {
            return null;
        }
    }


    public static void cleanupTempAttachments(Context ctx) {
        File tempDir = new File(ctx.getCacheDir(), AttachmentsConst.ATTACH_TEMP);

        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }

    private static String md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(data));
        } catch (Exception ignored) {
            return "md5";
        }
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
