package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** V20: a milestone such as P1M15D is read out with the right singular and plural in English. */
class MaintenanceMilestonesTest {

    @Test
    void singleUnitsUseSingularAndPlural() {
        assertEquals("1 month", MaintenanceMilestones.describe("P1M", "en"));
        assertEquals("6 months", MaintenanceMilestones.describe("P6M", "en"));
        assertEquals("1 day", MaintenanceMilestones.describe("P1D", "en"));
        assertEquals("15 days", MaintenanceMilestones.describe("P15D", "en"));
        assertEquals("6 tháng", MaintenanceMilestones.describe("P6M", "vi"));
    }

    @Test
    void mixedPeriodsAgreeEachPart() {
        assertEquals("1 month 15 days", MaintenanceMilestones.describe("P1M15D", "en"));
        assertEquals("2 months 1 day", MaintenanceMilestones.describe("P2M1D", "en"));
        assertEquals("1 month 1 day", MaintenanceMilestones.describe("P1M1D", "en"));
        assertEquals("1 tháng 15 ngày", MaintenanceMilestones.describe("P1M15D", "vi"));
    }
}
