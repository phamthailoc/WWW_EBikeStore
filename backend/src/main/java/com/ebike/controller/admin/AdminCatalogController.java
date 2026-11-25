package com.ebike.controller.admin;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.dto.admin.request.CatalogCategoryRequest;
import com.ebike.dto.admin.request.CatalogProductRequest;
import com.ebike.dto.admin.response.CatalogCategoryRow;
import com.ebike.dto.admin.response.CatalogProductDetail;
import com.ebike.dto.admin.response.CatalogProductRow;
import com.ebike.service.admin.AdminCatalogService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The admin catalogue: bikes and categories. /api/v1/admin/** already needs the admin permission; each action also checks its own. */
@RestController
@RequestMapping("/api/v1/admin/catalog")
public class AdminCatalogController {

    private final AdminCatalogService catalog;

    public AdminCatalogController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_UPDATE + "')")
    public List<CatalogProductRow> products(@RequestParam(required = false) String search, @RequestParam(required = false) Integer categoryId) {
        return catalog.listProducts(search, categoryId);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_UPDATE + "')")
    public CatalogProductDetail product(@PathVariable Long id) {
        return catalog.getProduct(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_CREATE + "')")
    public CatalogProductDetail createProduct(@RequestBody CatalogProductRequest request) {
        return catalog.createProduct(request);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_UPDATE + "')")
    public CatalogProductDetail updateProduct(@PathVariable Long id, @RequestBody CatalogProductRequest request) {
        return catalog.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_DELETE + "')")
    public void deleteProduct(@PathVariable Long id) {
        catalog.deleteProduct(id);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.CATEGORY_MANAGE + "')")
    public List<CatalogCategoryRow> categories(@RequestParam(required = false) String search) {
        return catalog.listCategories(search);
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.CATEGORY_MANAGE + "')")
    public CatalogCategoryRow category(@PathVariable Integer id) {
        return catalog.getCategory(id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.CATEGORY_MANAGE + "')")
    public CatalogCategoryRow createCategory(@RequestBody CatalogCategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.CATEGORY_MANAGE + "')")
    public CatalogCategoryRow updateCategory(@PathVariable Integer id, @RequestBody CatalogCategoryRequest request) {
        return catalog.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.CATEGORY_MANAGE + "')")
    public void deleteCategory(@PathVariable Integer id) {
        catalog.deleteCategory(id);
    }
}
