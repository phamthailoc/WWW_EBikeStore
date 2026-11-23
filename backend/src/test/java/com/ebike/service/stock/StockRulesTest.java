package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.service.stock.StockRules.Kind;
import com.ebike.service.stock.StockRules.LineInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V26: the pure rules of stock receipts and the ledger. */
class StockRulesTest {

    private static LineInput line(Long product, Long variant, Integer quantity) {
        return new LineInput(product, variant, quantity);
    }

    @Test void aReceiptNeedsAtLeastOneLine() {
        assertEquals(Kind.NO_LINES, StockRules.checkLines(null).kind());
        assertEquals(Kind.NO_LINES, StockRules.checkLines(List.of()).kind());
    }

    @Test void aReceiptCannotHaveMoreThanThirtyLines() {
        List<LineInput> lines = new ArrayList<>();
        for (long i = 1; i <= StockRules.MAX_LINES + 1; i++) {
            lines.add(line(i, null, 1));
        }
        assertEquals(Kind.TOO_MANY_LINES, StockRules.checkLines(lines).kind());
        assertNull(StockRules.checkLines(lines.subList(0, StockRules.MAX_LINES)));
    }

    @Test void theQuantityOfALineIsAWholeNumberFromOneToAThousand() {
        assertEquals(Kind.BAD_QUANTITY, StockRules.checkLines(List.of(line(1L, 2L, null))).kind());
        assertEquals(Kind.BAD_QUANTITY, StockRules.checkLines(List.of(line(1L, 2L, 0))).kind());
        assertEquals(Kind.BAD_QUANTITY, StockRules.checkLines(List.of(line(1L, 2L, -3))).kind());
        assertEquals(Kind.QUANTITY_TOO_LARGE, StockRules.checkLines(List.of(line(1L, 2L, 1001))).kind());
        assertNull(StockRules.checkLines(List.of(line(1L, 2L, 1), line(1L, 3L, 1000))));
    }

    @Test void theProblemNamesTheLineAsThePersonCountsItFromOne() {
        var problem = StockRules.checkLines(List.of(line(1L, 2L, 5), line(1L, 3L, 5), line(2L, null, 0)));
        assertEquals(Kind.BAD_QUANTITY, problem.kind());
        assertEquals(3, problem.line());
    }

    @Test void aLineNeedsABikeAndAColourThatIsAnId() {
        assertEquals(Kind.MISSING_PRODUCT, StockRules.checkLines(List.of(line(null, null, 1))).kind());
        assertEquals(Kind.MISSING_PRODUCT, StockRules.checkLines(List.of(line(0L, null, 1))).kind());
        assertEquals(Kind.MISSING_PRODUCT, StockRules.checkLines(List.of(line(1L, -4L, 1))).kind());
        assertEquals(Kind.MISSING_PRODUCT, StockRules.checkLines(java.util.Collections.singletonList(null)).kind());
    }

    @Test void theSameColourTwiceIsRefusedButTwoColoursOfOneBikeAreFine() {
        var problem = StockRules.checkLines(List.of(line(1L, 2L, 1), line(1L, 3L, 1), line(1L, 2L, 4)));
        assertEquals(Kind.DUPLICATE_LINE, problem.kind());
        assertEquals(3, problem.line());
        assertEquals(Kind.DUPLICATE_LINE, StockRules.checkLines(List.of(line(1L, null, 1), line(1L, null, 2))).kind());
        assertNull(StockRules.checkLines(List.of(line(1L, 2L, 1), line(1L, 3L, 1), line(5L, null, 2))));
    }

    @Test void stockCannotGrowPastTheCeiling() {
        assertTrue(StockRules.fitsOnShelf(99_000, 1000));
        assertFalse(StockRules.fitsOnShelf(99_001, 1000));
        assertTrue(StockRules.fitsOnShelf(null, 5));
        // no overflow even for a figure near the int limit
        assertFalse(StockRules.fitsOnShelf(Integer.MAX_VALUE, 1));
    }

