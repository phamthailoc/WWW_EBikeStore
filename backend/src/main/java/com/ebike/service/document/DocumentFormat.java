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
    private static final String[] EN_ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] EN_TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final String[] EN_SCALES = {"", "thousand", "million", "billion", "trillion"};

    private DocumentFormat() {
    }

    /** "en" or "vi": anything that does not start with "en" is Vietnamese, the shop's base language. */
    public static String language(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    /** 1.234.000 ₫ in Vietnamese, 1,234,000 ₫ in English. A missing amount reads as zero. */
    public static String money(BigDecimal amount, String language) {
        boolean english = "en".equals(language(language));
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(english ? ',' : '.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        BigDecimal rounded = (amount == null ? BigDecimal.ZERO : amount).setScale(0, RoundingMode.HALF_UP);
        return format.format(rounded) + " ₫";
    }

    /** 23/11/2026 14:05 in Vietnamese, 23 Nov 2026 14:05 in English; a dash when there is no time. */
    public static String dateTime(LocalDateTime value, String language) {
        if (value == null) {
            return "—";
        }
        return value.format(formatter(language, "dd/MM/yyyy HH:mm", "dd MMM yyyy HH:mm"));
    }

    public static String date(LocalDateTime value, String language) {
        if (value == null) {
            return "—";
        }
        return value.format(formatter(language, "dd/MM/yyyy", "dd MMM yyyy"));
    }

    private static DateTimeFormatter formatter(String language, String vietnamese, String english) {
        return "en".equals(language(language))
            ? DateTimeFormatter.ofPattern(english, Locale.ENGLISH)
            : DateTimeFormatter.ofPattern(vietnamese, Locale.ROOT);
    }

    /** "Một triệu hai trăm nghìn đồng chẵn" or "One million two hundred thousand Vietnamese dong". Amounts are whole dong; a negative one reads as zero. */
    public static String moneyInWords(BigDecimal amount, String language) {
        long value = amount == null ? 0 : Math.max(0, amount.setScale(0, RoundingMode.HALF_UP).longValue());
        if ("en".equals(language(language))) {
            return capitalize(englishWords(value)) + " Vietnamese dong";
        }
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

    static String englishWords(long value) {
        if (value == 0) {
            return EN_ONES[0];
        }
        ArrayDeque<String> parts = new ArrayDeque<>();
        int scale = 0;
        long rest = value;
        while (rest > 0) {
            int group = (int) (rest % 1000);
            if (group > 0) {
                String name = EN_SCALES[Math.min(scale, EN_SCALES.length - 1)];
                String text = englishTriple(group);
                parts.addFirst(name.isEmpty() ? text : text + " " + name);
            }
            rest /= 1000;
            scale++;
        }
        StringBuilder words = new StringBuilder();
        for (String part : parts) {
            append(words, part);
        }
        return words.toString();
    }

    private static String englishTriple(int group) {
        StringBuilder words = new StringBuilder();
        int hundreds = group / 100;
        int rest = group % 100;
        if (hundreds > 0) {
            words.append(EN_ONES[hundreds]).append(" hundred");
        }
        if (rest > 0) {
            if (rest < 20) {
                append(words, EN_ONES[rest]);
            } else {
                String tens = EN_TENS[rest / 10];
                append(words, rest % 10 == 0 ? tens : tens + "-" + EN_ONES[rest % 10]);
            }
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
