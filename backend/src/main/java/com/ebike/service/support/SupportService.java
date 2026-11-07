package com.ebike.service.support;

import com.ebike.dto.support.SupportRequestCreateRequest;
import com.ebike.dto.support.SupportRequestResponse;
import com.ebike.dto.support.SupportRequestUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.support.SupportRequest;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.support.SupportRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Support requests from the support page (Task 14): visitors write, staff read and answer. */
@Service
public class SupportService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");
    private static final Set<String> STATUSES = Set.of(SupportRequest.NEW, SupportRequest.IN_PROGRESS, SupportRequest.RESOLVED);
    /** At most this many requests per email per hour, so the form cannot be used to flood the staff. */
    private static final int MAX_PER_HOUR = 5;

    private final SupportRequestRepository requests;
    private final UserRepository users;

    public SupportService(SupportRequestRepository requests, UserRepository users) {
        this.requests = requests;
        this.users = users;
    }

    @Transactional
    public SupportRequestResponse create(SupportRequestCreateRequest request, Authentication authentication) {
        if (request == null) {
            throw bad("Vui lòng nhập nội dung yêu cầu.");
        }
        String name = text(request.customerName());
        String email = text(request.email()).toLowerCase(Locale.ROOT);
        String phone = text(request.phone());
        String subject = text(request.subject());
        String message = text(request.message());
        if (name.isEmpty() || name.length() > 100) {
            throw bad("Vui lòng nhập họ tên (tối đa 100 ký tự).");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 255) {
            throw bad("Email chưa hợp lệ. Ví dụ đúng: khachhang@example.com.");
        }
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw bad("Số điện thoại chưa hợp lệ. Vui lòng nhập số Việt Nam bắt đầu bằng 0 và gồm 10-11 chữ số.");
        }
        if (subject.isEmpty() || subject.length() > 150) {
            throw bad("Vui lòng nhập tiêu đề (tối đa 150 ký tự).");
        }
        if (message.length() < 10 || message.length() > 3000) {
            throw bad("Nội dung cần từ 10 đến 3000 ký tự.");
        }
        if (requests.countByEmailIgnoreCaseAndCreatedAtAfter(email, LocalDateTime.now().minusHours(1)) >= MAX_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau.");
        }

        SupportRequest saved = new SupportRequest();
        saved.setUser(signedInUser(authentication));
        saved.setCustomerName(name);
        saved.setEmail(email);
        saved.setPhone(phone.isEmpty() ? null : phone);
        saved.setSubject(subject);
        saved.setMessage(message);
        return toResponse(requests.save(saved));
    }

    @Transactional(readOnly = true)
    public List<SupportRequestResponse> list(String status) {
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (filter != null && !STATUSES.contains(filter)) {
            throw bad("Trạng thái không hợp lệ.");
        }
        return (filter == null ? requests.findAllByOrderByCreatedAtDesc() : requests.findByStatusOrderByCreatedAtDesc(filter))
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public SupportRequestResponse update(Long id, SupportRequestUpdateRequest request) {
        SupportRequest found = requests.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hỗ trợ."));
        if (request != null && request.status() != null && !request.status().isBlank()) {
            String status = request.status().trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw bad("Trạng thái không hợp lệ.");
            }
            found.setStatus(status);
        }
        if (request != null && request.staffNote() != null) {
            String note = request.staffNote().trim();
            if (note.length() > 3000) {
                throw bad("Ghi chú tối đa 3000 ký tự.");
            }
            found.setStaffNote(note.isEmpty() ? null : note);
        }
        return toResponse(requests.save(found));
    }

    private User signedInUser(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
            || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return users.findByUsername(authentication.getName()).or(() -> users.findByEmail(authentication.getName())).orElse(null);
    }

    private SupportRequestResponse toResponse(SupportRequest request) {
        return new SupportRequestResponse(request.getId(), request.getCustomerName(), request.getEmail(), request.getPhone(),
            request.getSubject(), request.getMessage(), request.getStatus(), request.getStaffNote(),
            request.getCreatedAt(), request.getUpdatedAt());
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
