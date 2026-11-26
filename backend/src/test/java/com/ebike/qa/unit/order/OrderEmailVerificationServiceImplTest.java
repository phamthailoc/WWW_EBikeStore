package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderEmailOtpSendRequest;
import com.ebike.dto.order.request.OrderEmailOtpVerifyRequest;
import com.ebike.dto.order.response.OrderEmailOtpSendResponse;
import com.ebike.dto.order.response.OrderEmailOtpVerifyResponse;
import com.ebike.model.entity.order.OrderEmailVerificationSession;
import com.ebike.model.entity.order.OrderEmailVerificationStatus;
import com.ebike.repository.order.OrderEmailVerificationSessionRepository;
import com.ebike.service.order.impl.OrderEmailSender;
import com.ebike.service.order.impl.OrderEmailVerificationServiceImpl;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderEmailVerificationServiceImplTest {

    @Mock OrderEmailVerificationSessionRepository repository;
    @Mock OrderEmailSender emailSender;
    OrderEmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderEmailVerificationServiceImpl(repository, emailSender);
        when(repository.save(any(OrderEmailVerificationSession.class))).thenAnswer(invocation -> {
            OrderEmailVerificationSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });
    }

    private static String sha256(String code) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private OrderEmailVerificationSession pending(String code, int attempts, LocalDateTime expiresAt) throws Exception {
        OrderEmailVerificationSession session = new OrderEmailVerificationSession();
        session.setId(UUID.randomUUID());
        session.setEmail("khach@example.com");
        session.setOtpHash(sha256(code));
        session.setOtpExpiresAt(expiresAt);
        session.setResendAvailableAt(LocalDateTime.now());
        session.setAttemptCount(attempts);
        session.setStatus(OrderEmailVerificationStatus.PENDING);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    private OrderEmailVerificationSession verified(String email, LocalDateTime expiresAt) {
        OrderEmailVerificationSession session = new OrderEmailVerificationSession();
        session.setId(UUID.randomUUID());
        session.setEmail(email);
        session.setOtpHash("x");
        session.setOtpExpiresAt(expiresAt);
        session.setStatus(OrderEmailVerificationStatus.VERIFIED);
        session.setVerifiedAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    // --- send ---------------------------------------------------------------------------------------

    @Test
    void send_createsAPendingSessionWithA15MinuteCodeAnd3Attempts() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        OrderEmailOtpSendResponse response = service.sendVerificationCode(new OrderEmailOtpSendRequest("  Khach@Example.COM "));

        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());
        OrderEmailVerificationSession session = saved.getValue();
        assertThat(session.getEmail()).isEqualTo("khach@example.com");
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.PENDING);
        assertThat(session.getAttemptCount()).isZero();
        assertThat(Duration.between(before, session.getOtpExpiresAt())).isBetween(Duration.ofMinutes(15).minusSeconds(2), Duration.ofMinutes(15).plusSeconds(2));
        assertThat(Duration.between(before, session.getResendAvailableAt())).isBetween(Duration.ofSeconds(58), Duration.ofSeconds(62));
        assertThat(response.email()).isEqualTo("khach@example.com");
        assertThat(response.resendAfterSeconds()).isEqualTo(60);
        assertThat(response.maxAttempts()).isEqualTo(3);
        assertThat(response.verificationSessionId()).isEqualTo(session.getId());
    }

    @Test
    void send_theEmailedCodeIsSixDigitsAndOnlyItsHashIsStored() throws Exception {
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendOtp(org.mockito.ArgumentMatchers.eq("khach@example.com"), code.capture());
        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());

        assertThat(code.getValue()).matches("[1-9]\\d{5}");
        assertThat(saved.getValue().getOtpHash()).isEqualTo(sha256(code.getValue())).doesNotContain(code.getValue()).hasSize(64);
    }

    @Test
    void send_generatesDifferentCodesOverManyCalls() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"));
        }
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(40)).sendOtp(anyString(), captor.capture());
        codes.addAll(captor.getAllValues());
        assertThat(codes.size()).isGreaterThan(30);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "plain", "a@b", "a@b.c", "@example.com", "a b@example.com", "a@@example.com", "a@example.", "user@.com"})
    void send_rejectsInvalidEmails(String email) {
        assertBadRequest(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest(email)));
        verify(emailSender, never()).sendOtp(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void send_nullRequestIs400() {
        assertBadRequest(() -> service.sendVerificationCode(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.vn", "first.last+tag@sub.example.co.uk", "A_B-C@EXAMPLE.COM"})
    void send_acceptsNormalEmails(String email) {
        assertThat(service.sendVerificationCode(new OrderEmailOtpSendRequest(email)).email()).isEqualTo(email.toLowerCase());
    }

    @Test
    void send_aMailFailurePropagatesSoTheCustomerKnowsNoCodeWasSent() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(emailSender).sendOtp(anyString(), anyString());
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @Tag("bug")
    void bug_resendCooldownIsNotEnforcedOnTheServer() {
        // BUG (Medium): OrderEmailVerificationServiceImpl.java:47-70 sendVerificationCode writes resendAvailableAt (+60 s) and tells the browser
        // "resend after 60 seconds", but nothing ever reads it: a second request for the same email a moment later sends another mail and creates
        // another session. Expected: the second call within 60 s is refused (429/400) and no second mail goes out. Actual: both succeed.
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"));
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com")))
            .isInstanceOf(ResponseStatusException.class);
        verify(emailSender, times(1)).sendOtp(anyString(), anyString());
    }

    // --- verify -------------------------------------------------------------------------------------

    @Test
    void verify_nullRequestOrSessionIdIs400() {
        assertBadRequest(() -> service.verifyCode(null));
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(null, "123456")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "12345a", "abcdef", "12 456", "١٢٣٤٥٦", "-12345", "12.456"})
    void verify_rejectsCodesThatAreNotExactlySixDigits(String code) {
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(UUID.randomUUID(), code)));
        verify(repository, never()).findById(any());
    }

    @Test
    void verify_unknownSessionIs404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(id, "123456")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void verify_correctCodeVerifiesTheSession() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        OrderEmailOtpVerifyResponse response = service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321"));
        assertThat(response.verified()).isTrue();
        assertThat(response.email()).isEqualTo("khach@example.com");
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.VERIFIED);
        assertThat(session.getVerifiedAt()).isNotNull();
    }

    @Test
    void verify_surroundingWhitespaceInTheCodeIsIgnored() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), " 654321 "));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.VERIFIED);
    }

    @Test
    void verify_wrongCodeCountsAnAttemptAndIsSaved() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "111111")));
        assertThat(session.getAttemptCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.PENDING);
        verify(repository).save(session);
    }

    @Test
    void verify_thirdWrongCodeCancelsTheSession() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 2, LocalDateTime.now().plusMinutes(10));
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "111111")));
        assertThat(session.getAttemptCount()).isEqualTo(3);
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.CANCELLED);
    }

    @Test
    void verify_afterThreeWrongCodesEvenTheRightCodeIsRefused() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        for (int i = 0; i < 3; i++) {
            assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "000000")));
        }
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.CANCELLED);
    }

    @Test
    void verify_twoWrongThenTheRightCodeStillWorks() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 2, LocalDateTime.now().plusMinutes(10));
        service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321"));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.VERIFIED);
    }

    @Test
    void verify_aPendingSessionAlreadyAtTheLimitIsCancelled() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 3, LocalDateTime.now().plusMinutes(10));
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.CANCELLED);
    }

    @Test
    void verify_nullAttemptCountIsTreatedAsZero() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        session.setAttemptCount(null);
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "111111")));
        assertThat(session.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void verify_expiredCodeMarksTheSessionExpiredEvenForTheRightCode() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().minusSeconds(1));
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.EXPIRED);
    }

    @Test
    void verify_aCodeThatExpiresInAMomentIsStillAccepted() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusSeconds(30));
        service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321"));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.VERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERIFIED", "CANCELLED", "EXPIRED"})
    void verify_aSessionThatIsNotPendingCannotBeVerifiedAgain(String status) throws Exception {
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        session.setStatus(OrderEmailVerificationStatus.valueOf(status));
        assertBadRequest(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")));
    }

    @Test
    @Tag("bug")
    void bug_wrongOtpAttemptsAreRolledBackSoTheLimitOf3NeverHolds() throws Exception {
        // BUG (Critical): OrderEmailVerificationServiceImpl.java:78-79 verifyCode is a plain @Transactional method. A wrong code increments
        // attemptCount (and at 3 sets CANCELLED) and then throws ResponseStatusException, a RuntimeException: Spring rolls the transaction
        // back, so the increment is never committed. The counter stays at 0 forever and a 6-digit code can be brute forced
        // (only the per-IP rate limit protects it). The same rollback undoes the EXPIRED/CANCELLED status written by ensurePending().
        // Expected: noRollbackFor = ResponseStatusException (or a REQUIRES_NEW write) so the counter persists. Actual: default rollback rules.
        Method verify = OrderEmailVerificationServiceImpl.class.getMethod("verifyCode", OrderEmailOtpVerifyRequest.class);
        Transactional tx = verify.getAnnotation(Transactional.class);
        assertThat(tx).as("verifyCode is transactional").isNotNull();
        boolean keepsTheAttemptCounter = java.util.Arrays.stream(tx.noRollbackFor()).anyMatch(type -> type.isAssignableFrom(ResponseStatusException.class))
            || java.util.Arrays.stream(tx.noRollbackForClassName()).anyMatch(name -> name.contains("ResponseStatusException") || name.contains("RuntimeException"))
            || tx.propagation() == org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;
        assertThat(keepsTheAttemptCounter).as("the wrong-attempt counter must survive the exception").isTrue();
    }

    // --- assertVerifiedForOrder ---------------------------------------------------------------------

    @Test
    void assert_nullSessionIdIs400() {
        assertBadRequest(() -> service.assertVerifiedForOrder(null, "khach@example.com"));
    }

    @Test
    void assert_unknownSessionIs400() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertBadRequest(() -> service.assertVerifiedForOrder(id, "khach@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "EXPIRED", "CANCELLED"})
    void assert_onlyAVerifiedSessionCountsAsProof(String status) {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        session.setStatus(OrderEmailVerificationStatus.valueOf(status));
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), "khach@example.com"));
    }

    @Test
    void assert_verifiedStatusWithoutAVerifiedTimeIsNotProof() {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        session.setVerifiedAt(null);
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), "khach@example.com"));
    }

    @Test
    void assert_theSameProofCanBeUsedOnlyOnce() {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        service.assertVerifiedForOrder(session.getId(), "khach@example.com");
        assertThat(session.getConsumedAt()).isNotNull();
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), "khach@example.com"));
    }

    @Test
    void assert_proofForAnotherEmailIsRefusedAndNotConsumed() {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), "someone.else@example.com"));
        assertThat(session.getConsumedAt()).isNull();
    }

    @Test
    void assert_emailIsMatchedCaseInsensitivelyAndTrimmed() {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        service.assertVerifiedForOrder(session.getId(), "  KHACH@Example.com ");
        assertThat(session.getConsumedAt()).isNotNull();
    }

    @Test
    void assert_nullOrBlankEmailNeverMatches() {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), null));
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), "  "));
        assertThat(session.getConsumedAt()).isNull();
    }

    @Test
    void assert_anExpiredProofIsRefusedAndMarkedExpired() {
        OrderEmailVerificationSession session = verified("khach@example.com", LocalDateTime.now().minusSeconds(1));
        assertBadRequest(() -> service.assertVerifiedForOrder(session.getId(), "khach@example.com"));
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.EXPIRED);
        assertThat(session.getConsumedAt()).isNull();
    }
}
