package com.pasich.mynotes.utils.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Authentication result from Google Credential Manager. The ID token must not be persisted. */
public final class GoogleCredential {

    private final String uniqueId;
    private final String email;
    private final String displayName;
    private final String idToken;

    public GoogleCredential(
            @NonNull String uniqueId,
            @NonNull String email,
            @Nullable String displayName,
            @NonNull String idToken) {
        if (uniqueId.isEmpty() || email.isEmpty() || idToken.isEmpty()) {
            throw new IllegalArgumentException("Google credential fields must not be empty");
        }
        this.uniqueId = uniqueId;
        this.email = email;
        this.displayName = displayName;
        this.idToken = idToken;
    }

    @NonNull
    public String getUniqueId() {
        return uniqueId;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    /** Returns a short-lived token for server-side verification; callers must not persist it. */
    @NonNull
    public String getIdToken() {
        return idToken;
    }
}
