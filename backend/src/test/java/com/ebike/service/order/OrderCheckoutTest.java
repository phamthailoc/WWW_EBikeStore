package com.ebike.service.order;

import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.order.impl.OrderServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutTest {
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock OrderEmailVerificationService verification;
    OrderServiceImpl service;
    Product product;

    @BeforeEach void setup() {
        service = new OrderServiceImpl(orders, users, products, showrooms, verification);
        product = new Product();
        product.setId(1L); product.setName("KINETIC Test");
        product.setPrice(new BigDecimal("20300000")); product.setStockQuantity(8); product.setActive(true);
    }

    private void stock() { when(products.findById(1L)).thenReturn(Optional.of(product)); }
    private void save() { when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0)); }
    private OrderCreateRequest request(String method, String province, Long showroom, String address) {
        return new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            showroom,address,"PAY_LATER",null,null,null,false,"Checkout regression test",
            List.of(new OrderCreateItemRequest(1L,1)),method,province);
    }

    @Test void noCodeHasNoAutomaticDiscountAndServicesAreExplicit() {
        stock();
        var plain=service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,2)),null));
        assertEquals(0, plain.discountAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, plain.totalAmount().compareTo(new BigDecimal("40600000")));
        var withCode=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,1)),"EBIKE100"));
        assertEquals(0, withCode.totalAmount().compareTo(new BigDecimal("22700000")));
    }

    @Test void homeDeliveryStoresProvinceAndAddressWithoutShowroom() {
        stock(); save();
        var response=service.createOrder(request("HOME_DELIVERY","Hồ Chí Minh",null,"123 Nguyễn Trãi, Phường Bến Thành"),null);
        assertNull(response.shipment().pickupShowroom());
        assertEquals("HOME_DELIVERY",response.shipment().getDeliveryMethod());
        assertEquals("Hồ Chí Minh",response.shipment().getProvince());
        assertTrue(response.shipment().shippingAddress().contains("123 Nguyễn Trãi"));
        assertEquals(7,product.getStockQuantity());
        verifyNoInteractions(showrooms);
    }

    @Test void pickupUsesSelectedStoreAddressWithoutRequiringHomeAddress() {
        stock(); save();
        Showroom showroom=new Showroom();showroom.setId(1L);showroom.setName("KINETIC Trung Tâm");
        showroom.setAddress("456 Lê Lợi");showroom.setDistrict("Trung Tâm");showroom.setActive(true);
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        var response=service.createOrder(request("STORE_PICKUP",null,1L,null),null);
        assertEquals("STORE_PICKUP",response.shipment().getDeliveryMethod());
        assertEquals("KINETIC Trung Tâm - 456 Lê Lợi",response.shipment().shippingAddress());
    }

    @Test void homeDeliveryRequiresProvince() {
        assertThrows(ResponseStatusException.class,()->service.createOrder(request("HOME_DELIVERY","",null,"123 Nguyễn Trãi, Phường Bến Thành"),null));
        verifyNoInteractions(orders,products,showrooms);
    }

    @Test void pickupRequiresStoreAndInvalidMethodsAreRejected() {
        assertThrows(ResponseStatusException.class,()->service.createOrder(request("STORE_PICKUP",null,null,null),null));
        assertThrows(ResponseStatusException.class,()->service.createOrder(request("UNKNOWN",null,1L,null),null));
        verifyNoInteractions(orders,products,showrooms);
    }

    @Test void quotesRejectInsufficientStockAndInvalidCodes() {
        stock();product.setStockQuantity(1);
        assertThrows(ResponseStatusException.class,()->service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,2)),null)));
        assertThrows(ResponseStatusException.class,()->service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,1),new OrderCreateItemRequest(1L,1)),null)));
        assertThrows(ResponseStatusException.class,()->service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,1)),"INVALID")));
    }
}
