package com.ebike.service.order.impl;

import com.ebike.dto.order.request.OrderEmailOtpSendRequest;
import com.ebike.dto.order.request.OrderEmailOtpVerifyRequest;
import com.ebike.dto.order.response.OrderEmailOtpSendResponse;
import com.ebike.dto.order.response.OrderEmailOtpVerifyResponse;
import com.ebike.model.entity.order.OrderEmailVerificationSession;
import com.ebike.model.entity.order.OrderEmailVerificationStatus;
import com.ebike.repository.order.OrderEmailVerificationSessionRepository;
import com.ebike.service.order.OrderEmailVerificationService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderEmailVerificationServiceImpl implements OrderEmailVerificationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP_PATTERN = Pattern.compile("^\\d{6}$");
    private static final int OTP_TTL_MINUTES = 15;
    private static final int RESEND_AFTER_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 3;
    private static final String LOCKED_MESSAGE = "Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng gửi mã mới.";

    private static final Logger log = LoggerFactory.getLogger(OrderEmailVerificationServiceImpl.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final String DAILY_LOCK_MESSAGE = "Đã nhập sai mã quá nhiều lần. Vui lòng thử lại sau 24 giờ.";
    private static final String DAILY_REQUESTER_SEND_MESSAGE = "Bạn đã yêu cầu quá nhiều mã cho email này hôm nay. Vui lòng thử lại sau.";
    private static final String DAILY_SEND_MESSAGE = "Email này đã nhận quá nhiều mã trong hôm nay. Vui lòng thử lại sau.";

    private final SecureRandom secureRandom = new SecureRandom();
    private final OrderEmailVerificationSessionRepository repository;
    private final OrderEmailSender emailSender;
    private final SecretKeySpec hmacKey;
    private final int maxLockedSessionsPerRequesterPerDay;
    private final int maxSendsPerRequesterPerDay;
    private final int maxSendsPerDay;

    public OrderEmailVerificationServiceImpl(
        OrderEmailVerificationSessionRepository repository,
        OrderEmailSender emailSender,
        @Value("${app.order.email-otp.hmac-secret:}") String hmacSecret,
        @Value("${app.order.email-otp.max-locked-sessions-per-requester-per-day:3}") int maxLockedSessionsPerRequesterPerDay,
        @Value("${app.order.email-otp.max-sends-per-requester-per-day:5}") int maxSendsPerRequesterPerDay,
        @Value("${app.order.email-otp.max-sends-per-day:50}") int maxSendsPerDay
    ) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.maxLockedSessionsPerRequesterPerDay = maxLockedSessionsPerRequesterPerDay;
        this.maxSendsPerRequesterPerDay = maxSendsPerRequesterPerDay;
        this.maxSendsPerDay = maxSendsPerDay;
        byte[] keyBytes;
        if (hmacSecret == null || hmacSecret.isBlank()) {
            keyBytes = new byte[32];
            secureRandom.nextBytes(keyBytes);
            log.warn("app.order.email-otp.hmac-secret is not set: using a random key generated at startup, so pending email codes stop working after a restart. Set APP_ORDER_EMAIL_OTP_HMAC_SECRET in production.");
        } else {
            keyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                log.warn("app.order.email-otp.hmac-secret is shorter than {} bytes: use a longer random secret.", MIN_SECRET_BYTES);
            }
        }
        this.hmacKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    @Override
    @Transactional
    public OrderEmailOtpSendResponse sendVerificationCode(OrderEmailOtpSendRequest request, String requesterIp) {
        String email = normalizeEmail(request == null ? null : request.email());
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email chưa hợp lệ.");
        }

        LocalDateTime now = LocalDateTime.now();
        // The 60 s cooldown is enforced here, not only by the browser's countdown: a second request for the same email from the same requester is refused and sends no mail.
        // It is keyed by email AND requester, so a stranger who knows the address cannot keep the owner's own request at 429.
        String requesterKey = keyed("ip:" + (requesterIp == null || requesterIp.isBlank() ? "unknown" : requesterIp.trim()));
        repository.findFirstByEmailAndRequesterKeyOrderByCreatedAtDesc(email, requesterKey)
            .map(OrderEmailVerificationSession::getResendAvailableAt)
            .filter(availableAt -> availableAt != null && availableAt.isAfter(now))
            .ifPresent(availableAt -> {
                long waitSeconds = Math.max(1, (long) Math.ceil(Duration.between(now, availableAt).toMillis() / 1000.0));
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi " + waitSeconds + " giây trước khi gửi lại mã.");
            });

        // Daily caps. The lock and send limits are per (email, requester), so someone who knows the address can use up only their own allowance
        // and cannot lock the owner out; the larger per-email cap is just a mail-bomb guard against many requesters.
        LocalDateTime dayAgo = now.minusHours(24);
        if (repository.countByEmailAndRequesterKeyAndStatusAndAttemptCountGreaterThanEqualAndCreatedAtAfter(
                email, requesterKey, OrderEmailVerificationStatus.CANCELLED, MAX_ATTEMPTS, dayAgo) >= maxLockedSessionsPerRequesterPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, DAILY_LOCK_MESSAGE);
        }
        if (repository.countByEmailAndRequesterKeyAndCreatedAtAfter(email, requesterKey, dayAgo) >= maxSendsPerRequesterPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, DAILY_REQUESTER_SEND_MESSAGE);
        }
        if (repository.countByEmailAndCreatedAtAfter(email, dayAgo) >= maxSendsPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, DAILY_SEND_MESSAGE);
        }

        String code = generateOtp();

        OrderEmailVerificationSession session = new OrderEmailVerificationSession();
        session.setEmail(email);
        session.setRequesterKey(requesterKey);
        session.setOtpHash(hashOtp(code));
        session.setOtpExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        session.setResendAvailableAt(now.plusSeconds(RESEND_AFTER_SECONDS));
        session.setAttemptCount(0);
        session.setStatus(OrderEmailVerificationStatus.PENDING);

        OrderEmailVerificationSession saved = repository.save(session);
        emailSender.sendOtp(email, code);

        return new OrderEmailOtpSendResponse(
            saved.getId(),
            saved.getEmail(),
            saved.getOtpExpiresAt(),
            RESEND_AFTER_SECONDS,
            MAX_ATTEMPTS
        );
    }

    // noRollbackFor: the wrong-attempt counter and the CANCELLED/EXPIRED status are written right before the exception is thrown;
    // with the default rules the exception would roll those writes back and the limit of 3 attempts would never hold.
    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public OrderEmailOtpVerifyResponse verifyCode(OrderEmailOtpVerifyRequest request) {
        if (request == null || request.verificationSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu phiên xác thực email.");
        }
        String code = request.code() == null ? "" : request.code().trim();
        if (!OTP_PATTERN.matcher(code).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP phải gồm 6 chữ số.");
        }

        OrderEmailVerificationSession session = repository.findLockedById(request.verificationSessionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên xác thực email."));

        ensurePending(session);

        if (!matchesStoredHash(code, session.getOtpHash())) {
            int attempts = session.getAttemptCount() == null ? 0 : session.getAttemptCount();
            session.setAttemptCount(attempts + 1);
            boolean locked = session.getAttemptCount() >= MAX_ATTEMPTS;
            if (locked) {
                session.setStatus(OrderEmailVerificationStatus.CANCELLED);
            }
            repository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, locked ? LOCKED_MESSAGE : "Mã OTP chưa đúng.");
        }

        session.setStatus(OrderEmailVerificationStatus.VERIFIED);
        session.setVerifiedAt(LocalDateTime.now());
        repository.save(session);

        return new OrderEmailOtpVerifyResponse(session.getId(), session.getEmail(), true);
    }

    // Always joins the caller's order transaction, so the outcome (including the EXPIRED status written before a refusal) follows the caller's rollback rules.
    // The row lock makes two parallel orders spend one proof only once.
    @Override
    @Transactional
    public void assertVerifiedForOrder(UUID verificationSessionId, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (verificationSessionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng xác thực email bằng mã OTP trước khi đặt hàng.");
        }

        OrderEmailVerificationSession session = repository.findLockedById(verificationSessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên xác thực email không hợp lệ."));

        if (session.getStatus() != OrderEmailVerificationStatus.VERIFIED || session.getVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email chưa được xác thực OTP.");
        }
        if (session.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác thực email đã được sử dụng.");
        }
        if (session.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus(OrderEmailVerificationStatus.EXPIRED);
            repository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn. Vui lòng gửi lại mã.");
        }
        if (!session.getEmail().equals(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email đặt hàng không khớp với email đã xác thực.");
        }

        session.setConsumedAt(LocalDateTime.now());
        repository.save(session);
    }

    private void ensurePending(OrderEmailVerificationSession session) {
        boolean attemptsUsedUp = session.getAttemptCount() != null && session.getAttemptCount() >= MAX_ATTEMPTS;
        if (session.getStatus() != OrderEmailVerificationStatus.PENDING) {
            // a session cancelled by too many wrong codes keeps saying so; other cancellations/expiry stay generic
            String message = attemptsUsedUp && session.getStatus() == OrderEmailVerificationStatus.CANCELLED
                ? LOCKED_MESSAGE : "Phiên xác thực email không còn hiệu lực.";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (attemptsUsedUp) {
            session.setStatus(OrderEmailVerificationStatus.CANCELLED);
            repository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LOCKED_MESSAGE);
        }
        if (session.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus(OrderEmailVerificationStatus.EXPIRED);
            repository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn. Vui lòng gửi lại mã.");
        }
    }

    private String generateOtp() {
        return String.valueOf(secureRandom.nextInt(900000) + 100000);
    }

    private String hashOtp(String code) {
        return keyed("otp:" + code);
    }

    /** Keyed hash (HMAC-SHA256, hex): a leaked table cannot be reversed by hashing the million possible codes without the server secret. */
    private String keyed(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(HMAC_ALGORITHM + " is not available", exception);
        }
    }

    private boolean matchesStoredHash(String code, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hashOtp(code).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
