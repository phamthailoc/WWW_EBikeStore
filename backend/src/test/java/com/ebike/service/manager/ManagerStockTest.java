package com.ebike.service.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.request.ManagerProductStockUpdateRequest;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.manager.impl.ManagerServiceImpl;
import com.ebike.service.product.ProductService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V17: a product with colours sells from its colours, so its stock is the sum of the colours' stock. */
@ExtendWith(MockitoExtension.class)
class ManagerStockTest {
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ProductService productService;
    ManagerServiceImpl service;
    Product product;
    ProductVariant white;
    ProductVariant black;
    ProductVariant retired;

    @BeforeEach void setup() {
        service = new ManagerServiceImpl(orders, payments, users, products, productService);
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        white = variant(11L, 4, true);
        black = variant(12L, 6, true);
        retired = variant(13L, 9, false);
        product.getVariants().addAll(java.util.List.of(white, black, retired));
        product.setStockQuantity(10);
        lenient().when(products.findById(1L)).thenReturn(Optional.of(product));
        lenient().when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductVariant variant(long id, int stock, boolean active) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setStockQuantity(stock);
        variant.setActive(active);
        variant.setProduct(product);
        return variant;
    }

    private ManagerProductStockUpdateRequest stock(Integer quantity) { return new ManagerProductStockUpdateRequest(quantity); }

    @Test void restockingAColourUpdatesThatColourAndTheProductTotal() {
        service.updateVariantStock(1L, 11L, stock(10));
        assertEquals(10, white.getStockQuantity());
        assertEquals(6, black.getStockQuantity());
        // 10 + 6; the retired colour is not for sale and does not count
        assertEquals(16, product.getStockQuantity());
    }

    @Test void aSoldOutColourCanBeBroughtBack() {
        white.setStockQuantity(0);
        product.setStockQuantity(6);
        service.updateVariantStock(1L, 11L, stock(3));
        assertEquals(3, white.getStockQuantity());
        assertEquals(9, product.getStockQuantity());
    }

    @Test void aColourOfAnotherProductIsNotFound() {
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 99L, stock(1)));
        verify(products, never()).save(any());
    }

    @Test void negativeOrMissingQuantitiesAreRefused() {
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, stock(-1)));
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, stock(null)));
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, null));
        assertEquals(4, white.getStockQuantity());
    }

    @Test void theProductTotalCannotBeSetDirectlyWhenItHasColours() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.updateProductStock(1L, stock(50)));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(10, product.getStockQuantity());
    }

    @Test void aProductWithoutColoursStillTakesItsTotalDirectly() {
        product.getVariants().clear();
        service.updateProductStock(1L, stock(50));
        assertEquals(50, product.getStockQuantity());
    }
}
