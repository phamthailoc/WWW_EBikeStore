package com.ebike.productModule.controller;

import com.ebike.productModule.dto.response.AdminProductImageDto;
import com.ebike.productModule.service.AdminProductImageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/product-images")
public class AdminProductImageController {

    private final AdminProductImageService adminProductImageService;

    public AdminProductImageController(AdminProductImageService adminProductImageService) {
        this.adminProductImageService = adminProductImageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminProductImageDto> uploadImage(
        @RequestParam Long productId,
        @RequestParam(required = false) Long variantId,
        @RequestParam MultipartFile file,
        @RequestParam(required = false) String altText,
        @RequestParam(required = false) Integer sortOrder,
        @RequestParam(required = false) Boolean primaryImage
    ) {
        AdminProductImageDto response = adminProductImageService.uploadImage(
            productId,
            variantId,
            file,
            altText,
            sortOrder,
            primaryImage
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/by-product/{productId}")
    public List<AdminProductImageDto> listImagesByProduct(@PathVariable Long productId) {
        return adminProductImageService.listImagesByProduct(productId);
    }

    @GetMapping("/{imageId}")
    public AdminProductImageDto getImage(@PathVariable Long imageId) {
        return adminProductImageService.getImage(imageId);
    }

    @PutMapping(value = "/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminProductImageDto updateImage(
        @PathVariable Long imageId,
        @RequestParam(required = false) MultipartFile file,
        @RequestParam(required = false) String altText,
        @RequestParam(required = false) Integer sortOrder,
        @RequestParam(required = false) Boolean primaryImage
    ) {
        return adminProductImageService.updateImage(
            imageId,
            file,
            altText,
            sortOrder,
            primaryImage
        );
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
        adminProductImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }
}
