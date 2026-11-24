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

}
