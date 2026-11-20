package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.warranty.WarrantyClaim;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarrantyRulesTest {

    private static final LocalDateTime DELIVERED = LocalDateTime.of(2026, 1, 31, 9, 0);
    private static final List<Period> DEFAULTS = WarrantyRules.parseIntervals("P1M,P6M,P12M");

    @Test
    void warrantyStartsAtDeliveryAndFallsBackToTheOrderUpdate() {
        LocalDateTime updated = DELIVERED.plusDays(1);
        assertEquals(DELIVERED, WarrantyRules.startsAt(DELIVERED, updated));
        assertEquals(updated, WarrantyRules.startsAt(null, updated));
    }

    @Test
    void endIsStartPlusMonthsAndUnknownWithoutThem() {
        assertEquals(LocalDateTime.of(2028, 1, 31, 9, 0), WarrantyRules.endsAt(DELIVERED, 24));
        // 31 January + 1 month stays inside February
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), WarrantyRules.endsAt(DELIVERED, 1));
        assertNull(WarrantyRules.endsAt(DELIVERED, null));
        assertNull(WarrantyRules.endsAt(DELIVERED, 0));
        assertNull(WarrantyRules.endsAt(null, 24));
    }

    @Test
    void coverageIsActiveUpToAndIncludingTheLastMoment() {
        LocalDateTime ends = LocalDateTime.of(2028, 1, 31, 9, 0);
        assertEquals(WarrantyRules.ACTIVE, WarrantyRules.coverage(DELIVERED, 24, ends.minusSeconds(1)));
        assertEquals(WarrantyRules.ACTIVE, WarrantyRules.coverage(DELIVERED, 24, ends));
        assertEquals(WarrantyRules.EXPIRED, WarrantyRules.coverage(DELIVERED, 24, ends.plusSeconds(1)));
        assertEquals(WarrantyRules.UNKNOWN, WarrantyRules.coverage(DELIVERED, null, ends));
        assertEquals(WarrantyRules.NOT_STARTED, WarrantyRules.coverage(null, 24, ends));
    }

    @Test
    void intervalsAreParsedSortedAndDeduplicated() {
        assertEquals(List.of(Period.ofMonths(1), Period.ofMonths(6), Period.ofYears(1)), WarrantyRules.parseIntervals(" P12M, P1M ,P6M,P6M"));
        // P12M is normalised to one year, so P1Y and P12M are the same milestone
        assertEquals(1, WarrantyRules.parseIntervals("P1Y,P12M").size());
    }

    @Test
    void badOrEmptyIntervalSettingsFallBackSafely() {
        List<Period> FALLBACK = WarrantyRules.parseIntervals(WarrantyRules.DEFAULT_INTERVALS);
        assertEquals(7, FALLBACK.size());
        assertEquals(FALLBACK, WarrantyRules.parseIntervals(null));
        assertEquals(FALLBACK, WarrantyRules.parseIntervals(""));
        assertEquals(FALLBACK, WarrantyRules.parseIntervals("soon,P0M,-P1M"));
        assertEquals(List.of(Period.ofMonths(3)), WarrantyRules.parseIntervals("oops,P3M"));
    }

    @Test
    void dueMilestonesAreThoseAlreadyReached() {
        assertTrue(WarrantyRules.dueMilestones(DELIVERED, DEFAULTS, DELIVERED.plusDays(10)).isEmpty());
        assertEquals(List.of(Period.ofMonths(1)), WarrantyRules.dueMilestones(DELIVERED, DEFAULTS, DELIVERED.plusMonths(1)));
        assertEquals(List.of(Period.ofMonths(1), Period.ofMonths(6)), WarrantyRules.dueMilestones(DELIVERED, DEFAULTS, DELIVERED.plusMonths(7)));
        assertEquals(3, WarrantyRules.dueMilestones(DELIVERED, DEFAULTS, DELIVERED.plusYears(5)).size());
        assertTrue(WarrantyRules.dueMilestones(null, DEFAULTS, DELIVERED.plusYears(5)).isEmpty());
    }

    @Test
    void nextMaintenanceIsTheFirstMilestoneStillAhead() {
        assertEquals(WarrantyRules.dueAt(DELIVERED, Period.ofMonths(1)), WarrantyRules.nextMaintenanceAt(DELIVERED, DEFAULTS, DELIVERED.plusDays(3)));
        assertEquals(WarrantyRules.dueAt(DELIVERED, Period.ofMonths(6)), WarrantyRules.nextMaintenanceAt(DELIVERED, DEFAULTS, DELIVERED.plusMonths(2)));
        assertNull(WarrantyRules.nextMaintenanceAt(DELIVERED, DEFAULTS, DELIVERED.plusYears(3)));
        assertNull(WarrantyRules.nextMaintenanceAt(null, DEFAULTS, DELIVERED));
    }

    @Test
    void claimsMoveForwardOnly() {
        assertTrue(WarrantyRules.canMove(WarrantyClaim.NEW, WarrantyClaim.RECEIVED));
        assertTrue(WarrantyRules.canMove(WarrantyClaim.NEW, WarrantyClaim.REJECTED));
        assertTrue(WarrantyRules.canMove(WarrantyClaim.RECEIVED, WarrantyClaim.REPAIRING));
        assertTrue(WarrantyRules.canMove(WarrantyClaim.REPAIRING, WarrantyClaim.DONE));
        assertFalse(WarrantyRules.canMove(WarrantyClaim.NEW, WarrantyClaim.DONE));
        assertFalse(WarrantyRules.canMove(WarrantyClaim.REPAIRING, WarrantyClaim.RECEIVED));
        assertFalse(WarrantyRules.canMove(WarrantyClaim.DONE, WarrantyClaim.REPAIRING));
        assertFalse(WarrantyRules.canMove(WarrantyClaim.REJECTED, WarrantyClaim.NEW));
        assertFalse(WarrantyRules.canMove("NOPE", WarrantyClaim.DONE));
        assertFalse(WarrantyRules.canMove(null, WarrantyClaim.DONE));
    }

    @Test
    void staffMayEditTheNoteOfAnOpenClaimButNotReopenAFinishedOne() {
        assertTrue(WarrantyRules.canMove(WarrantyClaim.RECEIVED, WarrantyClaim.RECEIVED));
        assertFalse(WarrantyRules.canMove(WarrantyClaim.DONE, WarrantyClaim.DONE));
        assertFalse(WarrantyRules.canMove(WarrantyClaim.REJECTED, WarrantyClaim.REJECTED));
    }

    @Test
    void milestonesAreDescribedInWords() {
        assertEquals("1 tháng", MaintenanceMilestones.describe("P1M"));
        assertEquals("12 tháng", MaintenanceMilestones.describe("P1Y"));
        assertEquals("10 ngày", MaintenanceMilestones.describe("P10D"));
        assertEquals("not-a-period", MaintenanceMilestones.describe("not-a-period"));
    }
}
