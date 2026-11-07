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
        product.setSpecification(spec(com.ebike.model.entity.product.VehicleType.E_SCOOTER,"50",800)); // needs a plate
    }

    private static com.ebike.model.entity.product.ProductSpecification spec(com.ebike.model.entity.product.VehicleType type,String speed,int watts) {
        var spec=new com.ebike.model.entity.product.ProductSpecification();
        spec.setVehicleType(type);spec.setMaxSpeedKmh(new BigDecimal(speed));spec.setMotorPowerWatts(watts);
        return spec;
    }
    private Product kickScooter() {
        Product small=new Product();small.setId(2L);small.setName("KINETIC Mini");
        small.setPrice(new BigDecimal("5000000"));small.setStockQuantity(8);small.setActive(true);
        small.setSpecification(spec(com.ebike.model.entity.product.VehicleType.E_SCOOTER,"20",200)); // no plate
        when(products.findById(2L)).thenReturn(Optional.of(small));
        return small;
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

    @Test void quoteShowsStateRegistrationFeesAsAReferenceOnly() {
        stock();
        // 20.300.000 x 2% = 406.000 and x 5% = 1.015.000 (pricing defaults); neither is added to the total.
        var quote=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,1)),null));
        assertEquals(0, quote.registrationTaxLow().compareTo(new BigDecimal("406000")));
        assertEquals(0, quote.registrationTaxHigh().compareTo(new BigDecimal("1015000")));
        assertEquals(0, quote.totalAmount().compareTo(new BigDecimal("20800000")));
        assertTrue(quote.registrationServiceAvailable());
    }

    @Test void aSwitchedOffRegistrationServiceCannotBeOrdered() {
        stock();
        var rule=new com.ebike.model.entity.admin.PricingRule();rule.setCode("REGISTRATION_SERVICE");
        rule.setAmount(new BigDecimal("500000"));rule.setStatus("INACTIVE");rule.setEffectiveFrom(java.time.LocalDateTime.now().minusDays(1));
        when(pricingRules.findByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(pricingRules.findByCode("REGISTRATION_SERVICE")).thenReturn(Optional.of(rule));
        var without=service.quoteOrder(new OrderQuoteRequest(false,List.of(new OrderCreateItemRequest(1L,1)),null));
        assertFalse(without.registrationServiceAvailable());
        assertEquals(0, without.totalAmount().compareTo(new BigDecimal("20300000")));
        // A buyer who ticked the option before the admin switched it off still gets a quote, without the fee,
        // so checkout can untick and hide the option instead of showing an error.
        var ticked=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,1)),null));
        assertFalse(ticked.registrationServiceAvailable());
        assertEquals(0, ticked.registrationFee().compareTo(BigDecimal.ZERO));
        assertEquals(0, ticked.totalAmount().compareTo(new BigDecimal("20300000")));
        var req=new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            null,"123 Nguyễn Trãi, Phường Bến Thành","PAY_LATER",null,null,null,true,null,
            List.of(new OrderCreateItemRequest(1L,1)),"HOME_DELIVERY","Hà Nội");
        assertThrows(ResponseStatusException.class,()->service.createOrder(req,null));
        verify(orders,never()).save(any());
    }

    private Promotion liveCode() {
        Promotion code=new Promotion();org.springframework.test.util.ReflectionTestUtils.setField(code,"id",5L);code.setCode("ONCE10");code.setStatus("LIVE");
        code.setDiscountType("FIXED_AMOUNT");code.setDiscountValue(new BigDecimal("100000"));
        code.setUsageCount(0);code.setUsageLimit(0);
        when(promotions.findByCodeIgnoreCase("ONCE10")).thenReturn(Optional.of(code));
        return code;
    }
    private OrderCreateRequest withCode(String email) {
        return new OrderCreateRequest(null,"Khách Hàng","0901234567",email,null,
            1L,null,"PAY_LATER","ONCE10",null,null,false,null,List.of(new OrderCreateItemRequest(1L,1)),"STORE_PICKUP",null);
    }
    private void openShowroom() {
        Showroom showroom=new Showroom();showroom.setId(1L);showroom.setName("KINETIC Trung Tâm");
        showroom.setAddress("456 Lê Lợi");showroom.setDistrict("Trung Tâm");showroom.setActive(true);
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
    }

    @Test void aGuestCannotUseTheSameCodeTwiceWithTheSameEmail() {
        stock();save();liveCode();openShowroom();
        when(promotions.tryIncrementUsage(5L)).thenReturn(1);
        when(orders.existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot("khach@example.com",5L,com.ebike.model.entity.order.OrderStatus.CANCELLED))
            .thenReturn(false, true);
        var first=service.createOrder(withCode("khach@example.com"),null);
        assertEquals(0, first.discountAmount().compareTo(new BigDecimal("100000")));
        var ex=assertThrows(ResponseStatusException.class,()->service.createOrder(withCode("khach@example.com"),null));
        assertTrue(ex.getReason().contains("đã dùng mã"));
        verify(promotions,times(1)).tryIncrementUsage(5L);
        verify(orders,times(1)).save(any());
    }

    @Test void aSignedInCustomerCannotUseTheSameCodeTwice() {
        stock();liveCode();openShowroom();
        var user=new com.ebike.model.entity.auth.User();user.setId(42L);
        when(users.findByUsername("an")).thenReturn(Optional.of(user));
        var auth=mock(org.springframework.security.core.Authentication.class);when(auth.getName()).thenReturn("an");
        when(orders.existsByUserIdAndPromotionIdAndStatusNot(42L,5L,com.ebike.model.entity.order.OrderStatus.CANCELLED)).thenReturn(true);
        assertThrows(ResponseStatusException.class,()->service.createOrder(withCode("an@example.com"),auth));
        verify(promotions,never()).tryIncrementUsage(any());
        verify(orders,never()).save(any());
    }

    @Test void theOrderRemembersWhichCodeItUsed() {
        stock();liveCode();openShowroom();
        when(promotions.tryIncrementUsage(5L)).thenReturn(1);
        var saved=org.mockito.ArgumentCaptor.forClass(com.ebike.model.entity.order.Order.class);
        when(orders.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        service.createOrder(withCode("khach@example.com"),null);
        assertEquals(5L, saved.getValue().getPromotion().getId());
    }

    @Test void registrationServiceIsOnlyOfferedForVehiclesThatNeedAPlate() {
        stock();kickScooter();
        var plated=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,1)),null));
        assertTrue(plated.registrationServiceAvailable());
        assertTrue(plated.registrationRequired());
        assertEquals(0, plated.registrationFee().compareTo(new BigDecimal("500000")));

        var small=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(2L,1)),null));
        assertFalse(small.registrationServiceAvailable());
        assertFalse(small.registrationRequired());
        assertEquals(0, small.registrationFee().compareTo(BigDecimal.ZERO));
        assertEquals(0, small.totalAmount().compareTo(new BigDecimal("5000000")));
    }

    @Test void inAMixedOrderOnlyTheBikesThatNeedAPlateAreCharged() {
        stock();kickScooter();
        var quote=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,2),new OrderCreateItemRequest(2L,3)),null));
        assertTrue(quote.registrationServiceAvailable());
        assertEquals(0, quote.registrationFee().compareTo(new BigDecimal("1000000"))); // 2 plated x 500.000
    }

    @Test void anOrderCannotAskForRegistrationWhenNoBikeNeedsOne() {
        kickScooter();
        var req=new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            1L,null,"PAY_LATER",null,null,null,true,null,
            List.of(new OrderCreateItemRequest(2L,1)),"STORE_PICKUP",null);
        var ex=assertThrows(ResponseStatusException.class,()->service.createOrder(req,null));
        assertTrue(ex.getReason().contains("không thuộc diện phải đăng ký"), ex.getReason());
        verify(orders,never()).save(any());
    }

    @Test void registrationServiceIsChargedPerBike() {
        stock(); save(); openShowroom();
        // 2 bikes x 500.000 registration service (pricing default)
        var quote=service.quoteOrder(new OrderQuoteRequest(true,List.of(new OrderCreateItemRequest(1L,2)),null));
        assertEquals(0, quote.registrationFee().compareTo(new BigDecimal("1000000")));
        assertEquals(0, quote.registrationServicePerBike().compareTo(new BigDecimal("500000")));
        assertEquals(0, quote.totalAmount().compareTo(new BigDecimal("41600000")));
        var twoBikes=new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            1L,null,"PAY_LATER",null,null,null,true,null,
            List.of(new OrderCreateItemRequest(1L,2)),"STORE_PICKUP",null);
        var order=service.createOrder(twoBikes,null);
        assertEquals(0, order.registrationFee().compareTo(new BigDecimal("1000000")));
        assertEquals(0, order.totalAmount().compareTo(new BigDecimal("41600000")));
    }

    @Test void registrationHelpIsOnlyOfferedWhenTheBuyerCollectsAtTheStore() {
        // The owner declares the bike on their own VNeID account; the store can only help when they come in.
        stock(); showroomIn("TP. Ho Chi Minh");
        var items=List.of(new OrderCreateItemRequest(1L,1));
        var home=service.quoteOrder(new OrderQuoteRequest(true,items,null,"HOME_DELIVERY","Hồ Chí Minh"));
        assertTrue(home.registrationRequired(), "the bike still needs a plate");
        assertFalse(home.registrationServiceAvailable());
        assertEquals(0, home.registrationFee().compareTo(BigDecimal.ZERO));
        var pickup=service.quoteOrder(new OrderQuoteRequest(true,items,null,"STORE_PICKUP",null));
        assertTrue(pickup.registrationServiceAvailable());
        assertEquals(0, pickup.registrationFee().compareTo(new BigDecimal("500000")));

        var homeOrder=new OrderCreateRequest(null,"Khách Hàng","0901234567","checkout@example.com",null,
            null,"123 Nguyễn Trãi, Phường Bến Thành","PAY_LATER",null,null,null,true,null,
            items,"HOME_DELIVERY","Hồ Chí Minh");
        var ex=assertThrows(ResponseStatusException.class,()->service.createOrder(homeOrder,null));
        assertTrue(ex.getReason().contains("nhận xe tại cửa hàng"), ex.getReason());
        verify(orders,never()).save(any());
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
