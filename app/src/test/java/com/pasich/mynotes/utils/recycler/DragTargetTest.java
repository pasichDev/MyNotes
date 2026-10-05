package com.pasich.mynotes.utils.recycler;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.utils.recycler.DragTarget.Card;
import java.util.List;
import org.junit.Test;

public class DragTargetTest {

    // A list: card 0 is short and at the very top, card 1 is tall.
    private final Card first = new Card(0, 0, 0, 400, 100);
    private final Card tall = new Card(1, 0, 100, 400, 400);
    private final Card dragged = new Card(2, 0, 400, 400, 500);

    @Test
    public void ontoTheFirstCard_onceItsMiddleIsPassed() {
        // The dragged card's middle over the top half of the first card: no need to push the
        // card above the top of the list.
        assertThat(DragTarget.choose(new Card(1, 0, 100, 400, 200), 200, 40, List.of(first)))
                .isEqualTo(first);
        assertThat(DragTarget.choose(new Card(1, 0, 100, 400, 200), 200, 70, List.of(first)))
                .isNull();
    }

    @Test
    public void notBeforeTheMiddleHasPassedTheOtherCardsMiddle() {
        assertThat(DragTarget.choose(dragged, 200, 300, List.of(first, tall))).isNull();
        assertThat(DragTarget.choose(dragged, 200, 240, List.of(first, tall))).isEqualTo(tall);
    }

    @Test
    public void afterTradingPlaces_theCardsDoNotTradeBack() {
        // The short card was dragged down past the middle of the tall one, and they traded.
        Card tallNow = new Card(0, 0, 0, 400, 300);
        Card slotNow = new Card(1, 0, 300, 400, 400);
        assertThat(DragTarget.choose(slotNow, 200, 260, List.of(tallNow))).isNull();
    }

    @Test
    public void inTheGrid_sideways() {
        Card left = new Card(0, 0, 0, 200, 300);
        Card right = new Card(1, 200, 0, 400, 150);
        assertThat(DragTarget.choose(left, 290, 100, List.of(right))).isNull();
        assertThat(DragTarget.choose(left, 310, 100, List.of(right))).isEqualTo(right);
    }

    @Test
    public void nothingUnderTheMiddle_staysPut() {
        assertThat(DragTarget.choose(dragged, 200, 450, List.of(first, tall))).isNull();
    }
}
