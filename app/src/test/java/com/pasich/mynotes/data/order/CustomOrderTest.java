package com.pasich.mynotes.data.order;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class CustomOrderTest {

    @Test
    public void between_takesTheMidpointOrStepsPastAnEnd() {
        assertThat(CustomOrder.between(4096L, 1024L)).isEqualTo(2560L);
        assertThat(CustomOrder.between(null, 1024L)).isEqualTo(1024L + CustomOrder.STEP);
        assertThat(CustomOrder.between(1024L, null)).isEqualTo(0L);
        assertThat(CustomOrder.between(null, null)).isEqualTo(CustomOrder.STEP);
    }

    @Test
    public void between_asksForARenumberWhenNeighboursTouch() {
        assertThat(CustomOrder.between(1025L, 1024L)).isNull();
        assertThat(CustomOrder.between(1024L, 1024L)).isNull();
        assertThat(CustomOrder.between(1026L, 1024L)).isEqualTo(1025L);
    }

    @Test
    public void renumbered_spreadsTopFirst() {
        assertThat(CustomOrder.renumbered(3))
                .containsExactly(3 * CustomOrder.STEP, 2 * CustomOrder.STEP, CustomOrder.STEP)
                .inOrder();
    }
}
