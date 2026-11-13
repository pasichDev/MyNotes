package com.pasich.mynotes.utils.managers;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Purchase tokens the user has already been thanked for but that are not consumed yet.
 *
 * <p>A donation is thanked as soon as the purchase flow reports it and then consumed. When that
 * consume fails, the purchase comes back the next time billing connects; this record keeps it from
 * being thanked a second time. A token is forgotten once its purchase is consumed, because a
 * consumed purchase never comes back.
 */
final class ThankedPurchases {

    private static final String PREFS = "billing_thanked_purchases";
    private static final String KEY_TOKENS = "tokens";

    private final SharedPreferences prefs;

    ThankedPurchases(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean wasThanked(String purchaseToken) {
        return prefs.getStringSet(KEY_TOKENS, Set.of()).contains(purchaseToken);
    }

    void markThanked(String purchaseToken) {
        Set<String> tokens = tokens();
        if (tokens.add(purchaseToken)) save(tokens);
    }

    /**
     * Keeps only the tokens of purchases that still exist. A purchase refunded or revoked before
     * its consume never comes back to be forgotten.
     */
    void retainOnly(Set<String> ownedTokens) {
        Set<String> tokens = tokens();
        if (tokens.retainAll(ownedTokens)) save(tokens);
    }

    void forget(String purchaseToken) {
        Set<String> tokens = tokens();
        if (tokens.remove(purchaseToken)) save(tokens);
    }

    /** A copy: the set SharedPreferences returns must not be modified. */
    private Set<String> tokens() {
        return new HashSet<>(prefs.getStringSet(KEY_TOKENS, Set.of()));
    }

    private void save(Set<String> tokens) {
        prefs.edit().putStringSet(KEY_TOKENS, tokens).apply();
    }
}
