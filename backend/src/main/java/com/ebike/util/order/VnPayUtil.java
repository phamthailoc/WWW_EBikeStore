package com.ebike.util.order;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnPayUtil {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** The zone VNPay reads and writes its dates in. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** How long a payment page stays open after it was created. */
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);
    private static final String TXN_REF_PREFIX = "EBIKE";

    private VnPayUtil() {
    }

    /** When the payment attempt of a transaction reference was started (the reference carries it after the prefix), or null when it cannot be read. */
    public static LocalDateTime attemptStartedAt(String txnRef) {
        if (txnRef == null || !txnRef.startsWith(TXN_REF_PREFIX) || txnRef.length() < TXN_REF_PREFIX.length() + 14) {
            return null;
        }
        try {
            return LocalDateTime.parse(txnRef.substring(TXN_REF_PREFIX.length(), TXN_REF_PREFIX.length() + 14), VNPAY_DATE_FORMAT);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    /** True while the payment page of the reference may still be open, so the customer can still pay on it. */
    public static boolean attemptMayBeOpen(String txnRef, LocalDateTime nowInVnPayZone) {
        LocalDateTime started = attemptStartedAt(txnRef);
        return started != null && started.plus(PAYMENT_WINDOW).isAfter(nowInVnPayZone);
    }

    public static String formatDate(LocalDateTime dateTime) {
        return dateTime.format(VNPAY_DATE_FORMAT);
    }

    public static String buildQuery(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
            .filter(entry -> isPresent(entry.getValue()))
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    public static String hmacSHA512(String secretKey, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hash.append(String.format("%02x", value & 0xff));
            }
            return hash.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign VNPay payload", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
