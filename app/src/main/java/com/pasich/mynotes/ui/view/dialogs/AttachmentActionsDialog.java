package com.pasich.mynotes.ui.view.dialogs;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.BottomSheetAttachmentBinding;
import com.pasich.mynotes.utils.noteEditor.attach.AttachmentSecureStorage;
import com.pasich.mynotes.utils.noteEditor.models.EditorAttachment;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class AttachmentActionsDialog {

    public static void show(Context ctx, EditorAttachment attachment) {
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        BottomSheetAttachmentBinding binding =
                BottomSheetAttachmentBinding.inflate(dialog.getLayoutInflater());

        binding.setAttachment(attachment);

        binding.openAction.setOnClickListener(v -> {
            dialog.dismiss();
            openWith(ctx, attachment);
        });

        binding.downloadAction.setOnClickListener(v -> {
            dialog.dismiss();
            saveToDownloads(ctx, attachment);
        });

        dialog.setContentView(binding.getRoot());
        dialog.show();
    }


    private static void openWith(Context ctx, EditorAttachment att) {
        try {
            File decrypted = AttachmentSecureStorage.decryptTemp(ctx, att);
            if (decrypted == null) {
                Toast.makeText(ctx, "Decrypt failed", Toast.LENGTH_SHORT).show();
                return;
            }

            String mime = getMime(att.extension);
            if (mime == null) mime = "*/*";

            var uri = FileProvider.getUriForFile(
                    ctx,
                    ctx.getPackageName() + ".provider",
                    decrypted
            );

            var intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ctx.startActivity(android.content.Intent.createChooser(intent, "Open with"));

        } catch (Exception e) {
            Toast.makeText(ctx, "Can't open file", Toast.LENGTH_SHORT).show();
        }
    }


    private static void saveToDownloads(Context ctx, EditorAttachment att) {
        try {
            File decrypted = AttachmentSecureStorage.decryptTemp(ctx, att);
            if (decrypted == null) {
                Toast.makeText(ctx, "Decrypt failed", Toast.LENGTH_SHORT).show();
                return;
            }

            String mime = getMime(att.extension);
            if (mime == null) mime = "*/*";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, att.name);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            ContentResolver resolver = ctx.getContentResolver();

            Uri item = resolver.insert(collection, values);
            if (item == null) {
                Toast.makeText(ctx, "Save failed", Toast.LENGTH_SHORT).show();
                return;
            }

            try (
                    OutputStream out = resolver.openOutputStream(item);
                    FileInputStream in = new FileInputStream(decrypted)
            ) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    assert out != null;
                    out.write(buffer, 0, len);
                }
            }

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(item, values, null, null);
            AttachmentSecureStorage.cleanupTempAttachments(ctx);
            Toast.makeText(ctx, ctx.getString(R.string.savedDownloads), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(ctx, R.string.savedFailed, Toast.LENGTH_SHORT).show();
        }
    }


    private static String getMime(String ext) {
        return MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext.toLowerCase());
    }
}
