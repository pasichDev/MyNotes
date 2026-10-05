package com.pasich.mynotes.utils.recycler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * Picks the card a dragged card trades places with, kept free of Android types so it is covered by
 * plain JVM tests.
 *
 * <p>The card under the middle of the dragged card is the one, once that middle has passed the
 * middle of the card toward it. {@code ItemTouchHelper}'s own choice wants the dragged card's edge
 * past the other card's edge: onto the first card of the list that means above the top of the list,
 * so a drop there opened the card's menu instead. Waiting for the middle keeps two cards of
 * different heights from trading places back and forth.
 */
public final class DragTarget {

    private DragTarget() {}

    /** A card's position in the list and its bounds on screen. */
    public record Card(int position, int left, int top, int right, int bottom) {
        int centerX() {
            return (left + right) / 2;
        }

        int centerY() {
            return (top + bottom) / 2;
        }

        boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /**
     * @param slot where the dragged card is laid out (not where it is drawn under the finger).
     * @param centerX the middle of the dragged card as drawn now.
     * @param centerY the middle of the dragged card as drawn now.
     * @param candidates cards it may trade places with.
     * @return the card to trade places with, or null to stay.
     */
    @Nullable
    public static Card choose(
            @NonNull Card slot, int centerX, int centerY, @NonNull List<Card> candidates) {
        Card best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Card card : candidates) {
            if (card.position() == slot.position() || !card.contains(centerX, centerY)) continue;
            int dx = card.centerX() - slot.centerX();
            int dy = card.centerY() - slot.centerY();
            boolean passed =
                    Math.abs(dx) > Math.abs(dy)
                            ? Integer.signum(centerX - card.centerX()) * Integer.signum(dx) >= 0
                            : Integer.signum(centerY - card.centerY()) * Integer.signum(dy) >= 0;
            if (!passed) continue;
            long ddx = centerX - card.centerX();
            long ddy = centerY - card.centerY();
            long distance = ddx * ddx + ddy * ddy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = card;
            }
        }
        return best;
    }
}
