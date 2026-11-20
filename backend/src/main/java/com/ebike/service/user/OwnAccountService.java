package com.ebike.service.user;

import com.ebike.dto.auth.request.DeleteOwnAccountRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.admin.AuditLogService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A customer deletes their own account (V6). Same erasure as when an admin does it on their behalf
 * ({@link AccountErasure}), behind three checks: the password, the typed email, and no order still on its way.
 * Staff accounts cannot use this; an admin manages them.
 */
@Service
public class OwnAccountService {
    private static final List<OrderStatus> OPEN_ORDERS = List.of(
        OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED);

    private final UserRepository users;
    private final OrderRepository orders;
    private final PasswordEncoder passwordEncoder;
    private final AccountErasure erasure;
    private final AuditLogService auditLog;

    public OwnAccountService(UserRepository users, OrderRepository orders, PasswordEncoder passwordEncoder,
                             AccountErasure erasure, AuditLogService auditLog) {
        this.users = users;
        this.orders = orders;
        this.passwordEncoder = passwordEncoder;
        this.erasure = erasure;
        this.auditLog = auditLog;
    }

    @Transactional
    public void deleteOwnAccount(Authentication authentication, DeleteOwnAccountRequest request) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        }
        User user = users.findByUsername(authentication.getName())
            .or(() -> users.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập."));
        if (AccountErasure.DELETED.equals(user.getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản này đã bị xóa.");
        }
        if (user.getRoles().stream().anyMatch(role -> !"CUSTOMER".equals(role.getName()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản nhân viên do quản trị viên quản lý.");
        }
        // 400, not 401: the page treats 401 as "signed out".
        if (request == null || request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu không đúng.");
        }
        if (request.confirmEmail() == null || !request.confirmEmail().trim().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email xác nhận không khớp với tài khoản của bạn.");
        }
        if (orders.existsByUserIdAndStatusIn(user.getId(), OPEN_ORDERS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bạn còn đơn hàng chưa hoàn tất. Hãy đợi nhận xe hoặc hủy đơn trước khi xóa tài khoản.");
        }

        Long id = user.getId();
        erasure.erase(user);
        users.save(user);
        // The audit entry names the account by number only: the person's own details were just erased.
        auditLog.recordBy("tài khoản #" + id, "khách tự xóa tài khoản", "tài khoản #" + id);
    }
}
