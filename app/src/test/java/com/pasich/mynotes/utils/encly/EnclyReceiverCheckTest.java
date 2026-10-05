package com.pasich.mynotes.utils.encly;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class EnclyReceiverCheckTest {

    private static final String ACTIVITY = "com.pasich.encly.ImportFromMyNotesActivity";

    /** A certificate this test owns, so the legacy path can be shown to accept a real match. */
    private static final byte[] TEST_CERT = "test certificate".getBytes(StandardCharsets.UTF_8);

    private static final String TEST_PIN = hex(EnclyReceiverCheck.sha256(TEST_CERT));
    private static final byte[] OTHER_CERT = "someone else".getBytes(StandardCharsets.UTF_8);

    private static final class FakeInspector implements EnclyReceiverCheck.Inspector {
        int sdk = 34;
        boolean installed = true;
        String activity = ACTIVITY;
        boolean modernMatch = true;
        List<byte[]> legacy;
        byte[] askedSha;
        String askedAction;
        String askedPackage;

        @Override
        public int sdkInt() {
            return sdk;
        }

        @Override
        public boolean isInstalled(@NonNull String packageName) {
            askedPackage = packageName;
            return installed;
        }

        @Nullable
        @Override
        public String findActivity(@NonNull String packageName, @NonNull String action) {
            askedAction = action;
            return activity;
        }

        @Override
        public boolean hasSigningCertificateSha256(
                @NonNull String packageName, @NonNull byte[] sha256) {
            askedSha = sha256;
            return modernMatch;
        }

        @Nullable
        @Override
        public List<byte[]> legacySignatures(@NonNull String packageName) {
            return legacy;
        }
    }

    private static FakeInspector legacy(int sdk, byte[]... signers) {
        FakeInspector inspector = new FakeInspector();
        inspector.sdk = sdk;
        List<byte[]> list = new ArrayList<>();
        for (byte[] signer : signers) list.add(signer);
        inspector.legacy = list;
        return inspector;
    }

    @Test
    public void notInstalled() {
        FakeInspector inspector = new FakeInspector();
        inspector.installed = false;

        EnclyReceiverCheck.Result result = EnclyReceiverCheck.check(inspector);

        assertThat(result.status).isEqualTo(EnclyReceiverCheck.Status.NOT_INSTALLED);
        assertThat(result.activityClassName).isNull();
        assertThat(inspector.askedPackage).isEqualTo("com.pasich.encly");
    }

    @Test
    public void ready_onApi28Plus_asksForThePinnedEnclyDigestAndTheImportAction() {
        FakeInspector inspector = new FakeInspector();

        EnclyReceiverCheck.Result result = EnclyReceiverCheck.check(inspector);

        assertThat(result.status).isEqualTo(EnclyReceiverCheck.Status.READY);
        assertThat(result.activityClassName).isEqualTo(ACTIVITY);
        assertThat(hex(inspector.askedSha))
                .isEqualTo("6884c693354964276231e6d0336b965f690bf3f15a0707bb59505c208e5e7554");
        assertThat(inspector.askedAction).isEqualTo("com.pasich.encly.action.IMPORT_FROM_MY_NOTES");
    }

    @Test
    public void certificateMismatch_isUntrusted_evenWhenTheActionResolves() {
        FakeInspector inspector = new FakeInspector();
        inspector.modernMatch = false;

        EnclyReceiverCheck.Result result = EnclyReceiverCheck.check(inspector);

        assertThat(result.status).isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);
        assertThat(result.activityClassName).isNull();
    }

    @Test
    public void actionDoesNotResolve_needsUpdate() {
        FakeInspector inspector = new FakeInspector();
        inspector.activity = null;

        EnclyReceiverCheck.Result result = EnclyReceiverCheck.check(inspector);

        assertThat(result.status).isEqualTo(EnclyReceiverCheck.Status.NEEDS_UPDATE);
        assertThat(result.activityClassName).isNull();
    }

    @Test
    public void api27_singleSignerWithThePinnedDigest_isReady() {
        FakeInspector inspector = legacy(27, TEST_CERT);
        inspector.modernMatch = false; // must not be consulted below API 28

        EnclyReceiverCheck.Result result = EnclyReceiverCheck.check(inspector, TEST_PIN);

        assertThat(result.status).isEqualTo(EnclyReceiverCheck.Status.READY);
        assertThat(inspector.askedSha).isNull();
    }

    @Test
    public void api26_singleSignerWithAnotherDigest_isUntrusted() {
        FakeInspector inspector = legacy(26, OTHER_CERT);
        inspector.modernMatch = true; // would wrongly pass if consulted

        assertThat(EnclyReceiverCheck.check(inspector, TEST_PIN).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);
        assertThat(inspector.askedSha).isNull();
    }

    @Test
    public void api27_realPin_rejectsAnUnknownCertificate() {
        assertThat(EnclyReceiverCheck.check(legacy(27, TEST_CERT)).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);
    }

    @Test
    public void api27_moreThanOneSigner_isUntrusted_evenIfOneMatches() {
        FakeInspector inspector = legacy(27, TEST_CERT, OTHER_CERT);

        assertThat(EnclyReceiverCheck.check(inspector, TEST_PIN).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);
    }

    @Test
    public void api27_unreadableOrEmptySignatures_isUntrusted() {
        FakeInspector none = new FakeInspector();
        none.sdk = 27;
        none.legacy = null;
        assertThat(EnclyReceiverCheck.check(none, TEST_PIN).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);

        assertThat(EnclyReceiverCheck.check(legacy(27), TEST_PIN).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);

        assertThat(EnclyReceiverCheck.check(legacy(27, (byte[]) null), TEST_PIN).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);
    }

    @Test
    public void certificateIsCheckedBeforeTheActionIsTrusted() {
        FakeInspector inspector = legacy(27, OTHER_CERT);
        inspector.activity = null;

        // An impostor is reported as untrusted, not as "update Encly".
        assertThat(EnclyReceiverCheck.check(inspector, TEST_PIN).status)
                .isEqualTo(EnclyReceiverCheck.Status.UNTRUSTED);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
