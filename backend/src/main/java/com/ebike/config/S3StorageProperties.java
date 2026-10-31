package com.ebike.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public class S3StorageProperties {

    private String bucket = "kinetic-s3-bucket";
    private String region = "ap-southeast-1";
    private String publicBaseUrl = "";
    private String productImagePrefix = "products";

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getProductImagePrefix() {
        return productImagePrefix;
    }

    public void setProductImagePrefix(String productImagePrefix) {
        this.productImagePrefix = productImagePrefix;
    }
}
