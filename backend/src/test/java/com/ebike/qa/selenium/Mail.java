package com.ebike.qa.selenium;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the fake SMTP sink. */
public final class Mail {
    private static final Pattern OTP = Pattern.compile("(?<![\\d=])(\\d{6})(?!\\d)");
    private static final Api HTTP = new Api();

    private Mail() { }

    private static JsonNode all() { return HTTP.get(QaConfig.get("mail.url")).json(); }

    /** Id of the newest mail now; mails with a higher id are the ones sent after this call. */
    public static long lastId() {
        long max = 0;
        for (JsonNode m : all()) max = Math.max(max, m.get("id").asLong());
        return max;
    }

    /** Waits for a mail to the address with an id above afterId and returns its raw text. */
    public static String waitFor(String to, long afterId) {
        long end = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        while (System.currentTimeMillis() < end) {
            JsonNode newest = null;
            for (JsonNode m : all()) {
                if (m.get("id").asLong() > afterId && m.get("to").toString().toLowerCase().contains(to.toLowerCase())) newest = m;
            }
            if (newest != null) return newest.get("text").asText();
            Ui.pause(300);
        }
        throw new AssertionError("no mail for " + to + " within 20s");
    }

    /** The 6-digit code from the newest mail to the address sent after afterId. */
    public static String waitForOtp(String to, long afterId) {
        String text = waitFor(to, afterId);
        int body = text.indexOf("\r\n\r\n");
        Matcher m = OTP.matcher(body < 0 ? text : text.substring(body));
        if (!m.find()) throw new AssertionError("no 6 digit code in: " + text);
        return m.group(1);
    }
}
