package com.pasich.mynotes.utils.backup;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Scope;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

public class CloudCacheHelper {
    private static final String TAG = "CloudCacheHelper";

    private volatile GoogleSignInAccount googleSignInAccount;
    private volatile boolean isHasPermissionDrive;
    private volatile boolean isAuth;
    private volatile boolean isInstallPlayMarket;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private CompletableFuture<Void> initializationFuture;

    @Inject
    public CloudCacheHelper() {
        this.isAuth = false;
        this.isInstallPlayMarket = true;
    }

    /**
     * Асинхронна ініціалізація Google Services
     */
    public CompletableFuture<Void> initializeAsync(Context context, Scope accessDrive, boolean isPlayMarketInstall) {
        if (isInitialized.get()) {
            return CompletableFuture.completedFuture(null);
        }

        if (initializationFuture != null) {
            return initializationFuture;
        }

        synchronized (this) {
            if (initializationFuture == null) {
                initializationFuture = CompletableFuture.runAsync(() -> {
                    try {
                        Log.d(TAG, "Starting async Google Services initialization");
                        
                        if (isPlayMarketInstall) {
                            // Виконуємо блокуючі операції в background thread
                            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
                            if (account != null) {
                                boolean hasPermissions = GoogleSignIn.hasPermissions(account, accessDrive);
                                
                                // Оновлюємо стан атомарно
                                synchronized (CloudCacheHelper.this) {
                                    this.googleSignInAccount = account;
                                    this.isHasPermissionDrive = hasPermissions;
                                    this.isAuth = true;
                                    this.isInstallPlayMarket = true;
                                }
                                
                                Log.d(TAG, "Google Services initialized successfully with account");
                            } else {
                                synchronized (CloudCacheHelper.this) {
                                    this.googleSignInAccount = null;
                                    this.isHasPermissionDrive = false;
                                    this.isAuth = false;
                                    this.isInstallPlayMarket = true;
                                }
                                
                                Log.d(TAG, "Google Services initialized without account");
                            }
                        } else {
                            playMarketNoInstall();
                            Log.d(TAG, "Google Services initialized - Play Market not installed");
                        }
                        
                        isInitialized.set(true);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error during async Google Services initialization", e);
                        // Fallback до безпечного стану
                        playMarketNoInstall();
                        isInitialized.set(true);
                    }
                });
            }
        }

        return initializationFuture;
    }

    /**
     * Перевіряє, чи завершена ініціалізація
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }

    /**
     * Блокуючий метод для отримання результату ініціалізації
     * Використовувати тільки коли необхідно
     */
    public void waitForInitialization() {
        if (initializationFuture != null && !isInitialized.get()) {
            try {
                initializationFuture.join(); // Блокує поточний thread
            } catch (Exception e) {
                Log.e(TAG, "Error waiting for initialization", e);
            }
        }
    }

    public CloudCacheHelper build(GoogleSignInAccount account, boolean isHasPermissionDrive) {
        synchronized (this) {
            this.googleSignInAccount = account;
            this.isHasPermissionDrive = isHasPermissionDrive;
            this.isAuth = true;
            this.isInstallPlayMarket = true;
            this.isInitialized.set(true);
        }
        return this;
    }

    public void update(GoogleSignInAccount account, boolean isHasPermissionDrive, boolean isAuth) {
        synchronized (this) {
            this.googleSignInAccount = account;
            this.isHasPermissionDrive = isHasPermissionDrive;
            this.isAuth = isAuth;
        }
    }

    public void playMarketNoInstall() {
        synchronized (this) {
            this.googleSignInAccount = null;
            this.isHasPermissionDrive = false;
            this.isAuth = false;
            this.isInstallPlayMarket = false;
            this.isInitialized.set(true);
        }
    }

    public void clean() {
        synchronized (this) {
            this.googleSignInAccount = null;
            this.isHasPermissionDrive = false;
            this.isAuth = false;
        }
    }

    // Thread-safe getters
    public GoogleSignInAccount getGoogleSignInAccount() {
        return googleSignInAccount;
    }

    public boolean isHasPermissionDrive() {
        return isHasPermissionDrive;
    }

    public void setHasPermissionDrive(boolean hasPermissionDrive) {
        synchronized (this) {
            this.isHasPermissionDrive = hasPermissionDrive;
        }
    }

    public boolean isAuth() {
        return isAuth;
    }

    public boolean isInstallPlayMarket() {
        return isInstallPlayMarket;
    }

}
