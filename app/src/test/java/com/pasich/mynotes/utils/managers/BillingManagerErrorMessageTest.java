package com.pasich.mynotes.utils.managers;

import static com.google.common.truth.Truth.assertThat;

import com.android.billingclient.api.BillingClient.BillingResponseCode;
import org.junit.Test;

public class BillingManagerErrorMessageTest {

    /**
     * Since Play Billing 9 a Play Store blocked by the system (an OEM kids mode, for example)
     * answers BILLING_UNAVAILABLE, so the message must say billing is unavailable here.
     */
    @Test
    public void billingUnavailable_saysBillingIsUnavailableOnThisDevice() {
        assertThat(BillingManager.getBillingErrorMessage(BillingResponseCode.BILLING_UNAVAILABLE))
                .isEqualTo("Google Play billing is not available on this device");
    }

    @Test
    public void userCanceled_keepsItsMessage() {
        assertThat(BillingManager.getBillingErrorMessage(BillingResponseCode.USER_CANCELED))
                .isEqualTo("User pressed back or canceled a dialog");
    }
}
