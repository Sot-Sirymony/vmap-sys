package com.visionmapping.repository;

import java.math.BigDecimal;

/**
 * What a parent needs to know about its unarchived children to recalculate
 * itself: how many there are, what their progress adds up to, and how many are
 * complete. Three numbers instead of every child row, because the roll-up runs
 * on every task and step write.
 *
 * <p>The average is left to the caller rather than computed as SQL AVG so the
 * rounding stays exactly where it was — one BigDecimal division at scale 2,
 * HALF_UP — instead of moving into the database's numeric rules.
 */
public record ProgressRollup(long childCount, BigDecimal progressSum, long completedCount) {

    public boolean isEmpty() {
        return childCount == 0;
    }

    public boolean allComplete() {
        return childCount > 0 && completedCount == childCount;
    }
}
