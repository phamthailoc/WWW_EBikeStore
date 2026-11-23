package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.warranty.WarrantyRules;
import java.io.IOException;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

/** V27: who may open the documents of an order, which orders have them, and what the hand-over email carries. */
class OrderDocumentServiceTest {

    private OrderRepository orders;
    private InstallmentApplicationRepository installments;
    private UserRepository users;
    private UserPreferenceRepository preferences;
    private OrderDocumentService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        installments = mock(InstallmentApplicationRepository.class);
        users = mock(UserRepository.class);
        preferences = mock(UserPreferenceRepository.class);
        when(installments.findByOrderId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(preferences.findByUserId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        service = new OrderDocumentService(orders, installments, users, preferences, DocumentFixtures.store(), new PdfRenderer(), mock(org.springframework.transaction.PlatformTransactionManager.class),
            WarrantyRules.DEFAULT_INTERVALS);
        User owner = DocumentFixtures.user(7, "an");
        when(users.findByUsername("an")).thenReturn(Optional.of(owner));
        when(users.findByUsername("binh")).thenReturn(Optional.of(DocumentFixtures.user(8, "binh")));
    }

    private Order stored(Order order) {
        when(orders.findForDocuments(order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    private static Authentication customer(String name) {
        return new UsernamePasswordAuthenticationToken(name, "x", java.util.List.of(
            new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority(PermissionConstants.Customer.ORDER_VIEW_OWN)));
    }

    private static Authentication staff() {
        return new UsernamePasswordAuthenticationToken("quanly", "x", java.util.List.of(
            new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority(PermissionConstants.OrderManagement.ORDER_VIEW_ALL)));
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }

    private static int pages(PdfFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.content())) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void theOwnerDownloadsTheReceiptOfTheirOrder() throws IOException {
        stored(DocumentFixtures.order(1, OrderStatus.DELIVERED, 1, true));

        PdfFile file = service.receipt(1L, customer("an"), "vi");

        assertEquals("receipt-ORD-20261110-00000001.pdf", file.filename());
        assertEquals(1, pages(file));
    }

    @Test
    void anotherCustomerGetsTheSameNotFoundAsAMissingOrder() {
        stored(DocumentFixtures.order(1, OrderStatus.DELIVERED, 1, true));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.receipt(1L, customer("binh"), "vi"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.receipt(99L, customer("an"), "vi"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.warrantyCard(1L, null, customer("binh"), "vi"));
    }

    @Test
    void aVisitorAndAnUnknownAccountGetNothing() {
        stored(DocumentFixtures.order(1, OrderStatus.DELIVERED, 1, true));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByEmail("ghost")).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> service.receipt(1L, null, "vi"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.receipt(1L, customer("ghost"), "vi"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.receipt(null, customer("an"), "vi"));
    }

    @Test
    void anErrorIsWordedInTheLanguageOfThePageNotOfTheDocument() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.receipt(99L, customer("an"), "en", "vi"));

        assertEquals(DocumentText.text("vi", "error.notFound"), error.getReason());
    }

    @Test
    void theStaffMayOpenAnyOrderIncludingAGuestOrder() {
        Order guest = stored(DocumentFixtures.order(2, OrderStatus.DELIVERED, 1, true));
        guest.setUser(null);

        assertEquals("receipt-ORD-20261110-00000002.pdf", service.receipt(2L, staff(), "vi").filename());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.receipt(2L, customer("an"), "vi"));
    }

    @Test
    void aCustomerAccountSignedInByEmailIsStillTheOwner() {
        stored(DocumentFixtures.order(1, OrderStatus.DELIVERED, 1, true));
        when(users.findByUsername("an@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("an@example.com")).thenReturn(Optional.of(DocumentFixtures.user(7, "an")));

        assertEquals("receipt-ORD-20261110-00000001.pdf", service.receipt(1L, customer("an@example.com"), "vi").filename());
    }

    @Test
    void aCancelledOrderHasNoReceiptButAPendingOneHasAProvisionalReceipt() {
        stored(DocumentFixtures.order(3, OrderStatus.CANCELLED, 1, false));
        stored(DocumentFixtures.order(4, OrderStatus.PENDING, 1, false));

        assertStatus(HttpStatus.CONFLICT, () -> service.receipt(3L, customer("an"), "vi"));
        assertEquals("receipt-ORD-20261110-00000004.pdf", service.receipt(4L, customer("an"), "vi").filename());
    }

    @Test
    void theWarrantyCardNeedsADeliveredOrderWithNumberedBikes() {
        stored(DocumentFixtures.order(5, OrderStatus.SHIPPED, 1, true));
        stored(DocumentFixtures.order(6, OrderStatus.DELIVERED, 1, false));

        assertStatus(HttpStatus.CONFLICT, () -> service.warrantyCard(5L, null, customer("an"), "vi"));
        assertStatus(HttpStatus.CONFLICT, () -> service.warrantyCard(6L, null, customer("an"), "vi"));
    }

    @Test
    void theCardHasOnePagePerBikeOrJustTheChosenOne() throws IOException {
        stored(DocumentFixtures.order(7, OrderStatus.DELIVERED, 3, true));

        PdfFile all = service.warrantyCard(7L, null, customer("an"), "vi");
        PdfFile one = service.warrantyCard(7L, 502L, customer("an"), "en");

        assertEquals(3, pages(all));
        assertEquals("warranty-card-ORD-20261110-00000007.pdf", all.filename());
        assertEquals(1, pages(one));
        assertEquals("warranty-card-RLEBK00002.pdf", one.filename());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.warrantyCard(7L, 999L, customer("an"), "vi"));
    }

    @Test
    void theErrorWordsFollowTheLanguage() {
        stored(DocumentFixtures.order(3, OrderStatus.CANCELLED, 1, false));

        ResponseStatusException english = assertThrows(ResponseStatusException.class, () -> service.receipt(3L, customer("an"), "en"));
        ResponseStatusException vietnamese = assertThrows(ResponseStatusException.class, () -> service.receipt(3L, customer("an"), "vi"));

        assertEquals("A cancelled order has no sales receipt.", english.getReason());
        assertEquals("Đơn hàng đã hủy nên không có phiếu bán hàng.", vietnamese.getReason());
    }

    @Test
    void anInstalmentPlanIsReadForTheReceipt() {
        Order order = stored(DocumentFixtures.order(8, OrderStatus.CONFIRMED, 1, false));
        order.getPayment().setPaymentMethod(com.ebike.model.entity.order.PaymentMethod.INSTALLMENT);
        InstallmentApplication plan = new InstallmentApplication();
        plan.setDownPaymentPercent(30);
        plan.setTermMonths(12);
        when(installments.findByOrderId(8L)).thenReturn(Optional.of(plan));

        assertEquals("receipt-ORD-20261110-00000008.pdf", service.receipt(8L, customer("an"), "vi").filename());
    }

    // ---- the hand-over email ----

    @Test
    void theHandoverEmailCarriesTheReceiptAndTheCardInTheSavedLanguage() {
        stored(DocumentFixtures.order(10, OrderStatus.DELIVERED, 2, true));
        UserPreference preference = new UserPreference();
        preference.setLanguage("en");
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(preference));

        OrderDocumentService.DeliveryDocuments documents = service.forDeliveredOrder(10L).orElseThrow();

        assertEquals("an@example.com", documents.email());
        assertEquals("en", documents.language());
        assertEquals("Nguyễn Văn An", documents.recipientName());
        assertEquals(java.util.List.of("receipt-ORD-20261110-00000010.pdf", "warranty-card-ORD-20261110-00000010.pdf"),
            documents.files().stream().map(PdfFile::filename).toList());
    }

    @Test
    void aGuestOrderIsEmailedInVietnameseToTheCheckoutAddress() {
        Order order = stored(DocumentFixtures.order(11, OrderStatus.DELIVERED, 1, true));
        order.setUser(null);
        order.setCustomerEmail("khach@example.com");

        OrderDocumentService.DeliveryDocuments documents = service.forDeliveredOrder(11L).orElseThrow();

        assertEquals("khach@example.com", documents.email());
        assertEquals("vi", documents.language());
    }

    @Test
    void anOrderWhoseBikesHaveNoNumbersGetsTheReceiptOnly() {
        stored(DocumentFixtures.order(12, OrderStatus.DELIVERED, 1, false));

        OrderDocumentService.DeliveryDocuments documents = service.forDeliveredOrder(12L).orElseThrow();

        assertEquals(1, documents.files().size());
        assertTrue(documents.files().get(0).filename().startsWith("receipt-"));
    }

    @Test
    void nothingIsSentForAnErasedAccountAnOrderNotDeliveredOrNoAddress() {
        Order erased = stored(DocumentFixtures.order(13, OrderStatus.DELIVERED, 1, true));
        erased.getUser().setAccountStatus("DELETED");
        stored(DocumentFixtures.order(14, OrderStatus.SHIPPED, 1, true));
        Order noAddress = stored(DocumentFixtures.order(15, OrderStatus.DELIVERED, 1, true));
        noAddress.setUser(null);
        noAddress.setCustomerEmail(null);

        assertFalse(service.forDeliveredOrder(13L).isPresent());
        assertFalse(service.forDeliveredOrder(14L).isPresent());
        assertFalse(service.forDeliveredOrder(15L).isPresent());
        assertFalse(service.forDeliveredOrder(404L).isPresent());
    }
}
