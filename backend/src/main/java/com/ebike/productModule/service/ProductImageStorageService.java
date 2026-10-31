package com.ebike.productModule.service;

import com.ebike.config.S3StorageProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ProductImageStorageService {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    public ProductImageStorageService(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public String upload(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .contentType(contentType)
            .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
        return buildPublicUrl(key);
    }

    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .build();

        s3Client.deleteObject(request);
    }

    public String getBucket() {
        return properties.getBucket();
    }

    public String getRegion() {
        return properties.getRegion();
    }

    public String getProductImagePrefix() {
        return properties.getProductImagePrefix();
    }

    private String buildPublicUrl(String key) {
        String configured = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
        String baseUrl = configured.isEmpty()
            ? "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com"
            : configured.replaceAll("/+$", "");

        return baseUrl + "/" + encodeKey(key);
    }

    private String encodeKey(String key) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }
}