    @Test void anAdjustmentIsTheNewFigureMinusTheOldOne() {
        assertEquals(6, StockRules.adjustmentDelta(4, 10));
        assertEquals(-4, StockRules.adjustmentDelta(4, 0));
        assertEquals(0, StockRules.adjustmentDelta(4, 4));
        assertEquals(3, StockRules.adjustmentDelta(null, 3));
    }

    @Test void receiptNumbersAreDerivedFromTheIdAndReadBack() {
        assertEquals("PN-000012", StockRules.receiptNumber(12L));
        assertEquals("PN-1234567", StockRules.receiptNumber(1_234_567L));
        assertEquals(12L, StockRules.receiptIdOf("PN-000012"));
        assertEquals(12L, StockRules.receiptIdOf(" pn12 "));
        assertEquals(7L, StockRules.receiptIdOf("pn-7"));
        assertNull(StockRules.receiptIdOf("PN-"));
        assertNull(StockRules.receiptIdOf("PN-0"));
        assertNull(StockRules.receiptIdOf("Công ty PN"));
        assertNull(StockRules.receiptIdOf("PN-99999999999999999999999"));
        assertNull(StockRules.receiptIdOf(null));
    }

    @Test void aSearchTextBecomesALowerCaseLikePatternWithoutWildcards() {
        assertEquals("%dibao%", StockRules.likePattern("  DIBAO "));
        assertEquals("%a b%", StockRules.likePattern("a%b"));
        assertEquals("%", StockRules.likePattern("   "));
        assertEquals("%", StockRules.likePattern("%_\\"));
        assertEquals("%", StockRules.likePattern(null));
    }

    @Test void notesAreTrimmedAndBlankMeansNone() {
        assertEquals("Lô hàng tháng 11", StockRules.clean("  Lô   hàng tháng 11\t"));
        assertNull(StockRules.clean("   "));
        assertNull(StockRules.clean(null));
    }

    @Test void theLedgerMatchesTheStockOnlyWhenTheLinesAddUpToIt() {
        assertTrue(StockRules.ledgerMatches(10, 10));
        assertTrue(StockRules.ledgerMatches(null, 0));
        assertFalse(StockRules.ledgerMatches(10, 8));
    }

    @Test void eachReasonHasItsSign() {
        assertTrue(StockRules.signMatches(StockMovementReason.SALE, -1));
        assertFalse(StockRules.signMatches(StockMovementReason.SALE, 1));
        assertTrue(StockRules.signMatches(StockMovementReason.RECEIPT, 5));
        assertFalse(StockRules.signMatches(StockMovementReason.RECEIPT, -5));
        assertTrue(StockRules.signMatches(StockMovementReason.CANCEL_RESTORE, 2));
        assertTrue(StockRules.signMatches(StockMovementReason.OPENING, 9));
        assertTrue(StockRules.signMatches(StockMovementReason.ADJUSTMENT, -3));
        assertTrue(StockRules.signMatches(StockMovementReason.ADJUSTMENT, 3));
        assertFalse(StockRules.signMatches(StockMovementReason.ADJUSTMENT, 0));
        assertFalse(StockRules.signMatches(null, 1));
    }

    @Test void theReasonFilterAcceptsNamesAndRefusesStrangers() {
        assertEquals(StockMovementReason.values().length, StockRules.parseReasons(null).size());
        assertEquals(StockMovementReason.values().length, StockRules.parseReasons("  ").size());
        var some = StockRules.parseReasons("receipt, SALE");
        assertEquals(2, some.size());
        assertTrue(some.contains(StockMovementReason.RECEIPT) && some.contains(StockMovementReason.SALE));
        assertThrows(IllegalArgumentException.class, () -> StockRules.parseReasons("RECEIPT,GIFT"));
    }

    @Test void theNameOfWhoDidItIsTheFullNameElseTheUsername() {
        assertEquals("An Nguyễn", StockRules.displayName("An", "Nguyễn", "an"));
        assertEquals("Tài khoản đã xóa", StockRules.displayName("Tài khoản", "đã xóa", "deleted_5"));
        assertEquals("manager", StockRules.displayName(null, " ", "manager"));
        assertNull(StockRules.displayName(null, null, null));
        assertNotNull(StockRules.displayName("An", null, null));
    }
}
