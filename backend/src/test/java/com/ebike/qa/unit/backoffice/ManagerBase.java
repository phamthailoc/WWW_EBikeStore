package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.manager.impl.ManagerServiceImpl;
import com.ebike.service.order.OrderService;
import com.ebike.service.product.ProductService;
import com.ebike.service.stock.StockLedger;
import com.ebike.service.stock.StockAlertService;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Mocks and the service under test, shared by the ManagerService test classes. */
abstract class ManagerBase {

    OrderRepository orders = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    UserRepository users = mock(UserRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ProductService productService = mock(ProductService.class);
    AuditLogService audit = mock(AuditLogService.class);
    StockAlertService stockAlerts = mock(StockAlertService.class);
    InstallmentApplicationRepository installments = mock(InstallmentApplicationRepository.class);
    StockLedger ledger = mock(StockLedger.class);
    OrderService orderService = mock(OrderService.class);
    ManagerServiceImpl service;

    @BeforeEach
    void baseSetUp() {
        service = new ManagerServiceImpl(orders, payments, users, products, productService, audit, stockAlerts, installments,
            Duration.ofHours(72), ledger, orderService);
    }

    @AfterEach
    void baseTearDown() {
        Fx.signOut();
    }

    static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }
}
