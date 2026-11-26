package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.order.impl.OrderPlacedEmailService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPlacedEmailServiceTest {

    @Mock OrderRepository orders;
    @Mock JavaMailSender mailSender;

    Order order;
    User alice;

    @BeforeEach
    void setUp() {
        alice = OrderFixtures.user(1L, "alice", "alice@account.com");
        order = OrderFixtures.order(10L, "ORD-20261126-ABCD1234", OrderStatus.PENDING, alice);
        order.setCustomerEmail(" khach@example.com ");
        Product bike = OrderFixtures.bike(1L, "Kinetic X1", "10000000", 5, com.ebike.model.entity.product.VehicleType.E_BIKE);
        OrderFixtures.line(order, 1L, bike, null, 2, "10000000");
        order.setSubtotal(new BigDecimal("20000000"));
        order.setShippingFee(new BigDecimal("400000"));
        order.setTotalAmount(new BigDecimal("20400000"));
        OrderFixtures.payment(order, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "20400000");
        OrderFixtures.shipment(order, OrderFixtures.showroom(1L, "Hà Nội", "Cầu Giấy", true));
        when(orders.findById(10L)).thenReturn(Optional.of(order));
    }

    private OrderPlacedEmailService service(boolean enabled, String smtpHost, String baseUrl) {
        return new OrderPlacedEmailService(orders, mailSender, "shop@kinetic.vn", smtpHost, enabled, baseUrl);
    }

    @Test
    void noMailWhenDisabledOrWithoutSmtpHost() {
        assertThat(service(false, "smtp.local", "").compose(10L)).isEmpty();
        assertThat(service(true, "", "").compose(10L)).isEmpty();
        assertThat(service(true, null, "").compose(10L)).isEmpty();
        assertThat(service(true, "  ", "").compose(10L)).isEmpty();
    }

    @Test
    void noMailForAMissingOrder() {
        assertThat(service(true, "smtp.local", "").compose(404L)).isEmpty();
    }

    @Test
    void noMailWhenNoAddressIsKnown() {
        order.setCustomerEmail(null);
        order.getShipment().setRecipientEmail(" ");
        order.setUser(null);
        assertThat(service(true, "smtp.local", "").compose(10L)).isEmpty();
    }

    @Test
    void recipientPrefersTheOrderEmailThenTheShipmentThenTheAccount() {
        OrderPlacedEmailService service = service(true, "smtp.local", "");
        assertThat(service.compose(10L).orElseThrow().getTo()).containsExactly("khach@example.com");
        order.setCustomerEmail("");
        order.getShipment().setRecipientEmail(" ship@example.com ");
        assertThat(service.compose(10L).orElseThrow().getTo()).containsExactly("ship@example.com");
        order.getShipment().setRecipientEmail(null);
        assertThat(service.compose(10L).orElseThrow().getTo()).containsExactly("alice@account.com");
    }

    @Test
    void aGuestOrderIsEmailedWithoutALinkToAnAccountPage() {
        order.setUser(null);
        SimpleMailMessage message = service(true, "smtp.local", "https://shop.example.com").compose(10L).orElseThrow();
        assertThat(message.getText()).doesNotContain("https://shop.example.com");
    }

    @Test
    void anAccountOrderGetsALinkWithoutADoubleSlash() {
        SimpleMailMessage withSlash = service(true, "smtp.local", "https://shop.example.com/").compose(10L).orElseThrow();
        assertThat(withSlash.getText()).contains("https://shop.example.com/customer/orders/10").doesNotContain("com//customer");
        SimpleMailMessage without = service(true, "smtp.local", "https://shop.example.com").compose(10L).orElseThrow();
        assertThat(without.getText()).contains("https://shop.example.com/customer/orders/10");
        SimpleMailMessage noBase = service(true, "smtp.local", "").compose(10L).orElseThrow();
        assertThat(noBase.getText()).doesNotContain("/customer/orders/");
    }

    @Test
    void theMessageCarriesOrderNumberLinesAndVietnameseMoney() {
        SimpleMailMessage message = service(true, "smtp.local", "").compose(10L).orElseThrow();
        assertThat(message.getFrom()).isEqualTo("shop@kinetic.vn");
        assertThat(message.getSubject()).contains("ORD-20261126-ABCD1234");
        String text = message.getText();
        assertThat(text).contains("Kinetic X1 x 2").contains("20.000.000").contains("400.000").contains("20.400.000").contains("₫");
        assertThat(text).contains("Nhận tại showroom: Showroom 1, 1 Main Street");
        assertThat(text).contains("Thanh toán sau");
        assertThat(text).contains("đang chờ xác nhận");
    }

    @Test
    void discountAndRegistrationLinesAppearOnlyWhenPositive() {
        String plain = service(true, "smtp.local", "").compose(10L).orElseThrow().getText();
        assertThat(plain).doesNotContain("Giảm giá").doesNotContain("Dịch vụ đăng ký xe");
        order.setDiscountAmount(new BigDecimal("1000000"));
        order.setRegistrationFee(new BigDecimal("500000"));
        String full = service(true, "smtp.local", "").compose(10L).orElseThrow().getText();
        assertThat(full).contains("Giảm giá: -1.000.000").contains("Dịch vụ đăng ký xe: 500.000");
    }

    @Test
    void anOnlineOrderTellsTheCustomerItWaitsForPayment() {
        order.getPayment().setPaymentMethod(PaymentMethod.VNPAY);
        String text = service(true, "smtp.local", "").compose(10L).orElseThrow().getText();
        assertThat(text).contains("đang chờ thanh toán").contains("VNPay").contains("tự hủy");
    }

    @Test
    void anInstalmentOrderIsLabelled() {
        order.getPayment().setPaymentMethod(PaymentMethod.INSTALLMENT);
        assertThat(service(true, "smtp.local", "").compose(10L).orElseThrow().getText()).contains("Trả góp");
    }

    @Test
    void homeDeliveryShowsTheShippingAddress() {
        order.getShipment().setPickupShowroom(null);
        order.getShipment().setShippingAddress("12 Nguyen Hue, Hà Nội");
        assertThat(service(true, "smtp.local", "").compose(10L).orElseThrow().getText()).contains("Giao đến: 12 Nguyen Hue, Hà Nội");
    }

    @Test
    void missingMoneyFiguresPrintAsZeroInsteadOfCrashing() {
        order.setSubtotal(null);
        order.setShippingFee(null);
        order.setTotalAmount(null);
        order.getItems().iterator().next().setLineTotal(null);
        assertThat(service(true, "smtp.local", "").compose(10L).orElseThrow().getText()).contains("Tổng thanh toán: 0 ₫");
    }

    @Test
    void aMailServerRefusalIsSwallowedAndReportedAsFalse() {
        SimpleMailMessage message = new SimpleMailMessage();
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThat(service(true, "smtp.local", "").deliver(message)).isFalse();
    }

    @Test
    void aDeliveredMessageIsReportedAsTrue() {
        SimpleMailMessage message = new SimpleMailMessage();
        assertThat(service(true, "smtp.local", "").deliver(message)).isTrue();
        verify(mailSender).send(message);
    }
}
