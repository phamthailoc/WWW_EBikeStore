package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebike.util.order.VnPayUtil;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class VnPayUtilTest {

    // --- HMAC-SHA512 -------------------------------------------------------------------------------------

    @Test
    void hmac_matchesRfc4231TestVector() {
        // RFC 4231 test case 2
        assertThat(VnPayUtil.hmacSHA512("Jefe", "what do ya want for nothing?")).isEqualTo(
            "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737");
    }

    @Test
    void hmac_isLowerCaseHexOf128Characters() {
        String hash = VnPayUtil.hmacSHA512("secret", "data");
        assertThat(hash).hasSize(128).matches("[0-9a-f]{128}");
    }

    @Test
    void hmac_agreesWithTheJdkForUnicodeInput() throws Exception {
        String data = "vnp_OrderInfo=Thanh+toan+đơn+hàng+ĐÔ";
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("khóa-bí-mật".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder expected = new StringBuilder();
        for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            expected.append(String.format("%02x", b));
        }
        assertThat(VnPayUtil.hmacSHA512("khóa-bí-mật", data)).isEqualTo(expected.toString());
    }

    @Test
    void hmac_differentKeyOrDataGivesDifferentHash() {
        String base = VnPayUtil.hmacSHA512("k1", "a=1");
        assertThat(VnPayUtil.hmacSHA512("k2", "a=1")).isNotEqualTo(base);
        assertThat(VnPayUtil.hmacSHA512("k1", "a=2")).isNotEqualTo(base);
        assertThat(VnPayUtil.hmacSHA512("k1", "a=1")).isEqualTo(base);
    }

    @Test
    void hmac_emptyDataIsStillSigned() {
        assertThat(VnPayUtil.hmacSHA512("k", "")).hasSize(128);
    }

    @Test
    void hmac_emptyKeyIsRefusedWithAClearException() {
        // javax.crypto refuses an empty key: a shop with a blank VNPAY_HASH_SECRET must fail loudly, never sign with nothing
        assertThatThrownBy(() -> VnPayUtil.hmacSHA512("", "data")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hmac_nullKeyOrDataFailsLoudlyNotSilently() {
        assertThatThrownBy(() -> VnPayUtil.hmacSHA512(null, "data")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> VnPayUtil.hmacSHA512("k", null)).isInstanceOf(RuntimeException.class);
    }

    // --- query string -----------------------------------------------------------------------------------

    @Test
    void buildQuery_sortsKeysAlphabetically() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Amount", "100");
        params.put("vnp_Command", "pay");
        assertThat(VnPayUtil.buildQuery(params)).isEqualTo("vnp_Amount=100&vnp_Command=pay&vnp_Version=2.1.0");
    }

    @Test
    void buildQuery_skipsNullAndBlankValues() {
        Map<String, String> params = new HashMap<>();
        params.put("a", "1");
        params.put("b", null);
        params.put("c", "");
        params.put("d", "   ");
        assertThat(VnPayUtil.buildQuery(params)).isEqualTo("a=1");
    }

    @Test
    void buildQuery_urlEncodesValuesAndKeys() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_ReturnUrl", "http://localhost:8080/pay?x=1&y=2");
        params.put("vnp_OrderInfo", "Thanh toan don hang");
        assertThat(VnPayUtil.buildQuery(params))
            .isEqualTo("vnp_OrderInfo=Thanh+toan+don+hang&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8080%2Fpay%3Fx%3D1%26y%3D2");
    }

    @Test
    void buildQuery_encodesAmpersandAndEqualsSoAValueCannotInjectAParameter() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_OrderInfo", "x&vnp_Amount=1");
        String query = VnPayUtil.buildQuery(params);
        assertThat(query).isEqualTo("vnp_OrderInfo=x%26vnp_Amount%3D1");
        assertThat(query.split("&")).hasSize(1);
    }

    @Test
    void buildQuery_emptyMapIsEmptyString() {
        assertThat(VnPayUtil.buildQuery(new HashMap<>())).isEmpty();
    }

    @Test
    void buildQuery_doesNotTrimValuesButKeepsLeadingSpaceEncoded() {
        Map<String, String> params = new HashMap<>();
        params.put("k", " v");
        assertThat(VnPayUtil.buildQuery(params)).isEqualTo("k=+v");
    }

    @Test
    void buildQuery_isIndependentOfInsertionOrder_soSignatureIsStable() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("b", "2");
        first.put("a", "1");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("a", "1");
        second.put("b", "2");
        assertThat(VnPayUtil.buildQuery(first)).isEqualTo(VnPayUtil.buildQuery(second));
    }

    // --- txn ref time -----------------------------------------------------------------------------------

    @Test
    void attemptStartedAt_readsTheTimeAfterThePrefix() {
        assertThat(VnPayUtil.attemptStartedAt("EBIKE20261126093015ORD2026AB12CD34"))
            .isEqualTo(LocalDateTime.of(2026, 11, 26, 9, 30, 15));
    }

    @Test
    void attemptStartedAt_isNullWhenTheReferenceIsNotOurs() {
        assertThat(VnPayUtil.attemptStartedAt(null)).isNull();
        assertThat(VnPayUtil.attemptStartedAt("")).isNull();
        assertThat(VnPayUtil.attemptStartedAt("OTHER20261126093015XYZ")).isNull();
        assertThat(VnPayUtil.attemptStartedAt("ebike20261126093015XYZ")).isNull();
    }

    @Test
    void attemptStartedAt_isNullWhenTooShortOrNotADate() {
        assertThat(VnPayUtil.attemptStartedAt("EBIKE2026112609301")).isNull(); // 13 digits
        assertThat(VnPayUtil.attemptStartedAt("EBIKE20261326093015")).isNull(); // month 13
        assertThat(VnPayUtil.attemptStartedAt("EBIKE20261126253015")).isNull(); // hour 25
        assertThat(VnPayUtil.attemptStartedAt("EBIKEabcdefghijklmnop")).isNull();
    }

    @Test
    void attemptStartedAt_exactlyFourteenDigitsIsEnough() {
        assertThat(VnPayUtil.attemptStartedAt("EBIKE20261126093015")).isNotNull();
    }

    // --- 15 minute window -------------------------------------------------------------------------------

    @Test
    void paymentWindowIsFifteenMinutes() {
        assertThat(VnPayUtil.PAYMENT_WINDOW).isEqualTo(Duration.ofMinutes(15));
        assertThat(VnPayUtil.ZONE.getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void attemptMayBeOpen_trueInsideTheWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 26, 9, 30, 0);
        assertThat(VnPayUtil.attemptMayBeOpen("EBIKE20261126093000X", start)).isTrue();
        assertThat(VnPayUtil.attemptMayBeOpen("EBIKE20261126093000X", start.plusMinutes(14).plusSeconds(59))).isTrue();
    }

    @Test
    void attemptMayBeOpen_falseExactlyAtAndAfterTheBoundary() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 26, 9, 30, 0);
        assertThat(VnPayUtil.attemptMayBeOpen("EBIKE20261126093000X", start.plusMinutes(15))).isFalse();
        assertThat(VnPayUtil.attemptMayBeOpen("EBIKE20261126093000X", start.plusMinutes(15).plusSeconds(1))).isFalse();
    }

    @Test
    void attemptMayBeOpen_falseForAReferenceWeCannotRead() {
        assertThat(VnPayUtil.attemptMayBeOpen("garbage", LocalDateTime.now())).isFalse();
        assertThat(VnPayUtil.attemptMayBeOpen(null, LocalDateTime.now())).isFalse();
    }

    @Test
    void formatDate_usesVnPayPattern() {
        assertThat(VnPayUtil.formatDate(LocalDateTime.of(2026, 1, 2, 3, 4, 5))).isEqualTo("20260102030405");
    }

    @Test
    void formatDate_roundTripsThroughAttemptStartedAt() {
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 23, 59, 59);
        assertThat(VnPayUtil.attemptStartedAt("EBIKE" + VnPayUtil.formatDate(now) + "ORD")).isEqualTo(now);
    }
}
