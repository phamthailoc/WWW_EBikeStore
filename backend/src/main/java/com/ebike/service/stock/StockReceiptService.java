package com.ebike.service.stock;

import com.ebike.config.CacheNames;
import com.ebike.dto.stock.StockDtos.OptionColour;
import com.ebike.dto.stock.StockDtos.OptionProduct;
import com.ebike.dto.stock.StockDtos.ReceiptCreateRequest;
import com.ebike.dto.stock.StockDtos.ReceiptLineRequest;
import com.ebike.dto.stock.StockDtos.ReceiptLineResponse;
import com.ebike.dto.stock.StockDtos.ReceiptPage;
import com.ebike.dto.stock.StockDtos.ReceiptResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.model.entity.stock.StockReceiptLine;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockReceiptRepository;
import com.ebike.service.admin.AuditLogService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** V26: goods-received notes. A receipt adds stock to colours, writes one ledger line per colour and tells the customers waiting for a sold-out colour. */
@Service
public class StockReceiptService {

    static final int DEFAULT_PAGE = 20;
    static final int MAX_PAGE = 50;

    private final StockReceiptRepository receipts;
    private final ProductRepository products;
    private final StockLedger ledger;
    private final StockAlertService stockAlerts;
    private final AuditLogService auditLog;

    public StockReceiptService(
        StockReceiptRepository receipts,
        ProductRepository products,
        StockLedger ledger,
        StockAlertService stockAlerts,
        AuditLogService auditLog
    ) {
        this.receipts = receipts;
        this.products = products;
        this.ledger = ledger;
        this.stockAlerts = stockAlerts;
        this.auditLog = auditLog;
    }

