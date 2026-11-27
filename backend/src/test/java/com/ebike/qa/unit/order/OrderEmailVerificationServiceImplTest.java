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
import com.ebike.service.order.OrderEmailVerificationService;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderEmailVerificationServiceImplTest {

    @Mock OrderEmailVerificationSessionRepository repository;
    static final String SECRET = "test-hmac-secret";
    static final String IP = "203.0.113.7";

    @Mock OrderEmailSender emailSender;
    OrderEmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderEmailVerificationServiceImpl(repository, emailSender, SECRET, 3, 5, 50);
        when(repository.save(any(OrderEmailVerificationSession.class))).thenAnswer(invocation -> {
            OrderEmailVerificationSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });
    }

    private static String hmac(String value) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String otpHash(String code) throws Exception {
        return hmac("otp:" + code);
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
        session.setOtpHash(otpHash(code));
        session.setOtpExpiresAt(expiresAt);
        session.setResendAvailableAt(LocalDateTime.now());
        session.setAttemptCount(attempts);
        session.setStatus(OrderEmailVerificationStatus.PENDING);
        when(repository.findLockedById(session.getId())).thenReturn(Optional.of(session));
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
        when(repository.findLockedById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    // --- send ---------------------------------------------------------------------------------------

    @Test
    void send_createsAPendingSessionWithA15MinuteCodeAnd3Attempts() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        OrderEmailOtpSendResponse response = service.sendVerificationCode(new OrderEmailOtpSendRequest("  Khach@Example.COM "), IP);

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
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendOtp(org.mockito.ArgumentMatchers.eq("khach@example.com"), code.capture());
        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());

        assertThat(code.getValue()).matches("[1-9]\\d{5}");
        assertThat(saved.getValue().getOtpHash()).isEqualTo(otpHash(code.getValue())).doesNotContain(code.getValue()).hasSize(64);
    }

    @Test
    void send_generatesDifferentCodesOverManyCalls() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
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
        assertBadRequest(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest(email), IP));
        verify(emailSender, never()).sendOtp(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void send_nullRequestIs400() {
        assertBadRequest(() -> service.sendVerificationCode(null, IP));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.vn", "first.last+tag@sub.example.co.uk", "A_B-C@EXAMPLE.COM"})
    void send_acceptsNormalEmails(String email) {
        assertThat(service.sendVerificationCode(new OrderEmailOtpSendRequest(email), IP).email()).isEqualTo(email.toLowerCase());
    }

    @Test
    void send_aMailFailurePropagatesSoTheCustomerKnowsNoCodeWasSent() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(emailSender).sendOtp(anyString(), anyString());
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void regression_resendCooldownIsNotEnforcedOnTheServer() {
        rememberSavedSessionsPerEmail();
        // BUG (Medium): OrderEmailVerificationServiceImpl.java:47-70 sendVerificationCode writes resendAvailableAt (+60 s) and tells the browser
        // "resend after 60 seconds", but nothing ever reads it: a second request for the same email a moment later sends another mail and creates
        // another session. Expected: the second call within 60 s is refused (429/400) and no second mail goes out. Actual: both succeed.
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP))
            .isInstanceOf(ResponseStatusException.class);
        verify(emailSender, times(1)).sendOtp(anyString(), anyString());
    }

    /** Makes the mocked repository answer the cooldown lookup with the newest session saved for that email, like the real query. */
    private void rememberSavedSessionsPerEmail() {
        java.util.Map<String, OrderEmailVerificationSession> latest = new java.util.HashMap<>();
        when(repository.save(any(OrderEmailVerificationSession.class))).thenAnswer(invocation -> {
            OrderEmailVerificationSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            latest.put(session.getEmail() + "|" + session.getRequesterKey(), session);
            return session;
        });
        when(repository.findFirstByEmailAndRequesterKeyOrderByCreatedAtDesc(anyString(), anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(latest.get(invocation.<String>getArgument(0) + "|" + invocation.<String>getArgument(1))));
    }

    @Test
    void send_secondRequestWithin60SecondsIs429WithRemainingSecondsAndSendsNoMail() {
        rememberSavedSessionsPerEmail();
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest(" Khach@Example.com "), IP))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(e.getReason()).matches("Vui lòng đợi (\\d+) giây trước khi gửi lại mã\\.");
                int seconds = Integer.parseInt(e.getReason().replaceAll("\\D+", ""));
                assertThat(seconds).isBetween(1, 60);
            });
        verify(emailSender, times(1)).sendOtp(anyString(), anyString());
        verify(repository, times(1)).save(any(OrderEmailVerificationSession.class));
    }

    @Test
    void send_aDifferentEmailIsNotHeldByAnotherAddressesCooldown() {
        rememberSavedSessionsPerEmail();
        service.sendVerificationCode(new OrderEmailOtpSendRequest("a@example.com"), IP);
        service.sendVerificationCode(new OrderEmailOtpSendRequest("b@example.com"), IP);
        verify(emailSender, times(2)).sendOtp(anyString(), anyString());
    }

    @Test
    void send_afterTheCooldownHasPassedANewCodeIsSent() {
        rememberSavedSessionsPerEmail();
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());
        saved.getValue().setResendAvailableAt(LocalDateTime.now().minusSeconds(1));

        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        verify(emailSender, times(2)).sendOtp(anyString(), anyString());
    }

    // --- B1b: requester-keyed cooldown, HMAC codes, daily caps ----------------------------------------

    @Test
    void cooldown_sameEmailFromAnotherRequesterIsNotHeld() {
        rememberSavedSessionsPerEmail();
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), "198.51.100.1");
        // the owner asks from another address while a stranger keeps the first one in cooldown
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), "198.51.100.2");
        verify(emailSender, times(2)).sendOtp(anyString(), anyString());
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), "198.51.100.1"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void cooldown_requesterKeyIsAKeyedHashNotTheRawIp() throws Exception {
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRequesterKey()).isEqualTo(hmac("ip:" + IP)).doesNotContain(IP).hasSize(64);
    }

    @Test
    void cooldown_lookupAlwaysUsesANonNullKeyEvenWithoutAnIp() throws Exception {
        // rows from before the column existed have a null key; the lookup must never ask for null, or it would match them
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), null);
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), "  ");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).findFirstByEmailAndRequesterKeyOrderByCreatedAtDesc(anyString(), key.capture());
        assertThat(key.getAllValues()).containsOnly(hmac("ip:unknown"));
    }

    @Test
    void hmac_storedHashIsNotThePlainSha256OfTheCode() throws Exception {
        service.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendOtp(anyString(), code.capture());
        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOtpHash()).isNotEqualTo(sha256(code.getValue())).isEqualTo(otpHash(code.getValue())).hasSize(64);
    }

    @Test
    void hmac_correctCodeVerifiesWithTheSameSecret() throws Exception {
        OrderEmailVerificationSession session = pending("123456", 0, LocalDateTime.now().plusMinutes(5));
        assertThat(service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "123456")).verified()).isTrue();
    }

    @Test
    void hmac_aServiceWithAnotherSecretRefusesTheCode() throws Exception {
        OrderEmailVerificationSession session = pending("123456", 0, LocalDateTime.now().plusMinutes(5));
        OrderEmailVerificationServiceImpl other = new OrderEmailVerificationServiceImpl(repository, emailSender, "another-secret", 3, 5, 50);
        assertBadRequest(() -> other.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "123456")));
    }

    @Test
    void hmac_blankSecretGeneratesARandomKeyThatStillWorksWithinOneInstance() {
        OrderEmailVerificationServiceImpl random = new OrderEmailVerificationServiceImpl(repository, emailSender, "  ", 3, 5, 50);
        random.sendVerificationCode(new OrderEmailOtpSendRequest("khach@example.com"), IP);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendOtp(anyString(), code.capture());
        ArgumentCaptor<OrderEmailVerificationSession> saved = ArgumentCaptor.forClass(OrderEmailVerificationSession.class);
        verify(repository).save(saved.capture());
        OrderEmailVerificationSession session = saved.getValue();
        when(repository.findLockedById(session.getId())).thenReturn(Optional.of(session));
        assertThat(random.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), code.getValue())).verified()).isTrue();
        // a second instance with its own random key cannot read the first one's codes
        OrderEmailVerificationServiceImpl random2 = new OrderEmailVerificationServiceImpl(repository, emailSender, "", 3, 5, 50);
        session.setStatus(OrderEmailVerificationStatus.PENDING);
        assertBadRequest(() -> random2.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), code.getValue())));
    }

    private static final String MAIL = "khach@example.com";

    private void assertTooMany(String ip, String message) {
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), ip))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(e.getReason()).isEqualTo(message);
            });
        verify(emailSender, never()).sendOtp(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    private void lockedCount(String ip, long count) throws Exception {
        when(repository.countByEmailAndRequesterKeyAndStatusAndAttemptCountGreaterThanEqualAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(MAIL), org.mockito.ArgumentMatchers.eq(hmac("ip:" + ip)),
            org.mockito.ArgumentMatchers.eq(OrderEmailVerificationStatus.CANCELLED), org.mockito.ArgumentMatchers.eq(3), any(LocalDateTime.class))).thenReturn(count);
    }

    private void sendsCount(String ip, long count) throws Exception {
        when(repository.countByEmailAndRequesterKeyAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(MAIL), org.mockito.ArgumentMatchers.eq(hmac("ip:" + ip)), any(LocalDateTime.class))).thenReturn(count);
    }

    @Test
    void dailyCap_threeLockedSessionsOfARequesterBlockItsNextSendWithANeutralMessage() throws Exception {
        lockedCount(IP, 3);
        assertTooMany(IP, "Đã nhập sai mã quá nhiều lần. Vui lòng thử lại sau 24 giờ.");
    }

    @Test
    void dailyCap_twoLockedSessionsStillAllowASend() throws Exception {
        lockedCount(IP, 2);
        service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), IP);
        verify(emailSender).sendOtp(anyString(), anyString());
    }

    @Test
    void dailyCap_fiveSendsFromOneRequesterBlockTheSixth() throws Exception {
        sendsCount(IP, 5);
        assertTooMany(IP, "Bạn đã yêu cầu quá nhiều mã cho email này hôm nay. Vui lòng thử lại sau.");
    }

    @Test
    void dailyCap_fourSendsFromOneRequesterStillAllowTheFifth() throws Exception {
        sendsCount(IP, 4);
        service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), IP);
        verify(emailSender).sendOtp(anyString(), anyString());
    }

    @Test
    void dailyCap_anotherRequesterIsNotBlockedByTheFirstOnesCaps() throws Exception {
        lockedCount(IP, 3);
        sendsCount(IP, 5);
        service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), "198.51.100.9");
        verify(emailSender).sendOtp(anyString(), anyString());
    }

    @Test
    void dailyCap_globalCapOf50SendsPerEmailStillAppliesToEveryone() {
        when(repository.countByEmailAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(MAIL), any(LocalDateTime.class))).thenReturn(50L);
        assertTooMany("198.51.100.9", "Email này đã nhận quá nhiều mã trong hôm nay. Vui lòng thử lại sau.");
    }

    @Test
    void dailyCap_49SendsPerEmailStillAllowASend() {
        when(repository.countByEmailAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(MAIL), any(LocalDateTime.class))).thenReturn(49L);
        service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), IP);
        verify(emailSender).sendOtp(anyString(), anyString());
    }

    @Test
    void dailyCap_countsOnlyTheLast24Hours() {
        LocalDateTime before = LocalDateTime.now();
        service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), IP);
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).countByEmailAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(MAIL), since.capture());
        assertThat(Duration.between(since.getValue(), before)).isBetween(Duration.ofHours(24).minusSeconds(5), Duration.ofHours(24).plusSeconds(5));
        verify(repository).countByEmailAndRequesterKeyAndCreatedAtAfter(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(since.getValue()));
    }

    @Test
    void dailyCap_lockCapWinsOverTheSendCapWhenBothAreReached() throws Exception {
        lockedCount(IP, 3);
        sendsCount(IP, 5);
        assertTooMany(IP, "Đã nhập sai mã quá nhiều lần. Vui lòng thử lại sau 24 giờ.");
    }

    @Test
    void dailyCap_cooldownIsCheckedBeforeTheCaps() throws Exception {
        rememberSavedSessionsPerEmail();
        service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), IP);
        sendsCount(IP, 5);
        assertThatThrownBy(() -> service.sendVerificationCode(new OrderEmailOtpSendRequest(MAIL), IP))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).startsWith("Vui lòng đợi"));
    }

    // --- transaction rules (a real transaction proxy over a recording transaction manager) ------------------

    private OrderEmailVerificationService proxied(PlatformTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.addInterface(OrderEmailVerificationService.class);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (OrderEmailVerificationService) factory.getProxy();
    }

    @Test
    void tx_aWrongCodeCommitsTheCounterInsteadOfRollingBack() throws Exception {
        PlatformTransactionManager manager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        OrderEmailVerificationService proxy = proxied(manager);

        assertBadRequest(() -> proxy.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "111111")));

        verify(manager).commit(any());
        verify(manager, never()).rollback(any());
        assertThat(session.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void tx_threeWrongCodesThroughTheProxyKeepTheSessionLockedAgainstTheRightCode() throws Exception {
        PlatformTransactionManager manager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        OrderEmailVerificationSession session = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        OrderEmailVerificationService proxy = proxied(manager);

        for (int i = 0; i < 3; i++) {
            assertBadRequest(() -> proxy.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "000000")));
        }
        assertThat(session.getAttemptCount()).isEqualTo(3);
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.CANCELLED);

        assertThatThrownBy(() -> proxy.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(manager, never()).rollback(any());
    }

    @Test
    void tx_anUnexpectedErrorStillRollsBack() {
        PlatformTransactionManager manager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID id = UUID.randomUUID();
        when(repository.findLockedById(id)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> proxied(manager).verifyCode(new OrderEmailOtpVerifyRequest(id, "123456")))
            .isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(any());
    }

    @Test
    void lock_verifyAndAssertReadTheSessionWithTheRowLockNeverWithPlainFindById() throws Exception {
        OrderEmailVerificationSession pendingSession = pending("654321", 0, LocalDateTime.now().plusMinutes(10));
        service.verifyCode(new OrderEmailOtpVerifyRequest(pendingSession.getId(), "654321"));
        OrderEmailVerificationSession proof = verified("khach@example.com", LocalDateTime.now().plusMinutes(5));
        service.assertVerifiedForOrder(proof.getId(), "khach@example.com");

        verify(repository).findLockedById(pendingSession.getId());
        verify(repository).findLockedById(proof.getId());
        verify(repository, never()).findById(any());
    }

    @Test
    void verify_theWrongCodeThatUsesTheLastAttemptSaysTheSessionIsLocked() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 2, LocalDateTime.now().plusMinutes(10));
        assertThatThrownBy(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "111111")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getReason()).isEqualTo("Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng gửi mã mới."));
    }

    @Test
    void verify_laterCallsOnASessionLockedByAttemptsKeepSayingSo() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 3, LocalDateTime.now().plusMinutes(10));
        session.setStatus(OrderEmailVerificationStatus.CANCELLED);
        assertThatThrownBy(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getReason()).startsWith("Bạn đã nhập sai OTP quá số lần cho phép."));
    }

    @Test
    void verify_aSessionNotLockedByAttemptsStaysGeneric() throws Exception {
        OrderEmailVerificationSession session = pending("654321", 1, LocalDateTime.now().plusMinutes(10));
        session.setStatus(OrderEmailVerificationStatus.EXPIRED);
        assertThatThrownBy(() -> service.verifyCode(new OrderEmailOtpVerifyRequest(session.getId(), "654321")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getReason()).isEqualTo("Phiên xác thực email không còn hiệu lực."));
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
        verify(repository, never()).findLockedById(any());
    }

    @Test
    void verify_unknownSessionIs404() {
        UUID id = UUID.randomUUID();
        when(repository.findLockedById(id)).thenReturn(Optional.empty());
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
    void regression_wrongOtpAttemptsAreRolledBackSoTheLimitOf3NeverHolds() throws Exception {
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
        when(repository.findLockedById(id)).thenReturn(Optional.empty());
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
