package com.pasich.mynotes.utils.backup;


import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import javax.inject.Inject;

public class CloudCacheHelper {

    private GoogleSignInAccount googleSignInAccount;
    private boolean isHasPermissionDrive;
    private boolean isAuth;
    private boolean isInstallPlayMarket;

    @Inject
    public CloudCacheHelper() {
        this.isAuth = false;
        this.isInstallPlayMarket = true;
    }

    public CloudCacheHelper build(GoogleSignInAccount account, boolean isHasPermissionDrive) {
        this.googleSignInAccount = account;
        this.isHasPermissionDrive = isHasPermissionDrive;
        this.isAuth = true;
        this.isInstallPlayMarket = true;
        return this;
    }

    public void update(GoogleSignInAccount account, boolean isHasPermissionDrive, boolean isAuth) {
        this.googleSignInAccount = account;
        this.isHasPermissionDrive = isHasPermissionDrive;
        this.isAuth = isAuth;
    }

    public CloudCacheHelper playMarketNoInstall() {
        this.googleSignInAccount = null;
        this.isHasPermissionDrive = false;
        this.isAuth = false;
        this.isInstallPlayMarket = false;
        return this;
    }

    public void clean() {
        this.googleSignInAccount = null;
        this.isHasPermissionDrive = false;
        this.isAuth = false;
    }

    public GoogleSignInAccount getGoogleSignInAccount() {
        return googleSignInAccount;
    }


    public boolean isHasPermissionDrive() {
        return isHasPermissionDrive;
    }

    public void setHasPermissionDrive(boolean hasPermissionDrive) {
        isHasPermissionDrive = hasPermissionDrive;
    }

    public boolean isAuth() {
        return isAuth;
    }

    public boolean isInstallPlayMarket() {
        return isInstallPlayMarket;
    }

}