    /** Newest first. {@code q} is a supplier name or a receipt number (PN-000012); {@code beforeId} continues after the last receipt of the previous page. */
    @Transactional(readOnly = true)
    public ReceiptPage list(String q, Long beforeId, Integer limit) {
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
        long before = beforeId == null || beforeId <= 0 ? Long.MAX_VALUE : beforeId;
        String text = StockRules.clean(q);
        Long number = StockRules.receiptIdOf(text);
        List<StockReceipt> rows;
        boolean hasMore = false;
        if (number != null) {
            rows = receipts.findDetailById(number).filter(receipt -> receipt.getId() < before).map(List::of).orElse(List.of());
        } else {
            rows = new ArrayList<>(receipts.search(before, StockRules.likePattern(text), PageRequest.of(0, size + 1)));
            if (rows.size() > size) {
                hasMore = true;
                rows = rows.subList(0, size);
            }
        }
        return new ReceiptPage(rows.stream().map(StockReceiptService::toResponse).toList(), hasMore);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(Long id) {
        return receipts.findDetailById(id)
            .map(StockReceiptService::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Không tìm thấy phiếu nhập kho."));
    }

    /** The pick list of the receipt form: every bike with its colours and what each has now. */
    @Transactional(readOnly = true)
    public List<OptionProduct> options() {
        return products.findAllWithVariants().stream().map(product -> {
            List<ProductVariant> colours = product.getVariants().stream()
                .sorted(Comparator.comparing((ProductVariant v) -> !Boolean.TRUE.equals(v.getDefaultVariant())).thenComparing(ProductVariant::getId))
                .toList();
            boolean hasActive = colours.stream().anyMatch(v -> Boolean.TRUE.equals(v.getActive()));
            return new OptionProduct(
                product.getId(), product.getName(), Boolean.TRUE.equals(product.getActive()), hasActive, orZero(product.getStockQuantity()),
                colours.stream().map(v -> new OptionColour(
                    v.getId(), v.getColorName() != null ? v.getColorName() : v.getVariantName(), v.getColorHex(),
                    Boolean.TRUE.equals(v.getActive()), orZero(v.getStockQuantity()))).toList());
        }).toList();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public ReceiptResponse create(ReceiptCreateRequest request) {
        if (request == null) {
            throw bad("Vui lòng nhập thông tin phiếu nhập.");
        }
        String supplier = StockRules.clean(request.supplierName());
        if (supplier == null) {
            throw bad("Vui lòng nhập nhà cung cấp.");
        }
        if (supplier.length() > StockRules.MAX_SUPPLIER_LENGTH) {
            throw bad("Tên nhà cung cấp không được vượt quá " + StockRules.MAX_SUPPLIER_LENGTH + " ký tự.");
        }
        String note = StockRules.clean(request.note());
        if (note != null && note.length() > StockRules.MAX_NOTE_LENGTH) {
            throw bad("Ghi chú không được vượt quá " + StockRules.MAX_NOTE_LENGTH + " ký tự.");
        }
        List<ReceiptLineRequest> lines = request.lines() == null ? List.of() : request.lines();
        StockRules.Problem problem = StockRules.checkLines(lines.stream()
            .map(line -> line == null ? null : new StockRules.LineInput(line.productId(), line.variantId(), line.quantity())).toList());
        if (problem != null) {
            throw problemOf(problem);
        }

        // resolve every line first, so a bad line five refuses the receipt before any stock is touched
        record Resolved(Product product, ProductVariant variant, int quantity, int number) {
        }
        Map<Long, Product> bikes = new LinkedHashMap<>();
        List<Resolved> resolved = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            ReceiptLineRequest line = lines.get(i);
            int number = i + 1;
            Product product = bikes.get(line.productId());
            if (product == null) {
                product = products.findById(line.productId()).orElseThrow(() -> bad(
                    "Dòng " + number + ": không tìm thấy xe."));
                bikes.put(product.getId(), product);
            }
            ProductVariant variant = null;
            if (line.variantId() != null) {
                variant = product.getVariants().stream().filter(v -> line.variantId().equals(v.getId())).findFirst().orElseThrow(() -> bad(
                    "Dòng " + number + ": màu này không thuộc xe đã chọn."));
            } else if (product.getVariants().stream().anyMatch(v -> Boolean.TRUE.equals(v.getActive()))) {
                throw bad("Dòng " + number + ": xe này bán theo màu, hãy chọn màu nhận.");
            }
            resolved.add(new Resolved(product, variant, line.quantity(), number));
        }

        // The colours of every bike are locked together with the bike: the bike's total is the sum of its active colours, so it has to be read from
        // rows nobody else changes meanwhile.
        List<ProductVariant> allColours = bikes.values().stream().flatMap(product -> product.getVariants().stream()).toList();
        ledger.lock(bikes.values(), allColours);

        // every line is checked against the figures just read under the lock before the first one is applied
        for (Resolved line : resolved) {
            Integer current = line.variant() != null ? line.variant().getStockQuantity() : line.product().getStockQuantity();
            if (!StockRules.fitsOnShelf(current, line.quantity())) {
                throw bad("Dòng " + line.number() + ": tồn kho sau khi nhập sẽ vượt " + StockRules.MAX_STOCK + " xe.");
            }
        }
        List<StockLedger.Entry> entries = new ArrayList<>();
        List<ProductVariant> backInStock = new ArrayList<>();
        for (Resolved line : resolved) {
            Product product = line.product();
            ProductVariant variant = line.variant();
            Integer before = variant != null ? variant.getStockQuantity() : product.getStockQuantity();
            int after = orZero(before) + line.quantity();
            if (variant != null) {
                variant.setStockQuantity(after);
                if (Boolean.TRUE.equals(variant.getActive()) && Boolean.TRUE.equals(product.getActive())
                    && StockAlertRules.isRestock(before, after)) {
                    backInStock.add(variant);
                }
            } else {
                product.setStockQuantity(after);
            }
            entries.add(StockLedger.Entry.of(product, variant, line.quantity(), StockMovementReason.RECEIPT, null));
        }
        for (Product product : bikes.values()) {
            if (product.getVariants().stream().anyMatch(v -> Boolean.TRUE.equals(v.getActive()))) {
                // only active colours can be ordered, so only they make up what the bike has in stock (same rule as a manual stock edit)
                product.setStockQuantity(product.getVariants().stream()
                    .filter(v -> Boolean.TRUE.equals(v.getActive()))
                    .mapToInt(v -> orZero(v.getStockQuantity()))
                    .sum());
            }
        }

        User actor = ledger.currentStaff();
        StockReceipt receipt = new StockReceipt();
        receipt.setSupplierName(supplier);
        receipt.setNote(note);
        receipt.setCreatedBy(actor);
        for (Resolved line : resolved) {
            StockReceiptLine row = new StockReceiptLine();
            row.setProduct(line.product());
            row.setVariant(line.variant());
            row.setQuantity(line.quantity());
            row.setProductName(line.product().getName());
            String colour = line.variant() == null ? null
                : line.variant().getColorName() != null ? line.variant().getColorName() : line.variant().getVariantName();
            row.setColorName(colour != null && colour.length() > 100 ? colour.substring(0, 100) : colour);
            receipt.addLine(row);
        }
        StockReceipt saved = receipts.save(receipt);
        ledger.record(entries, null, null, saved, actor);
        products.saveAll(bikes.values());
        // customers waiting for a colour that was sold out hear about it once the receipt is committed
        Set<Long> told = new LinkedHashSet<>();
        for (ProductVariant variant : backInStock) {
            if (told.add(variant.getId())) {
                stockAlerts.notifyRestocked(variant);
            }
        }
        int total = resolved.stream().mapToInt(Resolved::quantity).sum();
        auditLog.record("nhập kho", StockRules.receiptNumber(saved.getId()) + ": " + supplier + " (" + total + " xe)");
        return toResponse(saved);
    }

    static ReceiptResponse toResponse(StockReceipt receipt) {
        List<ReceiptLineResponse> lines = receipt.getLines().stream()
            .map(line -> new ReceiptLineResponse(
                line.getProduct() == null ? null : line.getProduct().getId(),
                line.getVariant() == null ? null : line.getVariant().getId(),
                line.getProductName(), line.getColorName(), line.getQuantity()))
            .toList();
        User by = receipt.getCreatedBy();
        return new ReceiptResponse(
            receipt.getId(), StockRules.receiptNumber(receipt.getId()), receipt.getSupplierName(), receipt.getNote(),
            by == null ? null : StockRules.displayName(by.getFirstName(), by.getLastName(), by.getUsername()),
            receipt.getCreatedAt(), lines.size(), lines.stream().mapToInt(ReceiptLineResponse::quantity).sum(), lines);
    }

    private static ResponseStatusException problemOf(StockRules.Problem problem) {
        int line = problem.line();
        return switch (problem.kind()) {
            case NO_LINES -> bad("Phiếu nhập cần có ít nhất một dòng.");
            case TOO_MANY_LINES -> bad("Một phiếu nhập có tối đa " + StockRules.MAX_LINES + " dòng.");
            case MISSING_PRODUCT -> bad("Dòng " + line + ": vui lòng chọn xe.");
            case BAD_QUANTITY -> bad("Dòng " + line + ": số lượng phải là số nguyên lớn hơn 0.");
            case QUANTITY_TOO_LARGE -> bad("Dòng " + line + ": mỗi dòng nhập tối đa " + StockRules.MAX_LINE_QUANTITY + " xe.");
            case DUPLICATE_LINE -> bad("Dòng " + line + ": màu này đã có ở dòng trên, hãy gộp số lượng vào một dòng.");
        };
    }

    private static ResponseStatusException bad(String vietnamese) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, vietnamese);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
