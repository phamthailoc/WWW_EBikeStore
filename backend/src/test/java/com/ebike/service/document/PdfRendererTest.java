package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.service.warranty.WarrantyRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** V27: the real templates become real PDFs, with the Vietnamese letters intact. Reads the PDF back with PDFBox. */
class PdfRendererTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 8, 0);
    private final PdfRenderer renderer = new PdfRenderer();

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private static int pagesOf(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private byte[] receipt(Order order, String language) {
        return renderer.render("receipt", DocumentModels.receipt(order, null, DocumentFixtures.store(), NOW));
    }

    @Test
    void theVietnameseReceiptKeepsEveryAccentAndTheAmounts() throws IOException {
        Order order = DocumentFixtures.order(1, OrderStatus.DELIVERED, 2, true);

        byte[] pdf = receipt(order, "vi");

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        String text = textOf(pdf);
        assertTrue(text.contains("PHIẾU BÁN HÀNG"), text);
        assertTrue(text.contains("Công ty TNHH Kinetic E-Bike (mô phỏng)"), text);
        assertTrue(text.contains("Nguyễn Văn An"), text);
        assertTrue(text.contains("Kinetic Aero X"), text);
        assertTrue(text.contains("Xanh rêu"), text);
        assertTrue(text.contains("Số khung RLEBK00002"), text);
        assertTrue(text.contains("40.000.000 ₫"), text);
        assertTrue(text.contains("Bốn mươi triệu đồng chẵn"), text);
        assertTrue(text.contains("không phải hóa đơn điện tử"), text);
        assertTrue(text.contains("Mã số thuế: 0123456789"), text);
        assertEquals(1, pagesOf(pdf));
    }

    @Test
    void theCustomersOwnWordsAreTextNeverMarkup() throws IOException {
        Order order = DocumentFixtures.order(3, OrderStatus.DELIVERED, 1, true);

        String text = textOf(receipt(order, "vi"));

        assertTrue(text.contains("Gọi trước khi giao <b>nhé</b> & cảm ơn"), text);
    }

    @Test
    void controlCharactersInTheCustomersWordsDoNotBreakThePage() throws IOException {
        Order order = DocumentFixtures.order(3, OrderStatus.DELIVERED, 1, true);
        order.setNotes("Gọi trước\013 khi\b giao");
        order.getShipment().setRecipientName("An\013 Nguyễn");

        String text = textOf(receipt(order, "vi"));

        assertTrue(text.contains("Gọi trước khi giao"), text);
        assertTrue(text.contains("An Nguyễn"), text);
    }

    @Test
    void aProvisionalReceiptSaysSoOnThePage() throws IOException {
        Order order = DocumentFixtures.order(4, OrderStatus.CONFIRMED, 1, false);

        String text = textOf(receipt(order, "vi"));

        assertTrue(text.contains("PHIẾU TẠM TÍNH"), text);
        assertTrue(text.contains("số liệu có thể thay đổi"), text);
    }

    @Test
    void aLongOrderFlowsOntoMorePagesWithoutBreakingARow() throws IOException {
        Order order = DocumentFixtures.order(5, OrderStatus.DELIVERED, 10, true);
        // twenty lines of ten bikes each is the most an order can hold; copy the line to fill the page
        var firstItem = order.getItems().iterator().next();
        for (int index = 1; index < 19; index++) {
            var copy = new com.ebike.model.entity.order.OrderItem();
            copy.setId(100L + index);
            copy.setOrder(order);
            copy.setProduct(firstItem.getProduct());
            copy.setProductName("Kinetic Aero X " + index);
            copy.setUnitPrice(firstItem.getUnitPrice());
            copy.setQuantity(10);
            copy.setLineTotal(firstItem.getLineTotal());
            order.getItems().add(copy);
        }

        byte[] pdf = receipt(order, "vi");

        assertTrue(pagesOf(pdf) >= 2);
        assertTrue(textOf(pdf).contains("Kinetic Aero X 18"));
    }

    @Test
    void everyWarrantyCardTakesExactlyOnePage() throws IOException {
        Order order = DocumentFixtures.order(6, OrderStatus.DELIVERED, 3, true);
        List<OrderItemUnit> units = List.copyOf(order.getItems().iterator().next().getUnits());
        Map<String, Object> model = DocumentModels.warrantyCards(order, units, DocumentFixtures.store(),
            WarrantyRules.parseIntervals(WarrantyRules.DEFAULT_INTERVALS), NOW);

        byte[] pdf = renderer.render("warranty-card", model);

        assertEquals(3, pagesOf(pdf));
        String text = textOf(pdf);
        assertTrue(text.contains("PHIẾU BẢO HÀNH"), text);
        assertTrue(text.contains("RLEBK00001") && text.contains("RLEBK00003"), text);
        assertTrue(text.contains("24 tháng"), text);
        assertTrue(text.contains("20/11/2028"), text);
        assertTrue(text.contains("Sau 36 tháng kể từ ngày bàn giao"), text);
        assertTrue(text.contains("https://shop.example.com/warranty"), text);
    }

    @Test
    void aLongScheduleAndALongShowroomTextStillTakeOnePagePerCard() throws IOException {
        Order order = DocumentFixtures.order(6, OrderStatus.DELIVERED, 2, true);
        var showroom = order.getShipment().getPickupShowroom();
        showroom.setName("Showroom với một cái tên rất dài ".repeat(8));
        showroom.setAddress("Số 12 đường Nguyễn Văn Linh, phường Tân Phong, Quận 7, Thành phố Hồ Chí Minh ".repeat(6));
        List<OrderItemUnit> units = List.copyOf(order.getItems().iterator().next().getUnits());
        String manyIntervals = String.join(",", java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(month -> "P" + month + "M").toList());
        for (String language : List.of("vi", "en")) {
            Map<String, Object> model = DocumentModels.warrantyCards(order, units, DocumentFixtures.store(), WarrantyRules.parseIntervals(manyIntervals), NOW);

            assertEquals(2, pagesOf(renderer.render("warranty-card", model)), language);
        }
    }

}
