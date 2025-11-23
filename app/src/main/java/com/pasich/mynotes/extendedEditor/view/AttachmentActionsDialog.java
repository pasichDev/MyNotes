package com.pasich.mynotes.extendedEditor.view;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.BottomSheetAttachmentBinding;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class AttachmentActionsDialog {

    public static void show(
            Context ctx,
            EditorAttachment attachment,
            OnAttachmentDeleteListener deleteListener
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        BottomSheetAttachmentBinding binding =
                BottomSheetAttachmentBinding.inflate(dialog.getLayoutInflater());

        binding.setAttachment(attachment);

        // Check if the file exists
        File file = AttachmentStorage.read(ctx, attachment);
        boolean exists = file != null && file.exists();

        if (!exists) {
            //  FILE NOT FOUND → show error
            binding.previewItem.itemPreview.setVisibility(View.GONE);
            binding.errorFile.setText(ctx.getString(R.string.attachment_file_unavailable));

            binding.openAction.setVisibility(View.GONE);
            binding.errorCard.setVisibility(View.VISIBLE);
            binding.downloadAction.setVisibility(View.GONE);
            binding.deleteAttach.setBackground(ctx.getDrawable(R.drawable.bg_item_full));
        } else {
            // File exists → show everything
            binding.openAction.setOnClickListener(v -> {
                dialog.dismiss();
                openWith(ctx, attachment);
            });

            binding.downloadAction.setOnClickListener(v -> {
                dialog.dismiss();
                saveToDownloads(ctx, attachment);
            });
        }

        // DELETE — always visible
        binding.deleteAttach.setOnClickListener(v -> {
            dialog.dismiss();

            if (exists) {
                // Showing confirmation
                showConfirmDelete(ctx, attachment, deleteListener);
            } else {
                // File does not exist — simply delete without dialog
                deleteListener.onDeleteAttachment(attachment, true);
            }
        });

        dialog.setContentView(binding.getRoot());
        dialog.show();
    }


    private static void showConfirmDelete(Context ctx,
                                          EditorAttachment att,
                                          OnAttachmentDeleteListener listener) {

        new MaterialAlertDialogBuilder(
                ctx,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
                .setTitle(R.string.dialog_delete_title)
                .setMessage(R.string.dialog_delete_message)
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    listener.onDeleteAttachment(att, false);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_delete_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }


    private static void openWith(Context ctx, EditorAttachment att) {
        try {
            File mFile = AttachmentStorage.read(ctx, att);
            if (mFile == null) {
                Toast.makeText(ctx, ctx.getString(R.string.attachment_load_failed), Toast.LENGTH_SHORT).show();
                return;
            }

            String mime = getMime(att.extension);
            if (mime == null) mime = "*/*";

            var uri = FileProvider.getUriForFile(
                    ctx,
                    ctx.getPackageName() + ".provider",
                    mFile
            );

            var intent = new Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.attachment_open_with)));

        } catch (Exception e) {
            Toast.makeText(ctx, ctx.getString(R.string.attachment_open_failed), Toast.LENGTH_SHORT).show();
        }
    }


    private static void saveToDownloads(Context ctx, EditorAttachment att) {
        try {
            File file = AttachmentStorage.read(ctx, att);
            if (file == null) {
                Toast.makeText(ctx, ctx.getString(R.string.attachment_save_failed), Toast.LENGTH_SHORT).show();
                return;
            }

            String mime = getMime(att.extension);
            if (mime == null) mime = "*/*";

            ContentResolver resolver = ctx.getContentResolver();

            Uri collection;

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                // API 26–28 fallback → write to Files
                collection = MediaStore.Files.getContentUri("external");
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, att.name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }

            Uri item = resolver.insert(collection, values);
            if (item == null) {
                Toast.makeText(ctx, "Save failed", Toast.LENGTH_SHORT).show();
                return;
            }

            try (OutputStream out = resolver.openOutputStream(item);
                 FileInputStream in = new FileInputStream(file)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    assert out != null;
                    out.write(buffer, 0, len);
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(item, values, null, null);
            }

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
