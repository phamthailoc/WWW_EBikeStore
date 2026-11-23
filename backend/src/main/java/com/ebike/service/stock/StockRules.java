package com.ebike.service.stock;

import com.ebike.model.entity.stock.StockMovementReason;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The pure rules of V26 (stock receipts and the stock ledger), kept apart from the database so they can be tested alone. */
public final class StockRules {

    /** A receipt with more lines than this is a sign of a mistake (and of a request meant to hurt the server). */
    public static final int MAX_LINES = 30;
    /** One line is one delivery of one colour; a showroom does not receive more than this in one go. */
    public static final int MAX_LINE_QUANTITY = 1000;
    /** The most a colour (or a bike) may hold, so that a typing mistake cannot fill the shelves with a million bikes. */
    public static final int MAX_STOCK = 100_000;
    public static final int MAX_SUPPLIER_LENGTH = 150;
    public static final int MAX_NOTE_LENGTH = 500;

    private static final Pattern RECEIPT_NUMBER = Pattern.compile("^pn-?0*(\\d{1,18})$", Pattern.CASE_INSENSITIVE);

    private StockRules() {
    }

    /** What is wrong with a receipt's lines; the line is counted from 1 as the person sees it (0 when the whole receipt is meant). */
    public enum Kind { NO_LINES, TOO_MANY_LINES, MISSING_PRODUCT, BAD_QUANTITY, QUANTITY_TOO_LARGE, DUPLICATE_LINE }

    public record Problem(Kind kind, int line) {
    }

    public record LineInput(Long productId, Long variantId, Integer quantity) {
    }

    /** The first thing wrong with the lines, or null when they can be received. */
    public static Problem checkLines(List<LineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            return new Problem(Kind.NO_LINES, 0);
        }
        if (lines.size() > MAX_LINES) {
            return new Problem(Kind.TOO_MANY_LINES, 0);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            LineInput line = lines.get(i);
            if (line == null || line.productId() == null || line.productId() <= 0
                || (line.variantId() != null && line.variantId() <= 0)) {
                return new Problem(Kind.MISSING_PRODUCT, i + 1);
            }
            if (line.quantity() == null || line.quantity() <= 0) {
                return new Problem(Kind.BAD_QUANTITY, i + 1);
            }
            if (line.quantity() > MAX_LINE_QUANTITY) {
                return new Problem(Kind.QUANTITY_TOO_LARGE, i + 1);
            }
            // one colour twice in a receipt is almost always a slip; asking for one line keeps the note readable
            if (!seen.add(line.productId() + ":" + line.variantId())) {
                return new Problem(Kind.DUPLICATE_LINE, i + 1);
            }
        }
        return null;
    }

    /** True when {@code add} more bikes still fit on top of the current stock. */
    public static boolean fitsOnShelf(Integer current, int add) {
        return (long) (current == null ? 0 : current) + add <= MAX_STOCK;
    }

    /** The change an adjustment makes: new stock minus old stock (a missing old figure counts as none). */
    public static int adjustmentDelta(Integer before, int after) {
        return after - (before == null ? 0 : before);
    }

    /** The number printed for a receipt: PN-000012. It is derived from the id, so it never needs a column of its own. */
    public static String receiptNumber(Long id) {
        return "PN-" + String.format(Locale.ROOT, "%06d", id == null ? 0L : id);
    }

    /** The id a typed receipt number points to ("PN-000012", "pn12"), or null when the text is not a receipt number. */
    public static Long receiptIdOf(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = RECEIPT_NUMBER.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            long id = Long.parseLong(matcher.group(1));
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** A LIKE pattern for a typed supplier name: lower case, with the characters LIKE treats as wildcards taken out; nothing typed matches everything. */
    public static String likePattern(String raw) {
        if (raw == null) {
            return "%";
        }
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", " ").trim();
        return cleaned.isEmpty() ? "%" : "%" + cleaned + "%";
    }

    /** Whitespace trimmed and doubled spaces collapsed; null for a missing or blank text. */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("[ \\t]{2,}", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** True when the lines of a colour add up to the colour's stock, i.e. nothing changed the stock outside the ledger. */
    public static boolean ledgerMatches(Integer stock, long sumOfLines) {
        return (stock == null ? 0 : stock) == sumOfLines;
    }

    /** Sales take bikes away; receipts, cancellations and the opening balance add them; an adjustment goes either way but is never zero. */
    public static boolean signMatches(StockMovementReason reason, int change) {
        if (reason == null || change == 0) {
            return false;
        }
        return switch (reason) {
            case SALE -> change < 0;
            case RECEIPT, CANCEL_RESTORE, OPENING -> change > 0;
            case ADJUSTMENT -> true;
        };
    }

    /** The reasons a history request names ("RECEIPT,SALE"); no text means all of them. A name that is not a reason throws IllegalArgumentException. */
    public static Set<StockMovementReason> parseReasons(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.allOf(StockMovementReason.class);
        }
        Set<StockMovementReason> reasons = EnumSet.noneOf(StockMovementReason.class);
        for (String part : csv.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                reasons.add(StockMovementReason.valueOf(name.toUpperCase(Locale.ROOT)));
            }
        }
        return reasons.isEmpty() ? EnumSet.allOf(StockMovementReason.class) : reasons;
    }

    /** The name of the person who did it, as the page shows it: full name, else the username; the shell of an erased account reads as its anonymised name. */
    public static String displayName(String firstName, String lastName, String username) {
        String full = ((firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim())).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return username == null || username.isBlank() ? null : username.trim();
    }
}
