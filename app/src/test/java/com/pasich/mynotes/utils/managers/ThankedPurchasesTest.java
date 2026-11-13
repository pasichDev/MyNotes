package com.pasich.mynotes.utils.managers;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ThankedPurchasesTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void aNewPurchaseWasNotThanked() {
        assertThat(new ThankedPurchases(context).wasThanked("token-a")).isFalse();
    }

    /**
     * A purchase thanked in the purchase flow whose consume then failed comes back on the next
     * start; it must still be known as thanked there, in a new instance.
     */
    @Test
    public void aThankedPurchaseIsRememberedAcrossInstances() {
        new ThankedPurchases(context).markThanked("token-a");

        ThankedPurchases later = new ThankedPurchases(context);
        assertThat(later.wasThanked("token-a")).isTrue();
        assertThat(later.wasThanked("token-b")).isFalse();
    }

    /** A consumed purchase never comes back, so its token is dropped instead of piling up. */
    @Test
    public void forgetDropsTheToken() {
        ThankedPurchases thanked = new ThankedPurchases(context);
        thanked.markThanked("token-a");
        thanked.markThanked("token-b");

        thanked.forget("token-a");

        ThankedPurchases later = new ThankedPurchases(context);
        assertThat(later.wasThanked("token-a")).isFalse();
        assertThat(later.wasThanked("token-b")).isTrue();
    }
}
