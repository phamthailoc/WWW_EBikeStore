package com.ebike.service.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;

/** V27: how amounts, dates and "amount in words" are written on a document, in Vietnamese or English. Pure, so it can be tested alone. */
public final class DocumentFormat {

    private static final String[] VI_DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] VI_SCALES = {"", "nghìn", "triệu", "tỷ"};

    private DocumentFormat() {
    }

    /** 1.234.000 ₫. A missing amount reads as zero. */
    public static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        BigDecimal rounded = (amount == null ? BigDecimal.ZERO : amount).setScale(0, RoundingMode.HALF_UP);
        return format.format(rounded) + " ₫";
    }

    /** 23/11/2026 14:05; a dash when there is no time. */
    public static String dateTime(LocalDateTime value) {
        if (value == null) {
            return "—";
        }
        return value.format(formatter("dd/MM/yyyy HH:mm"));
    }

    public static String date(LocalDateTime value) {
        if (value == null) {
            return "—";
        }
        return value.format(formatter("dd/MM/yyyy"));
    }

    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
    }

    /** "Một triệu hai trăm nghìn đồng chẵn". Amounts are whole dong; a negative one reads as zero. */
    public static String moneyInWords(BigDecimal amount) {
        long value = amount == null ? 0 : Math.max(0, amount.setScale(0, RoundingMode.HALF_UP).longValue());
        return capitalize(vietnameseWords(value)) + " đồng chẵn";
    }

    static String vietnameseWords(long value) {
        if (value == 0) {
            return VI_DIGITS[0];
        }
        // groups of three digits, lowest first
        int[] groups = new int[7];
        int count = 0;
        long rest = value;
        while (rest > 0 && count < groups.length) {
            groups[count++] = (int) (rest % 1000);
            rest /= 1000;
        }
        StringBuilder words = new StringBuilder();
        for (int index = count - 1; index >= 0; index--) {
            int group = groups[index];
            if (group == 0) {
                continue;
            }
            boolean hasHigher = words.length() > 0;
            append(words, vietnameseTriple(group, hasHigher));
            String scale = scaleName(index);
            if (!scale.isEmpty()) {
                append(words, scale);
            }
        }
        return words.toString();
    }

    private static String scaleName(int index) {
        if (index < VI_SCALES.length) {
            return VI_SCALES[index];
        }
        // 10^12 is "nghìn tỷ", 10^15 "triệu tỷ"
        return VI_SCALES[index - 3] + " tỷ";
    }

    private static String vietnameseTriple(int group, boolean hasHigher) {
        int hundreds = group / 100;
        int tens = (group / 10) % 10;
        int units = group % 10;
        StringBuilder words = new StringBuilder();
        if (hundreds > 0 || (hasHigher && (tens > 0 || units > 0))) {
            words.append(VI_DIGITS[hundreds]).append(" trăm");
        }
        if (tens > 1) {
            append(words, VI_DIGITS[tens] + " mươi");
        } else if (tens == 1) {
            append(words, "mười");
        } else if (units > 0 && words.length() > 0) {
            append(words, "lẻ");
        }
        if (units > 0) {
            String digit = VI_DIGITS[units];
            if (units == 1 && tens > 1) {
                digit = "mốt";
            } else if (units == 5 && tens > 0) {
                digit = "lăm";
            }
            append(words, digit);
        }
        return words.toString();
    }

    private static void append(StringBuilder words, String part) {
        if (part.isEmpty()) {
            return;
        }
        if (words.length() > 0) {
            words.append(' ');
        }
        words.append(part);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
