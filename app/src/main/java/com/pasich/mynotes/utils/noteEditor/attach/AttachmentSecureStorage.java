package com.pasich.mynotes.utils.noteEditor.attach;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
public class AttachmentSecureStorage {

    private static final String TAG = "AttachmentSecureStorage";
    private final File baseDir;
    private final byte[] key;

    public AttachmentSecureStorage(Context ctx) {
        this.baseDir = new File(ctx.getFilesDir(), AttachmentsConst.ATTACH_DIR);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            Log.e(TAG, "Failed to create base attach dir");
        }
        key = SecureKeyProvider.getKey();
    }

    private File getNoteFolder(int noteId) {
        File dir = new File(baseDir, "note_" + noteId);
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

    public File saveEncrypted(int noteId, String originalName, byte[] raw) {
        try {
            File noteFolder = getNoteFolder(noteId);

            String ext = "";
            int dot = originalName.lastIndexOf('.');
            if (dot != -1) ext = originalName.substring(dot);

            String baseName = originalName.replace(ext, "").replace(" ", "_");

            long ts = System.currentTimeMillis();
            String md5 = md5(raw);

            String finalName = baseName + "_" + ts + "_" + md5 + ext + ".enc";

            File out = new File(noteFolder, finalName);

            byte[] encData = encrypt(raw);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(encData);
            }

            Log.d(TAG, "Saved encrypted: " + out.getAbsolutePath());
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

    private static String md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(data));
        } catch (Exception ignored) { return "md5"; }
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
