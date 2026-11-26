package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.Showroom;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.document.DocumentFormat;
import com.ebike.service.document.DocumentModels;
import com.ebike.service.document.DocumentText;
import com.ebike.service.document.OrderDocumentService;
import com.ebike.service.document.PdfFile;
import com.ebike.service.document.PdfRenderer;
import com.ebike.service.document.StoreProfile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class DocumentTest {

    private final StoreProfile store = new StoreProfile("Kinetic", "Kinetic Co", "0123", "1 Street", "0900", "a@b.c", "https://shop.vn/");
    private final LocalDateTime issued = LocalDateTime.of(2026, 11, 20, 14, 5);

    // ---------------------------------------------------------------- DocumentFormat

    @ParameterizedTest
    @CsvSource({
        "0,Không đồng chẵn",
        "1,Một đồng chẵn",
        "10,Mười đồng chẵn",
        "15,Mười lăm đồng chẵn",
        "21,Hai mươi mốt đồng chẵn",
        "25,Hai mươi lăm đồng chẵn",
        "105,Một trăm lẻ năm đồng chẵn",
        "1000,Một nghìn đồng chẵn",
        "1200000,Một triệu hai trăm nghìn đồng chẵn",
        "1000005,Một triệu không trăm lẻ năm đồng chẵn",
        "1000000000,Một tỷ đồng chẵn",
        "25990000,Hai mươi lăm triệu chín trăm chín mươi nghìn đồng chẵn"
    })
    void amountInWords(String amount, String expected) {
        assertThat(DocumentFormat.moneyInWords(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    void amountInWordsOfNegativeOrMissingReadsZeroAndRoundsHalfUp() {
        assertThat(DocumentFormat.moneyInWords(null)).isEqualTo("Không đồng chẵn");
        assertThat(DocumentFormat.moneyInWords(new BigDecimal("-500"))).isEqualTo("Không đồng chẵn");
        assertThat(DocumentFormat.moneyInWords(new BigDecimal("1.5"))).isEqualTo("Hai đồng chẵn");
        assertThat(DocumentFormat.moneyInWords(new BigDecimal("1.4"))).isEqualTo("Một đồng chẵn");
    }

    @Test
    void moneyUsesDotsAndTheDongSign() {
        assertThat(DocumentFormat.money(new BigDecimal("1234000"))).isEqualTo("1.234.000 ₫");
        assertThat(DocumentFormat.money(null)).isEqualTo("0 ₫");
        assertThat(DocumentFormat.money(new BigDecimal("999.5"))).isEqualTo("1.000 ₫");
        assertThat(DocumentFormat.money(new BigDecimal("0.4"))).isEqualTo("0 ₫");
    }

    @Test
    void datesUseDayMonthYearAndADashWhenMissing() {
        assertThat(DocumentFormat.dateTime(issued)).isEqualTo("20/11/2026 14:05");
        assertThat(DocumentFormat.date(issued)).isEqualTo("20/11/2026");
        assertThat(DocumentFormat.dateTime(null)).isEqualTo("—");
        assertThat(DocumentFormat.date(null)).isEqualTo("—");
    }

    // ---------------------------------------------------------------- DocumentText / PdfFile / StoreProfile

    @Test
    void textHoleFillingIsSinglePassAndMissingKeysShowThemselves() {
        assertThat(DocumentText.text("no.such.key")).isEqualTo("no.such.key");
        assertThat(DocumentText.text("card.months", 12)).isEqualTo("12 tháng");
        assertThat(DocumentText.text("card.months", "{0}")).isEqualTo("{0} tháng");
        assertThat(DocumentText.text("mail.subject", "Shop")).contains("Shop").contains("{1}");
        assertThat(DocumentText.text("card.months", "$1 \\")).isEqualTo("$1 \\ tháng");
    }

    @Test
    void everyTemplateLabelResolves() {
        Map<String, String> labels = DocumentText.labels();
        assertThat(labels).containsKeys("receipt.title", "card.title", "status.DELIVERED", "pay.PAID", "method.VNPAY");
        assertThat(labels.values()).noneMatch(String::isBlank);
    }

    @Test
    void pdfFileNamesAreSafeAscii() {
        assertThat(PdfFile.safeName("DH 2026/11\r\n\"x\"")).matches("[A-Za-z0-9._-]+");
        assertThat(PdfFile.safeName(null)).isEqualTo("document");
        assertThat(PdfFile.safeName("")).isEqualTo("document");
        assertThat(PdfFile.safeName("ảnh")).isEqualTo("_nh");
        assertThat(PdfFile.safeName("../../x")).doesNotContain("/");
    }

    @Test
    void localAddressesAreNotPrintedOnDocuments() {
        assertThat(StoreProfile.isLocalAddress("http://localhost:8080")).isTrue();
        assertThat(StoreProfile.isLocalAddress("http://127.0.0.1")).isTrue();
        assertThat(StoreProfile.isLocalAddress("http://[::1]:80")).isTrue();
        assertThat(StoreProfile.isLocalAddress("http://shop.localhost")).isTrue();
        assertThat(StoreProfile.isLocalAddress("https://shop.vn")).isFalse();
        assertThat(StoreProfile.isLocalAddress(null)).isFalse();
        assertThat(StoreProfile.isLocalAddress("not a url")).isFalse();
        assertThat(new StoreProfile("n", "", "", "", "", "", "http://localhost:8080").warrantyLookupUrl()).isEmpty();
        assertThat(store.warrantyLookupUrl()).isEqualTo("https://shop.vn/warranty");
        assertThat(new StoreProfile("Shop", "", " ", null, null, null, null).getLegalName()).isEqualTo("Shop");
    }

    // ---------------------------------------------------------------- order fixtures

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-001");
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("30000000"));
        order.setTotalAmount(new BigDecimal("30000000"));
        order.setItems(new LinkedHashSet<>());
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 11, 1, 9, 0));
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.of(2026, 11, 10, 9, 0));
        return order;
    }

    private OrderItemUnit unit(Order order, long id, String frame, String engine) {
        OrderItem item = order.getItems().stream().findFirst().orElseGet(() -> {
            OrderItem created = new OrderItem();
            created.setId(1L);
            created.setOrder(order);
            created.setProductName("Xe A");
            created.setQuantity(1);
            created.setUnitPrice(new BigDecimal("30000000"));
            created.setLineTotal(new BigDecimal("30000000"));
            order.getItems().add(created);
            return created;
        });
        OrderItemUnit unit = new OrderItemUnit();
        ReflectionTestUtils.setField(unit, "id", id);
        unit.setOrderItem(item);
        unit.setFrameNumber(frame);
        unit.setEngineNumber(engine);
        unit.setWarrantyMonths(24);
        item.getUnits().add(unit);
        return unit;
    }

    private Shipment shipment(Order order, String name) {
        Shipment shipment = new Shipment();
        shipment.setRecipientName(name);
        shipment.setPhoneNumber("0900000000");
        order.setShipment(shipment);
        return shipment;
    }

    private User erased() {
        User user = Fixtures.user(5, "deleted_5");
        user.setEmail("deleted_5@deleted.invalid");
        user.setAccountStatus(AccountErasure.DELETED);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> customer(Map<String, Object> model) {
        return (Map<String, Object>) model.get("customer");
    }

    // ---------------------------------------------------------------- numbering

    @Test
    void provisionalReceiptsAreNumberedTTAndFinalOnesPBH() {
        assertThat(DocumentModels.receiptNumber(order(OrderStatus.PENDING))).isEqualTo("TT-ORD-001");
        assertThat(DocumentModels.receiptNumber(order(OrderStatus.SHIPPED))).isEqualTo("TT-ORD-001");
        assertThat(DocumentModels.receiptNumber(order(OrderStatus.DELIVERED))).isEqualTo("PBH-ORD-001");
        assertThat(DocumentModels.isProvisional(order(OrderStatus.CANCELLED))).isTrue();
    }

    @Test
    void provisionalReceiptSaysSoAndHasNoDeliveryDate() {
        Map<String, Object> model = DocumentModels.receipt(order(OrderStatus.PROCESSING), null, store, issued);
        assertThat(model.get("provisional")).isEqualTo(true);
        assertThat((String) model.get("title")).contains("TẠM TÍNH");
        assertThat(model.get("deliveredAt")).isNull();
        assertThat(model.get("issuedAt")).isEqualTo("20/11/2026 14:05");
    }

    @Test
    void finalReceiptShowsTotalsInWordsAndOnlyChargesThatExist() {
        Order order = order(OrderStatus.DELIVERED);
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(new BigDecimal("1000000"));
        order.setIncludeRegistrationService(true);
        order.setRegistrationFee(new BigDecimal("500000"));
        order.setTotalAmount(new BigDecimal("29500000"));
        Map<String, Object> model = DocumentModels.receipt(order, null, store, issued);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> totals = (List<Map<String, Object>>) model.get("totals");
        assertThat(totals).extracting(r -> r.get("label")).containsExactly("Tiền hàng", "Dịch vụ đăng ký xe", "Giảm giá", "Tổng cộng");
        assertThat(totals.get(2).get("value")).isEqualTo("−1.000.000 ₫");
        assertThat(model.get("inWords")).isEqualTo("Hai mươi chín triệu năm trăm nghìn đồng chẵn");
    }

    @Test
    void warrantyCardNumberIsBHPlusFrameAndCarriesTheWarrantyPeriod() {
        Order order = order(OrderStatus.DELIVERED);
        shipment(order, "An");
        ReflectionTestUtils.setField(order, "deliveredAt", LocalDateTime.of(2026, 11, 5, 8, 0));
        OrderItemUnit unit = unit(order, 1, "FRAME123", "ENG1");
        Map<String, Object> model = DocumentModels.warrantyCards(order, List.of(unit), store, com.ebike.service.warranty.WarrantyRules.parseIntervals("P1M,P6M"), issued);
        @SuppressWarnings("unchecked")
        Map<String, Object> card = ((List<Map<String, Object>>) model.get("cards")).get(0);
        assertThat(card.get("number")).isEqualTo("BH-FRAME123");
        assertThat(card.get("startsAt")).isEqualTo("05/11/2026");
        assertThat(card.get("endsAt")).isEqualTo("05/11/2028");
        assertThat(card.get("hasPeriod")).isEqualTo(true);
        assertThat(card.get("periodText")).isEqualTo("24 tháng");
        assertThat(model.get("lookupUrl")).isEqualTo("https://shop.vn/warranty");
    }

    @Test
    void unitWithoutAWarrantyLengthSaysSoInsteadOfInventingOne() {
        Order order = order(OrderStatus.DELIVERED);
        OrderItemUnit unit = unit(order, 1, "FRAME123", "ENG1");
        unit.setWarrantyMonths(null);
        Map<String, Object> model = DocumentModels.warrantyCards(order, List.of(unit), store, List.of(), issued);
        @SuppressWarnings("unchecked")
        Map<String, Object> card = ((List<Map<String, Object>>) model.get("cards")).get(0);
        assertThat(card.get("hasPeriod")).isEqualTo(false);
        assertThat(card.get("endsAt")).isEqualTo("—");
        assertThat((String) card.get("periodText")).contains("Chưa có thời hạn");
    }

    // ---------------------------------------------------------------- privacy / XML safety

    @Test
    void erasedAccountPrintsNoEmailAndKeepsTheRecipientNameOnTheSalesRecord() {
        Order order = order(OrderStatus.DELIVERED);
        order.setUser(erased());
        order.setCustomerEmail("typed@checkout.com");
        shipment(order, "Nguyễn An");
        Map<String, Object> customer = customer(DocumentModels.receipt(order, null, store, issued));
        assertThat(customer.get("email")).isEqualTo("—");
        assertThat(customer.get("name")).isEqualTo("Nguyễn An");
    }

    @Test
    void erasedAccountWithoutARecipientNameIsShownAsDeleted() {
        Order order = order(OrderStatus.DELIVERED);
        order.setUser(erased());
        assertThat(customer(DocumentModels.receipt(order, null, store, issued)).get("name")).isEqualTo("Tài khoản đã xóa");
    }

    @Test
    void liveAccountEmailPriorityIsCheckoutThenRecipientThenAccount() {
        Order order = order(OrderStatus.DELIVERED);
        order.setUser(Fixtures.user(1, "alice"));
        assertThat(customer(DocumentModels.receipt(order, null, store, issued)).get("email")).isEqualTo("alice@example.com");
        shipment(order, "x").setRecipientEmail("r@x.com");
        assertThat(customer(DocumentModels.receipt(order, null, store, issued)).get("email")).isEqualTo("r@x.com");
        order.setCustomerEmail("c@x.com");
        assertThat(customer(DocumentModels.receipt(order, null, store, issued)).get("email")).isEqualTo("c@x.com");
        assertThat(DocumentModels.contactEmail(order)).isEqualTo("c@x.com");
    }

    @Test
    void controlCharactersThatXmlForbidsAreDroppedFromCustomerText() {
        Order order = order(OrderStatus.DELIVERED);
        order.setNotes("a\u000Bb\u0000c\u0008d\tkeep\nline￾e😀");
        shipment(order, "An\u0001 Bình");
        order.setCustomerEmail("x\u000C@y.com");
        Map<String, Object> model = DocumentModels.receipt(order, null, store, issued);
        assertThat(model.get("notes")).isEqualTo("abcd\tkeep\nlinee😀");
        assertThat(customer(model).get("name")).isEqualTo("An Bình");
        assertThat(customer(model).get("email")).isEqualTo("x@y.com");
    }

    @Test
    void aReceiptWithIllegalCharactersAndMarkupStillRendersToARealPdf() {
        Order order = order(OrderStatus.DELIVERED);
        order.setNotes("</td><b>bold</b> & \u000B vertical tab");
        shipment(order, "<script>x</script>");
        unit(order, 1, "FR\u0001AME99", "EN&G");
        PdfRenderer renderer = new PdfRenderer();
        byte[] pdf = renderer.render("receipt", DocumentModels.receipt(order, null, store, issued));
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String page = renderer.page("receipt", DocumentModels.receipt(order, null, store, issued));
        assertThat(page).doesNotContain("<script>x").contains("&lt;script&gt;");
    }

    @Test
    void warrantyCardRendersToAPdfForSeveralUnits() {
        Order order = order(OrderStatus.DELIVERED);
        shipment(order, "An");
        List<OrderItemUnit> units = List.of(unit(order, 1, "AAAAA1", "E1"), unit(order, 2, "BBBBB2", "E2"));
        byte[] pdf = new PdfRenderer().render("warranty-card",
            DocumentModels.warrantyCards(order, units, store, com.ebike.service.warranty.WarrantyRules.parseIntervals("P1M,P6M,P12M"), issued));
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    // ---------------------------------------------------------------- OrderDocumentService access rules

    private OrderRepository orders;
    private UserRepository users;
    private OrderDocumentService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        users = mock(UserRepository.class);
        service = new OrderDocumentService(orders, mock(InstallmentApplicationRepository.class), users, store, new PdfRenderer(),
            mock(PlatformTransactionManager.class), "P1M,P6M");
    }

    private static Authentication auth(String name, String... authorities) {
        return new UsernamePasswordAuthenticationToken(name, "x", AuthorityUtils.createAuthorityList(authorities));
    }

    private static void status(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private Order owned(OrderStatus status) {
        Order order = order(status);
        order.setUser(Fixtures.user(1, "alice"));
        shipment(order, "Alice");
        when(orders.findForDocuments(1L)).thenReturn(Optional.of(order));
        when(users.findByUsername("alice")).thenReturn(Optional.of(order.getUser()));
        return order;
    }

    @Test
    void ownerGetsTheReceiptWithASafeFilename() {
        owned(OrderStatus.DELIVERED);
        PdfFile file = service.receipt(1L, auth("alice", "order:view-own"));
        assertThat(file.filename()).isEqualTo("receipt-ORD-001.pdf");
        assertThat(new String(file.content(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void strangersAnonymousAndUnknownOrdersAllGetTheSame404() {
        owned(OrderStatus.DELIVERED);
        User bob = Fixtures.user(2, "bob");
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));
        status(() -> service.receipt(1L, auth("bob", "order:view-own")), HttpStatus.NOT_FOUND);
        status(() -> service.receipt(1L, null), HttpStatus.NOT_FOUND);
        status(() -> service.receipt(2L, auth("alice", "order:view-own")), HttpStatus.NOT_FOUND);
        status(() -> service.receipt(null, auth("alice", "order:view-own")), HttpStatus.NOT_FOUND);
        status(() -> service.receipt(1L, auth("alice")), HttpStatus.NOT_FOUND);
        Authentication unauthenticated = auth("alice", "order:view-own");
        unauthenticated.setAuthenticated(false);
        status(() -> service.receipt(1L, unauthenticated), HttpStatus.NOT_FOUND);
    }

    @Test
    void staffWithViewAllMayReadAnyOrderAndGuestOrdersAreStaffOnly() {
        Order order = owned(OrderStatus.DELIVERED);
        assertThat(service.receipt(1L, auth("manager", "order:view-all")).content()).isNotEmpty();
        order.setUser(null);
        status(() -> service.receipt(1L, auth("alice", "order:view-own")), HttpStatus.NOT_FOUND);
        assertThat(service.receipt(1L, auth("manager", "order:view-all")).content()).isNotEmpty();
    }

    @Test
    void cancelledOrdersHaveNoReceipt() {
        owned(OrderStatus.CANCELLED);
        status(() -> service.receipt(1L, auth("alice", "order:view-own")), HttpStatus.CONFLICT);
    }

    @Test
    void warrantyCardOnlyAfterDeliveryAndOnlyWithNumbers() {
        Order order = owned(OrderStatus.SHIPPED);
        status(() -> service.warrantyCard(1L, null, auth("alice", "order:view-own")), HttpStatus.CONFLICT);
        order.setStatus(OrderStatus.DELIVERED);
        status(() -> service.warrantyCard(1L, null, auth("alice", "order:view-own")), HttpStatus.CONFLICT);
        unit(order, 7, "FRAME123", "E");
        assertThat(service.warrantyCard(1L, null, auth("alice", "order:view-own")).filename()).isEqualTo("warranty-card-ORD-001.pdf");
        assertThat(service.warrantyCard(1L, 7L, auth("alice", "order:view-own")).filename()).isEqualTo("warranty-card-FRAME123.pdf");
        status(() -> service.warrantyCard(1L, 99L, auth("alice", "order:view-own")), HttpStatus.NOT_FOUND);
    }

    @Test
    void deliveryEmailDocumentsAreEmptyForErasedAccountsUndeliveredOrdersAndMissingAddresses() {
        Order order = owned(OrderStatus.SHIPPED);
        assertThat(service.forDeliveredOrder(1L)).isEmpty();
        order.setStatus(OrderStatus.DELIVERED);
        assertThat(service.forDeliveredOrder(1L)).isPresent();
        order.setUser(erased());
        assertThat(service.forDeliveredOrder(1L)).isEmpty();
        order.setUser(null);
        assertThat(service.forDeliveredOrder(1L)).isEmpty(); // guest with no address at all
        order.setCustomerEmail("g@x.com");
        OrderDocumentService.DeliveryDocuments docs = service.forDeliveredOrder(1L).orElseThrow();
        assertThat(docs.guest()).isTrue();
        assertThat(docs.hasCard()).isFalse();
        assertThat(docs.files()).hasSize(1);
        assertThat(docs.email()).isEqualTo("g@x.com");
        when(orders.findForDocuments(anyLong())).thenReturn(Optional.empty());
        assertThat(service.forDeliveredOrder(5L)).isEmpty();
    }

    @Test
    void deliveryEmailAddsTheWarrantyCardWhenVehiclesHaveNumbers() {
        Order order = owned(OrderStatus.DELIVERED);
        unit(order, 1, "FRAME123", "E");
        OrderDocumentService.DeliveryDocuments docs = service.forDeliveredOrder(1L).orElseThrow();
        assertThat(docs.hasCard()).isTrue();
        assertThat(docs.files()).extracting(PdfFile::filename).containsExactly("receipt-ORD-001.pdf", "warranty-card-ORD-001.pdf");
        assertThat(Set.of(docs.recipientName())).containsExactly("Alice");
    }
}
