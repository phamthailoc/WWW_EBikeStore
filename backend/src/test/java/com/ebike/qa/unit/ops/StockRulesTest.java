package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.service.stock.StockAlertMessages;
import com.ebike.service.stock.StockAlertRules;
import com.ebike.service.stock.StockRules;
import com.ebike.service.stock.StockRules.Kind;
import com.ebike.service.stock.StockRules.LineInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockRulesTest {

    private static LineInput line(Long p, Long v, Integer q) {
        return new LineInput(p, v, q);
    }

    @Test
    void noLines() {
        assertThat(StockRules.checkLines(null).kind()).isEqualTo(Kind.NO_LINES);
        assertThat(StockRules.checkLines(List.of()).kind()).isEqualTo(Kind.NO_LINES);
    }

    @Test
    void maxLinesBoundary() {
        List<LineInput> ok = new ArrayList<>();
        for (int i = 1; i <= StockRules.MAX_LINES; i++) ok.add(line((long) i, null, 1));
        assertThat(StockRules.checkLines(ok)).isNull();
        ok.add(line(999L, null, 1));
        assertThat(StockRules.checkLines(ok).kind()).isEqualTo(Kind.TOO_MANY_LINES);
    }

    @Test
    void quantityBoundaries() {
        assertThat(StockRules.checkLines(List.of(line(1L, null, 1)))).isNull();
        assertThat(StockRules.checkLines(List.of(line(1L, null, 1000)))).isNull();
        assertThat(StockRules.checkLines(List.of(line(1L, null, 1001))).kind()).isEqualTo(Kind.QUANTITY_TOO_LARGE);
        assertThat(StockRules.checkLines(List.of(line(1L, null, 0))).kind()).isEqualTo(Kind.BAD_QUANTITY);
        assertThat(StockRules.checkLines(List.of(line(1L, null, -5))).kind()).isEqualTo(Kind.BAD_QUANTITY);
        assertThat(StockRules.checkLines(List.of(line(1L, null, null))).kind()).isEqualTo(Kind.BAD_QUANTITY);
        assertThat(StockRules.checkLines(List.of(line(1L, null, Integer.MIN_VALUE))).kind()).isEqualTo(Kind.BAD_QUANTITY);
        assertThat(StockRules.checkLines(List.of(line(1L, null, Integer.MAX_VALUE))).kind()).isEqualTo(Kind.QUANTITY_TOO_LARGE);
    }

    @Test
    void missingProductReportsOneBasedLine() {
        StockRules.Problem p = StockRules.checkLines(List.of(line(1L, null, 1), line(null, null, 1)));
        assertThat(p.kind()).isEqualTo(Kind.MISSING_PRODUCT);
        assertThat(p.line()).isEqualTo(2);
        assertThat(StockRules.checkLines(List.of(line(0L, null, 1))).kind()).isEqualTo(Kind.MISSING_PRODUCT);
        assertThat(StockRules.checkLines(List.of(line(-1L, null, 1))).kind()).isEqualTo(Kind.MISSING_PRODUCT);
        assertThat(StockRules.checkLines(List.of(line(1L, 0L, 1))).kind()).isEqualTo(Kind.MISSING_PRODUCT);
        assertThat(StockRules.checkLines(Arrays.asList((LineInput) null)).kind()).isEqualTo(Kind.MISSING_PRODUCT);
    }

    @Test
    void duplicateColourRejectedButDifferentColourOk() {
        assertThat(StockRules.checkLines(List.of(line(1L, 5L, 1), line(1L, 6L, 1)))).isNull();
        assertThat(StockRules.checkLines(List.of(line(1L, 5L, 1), line(2L, 5L, 1)))).isNull();
        StockRules.Problem p = StockRules.checkLines(List.of(line(1L, 5L, 1), line(1L, 5L, 2)));
        assertThat(p.kind()).isEqualTo(Kind.DUPLICATE_LINE);
        assertThat(p.line()).isEqualTo(2);
        assertThat(StockRules.checkLines(List.of(line(1L, null, 1), line(1L, null, 1))).kind()).isEqualTo(Kind.DUPLICATE_LINE);
    }

    @Test
    void fitsOnShelfBoundary() {
        assertThat(StockRules.fitsOnShelf(99_000, 1000)).isTrue();
        assertThat(StockRules.fitsOnShelf(99_000, 1001)).isFalse();
        assertThat(StockRules.fitsOnShelf(null, 1000)).isTrue();
        assertThat(StockRules.fitsOnShelf(Integer.MAX_VALUE, 1)).isFalse();
        assertThat(StockRules.fitsOnShelf(100_000, 0)).isTrue();
    }

    @Test
    void adjustmentDelta() {
        assertThat(StockRules.adjustmentDelta(null, 5)).isEqualTo(5);
        assertThat(StockRules.adjustmentDelta(10, 3)).isEqualTo(-7);
        assertThat(StockRules.adjustmentDelta(4, 4)).isZero();
    }

    @Test
    void receiptNumberRoundTrip() {
        assertThat(StockRules.receiptNumber(12L)).isEqualTo("PN-000012");
        assertThat(StockRules.receiptNumber(null)).isEqualTo("PN-000000");
        assertThat(StockRules.receiptNumber(1234567L)).isEqualTo("PN-1234567");
        assertThat(StockRules.receiptIdOf("PN-000012")).isEqualTo(12L);
        assertThat(StockRules.receiptIdOf("pn12")).isEqualTo(12L);
        assertThat(StockRules.receiptIdOf("  Pn-0012 ")).isEqualTo(12L);
        assertThat(StockRules.receiptIdOf(StockRules.receiptNumber(987654L))).isEqualTo(987654L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "PN", "PN-", "PN-0", "PN-000", "12", "PN-12a", "XX-12", "PN--12", "PN-1 2", "PN-9999999999999999999"})
    void receiptIdOfRejects(String text) {
        assertThat(StockRules.receiptIdOf(text)).isNull();
    }

    @Test
    void receiptIdOfNull() {
        assertThat(StockRules.receiptIdOf(null)).isNull();
    }

    @Test
    void likePatternStripsWildcards() {
        assertThat(StockRules.likePattern(null)).isEqualTo("%");
        assertThat(StockRules.likePattern("  ")).isEqualTo("%");
        assertThat(StockRules.likePattern("%")).isEqualTo("%");
        assertThat(StockRules.likePattern("_")).isEqualTo("%");
        assertThat(StockRules.likePattern("ABC")).isEqualTo("%abc%");
        assertThat(StockRules.likePattern("a%b_c\\d")).isEqualTo("%a b c d%");
        assertThat(StockRules.likePattern("%%_")).isEqualTo("%");
    }

    @Test
    void clean() {
        assertThat(StockRules.clean(null)).isNull();
        assertThat(StockRules.clean("   ")).isNull();
        assertThat(StockRules.clean("\t")).isNull();
        assertThat(StockRules.clean("  a   b\t\tc ")).isEqualTo("a b c");
    }

    @Test
    void ledgerMatches() {
        assertThat(StockRules.ledgerMatches(null, 0)).isTrue();
        assertThat(StockRules.ledgerMatches(5, 5)).isTrue();
        assertThat(StockRules.ledgerMatches(5, 4)).isFalse();
        assertThat(StockRules.ledgerMatches(0, 3)).isFalse();
    }

    @Test
    void signMatchesEveryReason() {
        assertThat(StockRules.signMatches(StockMovementReason.SALE, -1)).isTrue();
        assertThat(StockRules.signMatches(StockMovementReason.SALE, 1)).isFalse();
        for (StockMovementReason r : new StockMovementReason[] {StockMovementReason.RECEIPT, StockMovementReason.CANCEL_RESTORE, StockMovementReason.OPENING}) {
            assertThat(StockRules.signMatches(r, 1)).isTrue();
            assertThat(StockRules.signMatches(r, -1)).isFalse();
        }
        assertThat(StockRules.signMatches(StockMovementReason.ADJUSTMENT, -3)).isTrue();
        assertThat(StockRules.signMatches(StockMovementReason.ADJUSTMENT, 3)).isTrue();
        for (StockMovementReason r : StockMovementReason.values()) {
            assertThat(StockRules.signMatches(r, 0)).as("zero never valid " + r).isFalse();
        }
        assertThat(StockRules.signMatches(null, 1)).isFalse();
    }

    @Test
    void parseReasons() {
        assertThat(StockRules.parseReasons(null)).containsExactlyInAnyOrder(StockMovementReason.values());
        assertThat(StockRules.parseReasons(" ")).containsExactlyInAnyOrder(StockMovementReason.values());
        assertThat(StockRules.parseReasons(",,")).containsExactlyInAnyOrder(StockMovementReason.values());
        assertThat(StockRules.parseReasons("receipt, SALE")).containsExactlyInAnyOrder(StockMovementReason.RECEIPT, StockMovementReason.SALE);
        assertThat(StockRules.parseReasons("SALE,SALE")).containsExactly(StockMovementReason.SALE);
        assertThatThrownBy(() -> StockRules.parseReasons("SALE,NOPE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayName() {
        assertThat(StockRules.displayName("An", "Nguyen", "u")).isEqualTo("An Nguyen");
        assertThat(StockRules.displayName(null, "Nguyen", "u")).isEqualTo("Nguyen");
        assertThat(StockRules.displayName(" ", " ", " bob ")).isEqualTo("bob");
        assertThat(StockRules.displayName(null, null, null)).isNull();
        assertThat(StockRules.displayName("", "", " ")).isNull();
    }

    // ---- StockAlertRules ----
    @Test
    void restockOnlyFromNoneToSome() {
        assertThat(StockAlertRules.isRestock(0, 1)).isTrue();
        assertThat(StockAlertRules.isRestock(null, 5)).isTrue();
        assertThat(StockAlertRules.isRestock(-2, 1)).isTrue();
        assertThat(StockAlertRules.isRestock(1, 10)).isFalse();
        assertThat(StockAlertRules.isRestock(0, 0)).isFalse();
        assertThat(StockAlertRules.isRestock(0, null)).isFalse();
        assertThat(StockAlertRules.isRestock(3, 0)).isFalse();
    }

    @Test
    void canNotify() {
        assertThat(StockAlertRules.canNotify(true, true, 1)).isTrue();
        assertThat(StockAlertRules.canNotify(true, true, 0)).isFalse();
        assertThat(StockAlertRules.canNotify(true, true, null)).isFalse();
        assertThat(StockAlertRules.canNotify(true, true, -1)).isFalse();
        assertThat(StockAlertRules.canNotify(false, true, 5)).isFalse();
        assertThat(StockAlertRules.canNotify(true, false, 5)).isFalse();
    }

    @Test
    void canReceive() {
        assertThat(StockAlertRules.canReceive("ACTIVE", true)).isTrue();
        assertThat(StockAlertRules.canReceive("ACTIVE", null)).isTrue();
        assertThat(StockAlertRules.canReceive("ACTIVE", false)).isFalse();
        assertThat(StockAlertRules.canReceive("DELETED", true)).isFalse();
        assertThat(StockAlertRules.canReceive("SUSPENDED", true)).isFalse();
        assertThat(StockAlertRules.canReceive(null, true)).isFalse();
        assertThat(StockAlertRules.canReceive("active", true)).isFalse();
    }

    @Test
    void canSubscribeOnlyWhenSoldOut() {
        assertThat(StockAlertRules.canSubscribe(0)).isTrue();
        assertThat(StockAlertRules.canSubscribe(null)).isTrue();
        assertThat(StockAlertRules.canSubscribe(-1)).isTrue();
        assertThat(StockAlertRules.canSubscribe(1)).isFalse();
    }

    // ---- StockAlertMessages ----
    @Test
    void messagesNeverPrintNull() {
        assertThat(StockAlertMessages.notificationBody("Bike", null)).doesNotContain("null").contains("màu bạn chọn");
        assertThat(StockAlertMessages.notificationBody("Bike", " ")).doesNotContain("null");
        assertThat(StockAlertMessages.emailSubject("Bike", null)).doesNotContain("null").doesNotContain("(");
        assertThat(StockAlertMessages.emailSubject("Bike", " Red ")).contains("(Red)");
        assertThat(StockAlertMessages.emailBody("Bike", null, null)).doesNotContain("null");
        assertThat(StockAlertMessages.emailBody("Bike", "Red", " http://x/p ")).contains("http://x/p");
    }

    @Test
    void productUrlAndPath() {
        assertThat(StockAlertMessages.productUrl("https://shop.example.com///", "my bike")).isEqualTo("https://shop.example.com/product/my%20bike");
        assertThat(StockAlertMessages.productUrl(null, "x")).isNull();
        assertThat(StockAlertMessages.productUrl("http://a", " ")).isNull();
        assertThat(StockAlertMessages.productPath(null)).isNull();
        assertThat(StockAlertMessages.productPath("a/b?c")).isEqualTo("/product/a%2Fb%3Fc");
    }

    @Test
    void notificationPathDropsTooLongLink() {
        assertThat(StockAlertMessages.notificationPath("abc")).isEqualTo("/product/abc");
        String longSlug = "a".repeat(255 - "/product/".length());
        assertThat(StockAlertMessages.notificationPath(longSlug)).hasSize(255);
        assertThat(StockAlertMessages.notificationPath(longSlug + "a")).isNull();
        assertThat(StockAlertMessages.notificationPath(null)).isNull();
    }
}
