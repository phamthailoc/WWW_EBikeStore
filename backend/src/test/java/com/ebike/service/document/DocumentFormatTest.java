package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** V27: amounts, dates and "amount in words" as printed on a document. */
class DocumentFormatTest {

    @Test
    void vietnameseWordsReadTheNumbersTheWayTheyAreSpoken() {
        assertEquals("Không đồng chẵn", DocumentFormat.moneyInWords(BigDecimal.ZERO));
        assertEquals("Một đồng chẵn", DocumentFormat.moneyInWords(BigDecimal.ONE));
        assertEquals("Mười lăm đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("15")));
        assertEquals("Hai mươi mốt đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("21")));
        assertEquals("Một trăm lẻ năm đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("105")));
        assertEquals("Một triệu hai trăm ba mươi bốn nghìn đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("1234000")));
        assertEquals("Hai mươi triệu đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("20000000")));
    }

    @Test
    void vietnameseWordsKeepTheZeroHundredsInsideALongNumber() {
        assertEquals("một triệu không trăm lẻ năm nghìn", DocumentFormat.vietnameseWords(1_005_000));
        assertEquals("một tỷ không trăm mười nghìn", DocumentFormat.vietnameseWords(1_000_010_000));
        assertEquals("ba mươi lăm triệu năm trăm nghìn", DocumentFormat.vietnameseWords(35_500_000));
        assertEquals("một nghìn tỷ", DocumentFormat.vietnameseWords(1_000_000_000_000L));
    }

    @Test
    void moneyUsesDotGroupingAndRoundsToWholeDong() {
        assertEquals("1.234.000 ₫", DocumentFormat.money(new BigDecimal("1234000")));
        assertEquals("0 ₫", DocumentFormat.money(null));
        assertEquals("12.346 ₫", DocumentFormat.money(new BigDecimal("12345.50")));
    }

    @Test
    void datesAreDayFirstAndAMissingOneIsADash() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 3, 14, 5);
        assertEquals("03/11/2026 14:05", DocumentFormat.dateTime(time));
        assertEquals("03/11/2026", DocumentFormat.date(time));
        assertEquals("—", DocumentFormat.dateTime(null));
        assertEquals("—", DocumentFormat.date(null));
    }

    @Test
    void aNegativeOrMissingAmountReadsAsZero() {
        assertTrue(DocumentFormat.moneyInWords(new BigDecimal("-5")).startsWith("Không"));
        assertTrue(DocumentFormat.moneyInWords(null).startsWith("Không"));
    }
}
