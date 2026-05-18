package com.pasich.mynotes.utils.backup;

import android.util.Base64;
import com.google.gson.Gson;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import java.nio.charset.StandardCharsets;

/**
 * Helper class responsible for encoding and decoding backup data.
 *
 * <p>The backup payload is serialized to JSON, encoded in Base64, and stored as a single string for
 * portability and corruption safety.
 *
 * <p>Decoding includes backward compatibility handling for older JSON formats that may not contain
 * certain fields.
 */
public class ScramblerBackupHelper {

    /**
     * Serializes a {@link JsonBackup} object into JSON and encodes it into Base64.
     *
     * @param jsonBackup The full backup model containing notes, tags, preferences etc.
     * @return Base64-encoded string representing the backup. Returns empty string if encoding
     *     fails.
     */
    public static String encodeString(JsonBackup jsonBackup) {
        try {
            String jsonString = new Gson().toJson(jsonBackup);
            return Base64.encodeToString(
                    jsonString.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Decodes a Base64 backup string and restores it into a {@link JsonBackup} object.
     *
     * <p>This method includes compatibility logic for old backup formats: If the tag model did not
     * contain the "position" field in older backups, the method assigns a default position (-1) for
     * tags that represent normal user tags.
     *
     * @param string Base64-encoded backup string
     * @return Decoded {@link JsonBackup} object, or backup object with error flag if corrupted
     */
    public static JsonBackup decodeString(String string) {
        try {

            byte[] decodedBytes = Base64.decode(string, Base64.DEFAULT);

            String jsonString = new String(decodedBytes, StandardCharsets.UTF_8);

            boolean hasPositionField = jsonString.contains("\"e\":");

            JsonBackup result = new Gson().fromJson(jsonString, JsonBackup.class);

            if (result != null) {
                result.setError(false);

                if (result.getTags() != null && !result.getTags().isEmpty()) {
                    for (int i = 0; i < result.getTags().size(); i++) {
                        Tag tag = result.getTags().get(i);
                        if (!hasPositionField && tag.getSystemAction() == 0) {
                            tag.setPosition(-1);
                        }
                    }
                }
            } else {
                return new JsonBackup().error();
            }

            return result;
        } catch (Exception e) {
            return new JsonBackup().error();
        }
    }
}
