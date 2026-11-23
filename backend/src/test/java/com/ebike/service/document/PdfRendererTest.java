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
        return renderer.render("receipt", DocumentModels.receipt(order, null, DocumentFixtures.store(), language, NOW));
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
    void theEnglishReceiptIsInEnglishAndNeverMixesInVietnameseLabels() throws IOException {
        Order order = DocumentFixtures.order(2, OrderStatus.DELIVERED, 1, true);

        String text = textOf(receipt(order, "en"));

        assertTrue(text.contains("SALES RECEIPT"), text);
        assertTrue(text.contains("Pick up at the showroom"), text);
        assertTrue(text.contains("20,000,000 ₫"), text);
        assertTrue(text.contains("Twenty million Vietnamese dong"), text);
        assertTrue(text.contains("Paid"), text);
        assertFalse(text.contains("PHIẾU BÁN HÀNG"), text);
        assertFalse(text.contains("Tạm tính"), text);
    }

    @Test
    void theCustomersOwnWordsAreTextNeverMarkup() throws IOException {
        Order order = DocumentFixtures.order(3, OrderStatus.DELIVERED, 1, true);

        String text = textOf(receipt(order, "vi"));

        assertTrue(text.contains("Gọi trước khi giao <b>nhé</b> & cảm ơn"), text);
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
            WarrantyRules.parseIntervals(WarrantyRules.DEFAULT_INTERVALS), "vi", NOW);

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
    void theEnglishWarrantyCardIsInEnglish() throws IOException {
        Order order = DocumentFixtures.order(7, OrderStatus.DELIVERED, 1, true);
        List<OrderItemUnit> units = List.copyOf(order.getItems().iterator().next().getUnits());

        String text = textOf(renderer.render("warranty-card", DocumentModels.warrantyCards(order, units, DocumentFixtures.store(),
            WarrantyRules.parseIntervals(WarrantyRules.DEFAULT_INTERVALS), "en", NOW)));

        assertTrue(text.contains("WARRANTY CARD"), text);
        assertTrue(text.contains("24 months"), text);
        assertTrue(text.contains("20 Nov 2028"), text);
        assertTrue(text.contains("6 months after hand-over"), text);
        assertFalse(text.contains("PHIẾU BẢO HÀNH"), text);
    }
}
