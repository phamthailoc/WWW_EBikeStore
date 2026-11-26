package com.ebike.qa.selenium;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs a VNPay return query the way the gateway does (HMAC-SHA512 over the sorted, URL-encoded parameters). */
public final class Vnpay {
    private Vnpay() { }

    public static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(QaConfig.get("vnpay.hashSecret").getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String query(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    /** The query string of a return URL: the parameters plus vnp_SecureHash. */
    public static String signedQuery(Map<String, String> params) {
        String q = query(params);
        return q + "&vnp_SecureHash=" + hmac(q);
    }

    /** Value of a parameter in a URL's query. */
    public static String param(String url, String name) {
        String q = URI.create(url).getRawQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && pair.substring(0, i).equals(name)) return URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
        }
        return null;
    }
}
