package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.config.order.VnPayProperties;
import com.ebike.controller.web.StorefrontController;
import com.ebike.controller.web.WorkspaceController;
import com.ebike.dto.product.response.CategorySummaryDto;
import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductFilterOptionsDto;
import com.ebike.dto.product.response.ProductSpecificationDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.service.product.ProductService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class WebControllersTest {

    private ProductService products;
    private StorefrontController storefront;

    @BeforeEach
    void setUp() {
        products = mock(ProductService.class);
        when(products.getFilterOptions()).thenReturn(new ProductFilterOptionsDto(List.of(), List.of(), List.of()));
        storefront = new StorefrontController(products, 14, "Asia/Ho_Chi_Minh");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static ProductSummaryDto summary(int n, boolean featured) {
        return new ProductSummaryDto((long) n, "P" + n, "p" + n, "d", BigDecimal.TEN, null, null, 0, 1, featured,
            new CategorySummaryDto(n % 3, "Cat" + (n % 3), "cat" + (n % 3)), List.of());
    }

    private List<ProductSummaryDto> stock(int count) {
        List<ProductSummaryDto> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(summary(i, i % 5 == 0));
        }
        when(products.getProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(list);
        return list;
    }

    private Model catalog(int page) {
        return catalog(null, null, null, "default", page);
    }

    private Model catalog(String q, Integer category, BigDecimal minPrice, String sort, int page) {
        Model model = new ConcurrentModel();
        assertThat(storefront.catalog(q, category, minPrice, null, null, null, null, null, null, false, sort, page, model)).isEqualTo("store/products");
        return model;
    }

    @SuppressWarnings("unchecked")
    private static List<ProductSummaryDto> shown(Model model) {
        return (List<ProductSummaryDto>) model.getAttribute("products");
    }

    // ---------------------------------------------------------------- catalogue paging

    @Test
    void catalogueShowsNineProductsPerPage() {
        stock(20);
        assertThat(shown(catalog(1))).hasSize(9);
        assertThat(shown(catalog(2))).hasSize(9);
        Model last = catalog(3);
        assertThat(shown(last)).hasSize(2);
        assertThat(last.getAttribute("pageCount")).isEqualTo(3);
        assertThat(last.getAttribute("productCount")).isEqualTo(20);
        assertThat(shown(catalog(2)).get(0).name()).isEqualTo("P10");
    }

    @Test
    void pageNumbersOutOfRangeAreClamped() {
        stock(20);
        assertThat(catalog(999).getAttribute("page")).isEqualTo(3);
        assertThat(shown(catalog(999))).hasSize(2);
        assertThat(catalog(0).getAttribute("page")).isEqualTo(1);
        assertThat(catalog(-7).getAttribute("page")).isEqualTo(1);
        assertThat(catalog(Integer.MAX_VALUE).getAttribute("page")).isEqualTo(3);
        assertThat(catalog(Integer.MIN_VALUE).getAttribute("page")).isEqualTo(1);
    }

    @Test
    void exactMultipleOfNineHasNoEmptyLastPage() {
        stock(18);
        assertThat(catalog(1).getAttribute("pageCount")).isEqualTo(2);
        assertThat(shown(catalog(5))).hasSize(9);
    }

    @Test
    void emptyCatalogueStillHasOnePage() {
        stock(0);
        Model model = catalog(1);
        assertThat(shown(model)).isEmpty();
        assertThat(model.getAttribute("pageCount")).isEqualTo(1);
        assertThat(model.getAttribute("page")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- noindex / canonical

    @Test
    void plainCatalogueAndItsPagesAreIndexableWithOwnPaths() {
        stock(20);
        Model first = catalog(1);
        assertThat(first.getAttribute("seoRobots")).isNull();
        assertThat(first.getAttribute("seoPath")).isEqualTo("/products");
        Model second = catalog(2);
        assertThat(second.getAttribute("seoRobots")).isNull();
        assertThat(second.getAttribute("seoPath")).isEqualTo("/products?page=2");
        assertThat(catalog(999).getAttribute("seoPath")).isEqualTo("/products?page=3");
    }

    @Test
    void anyFilterSearchOrSortMakesTheViewNoindexAndCanonicalToTheCatalogue() {
        stock(20);
        Model[] narrowed = {
            catalog("xe", null, null, "default", 2), catalog(null, 3, null, "default", 2), catalog(null, null, BigDecimal.ONE, "default", 2),
            catalog(null, null, null, "price-asc", 2), catalog(null, null, null, "bogus", 2)};
        for (Model model : narrowed) {
            assertThat(model.getAttribute("seoRobots")).isEqualTo("noindex,follow");
            assertThat(model.getAttribute("seoPath")).isEqualTo("/products");
        }
    }

    @Test
    void blankSearchTextOrBrandDoesNotCountAsNarrowing() {
        stock(3);
        Model model = new ConcurrentModel();
        storefront.catalog("   ", null, null, null, "  ", " ", "", null, null, false, "default", 1, model);
        assertThat(model.getAttribute("seoRobots")).isNull();
    }

    @Test
    void inStockTrueNarrowsTheView() {
        stock(3);
        Model model = new ConcurrentModel();
        storefront.catalog(null, null, null, null, null, null, null, null, null, true, "default", 1, model);
        assertThat(model.getAttribute("seoRobots")).isEqualTo("noindex,follow");
    }

    @ParameterizedTest
    @CsvSource({"price-asc,price,asc", "price-desc,price,desc", "name-asc,name,asc", "newest,newest,desc"})
    void sortOptionsAreMappedToTheServiceSort(String sort, String sortBy, String sortDir) {
        stock(1);
        catalog(null, null, null, sort, 1);
        verify(products).getProducts(null, null, null, null, null, null, null, null, null, false, sortBy, sortDir);
    }

    @Test
    void unknownSortKeepsTheDefaultOrder() {
        stock(1);
        catalog(null, null, null, "name-desc", 1);
        verify(products).getProducts(null, null, null, null, null, null, null, null, null, false, null, null);
    }

    @Test
    void filterValuesAreEchoedBackForTheForm() {
        stock(1);
        Model model = new ConcurrentModel();
        storefront.catalog("xe", 2, new BigDecimal("5"), new BigDecimal("9"), "Yadea", "E_BIKE", "LFP", new BigDecimal("1"), new BigDecimal("2"), true, "newest", 1, model);
        assertThat(model.getAttribute("query")).isEqualTo("xe");
        assertThat(model.getAttribute("categoryId")).isEqualTo(2);
        assertThat(model.getAttribute("brand")).isEqualTo("Yadea");
        assertThat(model.getAttribute("minPrice")).isEqualTo(new BigDecimal("5"));
        assertThat(catalog(1).getAttribute("categoryId")).isEqualTo("");
    }

    @Test
    void categoryChipsAreDistinct() {
        stock(9);
        @SuppressWarnings("unchecked")
        List<CategorySummaryDto> categories = (List<CategorySummaryDto>) catalog(1).getAttribute("categories");
        assertThat(categories).hasSize(3).doesNotHaveDuplicates();
    }

    // ---------------------------------------------------------------- home

    @Test
    void homeShowsFeaturedFourAndFallsBackToTheFirstFourWhenNoneAreFeatured() {
        stock(12);
        Model model = new ConcurrentModel();
        assertThat(storefront.home(null, model)).isEqualTo("store/home");
        @SuppressWarnings("unchecked")
        List<ProductSummaryDto> featured = (List<ProductSummaryDto>) model.getAttribute("featured");
        assertThat(featured).hasSize(2).allMatch(ProductSummaryDto::featured);
        assertThat(model.getAttribute("seoRobots")).isNull();
        List<ProductSummaryDto> none = new ArrayList<>(List.of(summary(1, false), summary(2, false), summary(3, false), summary(4, false), summary(5, false)));
        when(products.getProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(none);
        Model plain = new ConcurrentModel();
        storefront.home(null, plain);
        assertThat((List<?>) plain.getAttribute("featured")).hasSize(4);
    }

    @Test
    void homeSearchResultsAreNoindex() {
        stock(3);
        Model searched = new ConcurrentModel();
        storefront.home("xe", searched);
        assertThat(searched.getAttribute("seoRobots")).isEqualTo("noindex,follow");
        Model blank = new ConcurrentModel();
        storefront.home("  ", blank);
        assertThat(blank.getAttribute("seoRobots")).isNull();
        assertThat(blank.getAttribute("query")).isEqualTo("  ");
    }

    // ---------------------------------------------------------------- detail page flags

    private ProductDetailDto detail(String vehicleType) {
        ProductSpecificationDto spec = vehicleType == null ? null
            : new ProductSpecificationDto("m", "b", vehicleType, null, null, null, null, null, null, null, 12, false);
        return new ProductDetailDto(1L, "n", "xe-1", "d", BigDecimal.TEN, null, null, 0, 1, false, null, spec, List.of(), List.of());
    }

    private Model detailAs(Authentication authentication, String vehicleType) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(products.getProductBySlug("xe-1")).thenReturn(detail(vehicleType));
        Model model = new ConcurrentModel();
        assertThat(storefront.detail("xe-1", model)).isEqualTo("store/detail");
        return model;
    }

    @Test
    void visitorsSeeNotifyAndTestRideButtonsForARideableBike() {
        Model model = detailAs(new AnonymousAuthenticationToken("k", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")), "E_BIKE");
        assertThat(model.getAttribute("canNotifyStock")).isEqualTo(true);
        assertThat(model.getAttribute("stockAlertCustomer")).isEqualTo(false);
        assertThat(model.getAttribute("canTestRide")).isEqualTo(true);
        assertThat(model.getAttribute("testRideDays")).isEqualTo(14);
        assertThat(model.getAttribute("testRideToday").toString()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void customersSeeTheButtonsAndStaffDoNot() {
        Model customer = detailAs(new UsernamePasswordAuthenticationToken("c", "x", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")), "E_SCOOTER");
        assertThat(customer.getAttribute("stockAlertCustomer")).isEqualTo(true);
        assertThat(customer.getAttribute("canTestRide")).isEqualTo(true);
        Model staff = detailAs(new UsernamePasswordAuthenticationToken("m", "x", AuthorityUtils.createAuthorityList("ROLE_MANAGER")), "E_BIKE");
        assertThat(staff.getAttribute("canNotifyStock")).isEqualTo(false);
        assertThat(staff.getAttribute("canTestRide")).isEqualTo(false);
        assertThat(detailAs(null, "E_BIKE").getAttribute("canNotifyStock")).isEqualTo(true);
    }

    @Test
    void batteryAccessoryAndSpecLessProductsCannotBeTestRidden() {
        assertThat(detailAs(null, "BATTERY").getAttribute("canTestRide")).isEqualTo(false);
        assertThat(detailAs(null, "ACCESSORY").getAttribute("canTestRide")).isEqualTo(false);
        assertThat(detailAs(null, null).getAttribute("canTestRide")).isEqualTo(false);
        assertThat(detailAs(null, "ROCKET").getAttribute("canTestRide")).isEqualTo(false);
    }

    @Test
    void nonPositiveTestRideDaysAreRaisedToOne() {
        StorefrontController zero = new StorefrontController(products, 0, "Asia/Ho_Chi_Minh");
        when(products.getProductBySlug("xe-1")).thenReturn(detail("E_BIKE"));
        Model model = new ConcurrentModel();
        zero.detail("xe-1", model);
        assertThat(model.getAttribute("testRideDays")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- workspace

    private WorkspaceController workspace;

    private void initWorkspace() {
        MessageSource messages = mock(MessageSource.class);
        when(messages.getMessage(anyString(), any(), anyString(), any(Locale.class))).thenAnswer(i -> i.getArgument(2));
        VnPayProperties vnpay = mock(VnPayProperties.class);
        workspace = new WorkspaceController(vnpay, messages);
    }

    private Model open(String path, String... authorities) {
        initWorkspace();
        Model model = new ConcurrentModel();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        Authentication auth = new UsernamePasswordAuthenticationToken("u", "x", AuthorityUtils.createAuthorityList(authorities));
        assertThat(workspace.workspace(request, model, auth)).isEqualTo("store/workspace");
        return model;
    }

    private static List<String> hrefs(Model model) {
        List<String> list = new ArrayList<>();
        for (Object link : (List<?>) model.getAttribute("links")) {
            list.add((String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(link, "href"));
        }
        return list;
    }

    @Test
    void customerMenuHasOnlyCustomerPages() {
        Model model = open("/customer/orders", "ROLE_CUSTOMER", "profile:view");
        assertThat(model.getAttribute("workspaceRole")).isEqualTo("customer");
        assertThat(hrefs(model)).containsExactly("/customer", "/customer/orders", "/customer/payments", "/customer/notifications",
            "/customer/warranty", "/customer/test-rides", "/customer/profile", "/favorites");
        assertThat(model.getAttribute("api")).isEqualTo("/api/v1/orders");
    }

    @Test
    void managerMenuHasNoAdminPages() {
        Model model = open("/manager", "order:view-all");
        assertThat(model.getAttribute("workspaceRole")).isEqualTo("manager");
        assertThat(hrefs(model)).contains("/manager/orders", "/manager/reports").noneMatch(h -> h.startsWith("/admin"));
        assertThat(model.getAttribute("mode")).isEqualTo("dashboard");
    }

    @Test
    void adminMenuHasTheCatalogueRightAfterProductsAndNoStockReceiptsWithoutPermission() {
        Model model = open("/admin", "role:manage");
        List<String> hrefs = hrefs(model);
        assertThat(hrefs.indexOf("/admin/catalog")).isEqualTo(hrefs.indexOf("/admin/products") + 1);
        assertThat(hrefs).doesNotContain("/manager/stock-receipts");
        assertThat(model.getAttribute("canManageStock")).isEqualTo(false);
    }

    @Test
    void stockReceiptsMenuAppearsAfterTheStockPageOnlyForStaffWhoMayManageStock() {
        Model manager = open("/manager", "product:manage-stock");
        List<String> hrefs = hrefs(manager);
        assertThat(hrefs.indexOf("/manager/stock-receipts")).isEqualTo(hrefs.indexOf("/manager/inventory") + 1);
        Model customer = open("/customer", "product:manage-stock");
        assertThat(hrefs(customer)).doesNotContain("/manager/stock-receipts");
        Model admin = open("/admin", "product:manage-stock");
        List<String> adminLinks = hrefs(admin);
        assertThat(adminLinks.indexOf("/manager/stock-receipts")).isEqualTo(adminLinks.indexOf("/admin/inventory") + 1);
    }

    @Test
    void adminOpensTheStaffQueuesUnderAdminPathsButSharesTheManagerPage() {
        Model model = open("/admin/warranty", "role:manage");
        assertThat(model.getAttribute("pageKey")).isEqualTo("/manager/warranty");
        assertThat(model.getAttribute("workspaceRole")).isEqualTo("admin");
        assertThat(model.getAttribute("api")).isEqualTo("/api/v1/manager/warranty-claims");
        assertThat(model.getAttribute("loadDocuments")).isEqualTo(true);
    }

    @Test
    void staffWithoutACustomerProfileAreSentToTheirOwnQueueFromCustomerOnlyPages() {
        initWorkspace();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/warranty");
        Authentication staff = new UsernamePasswordAuthenticationToken("m", "x", AuthorityUtils.createAuthorityList("order:view-all"));
        assertThat(workspace.workspace(request, new ConcurrentModel(), staff)).isEqualTo("redirect:/manager/warranty");
        MockHttpServletRequest rides = new MockHttpServletRequest("GET", "/customer/test-rides");
        assertThat(workspace.workspace(rides, new ConcurrentModel(), staff)).isEqualTo("redirect:/manager/test-rides");
        Authentication customer = new UsernamePasswordAuthenticationToken("c", "x", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER", "profile:view"));
        assertThat(workspace.workspace(request, new ConcurrentModel(), customer)).isEqualTo("store/workspace");
        assertThat(workspace.workspace(rides, new ConcurrentModel(), customer)).isEqualTo("store/workspace");
    }

    @Test
    void exportButtonNeedsTheMatchingPermissionOnTheMatchingPage() {
        assertThat(open("/manager/orders", "order:view-all").getAttribute("canExport")).isEqualTo(true);
        assertThat(open("/manager/orders", "report:view").getAttribute("canExport")).isEqualTo(false);
        assertThat(open("/manager/reports", "report:view").getAttribute("canExport")).isEqualTo(true);
        assertThat(open("/manager/reports", "order:view-all").getAttribute("canExport")).isEqualTo(false);
        assertThat(open("/manager/customers", "order:view-all", "report:view").getAttribute("canExport")).isEqualTo(false);
    }

    @Test
    void updateButtonsNeedTheUpdatePermission() {
        assertThat(open("/manager/orders", "order:view-all").getAttribute("canUpdateOrders")).isEqualTo(false);
        assertThat(open("/manager/orders", "order:view-all", "order:update-status").getAttribute("canUpdateOrders")).isEqualTo(true);
    }

    @Test
    void orderDetailPagesCarryTheOrderIdInTheApiPath() {
        Model customer = open("/customer/orders/42", "ROLE_CUSTOMER");
        assertThat(customer.getAttribute("api")).isEqualTo("/api/v1/orders/42");
        assertThat(customer.getAttribute("mode")).isEqualTo("detail");
        assertThat(open("/manager/orders/7", "order:view-all").getAttribute("api")).isEqualTo("/api/v1/orders/7");
    }

    @Test
    void unknownWorkspacePathGetsTheNotFoundTitleAndNoApi() {
        Model model = open("/admin/does-not-exist", "role:manage");
        assertThat(model.getAttribute("title")).isEqualTo("Không tìm thấy trang");
        assertThat(model.getAttribute("api")).isEqualTo("");
    }

    @Test
    void anonymousAccessToTheWorkspaceControllerDoesNotCrash() {
        initWorkspace();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/manager/orders");
        assertThat(workspace.workspace(request, new ConcurrentModel(), null)).isEqualTo("store/workspace");
    }

    @Test
    void customerFlowPagesGetTheirTitleAndApi() {
        initWorkspace();
        Model model = new ConcurrentModel();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favorites");
        assertThat(workspace.customerFlow(request, model)).isEqualTo("store/flow");
        assertThat(model.getAttribute("api")).isEqualTo("/api/v1/favorites");
        assertThat(model.getAttribute("flow")).isEqualTo("favorites");
        Model success = new ConcurrentModel();
        workspace.customerFlow(new MockHttpServletRequest("GET", "/checkout/success"), success);
        assertThat(success.getAttribute("flow")).isEqualTo("checkout-success");
        assertThat(success.getAttribute("title")).isEqualTo("Đặt hàng thành công");
        Model chatbot = new ConcurrentModel();
        workspace.customerFlow(new MockHttpServletRequest("GET", "/chatbot"), chatbot);
        assertThat(chatbot.getAttribute("title")).isEqualTo("Trợ lý KINETIC");
    }
}
