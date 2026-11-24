package com.ebike.service.stock;

import com.ebike.common.i18n.RequestLanguage;
import com.ebike.dto.stock.StockDtos.BalanceResponse;
import com.ebike.dto.stock.StockDtos.HistoryResponse;
import com.ebike.dto.stock.StockDtos.MovementResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockMovementRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** V26: the history of one bike's stock, newest line first, with where each colour stands now. */
@Service
public class StockHistoryService {

    static final int DEFAULT_PAGE = 30;
    static final int MAX_PAGE = 100;

    private final ProductRepository products;
    private final StockMovementRepository movements;

    public StockHistoryService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(Long productId, Long variantId, String reasonsCsv, Long beforeId, Integer limit) {
        Product product = products.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy xe."));
        if (variantId != null && product.getVariants().stream().noneMatch(v -> variantId.equals(v.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu này của xe.");
        }
        Set<StockMovementReason> reasons;
        try {
            reasons = StockRules.parseReasons(reasonsCsv);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do lọc không hợp lệ.");
        }
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
        long before = beforeId == null || beforeId <= 0 ? Long.MAX_VALUE : beforeId;
        PageRequest page = PageRequest.of(0, size + 1);
        List<StockMovement> rows = new ArrayList<>(variantId != null
            ? movements.findOfVariant(variantId, reasons, before, page)
            : movements.findOfProduct(productId, reasons, before, page));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        // the balances are the same on every page: only the first page asks for them
        List<BalanceResponse> balances = beforeId == null ? balancesOf(product) : List.of();
        return new HistoryResponse(
            product.getId(), product.getName(), product.getStockQuantity() == null ? 0 : product.getStockQuantity(),
            balances, rows.stream().map(StockHistoryService::toResponse).toList(), hasMore);
    }

    private List<BalanceResponse> balancesOf(Product product) {
        List<BalanceResponse> balances = new ArrayList<>();
        List<ProductVariant> colours = product.getVariants().stream()
            .sorted(Comparator.comparing((ProductVariant v) -> !Boolean.TRUE.equals(v.getDefaultVariant())).thenComparing(ProductVariant::getId))
            .toList();
        for (ProductVariant variant : colours) {
            int stock = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
            long sum = movements.sumOfVariant(variant.getId());
            balances.add(new BalanceResponse(
                variant.getId(), variant.getColorName() != null ? variant.getColorName() : variant.getVariantName(),
                Boolean.TRUE.equals(variant.getActive()), stock, sum, StockRules.ledgerMatches(stock, sum)));
        }
        if (colours.stream().noneMatch(v -> Boolean.TRUE.equals(v.getActive()))) {
            // a bike without colours sells from its own stock
            int stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            long sum = movements.sumOfProductLevel(product.getId());
            balances.add(new BalanceResponse(null, null, true, stock, sum, StockRules.ledgerMatches(stock, sum)));
        }
        return balances;
    }

    static MovementResponse toResponse(StockMovement line) {
        User actor = line.getActor();
        ProductVariant variant = line.getVariant();
        return new MovementResponse(
            line.getId(), line.getReason().name(), line.getQuantityChange(), line.getBalanceAfter(),
            variant == null ? null : variant.getId(),
            variant == null ? null : variant.getColorName() != null ? variant.getColorName() : variant.getVariantName(),
            line.getOrderId(), line.getOrderNumber(),
            line.getReceipt() == null ? null : line.getReceipt().getId(),
            line.getReceipt() == null ? null : StockRules.receiptNumber(line.getReceipt().getId()),
            actor == null ? null : StockRules.displayName(actor.getFirstName(), actor.getLastName(), actor.getUsername()),
            line.getNote(), line.getCreatedAt());
    }
}
