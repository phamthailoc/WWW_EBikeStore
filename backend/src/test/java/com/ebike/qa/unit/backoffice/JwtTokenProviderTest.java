package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebike.model.entity.auth.User;
import com.ebike.service.auth.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 60_000L);
    }

    private static Claims parse(String token, String secret) {
        return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build()
            .parseClaimsJws(token).getBody();
    }

    @Test
    void generatedToken_carriesSubjectAndClaims() {
        User u = Fx.user(5L, "tien", "tien@example.com", "MANAGER");
        u.setFirstName("Le");
        u.setLastName("Tien");
        Claims c = parse(provider.generateToken(u), SECRET);
        assertThat(c.getSubject()).isEqualTo("tien");
        assertThat(c.get("userId", Integer.class)).isEqualTo(5);
        assertThat(c.get("email", String.class)).isEqualTo("tien@example.com");
        assertThat(c.get("roles", String.class)).isEqualTo("MANAGER");
        assertThat(c.get("fullName", String.class)).isEqualTo("Le Tien");
    }

    @Test
    void generatedToken_neverContainsPasswordHash() {
        User u = Fx.user(5L, "tien", "tien@example.com", "CUSTOMER");
        String token = provider.generateToken(u);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("hash:").doesNotContainIgnoringCase("password");
    }

    @Test
    void fullName_fallsBackToUsername_whenNamesMissingOrBlank() {
        User u = Fx.user(5L, "tien", "tien@example.com", "CUSTOMER");
        u.setFirstName("  ");
        u.setLastName(null);
        assertThat(parse(provider.generateToken(u), SECRET).get("fullName", String.class)).isEqualTo("tien");
    }

    @Test
    void expiry_isIssuedAtPlusConfiguredLifetime() {
        Claims c = parse(provider.generateToken(Fx.user(1L, "a", "a@b.com", "CUSTOMER")), SECRET);
        assertThat(c.getExpiration().getTime() - c.getIssuedAt().getTime()).isEqualTo(60_000L);
    }

    @Test
    void validateToken_trueForFreshToken_andExtractUsernameWorks() {
        String token = provider.generateToken(Fx.user(1L, "tien", "a@b.com", "CUSTOMER"));
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.extractUsername(token)).isEqualTo("tien");
    }

    @Test
    void expiredToken_isInvalid_andUsernameIsNull() {
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", -1_000L);
        String token = provider.generateToken(Fx.user(1L, "tien", "a@b.com", "CUSTOMER"));
        assertThat(provider.validateToken(token)).isFalse();
        assertThat(provider.extractUsername(token)).isNull();
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        String forged = Jwts.builder().setSubject("admin").claim("roles", "ADMIN")
            .setExpiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
        assertThat(provider.validateToken(forged)).isFalse();
        assertThat(provider.extractUsername(forged)).isNull();
    }

    @Test
    void tamperedPayload_isRejected() {
        String token = provider.generateToken(Fx.user(1L, "tien", "a@b.com", "CUSTOMER"));
        String[] parts = token.split("\\.");
        String evil = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"admin\",\"roles\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(provider.validateToken(parts[0] + "." + evil + "." + parts[2])).isFalse();
    }

    @Test
    void unsignedAlgNoneToken_isRejected() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String body = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"admin\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(provider.validateToken(header + "." + body + ".")).isFalse();
    }

    @Test
    void garbageNullAndEmptyTokens_areInvalid_notExceptions() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
        assertThat(provider.validateToken(null)).isFalse();
        assertThat(provider.extractUsername(null)).isNull();
        assertThat(provider.extractUsername("a.b.c")).isNull();
    }

    @Test
    void secretShorterThan256Bits_cannotSignTokens() {
        ReflectionTestUtils.setField(provider, "jwtSecret", "short-secret");
        assertThatThrownBy(() -> provider.generateToken(Fx.user(1L, "a", "a@b.com", "CUSTOMER")))
            .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    @Test
    void multipleRoles_areJoinedWithComma() {
        User u = Fx.user(1L, "a", "a@b.com", "ADMIN", "MANAGER");
        String roles = parse(provider.generateToken(u), SECRET).get("roles", String.class);
        assertThat(roles.split(",")).containsExactlyInAnyOrder("ADMIN", "MANAGER");
    }
}
