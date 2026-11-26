package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.product.response.AdminProductImageDto;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductImage;
import com.ebike.model.entity.product.ProductImageStatus;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StorageProvider;
import com.ebike.repository.product.ProductImageRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.service.product.AdminProductImageService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

class AdminProductImageServiceTest {

    private ProductRepository products;
    private ProductVariantRepository variants;
    private ProductImageRepository images;
    private com.ebike.service.product.ProductImageStorageService storage;
    private AdminProductImageService service;
    private Product product;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        variants = mock(ProductVariantRepository.class);
        images = mock(ProductImageRepository.class);
        storage = mock(com.ebike.service.product.ProductImageStorageService.class);
        service = new AdminProductImageService(products, variants, images, storage);
        product = Fixtures.product(5, "Bike", "100");
        when(products.findById(5L)).thenReturn(Optional.of(product));
        when(storage.getProductImagePrefix()).thenReturn("/products/");
        when(storage.getBucket()).thenReturn("bucket");
        when(storage.getRegion()).thenReturn("ap-1");
        when(storage.upload(anyString(), any(byte[].class), anyString())).thenAnswer(i -> "https://cdn/" + i.getArgument(0));
        when(images.save(any(ProductImage.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static MockMultipartFile png(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "image/png", bytes);
    }

    private static void status(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private AdminProductImageDto upload(MockMultipartFile file, Long variantId, Integer order, Boolean primary) {
        return service.uploadImage(5L, variantId, file, "  alt ", order, primary);
    }

    // ---- upload validation

    @Test
    void uploadStoresMetadataChecksumAndPublicUrl() {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        AdminProductImageDto dto = upload(png("front view.png", bytes), null, null, null);
        assertThat(dto.checksumSha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(dto.fileSize()).isEqualTo(3L);
        assertThat(dto.mimeType()).isEqualTo("image/png");
        assertThat(dto.altText()).isEqualTo("alt");
        assertThat(dto.sortOrder()).isZero();
        assertThat(dto.primaryImage()).isFalse();
        assertThat(dto.status()).isEqualTo(ProductImageStatus.ACTIVE);
        assertThat(dto.storageProvider()).isEqualTo(StorageProvider.S3);
        assertThat(dto.storageBucket()).isEqualTo("bucket");
        assertThat(dto.s3Key()).startsWith("products/5/gallery/").endsWith("-front-view.png");
        assertThat(dto.imageUrl()).isEqualTo("https://cdn/" + dto.s3Key());
    }

    @Test
    void nullAndEmptyFilesAreBadRequestsAndNothingIsUploaded() {
        status(() -> service.uploadImage(5L, null, null, null, null, null), HttpStatus.BAD_REQUEST);
        status(() -> upload(png("a.png", new byte[0]), null, null, null), HttpStatus.BAD_REQUEST);
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/pdf", "application/octet-stream", "video/mp4", "imag/png", "text/html"})
    void nonImageContentTypesAreRejected(String type) {
        status(() -> upload(new MockMultipartFile("file", "a.png", type, new byte[] {1}), null, null, null), HttpStatus.BAD_REQUEST);
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void missingContentTypeIsRejected() {
        status(() -> upload(new MockMultipartFile("file", "a.png", null, new byte[] {1}), null, null, null), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/webp", "IMAGE/PNG", "image/gif"})
    void imageContentTypesAreAccepted(String type) {
        assertThat(upload(new MockMultipartFile("file", "a.img", type, new byte[] {1}), null, null, null).mimeType()).isEqualTo(type);
    }

    /**
     * Expected: the file is checked to be an image (magic bytes), not just trusted by its declared Content-Type. Actual: an executable
     * posted as "image/png" is stored in the public bucket. AdminProductImageService.java:264-270.
     */
    @Test
    @Tag("bug")
    void bug_declaredImageTypeIsNotVerifiedAgainstContent() {
        MockMultipartFile fake = png("evil.png", "MZ\u0090\u0000 this is an executable".getBytes(StandardCharsets.ISO_8859_1));
        status(() -> upload(fake, null, null, null), HttpStatus.BAD_REQUEST);
    }

    /** Expected: scripts-capable SVG is not accepted as a product photo. Actual: "image/svg+xml" passes the image/ prefix check. */
    @Test
    @Tag("bug")
    void bug_svgWithScriptIsAcceptedAsProductImage() {
        MockMultipartFile svg = new MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8));
        status(() -> upload(svg, null, null, null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void tenMegabyteLimitLivesInSpringMultipartConfiguration() throws Exception {
        java.util.Properties properties = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        assertThat(properties.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("10MB");
        assertThat(properties.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("10MB");
    }

    // ---- keys / filenames

    @Test
    void filenamesAreSanitisedAndPathTraversalCannotEscapeTheKeyPrefix() {
        String key = upload(png("../../etc/pass wd.png", new byte[] {1}), null, null, null).s3Key();
        assertThat(key).startsWith("products/5/gallery/").doesNotContain("/../").doesNotContain(" ");
        assertThat(key.substring("products/5/gallery/".length())).doesNotContain("/");
    }

    @Test
    void vietnameseAndMissingFilenamesGetASafeName() {
        assertThat(upload(png("ảnh xe.png", new byte[] {1}), null, null, null).s3Key()).matches("products/5/gallery/[0-9a-f-]{36}-[A-Za-z0-9._-]+");
        assertThat(upload(png(null, new byte[] {1}), null, null, null).s3Key()).endsWith("-image.bin");
        assertThat(upload(png("   ", new byte[] {1}), null, null, null).s3Key()).endsWith("-image.bin");
    }

    @Test
    void twoUploadsOfTheSameFileGetDifferentKeys() {
        String a = upload(png("a.png", new byte[] {1}), null, null, null).s3Key();
        String b = upload(png("a.png", new byte[] {1}), null, null, null).s3Key();
        assertThat(a).isNotEqualTo(b);
    }

    /** Expected: an S3 key is at most 1024 bytes, so a very long file name is shortened or refused (400). Actual: it is put in the key as is. */
    @Test
    @Tag("bug")
    void bug_veryLongFilenameProducesAnOversizedS3Key() {
        String key;
        try {
            key = upload(png("a".repeat(3000) + ".png", new byte[] {1}), null, null, null).s3Key();
        } catch (ResponseStatusException e) {
            return;
        }
        assertThat(key.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(1024);
    }

    // ---- variant

    @Test
    void variantMustExistAndBelongToTheProduct() {
        when(variants.findById(9L)).thenReturn(Optional.empty());
        status(() -> upload(png("a.png", new byte[] {1}), 9L, null, null), HttpStatus.NOT_FOUND);
        Product other = Fixtures.product(6, "Other", "1");
        ProductVariant foreign = Fixtures.variant(other, 8, "F", false);
        when(variants.findById(8L)).thenReturn(Optional.of(foreign));
        status(() -> upload(png("a.png", new byte[] {1}), 8L, null, null), HttpStatus.BAD_REQUEST);
        ProductVariant own = Fixtures.variant(product, 7, "O", false);
        when(variants.findById(7L)).thenReturn(Optional.of(own));
        AdminProductImageDto dto = upload(png("a.png", new byte[] {1}), 7L, null, null);
        assertThat(dto.variantId()).isEqualTo(7L);
        assertThat(dto.s3Key()).contains("/variants/7/");
    }

    @Test
    void unknownProductIs404() {
        status(() -> service.uploadImage(404L, null, png("a.png", new byte[] {1}), null, null, null), HttpStatus.NOT_FOUND);
    }

    // ---- primary / order

    @Test
    void primaryUploadDemotesOnlyOtherActivePrimariesInTheSameScope() {
        ProductImage oldPrimary = Fixtures.image(product, 1, "p.png", 0);
        oldPrimary.setPrimaryImage(true);
        ProductImage deletedPrimary = Fixtures.image(product, 2, "d.png", 0);
        deletedPrimary.setPrimaryImage(true);
        deletedPrimary.setStatus(ProductImageStatus.DELETED);
        ProductVariant variant = Fixtures.variant(product, 7, "V", false);
        ProductImage variantPrimary = Fixtures.variantImage(product, variant, 3, "v.png", 0);
        variantPrimary.setPrimaryImage(true);

        AdminProductImageDto dto = upload(png("n.png", new byte[] {1}), null, 3, true);

        assertThat(dto.primaryImage()).isTrue();
        assertThat(dto.sortOrder()).isEqualTo(3);
        assertThat(oldPrimary.getPrimaryImage()).isFalse();
        assertThat(deletedPrimary.getPrimaryImage()).isTrue();
        assertThat(variantPrimary.getPrimaryImage()).isTrue();
    }

    @Test
    void nonPrimaryUploadLeavesExistingPrimaryAlone() {
        ProductImage oldPrimary = Fixtures.image(product, 1, "p.png", 0);
        oldPrimary.setPrimaryImage(true);
        upload(png("n.png", new byte[] {1}), null, null, false);
        assertThat(oldPrimary.getPrimaryImage()).isTrue();
    }

    @Test
    void negativeSortOrderIsStoredAsGivenNoValidation() {
        assertThat(upload(png("n.png", new byte[] {1}), null, -5, null).sortOrder()).isEqualTo(-5);
    }

    // ---- failure handling

    @Test
    void failedDatabaseSaveRemovesTheUploadedObjectAndRethrows() {
        when(images.save(any(ProductImage.class))).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> upload(png("n.png", new byte[] {1}), null, null, null)).hasMessage("db down");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).delete(key.capture());
        assertThat(key.getValue()).startsWith("products/5/gallery/");
    }

    @Test
    void cleanupFailureDoesNotHideTheOriginalError() {
        when(images.save(any(ProductImage.class))).thenThrow(new IllegalStateException("db down"));
        doThrow(new RuntimeException("s3 down")).when(storage).delete(anyString());
        assertThatThrownBy(() -> upload(png("n.png", new byte[] {1}), null, null, null)).hasMessage("db down");
    }

    @Test
    void failedS3UploadSavesNothing() {
        when(storage.upload(anyString(), any(byte[].class), anyString())).thenThrow(new RuntimeException("s3 down"));
        assertThatThrownBy(() -> upload(png("n.png", new byte[] {1}), null, null, null)).hasMessage("s3 down");
        verify(images, never()).save(any());
    }

    // ---- update

    private ProductImage stored(long id, String key, StorageProvider provider) {
        ProductImage image = Fixtures.image(product, id, "old-url", 1);
        image.setS3Key(key);
        image.setStorageProvider(provider);
        when(images.findById(id)).thenReturn(Optional.of(image));
        return image;
    }

    @Test
    void updateWithoutFileOnlyChangesMetadata() {
        ProductImage image = stored(1, "old/key", StorageProvider.S3);
        service.updateImage(1L, null, "  new alt ", 4, null);
        assertThat(image.getAltText()).isEqualTo("new alt");
        assertThat(image.getSortOrder()).isEqualTo(4);
        verify(storage, never()).upload(anyString(), any(), anyString());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void updateKeepsFieldsThatAreNullAndClearsAltTextWithEmptyString() {
        ProductImage image = stored(1, "k", StorageProvider.S3);
        image.setAltText("keep");
        service.updateImage(1L, null, null, null, null);
        assertThat(image.getAltText()).isEqualTo("keep");
        service.updateImage(1L, null, "", null, null);
        assertThat(image.getAltText()).isEmpty();
    }

    @Test
    void replacingTheFileUploadsFirstThenDeletesTheOldObject() {
        ProductImage image = stored(1, "old/key", StorageProvider.S3);
        AdminProductImageDto dto = service.updateImage(1L, png("new.png", new byte[] {9}), null, null, null);
        assertThat(dto.s3Key()).isNotEqualTo("old/key");
        verify(storage).delete("old/key");
        assertThat(image.getImageUrl()).startsWith("https://cdn/");
    }

    @Test
    void replacingALocalImageDoesNotTouchS3Delete() {
        stored(1, "old/key", StorageProvider.LOCAL);
        service.updateImage(1L, png("new.png", new byte[] {9}), null, null, null);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void oldObjectDeleteFailureDoesNotFailTheUpdate() {
        stored(1, "old/key", StorageProvider.S3);
        doThrow(new RuntimeException("s3")).when(storage).delete("old/key");
        service.updateImage(1L, png("new.png", new byte[] {9}), null, null, null);
    }

    @Test
    void failedSaveAfterReplacementDeletesTheNewObjectNotTheOldOne() {
        stored(1, "old/key", StorageProvider.S3);
        when(images.save(any(ProductImage.class))).thenThrow(new IllegalStateException("db"));
        assertThatThrownBy(() -> service.updateImage(1L, png("new.png", new byte[] {9}), null, null, null)).hasMessage("db");
        verify(storage, never()).delete("old/key");
        verify(storage).delete(org.mockito.ArgumentMatchers.argThat(k -> !k.equals("old/key")));
    }

    @Test
    void nonImageReplacementIsRejectedBeforeAnythingIsUploaded() {
        stored(1, "old/key", StorageProvider.S3);
        status(() -> service.updateImage(1L, new MockMultipartFile("file", "a.txt", "text/plain", new byte[] {1}), null, null, null), HttpStatus.BAD_REQUEST);
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void emptyReplacementFileIsTreatedAsNoFile() {
        stored(1, "old/key", StorageProvider.S3);
        service.updateImage(1L, png("a.png", new byte[0]), "x", null, null);
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void makingAnImagePrimaryDemotesTheOthersButNotItself() {
        ProductImage other = Fixtures.image(product, 2, "o.png", 0);
        other.setPrimaryImage(true);
        ProductImage image = stored(1, "k", StorageProvider.S3);
        service.updateImage(1L, null, null, null, true);
        assertThat(image.getPrimaryImage()).isTrue();
        assertThat(other.getPrimaryImage()).isFalse();
        service.updateImage(1L, null, null, null, false);
        assertThat(image.getPrimaryImage()).isFalse();
    }

    @Test
    void deletedAndUnknownImagesAre404() {
        ProductImage gone = stored(1, "k", StorageProvider.S3);
        gone.setStatus(ProductImageStatus.DELETED);
        status(() -> service.updateImage(1L, null, null, null, null), HttpStatus.NOT_FOUND);
        status(() -> service.getImage(1L), HttpStatus.NOT_FOUND);
        status(() -> service.deleteImage(1L), HttpStatus.NOT_FOUND);
        status(() -> service.getImage(77L), HttpStatus.NOT_FOUND);
    }

    // ---- delete

    @Test
    void deleteMarksPendingThenDeletedAndRemovesTheS3Object() {
        ProductImage image = stored(1, "k", StorageProvider.S3);
        image.setPrimaryImage(true);
        service.deleteImage(1L);
        verify(storage).delete("k");
        assertThat(image.getStatus()).isEqualTo(ProductImageStatus.DELETED);
        assertThat(image.getDeletedAt()).isNotNull();
        assertThat(image.getPrimaryImage()).isFalse();
    }

    @Test
    void s3FailureOnDeleteKeepsTheImageHiddenAsPendingDeleteAndReturns502() {
        ProductImage image = stored(1, "k", StorageProvider.S3);
        doThrow(new RuntimeException("s3 down")).when(storage).delete("k");
        status(() -> service.deleteImage(1L), HttpStatus.BAD_GATEWAY);
        assertThat(image.getStatus()).isEqualTo(ProductImageStatus.PENDING_DELETE);
        assertThat(image.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteTransactionDoesNotRollBackOnTheBadGatewayError() throws Exception {
        Transactional tx = AdminProductImageService.class.getMethod("deleteImage", Long.class).getAnnotation(Transactional.class);
        assertThat(tx.noRollbackFor()).contains(ResponseStatusException.class);
    }

    @Test
    void pendingDeleteImageCanBeRetried() {
        ProductImage image = stored(1, "k", StorageProvider.S3);
        image.setStatus(ProductImageStatus.PENDING_DELETE);
        service.deleteImage(1L);
        assertThat(image.getStatus()).isEqualTo(ProductImageStatus.DELETED);
    }

    @Test
    void localOrKeylessImagesAreDeletedWithoutCallingS3() {
        stored(1, "k", StorageProvider.LOCAL);
        service.deleteImage(1L);
        ProductImage keyless = stored(2, "  ", StorageProvider.S3);
        service.deleteImage(2L);
        assertThat(keyless.getStatus()).isEqualTo(ProductImageStatus.DELETED);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void deletingThePrimaryPromotesTheFirstRemainingImageBySortOrderThenId() {
        ProductImage primary = stored(1, "k", StorageProvider.S3);
        primary.setPrimaryImage(true);
        primary.setSortOrder(0);
        ProductImage late = Fixtures.image(product, 4, "l.png", 5);
        ProductImage second = Fixtures.image(product, 3, "s.png", 2);
        ProductImage first = Fixtures.image(product, 2, "f.png", 2);
        service.deleteImage(1L);
        assertThat(first.getPrimaryImage()).isTrue();
        assertThat(second.getPrimaryImage()).isFalse();
        assertThat(late.getPrimaryImage()).isFalse();
    }

    @Test
    void deletingANonPrimaryImageDoesNotChangeThePrimary() {
        ProductImage keep = Fixtures.image(product, 9, "k.png", 0);
        keep.setPrimaryImage(true);
        ProductImage other = Fixtures.image(product, 8, "o.png", 1);
        stored(1, "k", StorageProvider.S3);
        service.deleteImage(1L);
        assertThat(other.getPrimaryImage()).isFalse();
        assertThat(keep.getPrimaryImage()).isTrue();
    }

    @Test
    void variantPrimaryIsPromotedWithinItsOwnScopeOnly() {
        ProductVariant variant = Fixtures.variant(product, 7, "V", false);
        ProductImage gallery = Fixtures.image(product, 10, "g.png", 0);
        ProductImage doomed = Fixtures.variantImage(product, variant, 1, "v1.png", 0);
        doomed.setPrimaryImage(true);
        doomed.setS3Key("k");
        doomed.setStorageProvider(StorageProvider.S3);
        ProductImage sibling = Fixtures.variantImage(product, variant, 2, "v2.png", 1);
        when(images.findById(1L)).thenReturn(Optional.of(doomed));
        service.deleteImage(1L);
        assertThat(sibling.getPrimaryImage()).isTrue();
        assertThat(gallery.getPrimaryImage()).isFalse();
    }

    // ---- list

    @Test
    void listingHidesRemovedImagesAndSortsBySortOrderThenId() {
        ProductImage b = Fixtures.image(product, 2, "b", 1);
        ProductImage a = Fixtures.image(product, 3, "a", 0);
        ProductImage c = Fixtures.image(product, 1, "c", 1);
        ProductImage gone = Fixtures.image(product, 4, "g", 0);
        gone.setStatus(ProductImageStatus.PENDING_DELETE);
        when(images.findByProductIdOrderBySortOrderAsc(5L)).thenReturn(List.of(gone, b, a, c));
        assertThat(service.listImagesByProduct(5L)).extracting(AdminProductImageDto::id).containsExactly(3L, 1L, 2L);
        status(() -> service.listImagesByProduct(404L), HttpStatus.NOT_FOUND);
    }
}
