package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** V27: amounts, dates and "amount in words" as printed on a document. */
class DocumentFormatTest {

    @Test
    void moneyUsesTheGroupingOfTheLanguage() {
        assertEquals("1.234.000 ₫", DocumentFormat.money(new BigDecimal("1234000"), "vi"));
        assertEquals("1,234,000 ₫", DocumentFormat.money(new BigDecimal("1234000"), "en"));
        assertEquals("0 ₫", DocumentFormat.money(null, "vi"));
        assertEquals("12.346 ₫", DocumentFormat.money(new BigDecimal("12345.50"), "vi"));
    }

    @Test
    void anUnknownLanguageIsVietnamese() {
        assertEquals("vi", DocumentFormat.language(null));
        assertEquals("vi", DocumentFormat.language("fr"));
        assertEquals("en", DocumentFormat.language("EN"));
        assertEquals("en", DocumentFormat.language(" en-US"));
    }

    @Test
    void datesFollowTheLanguageAndAMissingOneIsADash() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 3, 14, 5);
        assertEquals("03/11/2026 14:05", DocumentFormat.dateTime(time, "vi"));
        assertEquals("03 Nov 2026 14:05", DocumentFormat.dateTime(time, "en"));
        assertEquals("03/11/2026", DocumentFormat.date(time, "vi"));
        assertEquals("—", DocumentFormat.dateTime(null, "vi"));
        assertEquals("—", DocumentFormat.date(null, "en"));
    }

    @Test
    void vietnameseWordsReadTheNumbersTheWayTheyAreSpoken() {
        assertEquals("Không đồng chẵn", DocumentFormat.moneyInWords(BigDecimal.ZERO, "vi"));
        assertEquals("Một đồng chẵn", DocumentFormat.moneyInWords(BigDecimal.ONE, "vi"));
        assertEquals("Mười lăm đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("15"), "vi"));
        assertEquals("Hai mươi mốt đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("21"), "vi"));
        assertEquals("Một trăm lẻ năm đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("105"), "vi"));
        assertEquals("Một triệu hai trăm ba mươi bốn nghìn đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("1234000"), "vi"));
        assertEquals("Hai mươi triệu đồng chẵn", DocumentFormat.moneyInWords(new BigDecimal("20000000"), "vi"));
    }

    @Test
    void vietnameseWordsKeepTheZeroHundredsInsideALongNumber() {
        assertEquals("một triệu không trăm lẻ năm nghìn", DocumentFormat.vietnameseWords(1_005_000));
        assertEquals("một tỷ không trăm mười nghìn", DocumentFormat.vietnameseWords(1_000_010_000));
        assertEquals("ba mươi lăm triệu năm trăm nghìn", DocumentFormat.vietnameseWords(35_500_000));
        assertEquals("một nghìn tỷ", DocumentFormat.vietnameseWords(1_000_000_000_000L));
    }

    @Test
    void englishWordsAndTheDongSuffix() {
        assertEquals("Zero Vietnamese dong", DocumentFormat.moneyInWords(BigDecimal.ZERO, "en"));
        assertEquals("Twenty-one Vietnamese dong", DocumentFormat.moneyInWords(new BigDecimal("21"), "en"));
        assertEquals("One million two hundred thirty-four thousand Vietnamese dong", DocumentFormat.moneyInWords(new BigDecimal("1234000"), "en"));
        assertEquals("Twenty million Vietnamese dong", DocumentFormat.moneyInWords(new BigDecimal("20000000"), "en"));
    }

    @Test
    void aNegativeOrMissingAmountReadsAsZero() {
        assertTrue(DocumentFormat.moneyInWords(new BigDecimal("-5"), "vi").startsWith("Không"));
        assertTrue(DocumentFormat.moneyInWords(null, "en").startsWith("Zero"));
    }
}
