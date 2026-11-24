package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** V20: a milestone such as P1M15D is read out in months and days. */
class MaintenanceMilestonesTest {

    @Test
    void singleUnits() {
        assertEquals("1 tháng", MaintenanceMilestones.describe("P1M"));
        assertEquals("6 tháng", MaintenanceMilestones.describe("P6M"));
        assertEquals("1 ngày", MaintenanceMilestones.describe("P1D"));
        assertEquals("15 ngày", MaintenanceMilestones.describe("P15D"));
    }

    @Test
    void mixedPeriodsNameBothParts() {
        assertEquals("1 tháng 15 ngày", MaintenanceMilestones.describe("P1M15D"));
        assertEquals("2 tháng 1 ngày", MaintenanceMilestones.describe("P2M1D"));
    }
}
