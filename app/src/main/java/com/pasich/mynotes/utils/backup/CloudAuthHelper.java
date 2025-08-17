package com.pasich.mynotes.utils.backup;

import static com.pasich.mynotes.utils.constants.DriveScope.APPLICATION_NAME;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;

import java.util.Collections;

import javax.inject.Inject;

public class CloudAuthHelper {

    @Inject
    public CloudAuthHelper() {

    }


    public Task<GoogleSignInAccount> getResultAuth(Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            task.getResult(ApiException.class);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        return task;
    }


    public Drive getDriveCredentialService(@Nullable Account mAccount, Context mContext) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), GoogleAccountCredential.usingOAuth2(mContext, Collections.singleton(Scopes.DRIVE_APPFOLDER)).setSelectedAccount(mAccount)).setApplicationName(APPLICATION_NAME).build();
    }

}
