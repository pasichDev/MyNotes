package com.pasich.mynotes.utils.changelog;

import android.content.Context;
import android.util.Log;
import com.pasich.mynotes.R;
import com.pasich.mynotes.utils.UpdateChecker;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChangelogManager {

    private final Context context;
    private final UpdateChecker updateChecker;

    @Inject
    public ChangelogManager(@ApplicationContext Context context, UpdateChecker updateChecker) {
        this.context = context;
        this.updateChecker = updateChecker;
    }

    /** Read the entire changelog.md from raw */
    public String readRawChangelog() {
        try (InputStream inputStream = context.getResources().openRawResource(R.raw.changelog);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder content = new StringBuilder();
            String line;

            boolean skipHeader = true;

            while ((line = reader.readLine()) != null) {

                // Skip ‘# CHANGELOG’ and all empty lines after it
                if (skipHeader) {
                    if (line.trim().equalsIgnoreCase("# CHANGELOG") || line.trim().isEmpty()) {
                        continue;
                    } else {
                        skipHeader = false;
                    }
                }

                content.append(line).append("\n");
            }

            return content.toString();

        } catch (IOException e) {
            Log.e("ChangelogManager", "Failed to read changelog", e);
            return "";
        }
    }

    /** Extracts changelog only for the current version */
    public String getChangelogForCurrentVersion() {
        String version = updateChecker.getCurrentAppVersion();
        String fullText = readRawChangelog();

        String tag = "## [" + version + "]";
        int start = fullText.indexOf(tag);

        if (start == -1) {
            return "";
        }

        int nextHeader = fullText.indexOf("## [", start + tag.length());

        if (nextHeader == -1) {
            return fullText.substring(start).trim();
        }

        return fullText.substring(start, nextHeader).trim();
    }

    /** Mark changelog as read via UpdateChecker */
    public void markChangelogRead() {
        updateChecker.markVersionAsRead();
    }
}
