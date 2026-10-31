package com.ebike.orderModule.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnPayUtil {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private VnPayUtil() {
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
