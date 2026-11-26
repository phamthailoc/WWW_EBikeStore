package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.config.S3StorageProperties;
import com.ebike.service.product.ProductImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class ProductImageStorageServiceTest {

    private S3Client s3;
    private S3StorageProperties properties;
    private ProductImageStorageService service;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        properties = new S3StorageProperties();
        properties.setBucket("my-bucket");
        properties.setRegion("eu-west-1");
        service = new ProductImageStorageService(s3, properties);
    }

    @Test
    void uploadSendsBucketKeyAndContentTypeAndBuildsDefaultAwsUrl() {
        String url = service.upload("products/1/a b.png", new byte[] {1, 2}, "image/png");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("my-bucket");
        assertThat(captor.getValue().key()).isEqualTo("products/1/a b.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("https://my-bucket.s3.eu-west-1.amazonaws.com/products/1/a%20b.png");
    }

    @Test
    void configuredPublicBaseUrlWinsAndTrailingSlashesAreRemoved() {
        properties.setPublicBaseUrl("  https://cdn.example.com///  ");
        assertThat(service.upload("k/x.png", new byte[] {1}, "image/png")).isEqualTo("https://cdn.example.com/k/x.png");
    }

    @Test
    void blankOrNullPublicBaseUrlFallsBackToAws() {
        properties.setPublicBaseUrl("   ");
        assertThat(service.upload("x.png", new byte[] {1}, "image/png")).startsWith("https://my-bucket.s3.eu-west-1.amazonaws.com/");
        properties.setPublicBaseUrl(null);
        assertThat(service.upload("x.png", new byte[] {1}, "image/png")).startsWith("https://my-bucket.s3.");
    }

    @Test
    void specialCharactersInTheKeyArePercentEncodedButSlashesStay() {
        String url = service.upload("a/b+c/ảnh#1?.png", new byte[] {1}, "image/png");
        assertThat(url).endsWith("/a/b%2Bc/%E1%BA%A3nh%231%3F.png");
    }

    @Test
    void deleteSendsBucketAndKey() {
        service.delete("products/1/a.png");
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("my-bucket");
        assertThat(captor.getValue().key()).isEqualTo("products/1/a.png");
    }

    @Test
    void s3FailuresPropagateToTheCaller() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.upload("k", new byte[] {1}, "image/png")).hasMessage("boom");
    }

    @Test
    void gettersExposeConfiguration() {
        properties.setProductImagePrefix("pics");
        assertThat(service.getBucket()).isEqualTo("my-bucket");
        assertThat(service.getRegion()).isEqualTo("eu-west-1");
        assertThat(service.getProductImagePrefix()).isEqualTo("pics");
    }
}
