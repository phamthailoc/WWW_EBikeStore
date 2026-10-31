package com.ebike.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class S3StoragePropertiesDotenvTest {

    @Test
    void shouldLoadS3PropertiesFromDotenvFile() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(StorageConfiguration.class)
            .web(WebApplicationType.NONE)
            .run()) {

            S3StorageProperties properties = context.getBean(S3StorageProperties.class);

            assertEquals("kinetic-s3-bucket", properties.getBucket());
            assertEquals("ap-southeast-1", properties.getRegion());
        }
    }
}
