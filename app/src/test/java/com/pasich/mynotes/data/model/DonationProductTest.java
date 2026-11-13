package com.pasich.mynotes.data.model;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DonationProductTest {

    private static DonationProduct product(String id, String price, long micros) {
        return new DonationProduct(id, id, "", price, "ic_heart", micros);
    }

    private static List<String> idsSortedByPrice(DonationProduct... products) {
        List<DonationProduct> list = new ArrayList<>(List.of(products));
        list.sort(DonationProduct.BY_PRICE);
        List<String> ids = new ArrayList<>();
        for (DonationProduct p : list) ids.add(p.getId());
        return ids;
    }

    /** A thousands separator read as a decimal point put ¥1,000 before ¥160. */
    @Test
    public void byPrice_ordersByAmountNotByTheFormattedString() {
        assertThat(
                        idsSortedByPrice(
                                product("thousand", "¥1,000", 1_000_000_000L),
                                product("five_thousand", "¥5,000", 5_000_000_000L),
                                product("hundred_sixty", "¥160", 160_000_000L)))
                .containsExactly("hundred_sixty", "thousand", "five_thousand")
                .inOrder();
    }

    @Test
    public void byPrice_handlesCommaDecimals() {
        assertThat(
                        idsSortedByPrice(
                                product("big", "200,00 UAH", 200_000_000L),
                                product("small", "40,00 UAH", 40_000_000L)))
                .containsExactly("small", "big")
                .inOrder();
    }

    @Test
    public void byPrice_putsProductsWithoutAPriceLast() {
        assertThat(
                        idsSortedByPrice(
                                product("unknown", "N/A", DonationProduct.NO_PRICE),
                                product("priced", "₴40.00", 40_000_000L)))
                .containsExactly("priced", "unknown")
                .inOrder();
    }
}
