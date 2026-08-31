package com.pasich.mynotes.utils.auth;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class GoogleCredentialTest {

    @Test
    public void storesIdentityFieldsWithoutModification() {
        GoogleCredential credential =
                new GoogleCredential("account-id", "user@example.com", "User", "token");

        assertThat(credential.getUniqueId()).isEqualTo("account-id");
        assertThat(credential.getEmail()).isEqualTo("user@example.com");
        assertThat(credential.getDisplayName()).isEqualTo("User");
        assertThat(credential.getIdToken()).isEqualTo("token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyIdToken() {
        new GoogleCredential("account-id", "user@example.com", null, "");
    }
}
