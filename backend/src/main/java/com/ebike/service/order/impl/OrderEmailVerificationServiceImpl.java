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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
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

    private final SecureRandom secureRandom = new SecureRandom();
    private final OrderEmailVerificationSessionRepository repository;
    private final OrderEmailSender emailSender;

    public OrderEmailVerificationServiceImpl(
        OrderEmailVerificationSessionRepository repository,
        OrderEmailSender emailSender
    ) {
        this.repository = repository;
        this.emailSender = emailSender;
    }

    @Override
    @Transactional
    public OrderEmailOtpSendResponse sendVerificationCode(OrderEmailOtpSendRequest request) {
        String email = normalizeEmail(request == null ? null : request.email());
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email chưa hợp lệ.");
        }

        LocalDateTime now = LocalDateTime.now();
        // The 60 s cooldown is enforced here, not only by the browser's countdown: a second request for the same email is refused and sends no mail.
        repository.findFirstByEmailOrderByCreatedAtDesc(email)
            .map(OrderEmailVerificationSession::getResendAvailableAt)
            .filter(availableAt -> availableAt != null && availableAt.isAfter(now))
            .ifPresent(availableAt -> {
                long waitSeconds = Math.max(1, (long) Math.ceil(Duration.between(now, availableAt).toMillis() / 1000.0));
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi " + waitSeconds + " giây trước khi gửi lại mã.");
            });

        String code = generateOtp();

        OrderEmailVerificationSession session = new OrderEmailVerificationSession();
        session.setEmail(email);
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

        OrderEmailVerificationSession session = repository.findById(request.verificationSessionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên xác thực email."));

        ensurePending(session);

        if (!hashOtp(code).equals(session.getOtpHash())) {
            int attempts = session.getAttemptCount() == null ? 0 : session.getAttemptCount();
            session.setAttemptCount(attempts + 1);
            if (session.getAttemptCount() >= MAX_ATTEMPTS) {
                session.setStatus(OrderEmailVerificationStatus.CANCELLED);
            }
            repository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP chưa đúng.");
        }

        session.setStatus(OrderEmailVerificationStatus.VERIFIED);
        session.setVerifiedAt(LocalDateTime.now());
        repository.save(session);

        return new OrderEmailOtpVerifyResponse(session.getId(), session.getEmail(), true);
    }

    // noRollbackFor keeps the EXPIRED status written before the refusal; inside the caller's order transaction the caller still decides the outcome.
    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void assertVerifiedForOrder(UUID verificationSessionId, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (verificationSessionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng xác thực email bằng mã OTP trước khi đặt hàng.");
        }

        OrderEmailVerificationSession session = repository.findById(verificationSessionId)
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
        if (session.getStatus() != OrderEmailVerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên xác thực email không còn hiệu lực.");
        }
        if (session.getAttemptCount() != null && session.getAttemptCount() >= MAX_ATTEMPTS) {
            session.setStatus(OrderEmailVerificationStatus.CANCELLED);
            repository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã nhập sai OTP quá số lần cho phép.");
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
