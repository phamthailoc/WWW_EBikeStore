package com.ebike.service.order;

import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.repository.admin.PromotionRepository;
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
    @Mock PromotionRepository promotions;
    @Mock OrderEmailVerificationService verification;
    @Mock com.ebike.repository.admin.PricingRuleRepository pricingRules;
    OrderServiceImpl service;
    Product product;

    @BeforeEach void setup() {
        service = new OrderServiceImpl(orders, users, products, showrooms, promotions, verification, new com.ebike.service.admin.PricingService(pricingRules));
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
        Promotion ebike100=new Promotion();ebike100.setCode("EBIKE100");ebike100.setStatus("LIVE");
        ebike100.setDiscountType("FIXED_AMOUNT");ebike100.setDiscountValue(new BigDecimal("100000"));
        when(promotions.findByCodeIgnoreCase("EBIKE100")).thenReturn(Optional.of(ebike100));
        var withCode=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,1)),"EBIKE100"));
        // 20.300.000 + 500.000 registration service (pricing default) - 100.000 EBIKE100
        assertEquals(0, withCode.totalAmount().compareTo(new BigDecimal("20700000")));
    }

    private void showroomIn(String city) {
        Showroom showroom=new Showroom();showroom.setId(9L);showroom.setName("KINETIC Test");showroom.setCity(city);
        showroom.setDistrict("Quan 1");showroom.setAddress("1 Test");showroom.setActive(true);
        when(showrooms.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of(showroom));
    }
    private BigDecimal homeFee(String province, int bikes) {
        return service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,bikes)),null,"HOME_DELIVERY",province)).shippingFee();
    }

    @Test void pickupAtStoreHasNoShippingFee() {
        stock();
        var quote=service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,2)),null,"STORE_PICKUP","Hà Nội"));
        assertEquals(0, quote.shippingFee().compareTo(BigDecimal.ZERO));
        assertEquals(0, quote.totalAmount().compareTo(new BigDecimal("40600000")));
    }

    @Test void homeDeliveryInAProvinceWithAShowroomUsesTheSameCityFee() {
        stock();showroomIn("TP. Ho Chi Minh");
        // Province names come from the checkout list ("Hồ Chí Minh"); showrooms are stored without accents.
        assertEquals(0, homeFee("Hồ Chí Minh",1).compareTo(new BigDecimal("200000")));
        assertEquals(0, homeFee("Thành phố Hồ Chí Minh",1).compareTo(new BigDecimal("200000")));
        // The fee is per bike.
        assertEquals(0, homeFee("Hồ Chí Minh",2).compareTo(new BigDecimal("400000")));
    }

    @Test void homeDeliveryToAnotherProvinceUsesTheOtherProvinceFee() {
        stock();showroomIn("TP. Ho Chi Minh");
        assertEquals(0, homeFee("Hà Nội",1).compareTo(new BigDecimal("800000")));
        assertEquals(0, homeFee("Hà Nội",2).compareTo(new BigDecimal("1600000")));
    }

    @Test void homeDeliveryFeeFollowsTheAdminPricingRule() {
        stock();showroomIn("TP. Ho Chi Minh");
        var rule=new com.ebike.model.entity.admin.PricingRule();rule.setCode("HOME_DELIVERY_OTHER_PROVINCE");
        rule.setAmount(new BigDecimal("950000"));rule.setStatus("ACTIVE");rule.setEffectiveFrom(java.time.LocalDateTime.now().minusDays(1));
        when(pricingRules.findByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(pricingRules.findByCode("HOME_DELIVERY_OTHER_PROVINCE")).thenReturn(Optional.of(rule));
        assertEquals(0, homeFee("Hà Nội",1).compareTo(new BigDecimal("950000")));
    }

    @Test void homeDeliveryQuoteWaitsForAProvinceBeforeChargingDelivery() {
        stock();
        assertEquals(0, homeFee(null,1).compareTo(BigDecimal.ZERO));
        assertEquals(0, homeFee("  ",1).compareTo(BigDecimal.ZERO));
    }

    @Test void orderTotalIncludesTheDeliveryFeeAndIgnoresTheClientsOwn() {
        stock(); save();showroomIn("TP. Ho Chi Minh");
        var req=new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            null,"123 Nguyễn Trãi, Phường Bến Thành","PAY_LATER",null,BigDecimal.ZERO,null,false,null,
            List.of(new OrderCreateItemRequest(1L,2)),"HOME_DELIVERY","Hà Nội");
        var order=service.createOrder(req,null);
        assertEquals(0, order.shippingFee().compareTo(new BigDecimal("1600000")));
        assertEquals(0, order.totalAmount().compareTo(new BigDecimal("42200000")));
    }

    @Test void registrationServiceIsChargedPerBike() {
        stock(); save(); showroomIn("TP. Ho Chi Minh");
        // 2 bikes x 500.000 registration service (pricing default)
        var quote=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,2)),null));
        assertEquals(0, quote.registrationFee().compareTo(new BigDecimal("1000000")));
        assertEquals(0, quote.registrationServicePerBike().compareTo(new BigDecimal("500000")));
        assertEquals(0, quote.totalAmount().compareTo(new BigDecimal("41600000")));
        var twoBikes=new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            null,"123 Nguyễn Trãi, Phường Bến Thành","PAY_LATER",null,null,null,true,null,
            List.of(new OrderCreateItemRequest(1L,2)),"HOME_DELIVERY","Hồ Chí Minh");
        var order=service.createOrder(twoBikes,null);
        assertEquals(0, order.registrationFee().compareTo(new BigDecimal("1000000")));
        // + 2 bikes x 200.000 home delivery inside Hồ Chí Minh
        assertEquals(0, order.totalAmount().compareTo(new BigDecimal("42000000")));
    }

    @Test void homeDeliveryStoresProvinceAndAddressWithoutShowroom() {
        stock(); save();
        var response=service.createOrder(request("HOME_DELIVERY","Hồ Chí Minh",null,"123 Nguyễn Trãi, Phường Bến Thành"),null);
        verify(showrooms,never()).findById(any());
        assertNull(response.shipment().pickupShowroom());
        assertEquals("HOME_DELIVERY",response.shipment().getDeliveryMethod());
        assertEquals("Hồ Chí Minh",response.shipment().getProvince());
        assertTrue(response.shipment().shippingAddress().contains("123 Nguyễn Trãi"));
        assertEquals(7,product.getStockQuantity());
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

    @Test void aProductWithColorsRequiresAnActiveSelectedColor() {
        stock();
        var variant=new com.ebike.model.entity.product.ProductVariant();variant.setId(2L);variant.setActive(true);product.getVariants().add(variant);
        assertThrows(ResponseStatusException.class,()->service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,1)),null)));
        variant.setActive(false);
        assertThrows(ResponseStatusException.class,()->service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,1,2L)),null)));
    }
}
