package com.ebike.service.cart;

import com.ebike.dto.cart.CartLine;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.product.ProductRepository;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The shopping cart lives in the visitor's HTTP session on the server (guests and customers alike). Only the bike, the colour and the quantity are kept:
 * prices and stock are read again from the catalogue when the cart is shown and when the order is placed, so a cart can never carry an old price.
 */
@Service
public class SessionCartService {
    static final String ATTRIBUTE = "kinetic.cart";
    public static final int MAX_LINES = 20;
    public static final int MAX_QUANTITY = 10;

    private final ProductRepository products;

    public SessionCartService(ProductRepository products) {
        this.products = products;
    }

    @SuppressWarnings("unchecked")
    public List<CartLine> read(HttpSession session) {
        Object stored = session == null ? null : session.getAttribute(ATTRIBUTE);
        return stored instanceof List<?> list ? List.copyOf((List<CartLine>) list) : List.of();
    }

    /** Replaces the cart after checking every line against the catalogue; the checked lines are what is returned. */
    @Transactional(readOnly = true)
    public List<CartLine> replace(HttpSession session, List<CartLine> requested) {
        List<CartLine> lines = requested == null ? List.of() : requested;
        if (lines.size() > MAX_LINES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giỏ hàng tối đa " + MAX_LINES + " mẫu/màu xe.");
        }
        Set<String> seen = new HashSet<>();
        List<CartLine> checked = new ArrayList<>();
        for (CartLine line : lines) {
            if (line == null || line.id() == null || line.quantity() == null || line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng mỗi xe phải từ 1 đến " + MAX_QUANTITY + ".");
            }
            if (!seen.add(line.id() + ":" + line.variantId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một mẫu xe và màu chỉ xuất hiện một lần trong giỏ hàng.");
            }
            // a bike that no longer exists is dropped; one that is only switched off stays, so the cart page can say it is unavailable (the order is refused for it)
            Product product = products.findById(line.id()).orElse(null);
            if (product == null) {
                continue;
            }
            if (line.variantId() != null && product.getVariants().stream().noneMatch(candidate -> Objects.equals(candidate.getId(), line.variantId()))) {
                continue;
            }
            checked.add(new CartLine(product.getId(), line.variantId(), line.quantity(), product.getSlug()));
        }
        if (session != null) {
            session.setAttribute(ATTRIBUTE, new ArrayList<>(checked));
        }
        return checked;
    }

    public void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    /** After an order is placed the bought quantities leave the cart; lines of other bikes stay. A buy-now order that was never in the cart changes nothing. */
    public void removePurchased(HttpSession session, List<OrderCreateItemRequest> bought) {
        if (session == null || bought == null || bought.isEmpty()) {
            return;
        }
        List<CartLine> remaining = new ArrayList<>();
        for (CartLine line : read(session)) {
            int purchased = bought.stream()
                .filter(item -> item != null && Objects.equals(item.productId(), line.id()) && Objects.equals(item.productVariantId(), line.variantId()))
                .mapToInt(item -> item.quantity() == null ? 0 : item.quantity())
                .sum();
            if (line.quantity() > purchased) {
                remaining.add(new CartLine(line.id(), line.variantId(), line.quantity() - purchased, line.slug()));
            }
        }
        if (remaining.isEmpty()) {
            clear(session);
        } else {
            session.setAttribute(ATTRIBUTE, remaining);
        }
    }
}
