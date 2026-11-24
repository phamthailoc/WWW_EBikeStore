package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** V24: the notification and email words are in the language of the customer's profile, and say what to do next. */
class InstallmentMessagesTest {

    private static final BigDecimal DOWN = new BigDecimal("9000000");
    private static final BigDecimal MONTHLY = new BigDecimal("1925000");
    private static final OffsetDateTime DEADLINE = OffsetDateTime.of(2026, 11, 25, 9, 30, 0, 0, ZoneOffset.ofHours(7));

    @Test
    void anApprovalWithoutADeadlineLeavesTheDeadlineSentenceOut() {
        String en = InstallmentMessages.body(InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 6, null, null);
        assertFalse(en.contains("Please pay it before"));
    }

}
