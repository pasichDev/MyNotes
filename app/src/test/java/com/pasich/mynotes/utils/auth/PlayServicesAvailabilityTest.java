package com.pasich.mynotes.utils.auth;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Whether sync is offered at all on a given device.
 *
 * <p>Every branch here decides whether the account tab shows controls or a notice, and only the
 * happy one ever runs on a development device — which is why they are pinned rather than trusted.
 */
public class PlayServicesAvailabilityTest {

    @Test
    public void offersSyncWhenPlayServicesAreInstalledAndEnabled() {
        assertThat(PlayServicesAvailability.isAvailable(packageName -> Boolean.TRUE)).isTrue();
    }

    @Test
    public void withholdsSyncWhenPlayServicesAreNotInstalled() {
        assertThat(PlayServicesAvailability.isAvailable(packageName -> null)).isFalse();
    }

    @Test
    public void withholdsSyncWhenPlayServicesAreInstalledButDisabled() {
        // A user can disable the package in system settings; the APIs then fail the same way as
        // on a device that never had it.
        assertThat(PlayServicesAvailability.isAvailable(packageName -> Boolean.FALSE)).isFalse();
    }

    @Test
    public void withholdsSyncRatherThanCrashingWhenThePackageTableRefusesToAnswer() {
        // A dead package manager throws from a binder call. Sync hiding itself is recoverable;
        // taking the backup screen down with it is not.
        assertThat(
                        PlayServicesAvailability.isAvailable(
                                packageName -> {
                                    throw new IllegalStateException("package manager is dead");
                                }))
                .isFalse();
    }

    @Test
    public void asksAboutThePlayServicesPackage() {
        String[] asked = new String[1];
        PlayServicesAvailability.isAvailable(
                packageName -> {
                    asked[0] = packageName;
                    return Boolean.TRUE;
                });

        // The manifest declares this exact package under <queries>; a mismatch would make the
        // lookup report "absent" on every API 30+ device.
        assertThat(asked[0]).isEqualTo("com.google.android.gms");
        assertThat(asked[0]).isEqualTo(PlayServicesAvailability.PLAY_SERVICES_PACKAGE);
    }
}
