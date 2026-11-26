package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.ebike.model.entity.warranty.WarrantyClaim;
import com.ebike.service.warranty.MaintenanceMilestones;
import com.ebike.service.warranty.WarrantyMessages;
import com.ebike.service.warranty.WarrantyRules;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class WarrantyRulesTest {

    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 31, 14, 30);

    @Test
    void startsAtPrefersDeliveryTime() {
        LocalDateTime d = LocalDateTime.of(2024, 3, 1, 9, 0);
        assertThat(WarrantyRules.startsAt(d, START)).isEqualTo(d);
        assertThat(WarrantyRules.startsAt(null, START)).isEqualTo(START);
        assertThat(WarrantyRules.startsAt(null, null)).isNull();
    }

    @Test
    void endsAtUsesCalendarMonths() {
        assertThat(WarrantyRules.endsAt(START, 1)).isEqualTo(LocalDateTime.of(2024, 2, 29, 14, 30));
        assertThat(WarrantyRules.endsAt(START, 24)).isEqualTo(LocalDateTime.of(2026, 1, 31, 14, 30));
        assertThat(WarrantyRules.endsAt(null, 12)).isNull();
        assertThat(WarrantyRules.endsAt(START, null)).isNull();
        assertThat(WarrantyRules.endsAt(START, 0)).isNull();
        assertThat(WarrantyRules.endsAt(START, -3)).isNull();
    }

    @Test
    void coverageCoversWholeLastDay() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 15, 16, 0);
        assertThat(WarrantyRules.coverage(start, 12, LocalDateTime.of(2025, 1, 15, 23, 59, 59))).isEqualTo(WarrantyRules.ACTIVE);
        assertThat(WarrantyRules.coverage(start, 12, LocalDateTime.of(2025, 1, 15, 23, 59, 59, 999_999_999))).isEqualTo(WarrantyRules.ACTIVE);
        assertThat(WarrantyRules.coverage(start, 12, LocalDateTime.of(2025, 1, 16, 0, 0))).isEqualTo(WarrantyRules.EXPIRED);
        assertThat(WarrantyRules.coverage(start, 12, start)).isEqualTo(WarrantyRules.ACTIVE);
    }

    @Test
    void coverageUnknownAndNotStarted() {
        LocalDateTime now = LocalDateTime.of(2030, 1, 1, 0, 0);
        assertThat(WarrantyRules.coverage(null, 12, now)).isEqualTo(WarrantyRules.NOT_STARTED);
        assertThat(WarrantyRules.coverage(null, null, now)).isEqualTo(WarrantyRules.NOT_STARTED);
        assertThat(WarrantyRules.coverage(START, null, now)).isEqualTo(WarrantyRules.UNKNOWN);
        assertThat(WarrantyRules.coverage(START, 0, now)).isEqualTo(WarrantyRules.UNKNOWN);
    }

    @Test
    void parseIntervalsDefaultsAndSorting() {
        assertThat(WarrantyRules.parseIntervals(null)).hasSize(7);
        assertThat(WarrantyRules.parseIntervals("")).hasSize(7);
        assertThat(WarrantyRules.parseIntervals("garbage,,P0M,-P1M,P-1M")).hasSize(7);
        List<Period> p = WarrantyRules.parseIntervals("P12M, P1M ,P6M,P6M,junk");
        assertThat(p).containsExactly(Period.ofMonths(1), Period.ofMonths(6), Period.ofYears(1));
        assertThat(WarrantyRules.parseIntervals("P1Y,P12M")).hasSize(1);
        assertThat(WarrantyRules.parseIntervals("P10D,P1M")).containsExactly(Period.ofDays(10), Period.ofMonths(1));
    }

    @Test
    void defaultIntervalsAreTheDocumentedSeven() {
        List<Period> p = WarrantyRules.parseIntervals(WarrantyRules.DEFAULT_INTERVALS);
        assertThat(p.stream().map(Period::toTotalMonths).toList()).containsExactly(1L, 6L, 12L, 18L, 24L, 30L, 36L);
    }

    @Test
    void dueMilestonesBoundary() {
        List<Period> iv = WarrantyRules.parseIntervals("P1M,P6M");
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 10, 0);
        assertThat(WarrantyRules.dueMilestones(start, iv, LocalDateTime.of(2024, 2, 1, 9, 59, 59))).isEmpty();
        assertThat(WarrantyRules.dueMilestones(start, iv, LocalDateTime.of(2024, 2, 1, 10, 0))).containsExactly(Period.ofMonths(1));
        assertThat(WarrantyRules.dueMilestones(start, iv, LocalDateTime.of(2030, 1, 1, 0, 0))).hasSize(2);
        assertThat(WarrantyRules.dueMilestones(null, iv, LocalDateTime.now())).isEmpty();
    }

    @Test
    void nextMaintenance() {
        List<Period> iv = WarrantyRules.parseIntervals("P1M,P6M");
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 10, 0);
        assertThat(WarrantyRules.nextMaintenanceAt(start, iv, start)).isEqualTo(LocalDateTime.of(2024, 2, 1, 10, 0));
        assertThat(WarrantyRules.nextMaintenanceAt(start, iv, LocalDateTime.of(2024, 2, 1, 10, 0))).isEqualTo(LocalDateTime.of(2024, 7, 1, 10, 0));
        assertThat(WarrantyRules.nextMaintenanceAt(start, iv, LocalDateTime.of(2024, 7, 1, 10, 0))).isNull();
        assertThat(WarrantyRules.nextMaintenanceAt(null, iv, start)).isNull();
    }

    @Test
    void statusTransitions() {
        String[] all = {"NEW", "RECEIVED", "REPAIRING", "DONE", "REJECTED"};
        assertThat(WarrantyRules.canMove("NEW", "RECEIVED")).isTrue();
        assertThat(WarrantyRules.canMove("NEW", "REJECTED")).isTrue();
        assertThat(WarrantyRules.canMove("NEW", "REPAIRING")).isFalse();
        assertThat(WarrantyRules.canMove("NEW", "DONE")).isFalse();
        assertThat(WarrantyRules.canMove("RECEIVED", "REPAIRING")).isTrue();
        assertThat(WarrantyRules.canMove("RECEIVED", "DONE")).isTrue();
        assertThat(WarrantyRules.canMove("RECEIVED", "NEW")).isFalse();
        assertThat(WarrantyRules.canMove("REPAIRING", "DONE")).isTrue();
        assertThat(WarrantyRules.canMove("REPAIRING", "RECEIVED")).isFalse();
        for (String to : all) {
            assertThat(WarrantyRules.canMove("DONE", to)).as("DONE->" + to).isFalse();
            assertThat(WarrantyRules.canMove("REJECTED", to)).as("REJECTED->" + to).isFalse();
        }
        assertThat(WarrantyRules.canMove("NEW", "NEW")).isTrue();
        assertThat(WarrantyRules.canMove("REPAIRING", "REPAIRING")).isTrue();
        assertThat(WarrantyRules.canMove(null, "NEW")).isFalse();
        assertThat(WarrantyRules.canMove("NEW", null)).isFalse();
        assertThat(WarrantyRules.canMove("NEW", "bogus")).isFalse();
        assertThat(WarrantyRules.canMove("new", "RECEIVED")).isFalse();
    }

    @Test
    void openAndKnown() {
        assertThat(WarrantyRules.isOpen(WarrantyClaim.NEW)).isTrue();
        assertThat(WarrantyRules.isOpen(WarrantyClaim.RECEIVED)).isTrue();
        assertThat(WarrantyRules.isOpen(WarrantyClaim.REPAIRING)).isTrue();
        assertThat(WarrantyRules.isOpen(WarrantyClaim.DONE)).isFalse();
        assertThat(WarrantyRules.isOpen(WarrantyClaim.REJECTED)).isFalse();
        assertThat(WarrantyRules.isOpen(null)).isFalse();
        assertThat(WarrantyRules.isKnownStatus("DONE")).isTrue();
        assertThat(WarrantyRules.isKnownStatus("ALL")).isFalse();
    }

    @Test
    @Tag("bug")
    void bug_isKnownStatusNullMustBeFalse() {
        // Expected: false (canMove() guards null the same way). Actual: NullPointerException from Map.of(..).containsKey(null).
        // WarrantyRules.java: isKnownStatus
        assertThat(WarrantyRules.isKnownStatus(null)).isFalse();
    }

    @Test
    void describeMilestones() {
        assertThat(MaintenanceMilestones.describe("P1M")).isEqualTo("1 tháng");
        assertThat(MaintenanceMilestones.describe("P6M")).isEqualTo("6 tháng");
        assertThat(MaintenanceMilestones.describe("P1Y")).isEqualTo("12 tháng");
        assertThat(MaintenanceMilestones.describe("P1Y6M")).isEqualTo("18 tháng");
        assertThat(MaintenanceMilestones.describe("P10D")).isEqualTo("10 ngày");
        assertThat(MaintenanceMilestones.describe("P1M10D")).isEqualTo("1 tháng 10 ngày");
        assertThat(MaintenanceMilestones.describe("nonsense")).isEqualTo("nonsense");
    }

    @Test
    @Tag("bug")
    void bug_describeNullMilestoneMustNotThrow() {
        // Expected: an unreadable milestone (null included) is returned as-is: the method swallows unparsable text on purpose.
        // Actual: Period.parse(null) throws NullPointerException; only DateTimeParseException is caught.
        // MaintenanceMilestones.java:12-26
        assertDoesNotThrow(() -> MaintenanceMilestones.describe(null));
    }

    @Test
    void warrantyUrlAndEmail() {
        assertThat(WarrantyMessages.warrantyUrl(null)).isNull();
        assertThat(WarrantyMessages.warrantyUrl(" ")).isNull();
        assertThat(WarrantyMessages.warrantyUrl(" https://x.com/ ")).isEqualTo("https://x.com/customer/warranty");
        assertThat(WarrantyMessages.maintenanceEmailBody("B", "F1", "P6M", null)).doesNotContain("null").contains("6 tháng");
        assertThat(WarrantyMessages.maintenanceEmailBody("B", "F1", "P6M", "http://u")).contains("http://u");
        assertThat(WarrantyMessages.maintenanceEmailSubject("B")).contains("B");
        assertThat(WarrantyMessages.claimBody("B", "F1", "REJECTED")).contains("không được chấp nhận");
        assertThat(WarrantyMessages.claimBody("B", "F1", WarrantyMessages.NOTE_UPDATED)).contains("ghi chú mới");
        assertThat(WarrantyMessages.claimBody("B", "F1", "WEIRD")).contains("WEIRD");
    }
}
